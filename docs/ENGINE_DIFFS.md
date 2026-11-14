# Engine differences log

Known, deliberate differences between the engine, its oracle (Skyfield + DE440) and the written specs.
Each entry: what differs, how large, and who decides. Nothing here may be used to loosen a tolerance.

## M01 Astronomy core, [C1]-[C4] (2026-11-14)

### Time scales (spec 3.2)
- **deltaT step at the leap-table horizon. VERIFY / TODO(owner).** The rule is implemented literally: instants in
  `[1972-01-01, validThrough]` use TT = UTC + (TAI-UTC) + 32.184 s; all others are read as UT1 with
  TT = UT1 + deltaT(y). At `validThrough` TT-UT therefore jumps from 69.184 s to about 75.07 s (+5.9 s; the
  2005-2050 polynomial gives 75.074584 s at 2026.0). The oracle (`tools/oracle/timescale.py`) uses the same
  rule, so golden tests agree. In absolute terms the step moves the ephemeris argument by 5.9 s for instants
  after the horizon: about 0.24 arcsec for the Sun and 3.2 arcsec for the Moon (Earth rotation is unaffected,
  the Instant is read as UT1), until the owner decides the blend.
- **`validThrough` is confirmed (2026-11-14).** `TimeTables.DEFAULT.validThrough` = 2027-01-01T00:00:00Z,
  identical to `timescale.VALID_THROUGH` (asserted in `TimeScalesTest`). IERS Bulletin C 72 (2026-07-06) states
  no leap second at the end of December 2026; the KDoc cites it. This is the conservative end of the period
  the bulletin covers. Revisit at Bulletin C 73: changing it means changing both places and regenerating the
  golden data.
- **Decimal year. VERIFY.** y = 2000 + (jdUt - 2451544.5) / 365.2425 (continuous), not Espenak's
  month-midpoint y = year + (month - 0.5) / 12. The two differ by up to half a month, so deltaT differs by up
  to about 0.1 s (largest near 2100, where deltaT grows by about 2.3 s per year).
- **deltaT rows before 1900. VERIFY.** Entered from the published Espenak-Meeus expressions without access to
  the NASA "Polynomial Expressions for Delta T" page (no download approved). Cross-checked over years
  -1000..2300 against Astronomy Engine's independent implementation of the same polynomials
  (`DeltaTCrossCheckTest`, worst difference 1e-5 s = Double resolution of the comparison). Still needs a human
  read against the NASA page. The oracle has no pre-1900 rows (golden data starts 1900-01-01).
- **UT1 = UTC** inside the table (DUT1, under 0.9 s, ignored), per ENGINE.md section 3.
- **TDB - TT** uses the two-term spec formula (0.001657 sin g + 0.000014 sin 2g s); Skyfield uses a longer
  series. The difference is of the order of tens of microseconds (estimate, not measured separately), far
  below 1e-4 arcsec even for the Moon.

### Earth orientation (spec 3.3)
- **Nutation model.** Engine: IAU 2000B (77 terms, port of ERFA `nut00b`). Oracle: IAU 2000A. Measured in a
  scratch run (400 random epochs 1900-2100, not a gate): RAHU_MEAN, whose only non-polynomial input is the
  nutation in longitude, differs by at most 0.00206 arcsec; every other body shows the same common offset.
- **GAST** omits the complementary terms of the equation of the equinoxes (under 0.003 arcsec).
- **ERFA test vectors.** The `EarthOrientationTest` expectations are the values quoted in the spec; they were
  not re-read from ERFA `t_erfa_c.c` (only the `nut00b.c` download was approved). All pass at the spec
  tolerances (1e-12 .. 1e-14 rad).

### DE440 reader and apparent place (spec 3.4, 3.5)
- **Mars, Jupiter, Saturn are system barycentres 4, 5, 6**, as in the oracle. The Jupiter barycentre is up to
  about 0.08 arcsec from the planet centre as seen from Earth; Saturn and Mars are far smaller.
- **Light deflection: Sun only.** Jupiter and Saturn deflection (at most 0.017 arcsec at the limb) is omitted;
  the oracle includes it. Add it with the same formula only if a golden vector needs it. The Sun's position
  for the deflection is taken at the observation instant, not at the photon's closest approach.
- **Earth** is derived from EMB and the Moon segment with the header EMRAT (81.30056822149723), not from a
  separate 3->399 segment; agreement with the kernel is at the rounding level.
- **Time resolution.** A TT Julian Day is one Double (about 40 microseconds). TDB - TT and the light-time are
  passed to the reader as a separate small offset, so they are not quantised.
- **Range.** `De440Ephemeris` throws `EphemerisRangeException` outside TT JD [2415020.5, 2488069.5]; the
  speed's +/- 0.005 d samples at the range ends use the excerpt's margin (file covers 2414988.5..2488104.5 TDB).

