#!/usr/bin/env python
"""Eclipse oracle (docs/specs/M04.md sections 3.9, 3.10). Dev tool only; offline; byte-reproducible.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_eclipses.py          (writes eclipses.json)
Check: tools/oracle/.venv/bin/python tools/oracle/gen_eclipses.py --check  (regenerates in memory, byte-compares
       with the committed file, exits non-zero on any difference or missing file)
Global eclipses 2000-2050 (greatest in [2000-01-01, 2051-01-01)) plus local circumstances at 12 fixed sites. No
randomness: every syzygy with |beta_M| <= 2 deg is evaluated, nothing is filtered, reseeded or relabelled.
Exits non-zero, writing nothing, if the fragile (Base) share exceeds 5 % or the coverage minimums fail.
Never reads rules/*.json, Kotlin or engine output. One worker process per site.
"""
import json
import sys
from multiprocessing import Pool
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eclipse_ref as er  # noqa: E402
import gen_riseset as gr  # noqa: E402
import oracle_common as oc  # noqa: E402
import timescale as tsm  # noqa: E402

GOLD = oc.RES / "golden"
OUT = "eclipses.json"
GEN_VERSION = 1
FRAGILE_CAP = 0.05
DELTA_P, DELTA_B = 0.001, 0.02          # magnitude margins (Precision / Base)
TAU_P_MS, TAU_B_MS = 30_000, 150_000    # horizon-crossing margins
SLIVER_DEG = 0.1                        # closest approach to the horizon counted as fragile visibility
SUTAK_SOLAR_MS, SUTAK_LUNAR_MS = 43_200_000, 32_400_000
NEAR_MISS = DELTA_B                     # non-eclipses within this margin are kept as (fragile) rows
Y0, Y1 = 2000, 2051                     # greatest in [Y0-01-01, Y1-01-01)

_C = {c[0]: c for c in gr.CITIES}
SITES = [  # name, lat, lon, offset minutes (fixed standard offsets, spec 3.10)
    ("delhi", _C["delhi"][1], _C["delhi"][2], 330), ("mumbai", _C["mumbai"][1], _C["mumbai"][2], 330),
    ("chennai", _C["chennai"][1], _C["chennai"][2], 330), ("kolkata", _C["kolkata"][1], _C["kolkata"][2], 330),
    ("newyork", _C["newyork"][1], _C["newyork"][2], -300), ("london", _C["london"][1], _C["london"][2], 0),
    ("sydney", -33.8688, 151.2093, 600), ("auckland", _C["auckland"][1], _C["auckland"][2], 720),
    ("suva", _C["suva"][1], _C["suva"][2], 720), ("tromso", _C["tromso"][1], _C["tromso"][2], 60),
    ("dallas", 32.7767, -96.7970, -360), ("thiruvananthapuram", 8.5241, 76.9366, 330),
]
KINDS = ["LUNAR_PENUMBRAL", "LUNAR_PARTIAL", "LUNAR_TOTAL", "SOLAR_PARTIAL", "SOLAR_ANNULAR", "SOLAR_TOTAL"]


def dumps(o):
    return json.dumps(o, sort_keys=True, separators=(",", ":"))


def ms_of(y, m, d):
    return tsm._days_from_civil(y, m, d) * 86_400_000


