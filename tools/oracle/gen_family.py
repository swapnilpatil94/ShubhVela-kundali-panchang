#!/usr/bin/env python
"""Family dates oracle (docs/specs/M10.md task C2). Dev tool only; offline; byte-reproducible.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_family.py            (writes family_dates.json)
Check: tools/oracle/.venv/bin/python tools/oracle/gen_family.py --check    (regenerates, byte-compares, exits
       non-zero on any difference or a missing file)
Independent of the Kotlin engine and of engine output: janma facts and the nakshatra_in_lunar_month rule come
from family_ref.py, the tithi and solar-month rules from festival_ref.py (M04 oracle), the local birth times of the
historical-offset rows from the gen_tz fixture (Python zoneinfo). Synthetic instants and places only.
Exits non-zero, writing nothing, if a fragile (Base) share exceeds 2 % for a kind or the janma rows, a must-have
row is missing, or a phantom occurrence has no row to attach to.
"""
import bisect
import hashlib
import json
import random
import sys
import time
from multiprocessing import Pool
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import calendar_ref as cr  # noqa: E402
import family_ref as fam  # noqa: E402
import festival_ref as fr  # noqa: E402
import gen_festivals as gf  # noqa: E402
import oracle_common as oc  # noqa: E402
import panchang_ref as pr  # noqa: E402
import timescale as tsm  # noqa: E402

GOLD = oc.RES / "golden"
OUT = "family_dates.json"
GEN_VERSION = 1
SEED = 20261116
N_RANDOM = 300
N_OCC = 60            # random births whose dates are generated (all must-have births too)
FRAGILE_CAP = 0.02
DAY = fr.DAY
OCC_CITIES = ["delhi", "mumbai", "chennai", "kolkata", "newyork", "london", "sydney", "auckland"]
D_FIRST = tsm._days_from_civil(2024, 1, 1)
D_LAST = tsm._days_from_civil(2030, 12, 31)
LIMB_G0 = (tsm._days_from_civil(2023, 9, 1) * DAY) // pr.GRID_MS
LIMB_G1 = (tsm._days_from_civil(2031, 9, 1) * DAY) // pr.GRID_MS
SK_G0 = (tsm._days_from_civil(2022, 12, 1) * DAY - cr.WORLD_START) // cr.GRID_MS
SK_G1 = (tsm._days_from_civil(2032, 3, 1) * DAY - cr.WORLD_START) // cr.GRID_MS
RANDOM_LO = fam.ms_of(1900, 3, 1)      # engine lunations reach 62 days either side of an instant
RANDOM_HI = fam.ms_of(2100, 10, 31)