### Lunar nodes (spec 3.6). VERIFY both definitions with owner/pandit
- **Mean node polynomial.** Spec 3.6 (implemented) uses the IERS 2003 expression
  125.04455501 deg - 6962890.5431" t + ...; ENGINE.md section 4 quotes Meeus 47.7 (125.0445479 deg - ...).
  They differ by about 0.03 arcsec at J2000 and slowly more away from it. The spec won; ENGINE.md was not edited.
- **Nutation in longitude is added to both nodes** (RAHU_MEAN and RAHU_TRUE), so they are on the true equinox
  of date like the planets.
- **True node = osculating node**: h = r x v of the geometric geocentric Moon (no light-time, no aberration),
  rotated to the mean ecliptic of date without a frame-rate term.
- **Ketu is computed from Rahu**: longitude + 180 deg, identical speed (bit for bit), latitude 0, distance 0.0.

### Scratch cross-check (information only, not a gate)
400 random TT epochs in 1900-2100 against Skyfield 1.55 + de440.bsp with the spec 3.9 recipe (temporary
script and test, not committed): max |d lon| 0.0021 arcsec for all nine bodies (limit 0.05 / 0.1), max
|d lat| 3e-5 arcsec, max |d speed| 6.7e-7 deg/day (limit 1e-5). One year of daily positions for all 11 bodies
took about 5 ms on the JVM (limit 300 ms). The committed gates are G1-G3 and B6, still to be written.

### Base backend: Astronomy Engine (spec 3.7)
- **Moon has no light-time.** `geoVector(Body.Moon, ...)` returns the library's geometric Moon whatever the
  aberration argument is, so the Base Moon leads the apparent one by about 0.7 arcsec in longitude. Used
  literally as the spec says; evaluating the library at tt minus the light-time would remove it (our own
  code, one more library call) if the owner wants it.
- **Frame.** The library's vectors are in the J2000 mean equator frame and are rotated with the section 3.3
  matrix as if they were GCRS (frame bias, under 0.02 arcsec). No light deflection. The library takes TT as
  its ephemeris time (no TDB - TT term, under 0.002 s).
- **Distances** come from the library's truncated series and are not gated: against Precision over 500 epochs
  the relative difference is up to 1.4e-5 (Sun), 1.3e-4 (Moon) and 1.6e-4 (Saturn).
- **Nodes.** RAHU_MEAN is our formula in both backends (identical values). RAHU_TRUE uses the library's
  `geoMoonState`; it differs from Precision by up to about 22 arcsec (provisional gate 60).

### Rise and set (spec 3.8)
- **Aberration before parallax.** The pipeline takes the geocentric apparent place and then subtracts the
  observer. Annual aberration is thus applied for the geocentric direction, not the topocentric one, which
  moves the Moon's altitude by up to 0.37 arcsec against the oracle (the Sun: 0.02 arcsec, mostly diurnal
  aberration, which is ignored). In time that is about 0.03 s for an ordinary moonrise and up to 0.45 s for
  the slowest non-fragile crossings at Tromso.
- **Event instant.** Brent's method runs until the bracket is 0.5 s wide; the reported instant is the chord
  crossing inside that bracket (milliseconds from the root of our own f).
- **Sampling.** Crossings are found from the sign of f at 600 s steps, as in the oracle: a body that rises and
  sets again between two samples is not reported.
- **Thresholds.** Sun semidiameter fixed at 16 arcmin; Moon semidiameter asin(1737.4 km / topocentric
  distance); refraction 34 arcmin x (P / 1010) x (283 / (273 + T)) (non-default scaling is VERIFY).
  CENTER_GEOMETRIC is topocentric too. Elevation raises the observer; horizon dip is not modelled.
- **High-latitude fallback** (`sunWithFallback`, VERIFY with pandit): see `docs/DECISIONS.md` (M01 C6).

### Scratch pre-flight against the committed golden JSON (information only, not a gate)
Temporary test, not committed, run on 2026-11-14 against `positions.json` (10,000 epochs) and `riseset.json`
(4,000 city-days, 45,738 non-fragile events, 38 fragile entries). The committed gates are G2/G3 (B5).
- Precision positions: max |d lon| 0.0025 arcsec for all nine bodies, |d lat| 0.0004, |d speed| 1.6e-6 deg/day.
- Base positions, max |d lon| / |d lat| arcsec: Sun 2.2 / 2.0, Moon 4.5 / 1.4, Mercury 12.2 / 8.7, Venus
  20.1 / 10.8, Mars 13.0 / 15.2, Jupiter 10.1 / 13.8, Saturn 12.9 / 21.8, mean node 0.0025, true node 21.5;
  max |d speed| 3.0e-3 deg/day (true node).
- Rise/set, Precision: Sun 0.002 s, Moon 0.45 s; kinds and counts equal on every non-fragile entry; fragile
  residual at most 0.33 arcsec. Base: Sun 1.04 s, Moon 1.42 s; kinds and counts equal on every non-fragile
  entry; fragile residual at most 1.42 arcsec (one fragile entry differs in event count, which is not compared).
- Correction to "Aberration before parallax" above: the Sun's 0.02 arcsec altitude residual (opposite sign at
  rise and set) was measured, but its cause was not isolated; read "mostly diurnal aberration" as a guess.