def utc_str(ms):
    d, r = divmod(int(ms), 86_400_000)
    y = 1970
    # civil from days (inverse of timescale._days_from_civil), proleptic Gregorian
    z = d + 719468
    era = z // 146097
    doe = z - era * 146097
    yoe = (doe - doe // 1460 + doe // 36524 - doe // 146096) // 365
    yy = yoe + era * 400
    doy = doe - (365 * yoe + yoe // 4 - yoe // 100)
    mp = (5 * doy + 2) // 153
    dd = doy - (153 * mp + 2) // 5 + 1
    mm = mp + (3 if mp < 10 else -9)
    yy += mm <= 2
    s = r // 1000
    return f"{yy:04d}-{mm:02d}-{dd:02d} {s // 3600:02d}:{s // 60 % 60:02d}:{s % 60:02d}Z"


def find_contacts(gfun, centers, codes, half_h):
    """Roots of g (codes 0.. per task) either side of each centre (g < 0 there): (left_ms, right_ms, rateL, rateR)."""
    centers = np.asarray(centers, dtype=np.int64)
    codes = np.asarray(codes, dtype=int)
    t = centers.size
    if t == 0:
        return []
    n = half_h * 6
    off = np.arange(-n, n + 1, dtype=np.int64) * er.GRID_MS
    grid = centers[:, None] + off[None, :]
    g = gfun(grid.ravel(), np.repeat(codes, off.size)).reshape(t, 2 * n + 1)
    if not (g[:, n] < 0).all():
        oc.die("contact search: centre not inside the threshold")
    ge_r = g[:, n:] >= 0
    ge_l = g[:, :n + 1][:, ::-1] >= 0
    if not (ge_r.any(1).all() and ge_l.any(1).all()):
        oc.die("contact search: no crossing within the grid")
    kr, kl = ge_r.argmax(1), ge_l.argmax(1)
    rows = np.arange(t)
    right = er.bisect_root(gfun, grid[rows, n + kr - 1], grid[rows, n + kr], codes, np.ones(t, dtype=bool))
    left = er.bisect_root(gfun, grid[rows, n - kl], grid[rows, n - kl + 1], codes, np.zeros(t, dtype=bool))
    rl, rr = er.rate_arcsec(gfun, left, codes), er.rate_arcsec(gfun, right, codes)
    return [(int(a), int(b), float(c), float(d)) for a, b, c, d in zip(left, right, rl, rr)]


# ---------------------------------------------------------------------------------------------- global stage
def lunar_kind(um, pm):
    if pm <= 0:
        return None
    return "LUNAR_TOTAL" if um >= 1 else ("LUNAR_PARTIAL" if um > 0 else "LUNAR_PENUMBRAL")


def global_stage():
    eph, ksha = oc.load_kernel()
    ts = oc.timescale()
    geo = er.Geo(eph, ts)
    a, b = ms_of(Y0 - 1, 12, 1), ms_of(Y1, 2, 1)
    new = geo.syzygies(a, b, False)
    full = geo.syzygies(a, b, True)
    cand = np.concatenate([new, full])
    solar = np.concatenate([np.ones(new.size, bool), np.zeros(full.size, bool)])
    _, _, beta = geo.ecliptic(cand)
    keep = np.abs(beta) <= er.BETA_MAX_DEG
    cand, solar = cand[keep], solar[keep]
    nsyz = int(new.size + full.size)
    n = er.LUNAR_HALF_H * 6
    off = np.arange(-n, n + 1, dtype=np.int64) * er.GRID_MS
    grid = cand[:, None] + off[None, :]
    v = geo.vals(grid.ravel())
    gam = np.where(solar[:, None], v["gs"].reshape(grid.shape), v["gl"].reshape(grid.shape))
    k = gam.argmin(1)
    if not ((k > 0) & (k < 2 * n)).all():
        oc.die("greatest-eclipse bracket at the grid edge")
    rows_i = np.arange(cand.size)

    def gfun(ms):
        vv = geo.vals(ms)
        return np.where(solar_sel[0], vv["gs"], vv["gl"])

    solar_sel = [solar]
    gt = er.golden_min(gfun, grid[rows_i, k - 1], grid[rows_i, k + 1])
    g0 = np.rint(gt).astype(np.int64)
    lo_ms, hi_ms = ms_of(Y0, 1, 1), ms_of(Y1, 1, 1)
    sel = (g0 >= lo_ms) & (g0 < hi_ms)
    g0, solar = g0[sel], solar[sel]
    vg = geo.vals(g0)
    vm = geo.vals(g0 - 60_000)
    vp = geo.vals(g0 + 60_000)
    ru, rp = geo.rho(vg)
    gam_g = np.where(solar, vg["gs"], vg["gl"])
    curv = (np.where(solar, vp["gs"], vp["gl"]) + np.where(solar, vm["gs"], vm["gl"]) - 2 * gam_g) / 3600.0 * er.RAD_ARCSEC
    um = (ru + vg["sM"] - vg["gl"]) / (2 * vg["sM"])
    pm = (rp + vg["sM"] - vg["gl"]) / (2 * vg["sM"])
    limit = vg["pM"] - vg["pS"] + vg["sM"] + vg["sS"]
    sm = (limit - vg["gs"]) / (2 * vg["sS"])
    marg = np.where(solar, sm, pm)
    use = marg > -NEAR_MISS
    idx = np.nonzero(use)[0]
    ecl = marg > 0
    # lunar contacts
    lun = [i for i in idx if (not solar[i]) and ecl[i]]
    tasks, tcodes, towner = [], [], []
    for i in lun:
        for c in ((0,) + ((1,) if um[i] > 0 else ()) + ((2,) if um[i] >= 1 else ())):
            tasks.append(int(g0[i]))
            tcodes.append(c)
            towner.append(int(i))
    res = find_contacts(geo.g_lunar, tasks, tcodes, er.LUNAR_HALF_H)
    names = {0: ("P1", "P4"), 1: ("U1", "U4"), 2: ("U2", "U3")}
    cont = {}
    for (l, r, rl, rr), c, i in zip(res, tcodes, towner):
        cont.setdefault(i, []).append((names[c][0], l, rl))
        cont.setdefault(i, []).append((names[c][1], r, rr))
    order = {"P1": 0, "U1": 1, "U2": 2, "U3": 3, "U4": 4, "P4": 5}
    rows = []
    for i in idx:
        i = int(i)
        sol = bool(solar[i])
        e = bool(ecl[i])
        kind = (lunar_kind(float(um[i]), float(pm[i])) if (e and not sol) else None)
        cs = sorted(cont.get(i, []), key=lambda x: order[x[0]])
        fp, fb = [], []
        for dlt, lst in ((DELTA_P, fp), (DELTA_B, fb)):
            if sol:
                if abs(sm[i]) < dlt:
                    lst.append("kind")
            elif abs(um[i]) < dlt or abs(um[i] - 1) < dlt or abs(pm[i]) < dlt:
                lst.append("kind")
            lst.extend("C:" + c for c, _, r in cs if r < er.CONTACT_RATE_ARCSEC)
        rows.append([int(sol), int(g0[i]), round(float(np.degrees(gam_g[i])), 9), kind,
                     None if sol else round(float(um[i]), 8), None if sol else round(float(pm[i]), 8),
                     [[c, t, round(r, 4)] for c, t, r in cs], round(float(curv[i]), 6), int(e), fp, fb,
                     round(float(sm[i]), 8) if sol else None])
    rows.sort(key=lambda r: (r[1], r[0]))
    return rows, ksha, nsyz, int(cand.size)


# ---------------------------------------------------------------------------------------------- local stage
def vis_info(ev, body, ia, ib, kind_has_vis=True):
    vs, ve, cr, ext = er.visibility(ev, ia, ib, body)
    fp, fb = [], []
    for tau, lst in ((TAU_P_MS, fp), (TAU_B_MS, fb)):
        if any(abs(c - ia) <= tau or abs(c - ib) <= tau for c in cr) or (ext == ext and abs(ext) < SLIVER_DEG):
            lst.append("vis")
    return vs, ve, fp, fb


def site_task(args):
    si, grows = args
    name, lat, lon, off = SITES[si]
    eph, _ = oc.load_kernel()
    ts = oc.timescale()
    geo = er.Geo(eph, ts)
    ev = er.SiteEv(eph, ts, lat, lon)
    out = []
    # ---- solar
    sidx = [i for i, r in enumerate(grows) if r[0] == 1 and r[8] == 1]
    gms = np.array([grows[i][1] for i in sidx], dtype=np.int64)
    n = er.SOLAR_HALF_H * 6
    offs = np.arange(-n, n + 1, dtype=np.int64) * er.GRID_MS
    grid = gms[:, None] + offs[None, :]
    sg = ev.sigma(grid.ravel()).reshape(grid.shape)
    k = sg.argmin(1)
    k = np.clip(k, 1, 2 * n - 1)
    ri = np.arange(gms.size)
    tmax = er.golden_min(ev.sigma, grid[ri, k - 1], grid[ri, k + 1])
    vv = ev.vals(tmax)
    mag = (vv["sS"] + vv["sM"] - vv["sig"]) / (2 * vv["sS"])
    keepi = [j for j in range(gms.size) if mag[j] > -NEAR_MISS]
    cj = [j for j in keepi if mag[j] > 0]
    tasks, tcodes, towner = [], [], []
    for j in cj:
        central = vv["sig"][j] <= abs(vv["sM"][j] - vv["sS"][j])
        for c in ((0,) + ((1,) if central else ())):
            tasks.append(int(tmax[j]))
            tcodes.append(c)
            towner.append(j)
    res = find_contacts(ev.g_solar, tasks, tcodes, er.SOLAR_HALF_H)
    names = {0: ("C1", "C4"), 1: ("C2", "C3")}
    cont = {}
    for (l, r, rl, rr), c, j in zip(res, tcodes, towner):
        cont.setdefault(j, []).append((names[c][0], l, rl))
        cont.setdefault(j, []).append((names[c][1], r, rr))
    order = {"C1": 0, "C2": 1, "C3": 2, "C4": 3}
    for j in keepi:
        m = float(mag[j])
        sig, sS, sM = float(vv["sig"][j]), float(vv["sS"][j]), float(vv["sM"][j])
        cs = sorted(cont.get(j, []), key=lambda x: order[x[0]])
        kind = None
        vs = ve = ss = se = None
        fp, fb = [], []
        if m > 0:
            if sM > sS and sig <= sM - sS:
                kind = "SOLAR_TOTAL"
            elif sM < sS and sig <= sS - sM:
                kind = "SOLAR_ANNULAR"
            else:
                kind = "SOLAR_PARTIAL"
            c1 = next(t for c, t, _ in cs if c == "C1")
            c4 = next(t for c, t, _ in cs if c == "C4")
            vs, ve, vfp, vfb = vis_info(ev, 0, c1, c4)
            fp += vfp
            fb += vfb
            if vs is not None:
                ss, se = c1 - SUTAK_SOLAR_MS, c4
        for dlt, lst in ((DELTA_P, fp), (DELTA_B, fb)):
            two_s = 2 * sS
            if abs(m) < dlt or abs(m - 1) < dlt or abs(sig - abs(sM - sS)) < dlt * two_s:
                lst.append("kind")
            lst.extend("C:" + c for c, _, r in cs if r < er.CONTACT_RATE_ARCSEC)
        a = er.RAD_ARCSEC
        out.append([sidx[j], kind, round(m, 8), int(tmax[j]), [[c, t, round(r, 4)] for c, t, r in cs], vs, ve, ss, se,
                    fp, fb, [round(sig * a, 4), round(sS * a, 4), round(sM * a, 4)]])
    # ---- lunar (global geometry; contacts live in the global rows)
    for i, r in enumerate(grows):
        if r[0] == 0 and r[8] == 1:
            kind = r[3]
            cmap = {c: t for c, t, _ in r[6]}
            ia, ib = (cmap["P1"], cmap["P4"]) if kind == "LUNAR_PENUMBRAL" else (cmap["U1"], cmap["U4"])
            vs, ve, fp, fb = vis_info(ev, 1, ia, ib)
            # kind/magnitude/contact fragility of a lunar eclipse is a property of the global geometry and lives
            # in the global row (reached through globalIndex); the local row only marks its own visibility.
            ss = se = None
            if vs is not None and kind != "LUNAR_PENUMBRAL":
                ss, se = cmap["U1"] - SUTAK_LUNAR_MS, cmap["U4"]
            mag_l = r[5] if kind == "LUNAR_PENUMBRAL" else r[4]
            out.append([i, kind, mag_l, r[1], [], vs, ve, ss, se, fp, fb, None])
    out.sort(key=lambda r: (r[3], r[0]))
    nlost = int(sum(1 for r in out if r[1] is None))
    return dict(name=name, rows=out, nonEclipse=nlost)


# ---------------------------------------------------------------------------------------------- assemble
def generate():
    grows, ksha, nsyz, ncand = global_stage()
    with Pool(len(SITES)) as pool:
        sites = pool.map(site_task, [(i, grows) for i in range(len(SITES))])
    gcount = {k: 0 for k in KINDS[:3]}
    gcount.update(SOLAR=0)
    for r in grows:
        if r[8] == 1:
            if r[0] == 1:
                gcount["SOLAR"] += 1
            else:
                gcount[r[3]] += 1
    nonecl_g = sum(1 for r in grows if r[8] == 0)
    lcount = {k: 0 for k in KINDS}
    for s in sites:
        for r in s["rows"]:
            if r[1]:
                lcount[r[1]] += 1
    nloc = sum(len(s["rows"]) for s in sites)
    nloc_non = sum(s["nonEclipse"] for s in sites)
    total = len(grows) + nloc
    fragB = sum(1 for r in grows if r[10]) + sum(1 for s in sites for r in s["rows"] if r[10])
    fragP = sum(1 for r in grows if r[9]) + sum(1 for s in sites for r in s["rows"] if r[9])
    print(f"syzygies {nsyz}, |beta|<=2 candidates {ncand}; global rows {len(grows)} (eclipses "
          f"{len(grows) - nonecl_g}, near-miss non-eclipses {nonecl_g}) {gcount}")
    print(f"local rows {nloc} (eclipses {nloc - nloc_non}, near-miss non-eclipses {nloc_non}) {lcount}")
    share_b, share_p = fragB / total, fragP / total
    tags = {}
    for r in list(grows) + [r for s in sites for r in s["rows"]]:
        for t in set(x.split(":")[0] for x in r[10] if True):
            tags[t] = tags.get(t, 0) + 1
    print(f"fragile Base rows by tag {tags}")
    print(f"fragile Base {fragB}/{total} = {100 * share_b:.3f} % (cap {100 * FRAGILE_CAP:.0f} %), "
          f"Precision {fragP}/{total} = {100 * share_p:.3f} %")
    bad = []
    if share_b > FRAGILE_CAP:
        bad.append(f"fragile Base share {100 * share_b:.3f} % > {100 * FRAGILE_CAP:.0f} %")
    # coverage: dallas 2024-04-08 total, thiruvananthapuram 2010-01-15 annular, every lunar kind
    d0, d1 = ms_of(2024, 4, 8), ms_of(2024, 4, 9)
    t0, t1 = ms_of(2010, 1, 15), ms_of(2010, 1, 16)
    dal = next(s for s in sites if s["name"] == "dallas")
    tvm = next(s for s in sites if s["name"] == "thiruvananthapuram")
    if not any(r[1] == "SOLAR_TOTAL" and d0 <= r[3] < d1 for r in dal["rows"]):
        bad.append("no local SOLAR_TOTAL for dallas 2024-04-08")
    if not any(r[1] == "SOLAR_ANNULAR" and t0 <= r[3] < t1 for r in tvm["rows"]):
        bad.append("no local SOLAR_ANNULAR for thiruvananthapuram 2010-01-15")
    for k in KINDS[:3]:
        if not any(r[3] == k and not r[9] for r in grows):
            bad.append(f"no non-fragile global {k}")
    for k in ("SOLAR_PARTIAL", "SOLAR_ANNULAR", "SOLAR_TOTAL"):
        if lcount[k] == 0:
            bad.append(f"no local {k}")
    for s in sites:
        print(f"  {s['name']:20s} rows {len(s['rows'])}")
    # sanity lines (not gates)
    g24 = next(r for r in grows if r[0] == 1 and r[8] == 1 and d0 <= r[1] < d1)
    l22 = next(r for r in grows if r[0] == 0 and r[8] == 1 and ms_of(2022, 11, 8) <= r[1] < ms_of(2022, 11, 9))
    print(f"sanity solar 2024-04-08 geocentric greatest {utc_str(g24[1])}, gamma {g24[2]:.4f} deg")
    print(f"sanity lunar 2022-11-08 greatest {utc_str(l22[1])} {l22[3]} umbral {l22[4]} contacts "
          f"{[(c, utc_str(t)) for c, t, _ in l22[6]]}")
    dl = next(r for r in dal["rows"] if r[1] == "SOLAR_TOTAL" and d0 <= r[3] < d1)
    print(f"sanity dallas 2024-04-08 max {utc_str(dl[3])} mag {dl[2]} C2 {utc_str(dl[4][1][1])} C3 "
          f"{utc_str(dl[4][2][1])}")
    if bad:
        oc.die("; ".join(bad) + "; nothing written")
    smeta = [{"name": n, "lat": la, "lon": lo, "offsetMin": o, "rows": len(s["rows"]), "nonEclipseRows": s["nonEclipse"]}
             for (n, la, lo, o), s in zip(SITES, sites)]
    meta = {"generator": "gen_eclipses.py", "genVersion": GEN_VERSION, "schema": 1, "versions": oc.versions(),
            "kernelSha256": ksha, "seed": None, "randomness": "none (every |beta_M| <= 2 deg syzygy evaluated)",
            "range": [f"{Y0}-01-01", f"{Y1}-01-01"], "sites": smeta,
            "constants": {"R_E_km": er.R_E, "R_M_km": er.R_M, "R_S_km": er.R_S, "AU_km": er.AU_KM,
                          "enlargement": "1+1/85 on pi_M", "earth": "spherical (shadow), WGS84 observer (local)",
                          "betaMaxDeg": er.BETA_MAX_DEG},
            "counts": {"syzygies": nsyz, "candidates": ncand, "globalRows": len(grows), "globalEclipses": gcount,
                       "globalNearMiss": nonecl_g, "localRows": nloc, "localEclipses": lcount,
                       "localNearMiss": nloc_non},
            "fragile": {"capShare": FRAGILE_CAP, "baseRows": fragB, "precisionRows": fragP, "rows": total,
                        "deltaPrecision": DELTA_P, "deltaBase": DELTA_B, "tauPrecisionSeconds": 30,
                        "tauBaseSeconds": 150, "contactRateArcsecPerSecond": er.CONTACT_RATE_ARCSEC,
                        "sliverDeg": SLIVER_DEG,
                        "tags": "kind (magnitude/centrality margin), vis (horizon crossing near interval end), "
                                "C:<contact> (contact rate below the limit; both levels)"},
            "grid": {"greatestGridMinutes": 10, "greatestRefineSeconds": 0.1, "contactRefineMs": 1,
                     "visibilityGridMinutes": 5, "lunarWindowHours": er.LUNAR_HALF_H,
                     "solarLocalWindowHours": er.SOLAR_HALF_H, "rateStepSeconds": 30, "curvStepSeconds": 60},
            "sutak": {"solarBeforeC1Seconds": 43200, "lunarBeforeU1Seconds": 32400,
                      "rule": "only if visible and not penumbral; solar [C1-43200 s, C4], lunar [U1-32400 s, U4]"},
            "convention": er.SIDE + " (visible = first..last instant of the interval with altitude >= threshold)",
            "globalRow": "[solar 0|1, greatestMs, gammaDeg (min angle, geocentric; lunar: Moon-antisolar, solar: "
                         "Moon-Sun), lunarKind|null, umbralMag|null, penumbralMag|null, [[contact,ms,rateArcsecPerSec]], "
                         "curvArcsecPerSec2, eclipse 0|1 (0 = near-miss within 0.02 of the limit, kind null), "
                         "fragP[], fragB[], solarMargin|null = (limit-gamma)/(2 s_S)]; sorted (greatest, solar); "
                         "lunar contacts P1,U1,U2,U3,U4,P4 as applicable; solar global contacts not defined",
            "localRow": "[globalIndex, kind|null, magnitude (solar: obscuration-free magnitude (s'S+s'M-sigma)/(2 s'S); "
                        "lunar: umbral, penumbral for LUNAR_PENUMBRAL), maxAtMs, [[contact,ms,rate]] (solar C1..C4; "
                        "lunar [] = see global row), visibleStartMs|null, visibleEndMs|null, sutakStartMs|null, "
                        "sutakEndMs|null, fragP[], fragB[] (solar: kind, vis, C:<contact>; lunar: vis only, kind/contact fragility is read "
                        "from the global row), [sigmaMin, s'S, s'M] arcsec|null (solar)]; solar rows only "
                        "where sigma_min < s'S+s'M or within 0.02 of it (kind null, magnitude <= 0); lunar rows for "
                        "every lunar eclipse; sorted (maxAt, globalIndex)"}
    text = '{"meta":' + dumps(meta) + ',\n"global":[\n' + ",\n".join(dumps(r) for r in grows) + "\n],\n"
    text += '"local":[\n' + ",\n".join('{"site":"%s","rows":[\n' % s["name"] + ",\n".join(dumps(r) for r in s["rows"])
                                       + "\n]}" for s in sites) + "\n]}\n"
    return {OUT: text}


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
        raise SystemExit(f"gen_eclipses --check: {bad} fixture(s) differ from the generator output")


if __name__ == "__main__":
    main()
