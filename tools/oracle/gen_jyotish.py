#!/usr/bin/env python
"""Jyotish oracle (docs/specs/M02.md section 3.10). Dev tool only; offline; byte-reproducible.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_jyotish.py          (writes the six fixtures)
Check: tools/oracle/.venv/bin/python tools/oracle/gen_jyotish.py --check  (regenerates in memory, byte-compares
       with the committed files, exits non-zero on any difference or missing file)
Writes ayanamsa.json, house_geometry.json, house_angles.json, dasha.json, vargas.json, ingress.json under
engine-golden/src/main/resources/golden/. Reads positions.json (M01 oracle output) for the TT epochs only; never
reads rules/*.json or any engine output. Synthetic instants and places only.
"""
import json
import math
import sys
from fractions import Fraction
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_positions as gp  # noqa: E402
import jyotish_ref as jr  # noqa: E402
import oracle_common as oc  # noqa: E402
import timescale as tsm  # noqa: E402
from gen_riseset import CITIES  # noqa: E402

GOLD = oc.RES / "golden"
SEED = 20261114
GEN_VERSION = 1
DAY_MS = 86_400_000
MS_1900 = tsm._days_from_civil(1900, 1, 1) * DAY_MS
MS_2100 = tsm._days_from_civil(2100, 1, 1) * DAY_MS  # exclusive
MS_1950 = tsm._days_from_civil(1950, 1, 1) * DAY_MS
MS_2050 = tsm._days_from_civil(2050, 1, 1) * DAY_MS

# Typed IAE values, spec section 4 (JD, kind, degrees, minutes, arcsec); quoted from MASTER section 1.
IAE = [(2458484.5, "true", 24, 7, 5.986), (2458849.5, "true", 24, 7, 54.841),
       (2447527.5, "mean", 23, 42, 12.34), (2447892.5, "mean", 23, 43, 2.62)]


def dumps(o):
    return json.dumps(o, sort_keys=True, separators=(",", ":"))


def r12(x):
    return round(float(x), 12)


def meta_of(**kw):
    m = {"generator": "gen_jyotish.py", "genVersion": GEN_VERSION, "schema": 1, "versions": oc.versions()}
    m.update(kw)
    return dumps(m)


def doc(meta, key, lines):
    return '{"meta":' + meta + ',"' + key + '":[\n' + ",\n".join(lines) + "\n]}\n"


def row(*cols):
    return dumps(list(cols))


# -------------------------------------------------------------------------------------------- ayanamsa.json
def gen_ayanamsa(ts, ksha):
    pos = json.loads((GOLD / "positions.json").read_text())
    tt = [r[0] for r in pos["v"][:2000]]
    whole = np.floor(np.array(tt))
    frac = np.array(tt) - whole
    lm, lt = jr.lahiri(ts, whole, frac)
    cm, ct = jr.chitra(ts, whole, frac)
    iae, worst = [], 0.0
    for jd, kind, d, m, s in IAE:
        deg = d + m / 60.0 + s / 3600.0
        w = math.floor(jd)
        mean, true = jr.lahiri(ts, np.array([w]), np.array([jd - w]))
        got = float((true if kind == "true" else mean)[0])
        worst = max(worst, abs(got - deg) * 3600.0)
        if abs(got - deg) * 3600.0 > 0.1:
            oc.die(f"own Lahiri misses IAE row {jd} {kind} by {abs(got - deg) * 3600.0:.4f} arcsec")
        iae.append(row(jd, kind, r12(deg)))
    print(f"ayanamsa: worst own-Lahiri vs IAE = {worst:.4f} arcsec; dpsi(t0) = {jr.dpsi_t0_arcsec(ts):.4f} arcsec")
    grid = [row(tt[i], r12(lm[i]), r12(lt[i]), r12(cm[i]), r12(ct[i])) for i in range(2000)]
    meta = meta_of(kernelSha256=ksha, iaeCount=len(iae), gridCount=len(grid),
                   gridCols=["tt", "lahiriMean", "lahiriTrue", "chitraMean", "chitraTrue"],
                   iaeCols=["jd", "kind", "deg"], source="first 2000 TT epochs of positions.json")
    return '{"meta":' + meta + ',"iae":[\n' + ",\n".join(iae) + '\n],"grid":[\n' + ",\n".join(grid) + "\n]}\n"


