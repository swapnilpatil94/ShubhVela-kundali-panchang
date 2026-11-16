#!/usr/bin/env python
"""Panchang oracle (docs/specs/M03.md section 3.5). Dev tool only; offline; byte-reproducible.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_panchang.py          (writes the two fixtures)
Check: tools/oracle/.venv/bin/python tools/oracle/gen_panchang.py --check  (regenerates in memory, byte-compares
       with the committed files, exits non-zero on any difference or missing file)
Writes panchang_limbs.json, panchang_windows.json and panchang_highlat.json under
engine-golden/src/main/resources/golden/. panchang_highlat.json (Fable review M1): sun days of four high-latitude
places at their real civil offsets, around sunsets that cross local midnight and true polar day/night.
panchang_reference.json is hand-written (status TODO_OWNER) and only checked for existence. Never reads
rules/*.json or engine output. Synthetic places and dates only. Exits non-zero, writing nothing, if the fragile
share exceeds 2 % for any kind or the kshaya/adhika coverage minimum is not met. One worker process per city.
"""
import json
import sys
from multiprocessing import Pool
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_riseset as gr  # noqa: E402
import oracle_common as oc  # noqa: E402
import panchang_ref as pr  # noqa: E402
import timescale as tsm  # noqa: E402

GOLD = oc.RES / "golden"
SEED = 20261116
GEN_VERSION = 1
OFFSETS = [330, 330, 330, 330, 330, 0, -300, 720, 720, 60]  # same order as gr.CITIES
TAU_MS, TAU_BAD_MS = 150_000, 600_000
FRAGILE_CAP = 0.02
MIN_TITHI, MIN_NAK = 20, 10
N_RANDOM, N_WIN_RANDOM = 100, 54
PAD_MS = 3 * pr.DAY_MS


def dumps(o):
    return json.dumps(o, sort_keys=True, separators=(",", ":"))


def city_dates(ci):
    d0 = tsm._days_from_civil(2026, 1, 1)
    block = list(range(d0, d0 + 730))
    rng = np.random.default_rng(SEED + ci)
    rand = [int(x) for x in rng.integers(tsm._days_from_civil(1900, 1, 2), tsm._days_from_civil(2099, 12, 30) + 1,
                                         N_RANDOM)]
    alld = sorted(block + rand)  # a random date may repeat a block date: kept (8,300 city-days, no redraw)
    win = sorted(block[::5] + rand[:N_WIN_RANDOM])
    return alld, win


def flag_str(f, x, s, u):
    return ("F" if f else "") + ("X" if x else "") + ("S" if s else "") + ("U" if u else "")


