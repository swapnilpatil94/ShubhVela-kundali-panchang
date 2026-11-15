#!/usr/bin/env python
"""Sky geometry oracle (docs/specs/M07R.md section 6 "Sky geometry", task B5). Dev tool only.

Run:   tools/oracle/.venv/bin/python -I tools/oracle/gen_sky.py
Check: tools/oracle/.venv/bin/python -I tools/oracle/gen_sky.py --check  (regenerates in memory, byte-compares
       with the committed fixture, writes nothing, exits non-zero on a difference)
Writes engine-golden/src/main/resources/golden/sky_geometry.json. Deterministic: fixed seed, sorted epochs, no
timestamps. Times are integer UTC epoch milliseconds; TT and UT1 follow the engine rule (timescale.py).

Everything is Skyfield's own geometry (no code shared with the engine, no Meeus formulas):
  body         elongation  = separation of the apparent (aberrated, deflected) geocentric places of body and Sun
               phase angle = angle at the body between the light leaving for Earth and the light arriving from
                             the Sun: v1 = -(earth.at(t).observe(body)) astrometric, v2 = body.at(t - tau)
                             .observe(sun) astrometric (light-time both legs, no aberration, no deflection)
               k           = (1 + cos i) / 2
               chi         = skyfield.trigonometry.position_angle_of(body, sun) on apparent RA/Dec of date
  ring         B           = asin(u . p): u = unit vector Saturn -> Earth (astrometric, ICRF), p = Saturn's
                             north pole (IAU WGCCRE 2009, ICRF): alpha0 = 40.589 - 0.036 T, delta0 = 83.537 -
                             0.004 T deg, T = Julian centuries of TDB from J2000.0 (Archinal et al. 2011,
                             Celest. Mech. Dyn. Astr. 109, 101-135, table 2; same values as the 2006/2015 reports)
  parallactic  q           = position_angle_of(body, zenith): zenith = (RA = GAST + east longitude, Dec =
                             geodetic latitude), apparent geocentric RA/Dec of date of the body; z = angle
                             body-zenith (stored so that the test can weight q by sin z)
Rows (one per line):
  ["body", utcMs, BODY, elongationDeg, phaseAngleDeg, k, chiDeg]
  ["ring", utcMs, BDeg]
  ["parallactic", utcMs, placeIndex, "MOON", qDeg, zDeg]
"""
import json
import sys
from pathlib import Path

import numpy as np
from skyfield.trigonometry import position_angle_of
from skyfield.units import Angle

sys.path.insert(0, str(Path(__file__).resolve().parent))
import oracle_common as oc  # noqa: E402
import timescale as tsm  # noqa: E402

OUT = oc.RES / "golden" / "sky_geometry.json"
SEED = 20261115
N_RANDOM = 2000
# Fixed epochs (UTC): the dates of Meeus examples 45.a and 48.a (oracle values only, never the book's) and
# 2026-11-15 at Pune sunrise-ish / evening.
FIXED = [(1992, 12, 16, 0), (1992, 4, 12, 0), (2026, 11, 15, 1), (2026, 11, 15, 14)]
BODIES = [("MOON", "moon"), ("MERCURY", "mercury"), ("VENUS", "venus"), ("MARS", "mars barycenter"),
          ("JUPITER", "jupiter barycenter"), ("SATURN", "saturn barycenter")]
PLACES = [("pune", 18.5204, 73.8567), ("delhi", 28.6139, 77.2090), ("quito", -0.1807, -78.4678),
          ("london", 51.5074, -0.1278), ("newyork", 40.7128, -74.0060), ("auckland", -36.8485, 174.7633),
          ("ushuaia", -54.8019, -68.3030), ("tromso", 69.6492, 18.9553)]
POLE = {"alpha0Deg": [40.589, -0.036], "delta0Deg": [83.537, -0.004],
        "source": "IAU WGCCRE 2009 (Archinal et al. 2011, CMDA 109:101, table 2)", "T": "Julian centuries TDB"}
CHUNK = 1000
DAY_MS = 86_400_000


def epochs():
    rng = np.random.default_rng(SEED)
    lo = tsm._days_from_civil(1900, 1, 2) * DAY_MS
    hi = tsm._days_from_civil(2099, 12, 30) * DAY_MS
    ms = rng.integers(lo, hi, N_RANDOM, dtype=np.int64)
    fixed = [tsm._days_from_civil(y, m, d) * DAY_MS + h * 3_600_000 for y, m, d, h in FIXED]
    return np.sort(np.concatenate([ms, np.array(fixed, dtype=np.int64)]))


def unit(v):
    return v / np.sqrt((v * v).sum(axis=0))