def dumps(o):
    return json.dumps(o, sort_keys=True, separators=(",", ":"))


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ------------------------------------------------------------------------------------------------ world
def occ_world(pool):
    """Tithi and nakshatra boundaries (1 h grid + bisection) and sankrantis (12 h grid) for 2023-09..2031-09."""
    n = LIMB_G1 - LIMB_G0
    cuts = [LIMB_G0 + n * i // 24 for i in range(25)]
    res = pool.map(gf.limb_slice, [(cuts[i], cuts[i + 1]) for i in range(24)])
    tt = [t for r in res for t in r[0][0]]
    ti = [i for r in res for i in r[0][1]]
    nt = [t for r in res for t in r[2][0]]
    ni = [i for r in res for i in r[2][1]]
    p0 = ti.index(1)
    tt, ti = tt[p0:], ti[p0:]
    nlun = (len(tt) - 1) // 30
    tt, ti = tt[:30 * nlun + 1], ti[:30 * nlun + 1]
    assert all(b > a for a, b in zip(tt, tt[1:])) and all(b > a for a, b in zip(nt, nt[1:]))
    assert all(ti[i] == i % 30 + 1 for i in range(len(ti))), "tithi sequence broken"
    assert all(ni[i + 1] == (ni[i] + 1) % 27 for i in range(len(ni) - 1)), "nakshatra sequence broken"
    m = SK_G1 - SK_G0
    cuts = [SK_G0 + m * i // 12 for i in range(13)]
    sk = sorted((x for part in pool.map(gf.sank_slice, [(cuts[i], cuts[i + 1]) for i in range(12)]) for x in part),
                key=lambda x: x[1])
    assert all(b[1] > a[1] and b[0] == (a[0] + 1) % 12 for a, b in zip(sk, sk[1:])), "sankranti sequence broken"
    return tt, nt, ni, [x[0] for x in sk], [x[1] for x in sk]


# ------------------------------------------------------------------------------------------ must-have births
def first_run(sky, year, rashi, nak):
    """Middle of the first run of >= 4 whole hours of [year] with the Sun in [rashi] and the Moon in [nak]."""
    for y in (year, year + 1, year + 2):
        g = np.arange(fam.ms_of(y, 1, 1), fam.ms_of(y + 1, 1, 1), 3_600_000, dtype=np.int64)
        _t, n, r = fam.indices(sky, g)
        ok = (n == nak) & (r == rashi)
        run = 0
        for i, v in enumerate(ok.tolist()):
            run = run + 1 if v else 0
            if run == 4:
                return int(g[i - 2]) + 1_800_000
    raise AssertionError("no instant with the requested sun rashi and nakshatra")


def must_haves(sky, tb, nbm, nbi, ski, skt, tz):
    """[(ms, tag, region|None)] of the spec's must-have rows (region None = the index rule)."""
    eph, _ = oc.load_kernel()
    sd = fr.City(eph, oc.timescale(), "delhi", 330)
    sd.ensure(range(tsm._days_from_civil(2024, 10, 1), tsm._days_from_civil(2027, 3, 1)))
    w = cr.World([tb[30 * k] for k in range((len(tb) - 1) // 30 + 1)], skt, ski)
    out = []
    k = next(k for k in range(len(w.NM) - 1) if w.lun(k)[1] and cr.utc_year(w.NM[k]) == 2026)
    out.append((w.NM[k] + 10 * DAY + 3 * 3_600_000, "adhika_masa_2026", None))
    y25 = fam.ms_of(2025, 1, 1)
    for i in range(bisect.bisect_left(tb, y25), len(tb) - 1):
        x0, x1 = tb[i], tb[i + 1]
        d = sd.containing(x0)
        if not any(x0 <= sd.get(x)[0] < x1 for x in (d, d + 1, d + 2)):
            out.append(((x0 + x1) // 2, "kshaya_tithi_delhi_2025", None))
            break
    i = bisect.bisect_left(tb, fam.ms_of(2027, 3, 1))
    out.append((tb[i] + 40_000, "tithi_boundary_plus_40s", None))
    j = bisect.bisect_left(nbm, fam.ms_of(2028, 6, 1))
    out.append((nbm[j] - 45_000, "nakshatra_boundary_minus_45s", None))
    twice = absent = None
    for j in range(bisect.bisect_left(skt, y25), len(skt) - 1):
        a, b = sd.start("TAMIL", skt[j])[0], sd.start("TAMIL", skt[j + 1])[0]
        if b > tsm._days_from_civil(2027, 1, 1):
            break
        count = [0] * 27
        for d in range(a, b):
            count[nbi[bisect.bisect_right(nbm, sd.get(d)[0]) - 1]] += 1
        if twice is None and max(count) >= 2:
            twice = (ski[j], count.index(max(count)), a)
        if absent is None and min(count) == 0:
            absent = (ski[j], count.index(0), a)
    assert twice and absent, "no solar month with a twice / absent nakshatra in 2025-2026"
    out.append((first_run(sky, 1995, twice[0], twice[1]), "nakshatra_twice_tamil_month", "TAMIL"))
    out.append((first_run(sky, 1996, absent[0], absent[1]), "nakshatra_absent_tamil_month", "TAMIL"))
    for tag, zone, (y, mo, d, h, mi) in (("mumbai_pre_1955", "Asia/Kolkata", (1950, 3, 10, 7, 15)),
                                         ("war_time_1943", "Asia/Kolkata", (1943, 8, 20, 21, 40)),
                                         ("auckland_dateline", "Pacific/Auckland", (2001, 12, 31, 23, 30))):
        ms, off = fam.local_to_ms(tz, zone, fam.ms_of(y, mo, d, h, mi) // 1000)
        out.append((ms, f"{tag}_offset_{off}", None))
    return out, {"twice": twice, "absent": absent}


# ------------------------------------------------------------------------------------- unknown-time days
DAY_CITIES = ["delhi", "newyork"]
N_DAY_RANDOM = 60


def day_rows(sky, wj, tb, nbm, nbi):
    """docs/specs/M10.md rule 2 (janmaOfDay): per city every 2024-2030 civil day with one tithi and one nakshatra
    over [local midnight, next midnight) plus N_DAY_RANDOM random days (seed SEED + 1). Row: [date, city, tithis,
    nakshatras, facts at sunrise..., fragP, fragB]; fragile if a tithi / nakshatra boundary lies within tauL of
    either midnight or the sunrise facts are fragile (family_ref.janma_rows)."""
    eph, _ = oc.load_kernel()
    rng = random.Random(SEED + 1)
    out = []
    for name in DAY_CITIES:
        off = cr.CITY_OFFSETS[cr.CITY_NAMES.index(name)]

        def lists(d):
            m0 = d * DAY - off * 60_000
            m1 = m0 + DAY
            i0, j0 = bisect.bisect_right(tb, m0) - 1, bisect.bisect_right(nbm, m0) - 1
            ti = [i % 30 + 1 for i in range(i0, len(tb) - 1) if tb[i] < m1]
            nk = [nbi[j] for j in range(j0, len(nbm) - 1) if nbm[j] < m1]
            fr_ = {lvl: any(abs(b - m) <= fam.TAU_L[lvl] for m in (m0, m1)
                            for b in tb[i0:i0 + len(ti) + 2] + nbm[j0:j0 + len(nk) + 2]) for lvl in ("P", "B")}
            return ti, nk, fr_

        exact = [d for d in range(D_FIRST, D_LAST + 1) if len(lists(d)[0]) == 1 and len(lists(d)[1]) == 1]
        days = sorted(set(exact) | {rng.randrange(D_FIRST, D_LAST + 1) for _ in range(N_DAY_RANDOM)})
        sd = fr.City(eph, oc.timescale(), name, off)
        sd.ensure(days)
        facts = fam.janma_rows(sky, wj, [(sd.get(d)[0], "day") for d in days])
        for d, f in zip(days, facts):
            ti, nk, fl = lists(d)
            out.append([pr.date_str(d), name, ti, nk, f[2], list(f[3]), list(f[4]), f[5], f[6],
                        fl["P"] or f[7], fl["B"] or f[8]])
        print(f"  unknown-time days {name}: exact {len(exact)}, rows {len(days)}")
    return out


# ------------------------------------------------------------------------------------------ city worker
def lunar_units(ctx, df):
    r, adh = df["rule"], df["adhika"]
    kmin = next(k for k in range(ctx.nlun) if ctx.TB[30 * k] >= gf.K_START_MS)
    out = []
    for a in range(max(kmin, 2), ctx.nlun - 4):
        out.append(gf.Unit(lambda a=a: ctx.lunar_unit(r, adh, a), k=a - 2,
                           tb=tuple(range(30 * (a - 2), 30 * a + 31, 15)),
                           nak=[(r["nakshatra"], ctx.TB[30 * (a - 2)], ctx.TB[30 * a + 30])]))
    return out


def eval_def(ctx, df, lo=D_FIRST, hi=D_LAST):
    """gen_festivals.eval_def for a non-relative def, with the lunar month units of family_ref."""
    units = lunar_units(ctx, df) if df["rule"]["type"] == "nakshatra_in_lunar_month" else \
        gf.make_units(ctx, df, lo, hi)
    recs, phantoms = [], {"P": set(), "B": set()}
    for u in units:
        ctx.sd.used.clear()
        rec = u.run()
        used = set(ctx.sd.used)
        if rec is None:
            if u.k is not None:
                ph = gf.phantom_dates(ctx, u)
                for lvl in ph:
                    phantoms[lvl] |= {x for x in ph[lvl] if lo <= x <= hi}
            continue
        if not lo <= rec["date"] <= hi:
            continue
        res = gf.perturb(ctx, u, fr.decision(rec), used, None)
        rec["fragP"], rec["altP"] = res["P"][0], res["P"][1] | {rec["date"]}
        rec["fragB"], rec["altB"] = res["B"][0], res["B"][1] | {rec["date"]}
        recs.append(rec)
    seen, rows = set(), []
    for rec in recs:
        if rec["date"] not in seen:
            seen.add(rec["date"])
            rows.append(rec)
    rows.sort(key=lambda x: x["date"])
    orphans = 0
    for lvl in ("P", "B"):
        for dt in sorted(phantoms[lvl]):
            if dt in seen:
                continue
            near = min(rows, key=lambda x: abs(x["date"] - dt), default=None)
            if near is None or abs(near["date"] - dt) > 45:
                orphans += 1
                continue
            near["frag" + lvl] = True
            near["alt" + lvl].add(dt)
    return rows, orphans


def city_task(args):
    name, defs, tb, nbm, nbi, ski, skt = args
    eph, _ = oc.load_kernel()
    off = cr.CITY_OFFSETS[cr.CITY_NAMES.index(name)]
    sd = fr.City(eph, oc.timescale(), name, off)
    sd.sunflag_any = lambda d, c: bool(sd.t.get(d) and sd.t[d][c])
    t0 = time.time()
    sd.ensure(range(D_FIRST - 110, D_LAST + 70))
    nlun = (len(tb) - 1) // 30
    w = cr.World([tb[30 * k] for k in range(nlun + 1)], skt, ski)
    ctx = fam.FamCtx(sd, list(tb), list(nbm), list(nbi), w)
    ctx.sunflag = lambda d, c: sd.sunflag_any(d, c)
    per_def, orphans = {}, 0
    for df in defs:
        rows, o = eval_def(ctx, df)
        per_def[df["id"]] = rows
        orphans += o
    return dict(name=name, off=off, rows=per_def, orphans=orphans, total=time.time() - t0)


# ---------------------------------------------------------------------------------------------- generate
def generate():
    t_start = time.time()
    _, ksha = oc.load_kernel()
    eph, _ = oc.load_kernel()
    sky = pr.Sky(eph, oc.timescale())
    tz = json.loads((GOLD / "tz_offsets.json").read_text())
    with Pool(12) as pool:
        nm, _fm, sk = cr.build_world(pool)
        wj = cr.World([x[0] for x in nm], [x[1] for x in sk], [x[0] for x in sk])
        tb, nbm, nbi, ski, skt = occ_world(pool)
        print(f"worlds: {len(nm)} new moons 1898-2101; {(len(tb) - 1) // 30} lunations, {len(nbm)} nakshatra "
              f"boundaries, {len(skt)} sankrantis 2023-2031 ({time.time() - t_start:.0f} s)")
        rng = random.Random(SEED)
        births = [(rng.randrange(RANDOM_LO, RANDOM_HI), "random", None) for _ in range(N_RANDOM)]
        extra, found = must_haves(sky, tb, nbm, nbi, ski, skt, tz)
        births += extra
        jrows = fam.janma_rows(sky, wj, [(b[0], b[1]) for b in births])
        drows = day_rows(sky, wj, tb, nbm, nbi)
        occ = list(range(N_OCC)) + list(range(N_RANDOM, len(births)))
        defs = [d for b in occ for d in fam.defs_of(b, jrows[b], births[b][2] or fam.region_of(b))]
        res = pool.map(city_task, [(c, defs, tb, nbm, nbi, ski, skt) for c in OCC_CITIES], chunksize=1)
    bad = []
    adh = next(r for r in jrows if r[1] == "adhika_masa_2026")
    if not adh[3][1]:
        bad.append("adhika birth not in an adhika lunation")
    jfrag = [sum(r[7] for r in jrows), sum(r[8] for r in jrows)]
    print(f"janma rows {len(jrows)} fragP {jfrag[0]} fragB {jfrag[1]}; must-have rows "
          + ", ".join(f"{r[1]} {pr.date_str(r[0] // DAY)} frag {r[7]}/{r[8]}" for r in jrows[N_RANDOM:]))
    if jfrag[1] > FRAGILE_CAP * len(jrows):
        bad.append("janma fragB share above cap")
    kind_of = {d["id"]: d["kind"] for d in defs}
    stat = {k: [0, 0, 0] for k in ("TITHI_BIRTHDAY", "NAKSHATRA_BIRTHDAY", "SHRADDHA")}
    flagc, rows_out = {}, []
    for c in res:
        lines = []
        for df in defs:
            for rec in c["rows"][df["id"]]:
                lines.append((rec["date"], df["id"], gf.row_of(df, c["name"], rec)))
                s = stat[kind_of[df["id"]]]
                s[0] += 1
                s[1] += rec["fragP"]
                s[2] += rec["fragB"]
                for f in rec["flags"]:
                    flagc[f] = flagc.get(f, 0) + 1
        lines.sort(key=lambda x: (x[0], x[1]))
        rows_out.append((c["name"], c["off"], [x[2] for x in lines]))
        print(f"  {c['name']:9s} rows {len(lines)} orphan phantoms {c['orphans']} ({c['total']:.0f} s)")
        if c["orphans"]:
            bad.append("orphan phantom occurrences")
    for k, (n, fp, fb) in stat.items():
        print(f"  kind {k:19s} rows {n} fragP {fp} fragB {fb} ({100 * fb / max(n, 1):.3f} % of cap 2 %)")
        if fb > FRAGILE_CAP * n:
            bad.append(f"kind {k} fragB share above cap")
    print(f"flag counts {dict(sorted(flagc.items()))}")
    delhi = next(r for r in rows_out if r[0] == "delhi")[2]
    for tag, key, want in (("nakshatra_absent_tamil_month", "absent", 0), ("nakshatra_twice_tamil_month", "twice", 1)):
        b = next(i for i, r in enumerate(jrows) if r[1] == tag)
        fid = "fam_%d" % (10 * b + 2)
        a = found[key][2]
        got = [x for x in delhi if x[0] == fid and a <= tsm._days_from_civil(*map(int, x[1].split("-"))) < a + 32]
        print(f"  {tag}: Delhi month from {pr.date_str(a)}: rows {[x[1] for x in got]}")
        if len(got) != want:
            bad.append(f"{tag}: {len(got)} rows in the target month, want {want}")
    if bad:
        oc.die("; ".join(bad) + "; nothing written")
    dates = "|".join(f"{n}:{x[0]}:{x[1]}" for n, _, rr in rows_out for x in rr)
    n_rows = sum(len(r[2]) for r in rows_out)
    meta = {"genVersion": GEN_VERSION, "schema": 1, "generator": "gen_family.py", "versions": oc.versions(),
            "kernelSha256": ksha, "ayanamsa": "TRUE_LAHIRI", "seed": SEED, "range": "2024-01-01..2030-12-31",
            "randomBirths": N_RANDOM, "occurrenceBirths": len(occ), "janmaRows": len(jrows), "dayRows": len(drows),
            "dayExact": sum(1 for r in drows if len(r[2]) == 1 and len(r[3]) == 1), "defs": len(defs),
            "rows": n_rows, "datesSha256": sha(dates), "fragileCap": FRAGILE_CAP,
            "tauSeconds": {"P": 30, "B": 600}, "tauTithiSeconds": {"P": 30, "B": 150, "BonFSdays": 600},
            "fragileByKind": {k: {"rows": s[0], "fragP": s[1], "fragB": s[2]} for k, s in stat.items()},
            "janmaFragile": {"fragP": jfrag[0], "fragB": jfrag[1]}, "flagCounts": dict(sorted(flagc.items())),
            "cities": [{"name": n, "lat": cr.CITY_COORDS[n][0], "lon": cr.CITY_COORDS[n][1], "offsetMin": off,
                        "rows": len(rr)} for n, off, rr in rows_out],
            "janma": "[birth, tag, instantMs, tithi 1..30, amanta [masa, adhika, kshaya|null], purnimanta, "
                     "nakshatra 0..26, sunRashi 0..11, fragP, fragB]; fragile: a fact changes when the instant moves "
                     "+-tauL (tithi, nakshatra, labels) or +-tauS (sun rashi), or one nearby new moon / sankranti "
                     "moves +-tauS",
            "day": "[date, city, tithis, nakshatras (spans overlapping local midnight..next midnight, fixed city "
                   "offset), tithi, amanta, purnimanta, nakshatra, sunRashi at the day's sunrise, fragP, fragB]: "
                   "janmaOfDay; Exact iff one tithi and one nakshatra; fragile if a boundary is within tauL of a "
                   "midnight or the sunrise facts are fragile",
            "def": "[id, birth, kind, basis|null, system|null, solarRegion|null]: docs/specs/M10.md rule 8 defaults",
            "row": "festivals_oracle.json row layout (gen_festivals.row_of); nakshatra rows carry the month label "
                   "for the lunar basis and null for the solar basis"}
    jtext = ",\n".join(dumps([i, r[1], r[0], r[2], list(r[3]), list(r[4]), r[5], r[6], r[7], r[8]])
                       for i, r in enumerate(jrows))
    dtext = ",\n".join(dumps([d["id"], d["birth"], d["kind"], d["basis"], d["system"], d["region"]]) for d in defs)
    text = '{"meta":' + dumps(meta) + ',\n"janma":[\n' + jtext + '\n],\n"days":[\n' + ",\n".join(dumps(r) for r in drows) + \
        '\n],\n"defs":[\n' + dtext + '\n],\n"cities":[\n' + \
        ",\n".join('{"name":"%s","offsetMin":%d,"rows":[\n' % (n, off) + ",\n".join(dumps(x) for x in rr) + "\n]}"
                   for n, off, rr in rows_out) + "\n]}\n"
    print(f"rows {n_rows}; run time {time.time() - t_start:.0f} s")
    return {OUT: text}


def main():
    check = "--check" in sys.argv[1:]
    bad = 0
    for name, text in generate().items():
        path = GOLD / name
        data = text.encode("utf-8")
        h = hashlib.sha256(data).hexdigest()
        if check:
            same = path.is_file() and path.read_bytes() == data
            print(f"{'ok  ' if same else 'DIFF'} {name} sha256 {h[:16]}")
            bad += not same
        else:
            path.write_bytes(data)
            print(f"wrote {path} ({len(data)} bytes) sha256 {h[:16]}")
    if bad:
        raise SystemExit("gen_family --check: fixture differs from the generator output")


if __name__ == "__main__":
    main()