def city_task(ci):
    eph, _ = oc.load_kernel()
    ts = oc.timescale()
    name, lat, lon = gr.CITIES[ci]
    off = OFFSETS[ci]
    site = gr.Site(eph, ts, lat, lon)
    sky = pr.Sky(eph, ts)
    alld, win = city_dates(ci)
    assert len(alld) == 830 and len(win) == 200, (len(alld), len(win))
    uniq = sorted(set(alld))
    wss = {d: d * pr.DAY_MS - off * 60_000 for d in uniq}
    starts = sorted({w for w in wss.values()} | {w + pr.DAY_MS for w in wss.values()})
    sw = dict(zip(starts, pr.sun_windows(site, starts)))
    days = {}
    for d in uniq:
        ws = wss[d]
        ev, fv = sw[ws], sw[ws + pr.DAY_MS]
        x = pr.sun_day(ev, fv)
        x["S"] = ev["fragile"] or fv["fragile"]
        days[d] = x
    # grid cells covering [sunrise - 3 d, nextSunrise + 3 d] of every day
    ks = []
    for d in uniq:
        x = days[d]
        a, b = (x["sr"], x["nsr"]) if not x["U"] else (wss[d], wss[d] + pr.DAY_MS)
        ks.append(np.arange((a - PAD_MS) // pr.GRID_MS, (b + PAD_MS) // pr.GRID_MS + 2, dtype=np.int64))
    ks = np.unique(np.concatenate(ks))
    bnd = pr.find_boundaries(sky, ks)
    stats = {k: dict(pairs=0, fragile=0, kshaya=0, adhika=0, kshayaClean=0, adhikaClean=0) for k in pr.KINDS}
    day_rows = []
    for d in alld:
        x = days[d]
        flags = flag_str(x["F"], x["X"], x["S"], x["U"])
        if x["U"]:
            day_rows.append([pr.date_str(d), None, None, None, x["noon"], flags, None, [], [], []])
            continue
        sr, nsr = x["sr"], x["nsr"]
        tau = TAU_BAD_MS if (x["F"] or x["S"]) else TAU_MS
        at5, ksh, adh, frg = [], [], [], []
        for kid, kind in enumerate(pr.KINDS):
            t, idx, _rate = bnd[kid]
            i_sr = int(np.searchsorted(t, sr, side="right"))      # first boundary > sr
            at5.append(int(idx[i_sr - 1]))
            i_ns = int(np.searchsorted(t, nsr, side="right"))     # first boundary > nsr
            fr_here = False
            for which, inst in enumerate((sr, nsr)):
                j = int(np.searchsorted(t, inst))
                near = min(abs(int(t[q]) - inst) for q in (j - 1, j) if 0 <= q < t.size)
                stats[kind]["pairs"] += 1
                if near <= tau:
                    frg.append([kind, which])
                    stats[kind]["fragile"] += 1
                    fr_here = True
            for i in range(i_sr, i_ns - 1):  # spans [t_i, t_i+1) with start > sr and end <= nsr
                ksh.append([kind, int(idx[i])])
                stats[kind]["kshaya"] += 1
                stats[kind]["kshayaClean"] += not fr_here
            if i_ns == i_sr:  # no boundary in (sr, nsr]: the span starting before sr covers both
                adh.append([kind, int(idx[i_sr - 1])])
                stats[kind]["adhika"] += 1
                stats[kind]["adhikaClean"] += not fr_here
        day_rows.append([pr.date_str(d), x["sr"], x["ss"], x["nsr"], x["noon"], flags, at5, ksh, adh, frg])
    win_rows = []
    for d in win:
        x = days[d]
        flags = flag_str(x["F"], x["X"], x["S"], x["U"])
        ws = [] if x["U"] else [[k, p, c, n, a, b] for k, p, c, n, a, b in
                                pr.windows_for_day(d, off, x["sr"], x["ss"], x["nsr"])]
        win_rows.append([pr.date_str(d), flags, ws])
    flagc = {c: sum(1 for x in days.values() if x[c]) for c in "FXSU"}
    bl = {}
    for kid, kind in enumerate(pr.KINDS):
        t, idx, rate = bnd[kid]
        bl[kind] = [[int(i), int(m), float(r)] for m, i, r in zip(t, idx, rate)]
    return dict(name=name, off=off, bnd=bl, days=day_rows, wins=win_rows, stats=stats, flags=flagc)


# (name, lat, lon, offset minutes, [(first date, last date)]): real civil offsets in force on those dates.
HIGHLAT = [
    ("reykjavik", 64.1466, -21.9426, 0, [((2026, 6, 5), (2026, 6, 28))]),
    ("fairbanks", 64.8378, -147.7164, -480, [((2026, 5, 20), (2026, 6, 10))]),
    ("oulu", 65.0121, 25.4651, 180, [((2026, 5, 28), (2026, 6, 18))]),
    ("tromso_summer", 69.6492, 18.9553, 120, [((2026, 5, 8), (2026, 5, 28)), ((2026, 6, 18), (2026, 6, 24)),
                                              ((2026, 7, 18), (2026, 8, 2))]),
    ("tromso_winter", 69.6492, 18.9553, 60, [((2026, 11, 20), (2026, 12, 3)), ((2026, 12, 19), (2026, 12, 23)),
                                             ((2027, 1, 8), (2027, 1, 20))]),
]


def highlat_task(hi):
    """Rows [date, srMs|null, ssMs|null, nsrMs|null, flags(F,X,S,U), sources] for one place; sources has one
    letter per event (sunrise, sunset, next sunrise): R observed crossing, C LOCAL_NOON_PM_6H convention."""
    eph, _ = oc.load_kernel()
    ts = oc.timescale()
    name, lat, lon, off, ranges = HIGHLAT[hi]
    site = gr.Site(eph, ts, lat, lon)
    dates = []
    for a, b in ranges:
        dates += list(range(tsm._days_from_civil(*a), tsm._days_from_civil(*b) + 1))
    wss = {d: d * pr.DAY_MS - off * 60_000 for d in dates}
    starts = sorted({w for w in wss.values()} | {w + pr.DAY_MS for w in wss.values()})
    sw = dict(zip(starts, pr.sun_windows(site, starts)))
    rows = []
    for d in dates:
        ev, fv = sw[wss[d]], sw[wss[d] + pr.DAY_MS]
        x = pr.sun_day(ev, fv)
        sfrag = ev["fragile"] or fv["fragile"]
        flags = flag_str(x["F"], x["X"], sfrag, x["U"])
        if x["U"]:
            rows.append([pr.date_str(d), None, None, None, flags, ""])
            continue
        real = {t for _, t in ev["real"] + fv["real"]}
        src = "".join("R" if t in real else "C" for t in (x["sr"], x["ss"], x["nsr"]))
        rows.append([pr.date_str(d), x["sr"], x["ss"], x["nsr"], flags, src])
    return dict(name=name, lat=lat, lon=lon, off=off, rows=rows)


def generate_highlat(ksha):
    with Pool(len(HIGHLAT)) as pool:
        places = pool.map(highlat_task, range(len(HIGHLAT)))
    n = sum(len(p["rows"]) for p in places)
    src = {}
    for p in places:
        for r in p["rows"]:
            src[r[5] or "U"] = src.get(r[5] or "U", 0) + 1
    print(f"highlat city-days {n}, sources {dict(sorted(src.items()))}")
    meta = {"generator": "gen_panchang.py", "genVersion": GEN_VERSION, "schema": 1, "versions": oc.versions(),
            "kernelSha256": ksha, "convention": pr.CONV, "highLatitudeRule": "LOCAL_NOON_PM_6H", "cityDays": n,
            "sources": dict(sorted(src.items())),
            "places": [{"name": p["name"], "lat": p["lat"], "lon": p["lon"], "offsetMin": p["off"],
                        "days": len(p["rows"])} for p in places],
            "dayRow": "[date, srMs, ssMs, nsrMs, flags(F,X,S,U), sources]; sources = one letter per event "
                      "(sunrise, sunset, nextSunrise): R observed crossing, C convention (culmination -/+ 6 h); "
                      "ms null and sources empty when flags has U"}
    t = '{"meta":' + dumps(meta) + ',"places":[\n'
    t += ",\n".join('{"name":"%s","offsetMin":%d,"days":[\n' % (p["name"], p["off"])
                     + ",\n".join(dumps(r) for r in p["rows"]) + "\n]}" for p in places) + "\n]}\n"
    return t


def generate():
    _, ksha = oc.load_kernel()
    with Pool(len(gr.CITIES)) as pool:
        cities = pool.map(city_task, range(len(gr.CITIES)))
    tot = {k: dict(pairs=0, fragile=0, kshaya=0, adhika=0, kshayaClean=0, adhikaClean=0) for k in pr.KINDS}
    for c in cities:
        for k in pr.KINDS:
            for f in tot[k]:
                tot[k][f] += c["stats"][k][f]
    n_days = sum(len(c["days"]) for c in cities)
    n_win = sum(len(c["wins"]) for c in cities)
    print(f"city-days {n_days}, window city-days {n_win}")
    bad = []
    for k in pr.KINDS:
        t = tot[k]
        share = t["fragile"] / t["pairs"]
        print(f"  {k:10s} pairs {t['pairs']} fragile {t['fragile']} ({100 * share:.3f} %) kshaya {t['kshaya']} "
              f"(clean {t['kshayaClean']}) adhika {t['adhika']} (clean {t['adhikaClean']})")
        if share > FRAGILE_CAP:
            bad.append(f"{k} fragile share {100 * share:.3f} % > {100 * FRAGILE_CAP:.0f} %")
    for kind, need in (("TITHI", MIN_TITHI), ("NAKSHATRA", MIN_NAK)):
        for fld in ("kshayaClean", "adhikaClean"):
            if tot[kind][fld] < need:
                bad.append(f"{kind} non-fragile {fld[:-5]} {tot[kind][fld]} < {need}")
    for c in cities:
        print(f"  {c['name']:8s} flags {c['flags']} boundaries {{{', '.join(f'{k} {len(v)}' for k, v in c['bnd'].items())}}} "
              f"kshaya T/N {c['stats']['TITHI']['kshaya']}/{c['stats']['NAKSHATRA']['kshaya']} "
              f"adhika T/N {c['stats']['TITHI']['adhika']}/{c['stats']['NAKSHATRA']['adhika']}")
    if n_days != 8300 or n_win != 2000:
        bad.append(f"fixture sizes {n_days}/{n_win} != 8300/2000")
    if bad:
        oc.die("; ".join(bad) + "; nothing written")
    cmeta = [{"name": c["name"], "lat": gr.CITIES[i][1], "lon": gr.CITIES[i][2], "offsetMin": c["off"],
              "seed": SEED + i, "duplicateDates": 830 - len(set(city_dates(i)[0])), "cityDays": len(c["days"]), "windowDays": len(c["wins"]),
              "boundaryCounts": {k: len(v) for k, v in c["bnd"].items()}, "flagDays": c["flags"]}
             for i, c in enumerate(cities)]
    common = {"generator": "gen_panchang.py", "genVersion": GEN_VERSION, "schema": 1, "versions": oc.versions(),
              "kernelSha256": ksha, "seedBase": SEED, "cities": cmeta, "convention": pr.CONV}
    lm = dict(common, cityDays=n_days, kinds=pr.KINDS, gridSeconds=3600, tauSeconds=150, tauBadSeconds=600,
              fragileCap=FRAGILE_CAP, rateUnit="arcsec/s", boundaryRow="[indexAfter, epochMs, rate]",
              boundaryTotals={k: sum(len(c["bnd"][k]) for c in cities) for k in pr.KINDS},
              fragilePairs={k: [tot[k]["fragile"], tot[k]["pairs"]] for k in pr.KINDS},
              kshaya={k: tot[k]["kshaya"] for k in pr.KINDS}, adhika={k: tot[k]["adhika"] for k in pr.KINDS},
              kshayaNonFragile={k: tot[k]["kshayaClean"] for k in pr.KINDS},
              adhikaNonFragile={k: tot[k]["adhikaClean"] for k in pr.KINDS},
              dayRow="[date, srMs, ssMs, nsrMs, noonMs|null, flags(F,X,S,U), atSunrise[TITHI,KARANA,NAKSHATRA,"
                     "YOGA,MOON_RASHI], kshaya[[kind,index]], adhika[[kind,index]], fragile[[kind,0 sunrise|1 "
                     "nextSunrise]]]; sr/ss/nsr/atSunrise null when flags has U")
    wm = dict(common, windowCityDays=n_win, printedKinds=pr.PRINTED_KINDS, kindOrder=pr.KIND_ORDER,
              windowRow="[kind, part, code|null, night, startMs, endMs]; sorted by (start, kind ordinal, part), "
                        "then the three PRINTED_6AM windows (06:00-18:00 local, parts of 8) appended",
              dayRow="[date, flags, windows]", windowKinds=["RAHU_KAAL", "YAMAGANDA", "GULIKA", "ABHIJIT",
                                                            "BRAHMA_MUHURTA", "DURMUHURTA", "CHOGHADIYA", "HORA"])
    lt = '{"meta":' + dumps(lm) + ',"cities":[\n'
    parts = []
    for c in cities:
        s = '{"name":"%s","offsetMin":%d,"boundaries":{\n' % (c["name"], c["off"])
        s += ",\n".join('"%s":[%s]' % (k, ",".join(dumps(r) for r in c["bnd"][k])) for k in pr.KINDS)
        s += '},"days":[\n' + ",\n".join(dumps(r) for r in c["days"]) + "\n]}"
        parts.append(s)
    lt += ",\n".join(parts) + "\n]}\n"
    wt = '{"meta":' + dumps(wm) + ',"cities":[\n'
    wt += ",\n".join('{"name":"%s","offsetMin":%d,"days":[\n' % (c["name"], c["off"])
                     + ",\n".join(dumps(r) for r in c["wins"]) + "\n]}" for c in cities) + "\n]}\n"
    return {"panchang_limbs.json": lt, "panchang_windows.json": wt, "panchang_highlat.json": generate_highlat(ksha)}


def main():
    check = "--check" in sys.argv[1:]
    out = generate()
    bad = 0
    ref = GOLD / "panchang_reference.json"
    if check and not ref.is_file():
        print("DIFF panchang_reference.json (missing)")
        bad += 1
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
        raise SystemExit(f"gen_panchang --check: {bad} fixture(s) differ from the generator output")


if __name__ == "__main__":
    main()