# --------------------------------------------------------------------------------------- house_geometry.json
def gen_house_geometry():
    cases = []
    thetas = [0.5 + 3.0 * k for k in range(120)]
    for phi in (-45, -30, -15, 0, 15, 30, 45, 55, 60, 65):
        for eps in (23.43, 23.45):
            cases += [(th, float(phi), eps) for th in thetas]
    for phi in (66, -66, 67, 70, -70):
        cases += [(15.0 * k, float(phi), 23.44) for k in range(24)]
    lines, nplac = [], 0
    for th, phi, eps in cases:
        asc = jr.asc_vec(th, phi, eps)
        mc = jr.mc_bisect(th, eps)
        sc, ss = jr.sripati(asc, mc)
        pl = jr.placidus(th, phi, eps, asc, mc)
        nplac += pl is not None
        lines.append(row(th, phi, eps, r12(asc), r12(mc), [r12(v) for v in sc], [r12(v) for v in ss],
                         None if pl is None else [r12(v) for v in pl]))
    meta = meta_of(count=len(lines), placidusCount=nplac,
                   cols=["thetaDeg", "phiDeg", "epsDeg", "ascDeg", "mcDeg", "sripatiCusps", "sripatiStarts",
                         "placidusCusps"],
                   ascMethod="horizon/ecliptic vector intersection, east-positive", mcMethod="bisection",
                   placidusMethod="semi-arc bisection")
    return doc(meta, "v", lines)


# ----------------------------------------------------------------------------------------- house_angles.json
def gen_house_angles(ts):
    lines = []
    for ci, (name, lat, lon) in enumerate(CITIES):
        rng = np.random.default_rng(SEED + ci)
        ms = np.sort(rng.integers(MS_1900, MS_2100, size=50))
        t = oc.time_from_utc_ms(ts, ms)
        last = (t.gast * 15.0 + lon) % 360.0
        eps = jr.eps_true_deg(t)
        for i in range(50):
            lines.append(row(name, int(ms[i]), r12(last[i]), r12(eps[i])))
    meta = meta_of(count=len(lines), cols=["city", "epochMs", "lastDeg", "epsTrueDeg"], seedBase=SEED,
                   cities=[{"name": n, "lat": la, "lon": lo} for n, la, lo in CITIES],
                   range="1900-01-01..2099-12-31 UTC")
    return doc(meta, "v", lines)


# ---------------------------------------------------------------------------------------------- dasha.json
def _entry(order, birth_ms, p):
    return ["/".join(order[i][0] for i in p[0]), birth_ms + jr.round_half_up(p[1] * DAY_MS),
            birth_ms + jr.round_half_up(p[2] * DAY_MS)]


def _dasha_case(kind, moon, birth_ms, ydays, probes_ms, depth):
    Y = Fraction(repr(ydays))
    order, total, maha = jr.dasha_tree(moon, Y, kind)
    probes = []
    for off in probes_ms:
        xd = Fraction(int(off), DAY_MS)
        ch = jr.chain_at(order, total, maha, xd, depth)
        for p in ch:
            for b in (p[1], p[2]):
                if abs(b * DAY_MS - int(off)) <= 1:
                    oc.die("dasha probe within 1 ms of a boundary")
        probes.append({"ms": birth_ms + int(off), "chain": [_entry(order, birth_ms, p) for p in ch]})
    antar = [_entry(order, birth_ms, c) for mp in (maha[:9] if kind == "VIM" else maha)
             for c in jr.children(order, total, mp)]
    return {"kind": kind, "moon": moon, "birthMs": birth_ms, "yearDays": ydays,
            "maha": [_entry(order, birth_ms, p) for p in maha], "antar": antar, "probes": probes}