def angle_between(a, b):
    """Angle between column vectors, radians (atan2 of |a x b| and a . b: accurate at 0 and pi)."""
    c = np.cross(a.T, b.T).T
    return np.arctan2(np.sqrt((c * c).sum(axis=0)), (a * b).sum(axis=0))


def wrap180(x):
    return (x + 180.0) % 360.0 - 180.0


def chunk_rows(eph, ts, ms):
    t = oc.time_from_utc_ms(ts, ms)
    earth, sun = eph["earth"], eph["sun"]
    e_at = earth.at(t)
    sun_app = e_at.observe(sun).apparent()
    sun_radec = sun_app.radec("date")
    out = {"body": {}, "ring": None, "par": []}
    moon_radec = None
    for name, key in BODIES:
        target = eph[key]
        pe = e_at.observe(target)
        app = pe.apparent()
        elong = app.separation_from(sun_app).degrees
        t2 = ts.tt_jd(t.whole, t.tt_fraction - pe.light_time)
        ps = target.at(t2).observe(sun)
        i = angle_between(-pe.xyz.au, ps.xyz.au)
        k = 0.5 * (1.0 + np.cos(i))
        radec = app.radec("date")
        chi = position_angle_of(radec, sun_radec).degrees
        out["body"][name] = (elong, np.degrees(i), k, chi)
        if name == "MOON":
            moon_radec = radec
        if name == "SATURN":
            tcen = (t.tdb - 2451545.0) / 36525.0
            a0 = np.radians(POLE["alpha0Deg"][0] + POLE["alpha0Deg"][1] * tcen)
            d0 = np.radians(POLE["delta0Deg"][0] + POLE["delta0Deg"][1] * tcen)
            p = np.array([np.cos(d0) * np.cos(a0), np.cos(d0) * np.sin(a0), np.sin(d0)])
            u = unit(-pe.xyz.au)
            out["ring"] = np.degrees(np.arcsin((u * p).sum(axis=0)))
    ra, dec = moon_radec[0], moon_radec[1]
    for _, lat, lon in PLACES:
        zen = (Angle(degrees=np.full(ms.size, lat)), Angle(hours=(t.gast + lon / 15.0) % 24.0))
        q = wrap180(position_angle_of(moon_radec, zen).degrees)
        h = zen[1].radians - ra.radians
        phi = np.radians(lat)
        cz = np.sin(phi) * np.sin(dec.radians) + np.cos(phi) * np.cos(dec.radians) * np.cos(h)
        out["par"].append((q, np.degrees(np.arccos(np.clip(cz, -1.0, 1.0)))))
    return out


def r12(x):
    return round(float(x), 12)


def generate():
    eph, ksha = oc.load_kernel()
    ts = oc.timescale()
    ms_all = epochs()
    lines = []
    counts = {"epochs": int(ms_all.size), "body": 0, "ring": 0, "parallactic": 0}
    for c0 in range(0, ms_all.size, CHUNK):
        ms = ms_all[c0:c0 + CHUNK]
        r = chunk_rows(eph, ts, ms)
        for j, m in enumerate(ms.tolist()):
            for name, _ in BODIES:
                e, i, k, chi = r["body"][name]
                lines.append(json.dumps(["body", m, name, r12(e[j]), r12(i[j]), r12(k[j]), r12(chi[j])],
                                        separators=(",", ":")))
                counts["body"] += 1
            lines.append(json.dumps(["ring", m, r12(r["ring"][j])], separators=(",", ":")))
            counts["ring"] += 1
            for pi, (q, z) in enumerate(r["par"]):
                lines.append(json.dumps(["parallactic", m, pi, "MOON", r12(q[j]), r12(z[j])],
                                        separators=(",", ":")))
                counts["parallactic"] += 1
    meta = {"schema": 1, "seed": SEED, "versions": oc.versions(), "kernelSha256": ksha, "counts": counts,
            "bodies": [b for b, _ in BODIES], "places": [{"name": n, "lat": a, "lon": o} for n, a, o in PLACES],
            "saturnPole": POLE, "fixedUtc": ["%04d-%02d-%02dT%02d:00Z" % f for f in FIXED],
            "range": [int(ms_all[0]), int(ms_all[-1])]}
    return '{"meta":' + json.dumps(meta, sort_keys=True, separators=(",", ":")) + ',"rows":[\n' + \
        ",\n".join(lines) + "\n]}\n"


def main():
    check = "--check" in sys.argv[1:]
    data = generate().encode("utf-8")
    if check:
        same = OUT.is_file() and OUT.read_bytes() == data
        print(f"{'ok  ' if same else 'DIFF'} {OUT.name}")
        if not same:
            raise SystemExit("gen_sky --check: fixture differs from the generator output")
    else:
        OUT.write_bytes(data)
        print(f"wrote {OUT} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