def gen_dasha():
    rng = np.random.default_rng(SEED)
    years = [365.25, 365.2425, 360.0]
    nv, ny = 300, 100
    vm = rng.uniform(0.0, 360.0, nv)
    vb = rng.integers(MS_1900, MS_2100, nv)
    vp = rng.integers(0, 29_200 * DAY_MS, (nv, 20))
    ym = rng.uniform(0.0, 360.0, ny)
    yb = rng.integers(MS_1900, MS_2100, ny)
    yp = rng.integers(0, 40_000 * DAY_MS, (ny, 10))
    lines = []
    for i in range(nv):
        lines.append(dumps(_dasha_case("VIM", float(vm[i]), int(vb[i]), years[i // 100], np.sort(vp[i]), 5)))
    for i in range(ny):
        lines.append(dumps(_dasha_case("YOG", float(ym[i]), int(yb[i]), years[i % 3], np.sort(yp[i]), 3)))
    # json.dumps writes floats with repr, so the Moon longitude round-trips exactly.
    meta = meta_of(count=len(lines), vimshottari=nv, yogini=ny, seed=SEED, yearDaysSet=years,
                   vimProbesPerCase=20, yogProbesPerCase=10, vimProbeRangeDays=29200, yogProbeRangeDays=40000,
                   entry="[lordPath, startMs, endMs]; start/end = birthMs + round-half-up(day*86400000)",
                   yoginiRule="VERIFY: i0 = (n+3) mod 8, 32 maha (4 cycles), depth 3")
    return doc(meta, "v", lines)


# -------------------------------------------------------------------------------------------- vargas.json
def gen_vargas():
    rng = np.random.default_rng(SEED + 1)
    lons = [float(x) for x in rng.uniform(0.0, 360.0, 2000)]
    for j in range(720):
        b = 0.5 * j
        lons += [b, (b - 1e-9) % 360.0, (b + 1e-9) % 360.0]
    lines = []
    for lon in lons:
        lines.append(row(lon, [list(jr.varga_pos(v, lon)) for v in jr.VARGA_IDS]))
    meta = meta_of(count=len(lines), seed=SEED + 1, vargas=jr.VARGA_IDS,
                   cols=["lonDeg", "[rashi, part] per varga id; D3J = D3 JAGANNATH, others PARASHARA"])
    return doc(meta, "v", lines)


# ------------------------------------------------------------------------------------------- ingress.json
H = 0.005
GRAHAS = [("JUPITER", 5, 1.0), ("SATURN", 6, 1.0), ("RAHU", 7, 1.0)]  # index into gp.BODIES (RAHU = mean node)


def _sid_lon(eph, ts, ms, idx, dday=0.0):
    """Sidereal (Lahiri true) longitude at integer UTC ms; dday shifts TT by days (for speeds)."""
    t = oc.time_from_utc_ms(ts, np.atleast_1d(ms))
    whole = np.floor(t.whole)
    frac = (t.whole - whole) + t.tt_fraction + dday
    lon, _ = gp.lons_lats(eph, ts, whole, frac, False)
    _, at = jr.lahiri(ts, whole, frac)
    return (lon[idx] - at) % 360.0


def gen_ingress(eph, ts, ksha):
    lines, graze_checked = [], 0
    for name, idx, step_d in GRAHAS:
        step = int(step_d * DAY_MS)
        grid = np.arange(MS_1950, MS_2050 + 1, step, dtype=np.int64)
        lon = np.concatenate([_sid_lon(eph, ts, grid[i:i + 4000], idx) for i in range(0, len(grid), 4000)])
        s = np.minimum(11, np.floor(lon / 30.0)).astype(int)
        # graze: a sample within 1 arcsec of a sign boundary that is a local extremum of g (escalate, never filter)
        bnd = np.round(lon / 30.0) * 30.0
        g = (lon - bnd + 180.0) % 360.0 - 180.0
        for k in range(1, len(g) - 1):
            if abs(g[k]) < 1.0 / 3600.0:
                gm = (lon[k - 1] - bnd[k] + 180.0) % 360.0 - 180.0
                gp_ = (lon[k + 1] - bnd[k] + 180.0) % 360.0 - 180.0
                graze_checked += 1
                if (g[k] - gm) * (gp_ - g[k]) < 0:
                    oc.die(f"ingress graze: {name} sample {k} within 1 arcsec at a local extremum")
        for k in np.nonzero(s[:-1] != s[1:])[0]:
            d = int(s[k + 1] - s[k]) % 12
            if d == 1:
                retro, B = False, 30.0 * int(s[k + 1])
            elif d == 11:
                retro, B = True, 30.0 * int(s[k])
            else:
                oc.die(f"ingress: {name} jumps {d} signs in one step")
            lo, hi = int(grid[k]), int(grid[k + 1])
            glo = (float(lon[k]) - B + 180.0) % 360.0 - 180.0
            while hi - lo > 500:
                mid = (lo + hi) // 2
                gm = (float(_sid_lon(eph, ts, mid, idx)[0]) - B + 180.0) % 360.0 - 180.0
                if (gm < 0) == (glo < 0):
                    lo = mid
                else:
                    hi = mid
            at = (lo + hi + 1) // 2
            vp_ = float(_sid_lon(eph, ts, at, idx, H)[0])
            vm_ = float(_sid_lon(eph, ts, at, idx, -H)[0])
            speed = ((vp_ - vm_ + 180.0) % 360.0 - 180.0) / (2 * H)
            lines.append(row(name, int(s[k + 1]), retro, at, r12(speed), r12(B)))
    meta = meta_of(count=len(lines), kernelSha256=ksha, ayanamsa="LAHIRI", node="MEAN", range="1950-01-01..2050-01-01 UTC",
                   stepDays={"JUPITER": 1, "SATURN": 1, "RAHU": 1}, grazeSamplesChecked=graze_checked,
                   cols=["graha", "into", "retrograde", "epochMs", "speedDegPerDay", "boundaryDeg"])
    return doc(meta, "v", lines)


# -------------------------------------------------------------------------------------------------- driver
def generate():
    eph, ksha = oc.load_kernel()
    ts = oc.timescale()
    return {
        "ayanamsa.json": gen_ayanamsa(ts, ksha),
        "house_geometry.json": gen_house_geometry(),
        "house_angles.json": gen_house_angles(ts),
        "dasha.json": gen_dasha(),
        "vargas.json": gen_vargas(),
        "ingress.json": gen_ingress(eph, ts, ksha),
    }


def main():
    check = "--check" in sys.argv[1:]
    out = generate()
    bad = 0
    for name, text in out.items():
        path = GOLD / name
        data = text.encode("utf-8")
        if check:
            same = path.is_file() and path.read_bytes() == data
            print(f"{'ok  ' if same else 'DIFF'} {name}")
            bad += not same
        else:
            path.write_bytes(data)
            print(f"wrote {path} ({len(data)} bytes)")
    if bad:
        raise SystemExit(f"gen_jyotish --check: {bad} fixture(s) differ from the generator output")


if __name__ == "__main__":
    main()
