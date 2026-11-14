#!/usr/bin/env python
"""Calendar oracle (docs/specs/M04.md sections 3.1-3.3 and 3.10, task B2). Dev tool only; offline; byte-reproducible.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_calendar.py          (writes the three fixtures)
Check: tools/oracle/.venv/bin/python tools/oracle/gen_calendar.py --check  (regenerates in memory, byte-compares
       with the committed files, exits non-zero on any difference or missing file)
Writes calendar_lunations.json, calendar_days.json, calendar_solar.json under engine-golden/src/main/resources/golden/.
Never reads rules/*.json, Kotlin or engine output. Synthetic places and dates only. Exits non-zero, writing nothing,
if a fragile (Base) share exceeds 2 % or a coverage minimum fails. One worker process per city.
"""
import bisect
import hashlib
import json
import sys
import time
from multiprocessing import Pool
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import calendar_ref as cr  # noqa: E402
import gen_riseset as gr  # noqa: E402
import oracle_common as oc  # noqa: E402
import panchang_ref as pr  # noqa: E402
import timescale as tsm  # noqa: E402

GOLD = oc.RES / "golden"
GEN_VERSION = 1
SEED_BASE = 20261118
FRAGILE_CAP = 0.02
MIN_ADHIKA, MIN_KSHAYA, MIN_NEAR_CUTOFF = 60, 1, 20
DAY_MS = cr.DAY_MS
FIELDS = ["tithi", "amanta", "purnimanta", "shaka", "vikramChaitradi", "vikramKartikadi", "samvatsara",
          "samvatsaraTamil", "bangabda", "kollam", "ritu", "ayana"] + ["solar" + r for r in cr.REGIONS]


def dumps(o):
    return json.dumps(o, sort_keys=True, separators=(",", ":"))


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ------------------------------------------------------------------------------------------ perturbation
class Perturb:
    """Moves one world input at a time by +-tau and restores it."""

    def __init__(self, w):
        self.w = w

    def run(self, items, tau):
        """items = [(kind 'n'|'s', index)]; yields (kind, index) with the list entry moved (both signs)."""
        w = self.w
        for kind, i in items:
            arr = w.NM if kind == "n" else w.SKT
            orig = arr[i]
            for sign in (-1, 1):
                arr[i] = orig + sign * tau
                yield kind, i
            arr[i] = orig


def lunation_rows(nm, fm, sk, w, sens):
    lo_ms = tsm._days_from_civil(1900, 1, 1) * DAY_MS
    hi_ms = tsm._days_from_civil(2101, 1, 1) * DAY_MS
    fm_t = [x[0] for x in fm]
    skt = w.SKT
    rows = []
    pert = Perturb(w)
    for k in range(len(w.NM) - 1):
        if not (lo_ms <= w.NM[k] < hi_ms):
            continue
        t0, t1 = w.NM[k], w.NM[k + 1]
        i = bisect.bisect_right(fm_t, t0)
        assert fm_t[i] < t1, "full moon missing"
        lo, hi = bisect.bisect_left(skt, t0), bisect.bisect_left(skt, t1)
        base = w.lun(k)
        frag = {}
        for lvl in ("P", "B"):
            tau = cr.TAU_S[lvl]
            sn, ss = sens[lvl]
            items = [("n", j) for j in (k, k + 1) if sn[j]] + [("s", j) for j in range(lo - 1, hi + 2) if ss[j]]
            hit = False
            for _ in pert.run(items, tau):
                hit = hit or w.lun(k) != base
            frag[lvl] = hit
        rows.append([nm[k][0], nm[k][1], fm[i][0], fm[i][1], [[sk[j][0], sk[j][1], sk[j][2]] for j in range(lo, hi)],
                     base[0], base[1], base[2], frag["P"], frag["B"]])
    return rows


# -------------------------------------------------------------------------------------------- city worker
def city_task(args):
    ci, nm, skt, ski = args
    eph, _ = oc.load_kernel()
    ts = oc.timescale()
    name, off = cr.CITY_NAMES[ci], cr.CITY_OFFSETS[ci]
    sd = cr.SunDays(eph, ts, name, off)
    sky = pr.Sky(eph, ts)
    w = cr.World([x[0] for x in nm], skt, ski)
    sens = {lvl: cr.sens_flags(w, cr.TAU_S[lvl]) for lvl in ("P", "B")}
    pert = Perturb(w)
    cday = lambda ms: (ms + off * 60_000) // DAY_MS  # noqa: E731

    # ---- solar fixture: sankrantis 1950-2050
    s0 = tsm._days_from_civil(1950, 1, 1) * DAY_MS
    s1 = tsm._days_from_civil(2051, 1, 1) * DAY_MS
    sidx = [j for j, t in enumerate(skt) if s0 <= t < s1]
    need = set()
    for j in sidx:
        c = cday(skt[j])
        need.update(range(c - 2, c + 4))
    sd.ensure(need)
    solar_rows = []
    sstat = {r: dict(rows=0, fragP=0, fragB=0, near=0, altCutP=0, altCutB=0) for r in cr.REGIONS}
    for j in sidx:
        s = skt[j]
        nudge_sets = {lvl: sd.nudges(s, lvl) for lvl in ("P", "B")}
        for r in cr.REGIONS:
            date, cut = sd.start(r, s)
            fr, alt, altc = {}, {date}, {}
            for lvl in ("P", "B"):
                tau = cr.TAU_S[lvl]
                got = [sd.start(r, s + sg * tau) for sg in (-1, 1)]
                if r != "ODIA":   # every other input nudged too: sunrise, sunset, the cut-off (spec decision 9)
                    for nd in nudge_sets[lvl]:
                        sd.nudge = nd
                        got.append(sd.start(r, s))
                    sd.nudge = None
                seen = {g[0] for g in got}
                fr[lvl] = bool(seen - {date})
                alt |= seen
                # the cut-off of the neighbouring panchang day when a nudge changes which day holds the sankranti
                altc[lvl] = sorted({g[1] for g in got if g[1] is not None and g[1] != cut})
            solar_rows.append([r, ski[j], s, cut, pr.date_str(date), fr["P"], fr["B"],
                               [pr.date_str(x) for x in sorted(alt)], altc["P"], altc["B"]])
            for lvl, k in (("P", "altCutP"), ("B", "altCutB")):
                sstat[r][k] += bool(altc[lvl])
            st = sstat[r]
            st["rows"] += 1
            st["fragP"] += fr["P"]
            st["fragB"] += fr["B"]
            st["near"] += (cut is not None and not fr["P"] and abs(s - cut) < 600_000)

    # ---- days fixture
    d0 = tsm._days_from_civil(2024, 1, 1)
    block = [d0 + 5 * i for i in range(1461)]
    rng = np.random.default_rng(SEED_BASE + ci)
    rand = [int(x) for x in rng.integers(tsm._days_from_civil(1900, 1, 2), tsm._days_from_civil(2099, 12, 30) + 1, 100)]
    alld = sorted(block + rand)
    assert len(block) == 1461 and len(alld) == 1561
    sd.ensure(set(alld))
    udays = {d for d in alld if sd.t[d]["U"]}   # M03 "U" days: no usable sunrise/sunset pair, null row
    okd = [d for d in alld if d not in udays]
    need = set()
    for d in okd:
        sr, ss, nsr = sd.get(d)
        _, idx = cr.solar_need(w, nsr)
        for j in idx:
            c = cday(skt[j])
            need.update(range(c - 2, c + 4))
    sd.ensure(need)
    # tithi at sunrise and at the perturbed instants
    srs = [sd.get(d)[0] for d in okd]
    tauB = [cr.TAU_L_BAD_B if (sd.t[d]["F"] or sd.t[d]["S"]) else cr.TAU_L["B"] for d in okd]
    shifts = []
    for sr, tb in zip(srs, tauB):
        shifts += [sr, sr - cr.TAU_L["P"], sr + cr.TAU_L["P"], sr - tb, sr + tb]
    ls, lm, _ay = sky.lons(np.array(shifts, dtype=np.int64))
    tith = (np.floor(((lm - ls) % 360.0) / 12.0).astype(np.int64) + 1).reshape(-1, 5)

    def fields(sr, nsr, d, T, lunar=True, solar=True):
        f = {}
        if lunar:
            f.update(w.lunar_fields(sr, T))
        if solar:
            f.update(cr.solar_fields(w, sd, d, sr, nsr))
        return f

    day_rows = []
    fstat = {f: dict(fragP=0, fragB=0) for f in FIELDS}
    anyP = anyB = 0
    rowof = {}
    for n, d in enumerate(okd):
        sr, ss, nsr = sd.get(d)
        T = int(tith[n, 0])
        base = fields(sr, nsr, d, T)
        base["tithi"] = T
        frag = {}
        for lvl in ("P", "B"):
            tau = cr.TAU_S[lvl]
            sn, sk_s = sens[lvl]
            a0, a1 = bisect.bisect_left(w.NM, sr - 430 * DAY_MS), bisect.bisect_right(w.NM, sr + 100 * DAY_MS)
            k0, k1 = bisect.bisect_left(skt, sr - 430 * DAY_MS), bisect.bisect_right(skt, sr + 100 * DAY_MS)
            nitems = [("n", i) for i in range(a0, a1) if sn[i] or abs(w.NM[i] - sr) <= tau]
            _, sol_idx = cr.solar_need(w, nsr)
            lun_sk = {i for i in range(k0, k1) if sk_s[i]}
            sitems = [("s", i) for i in sorted(lun_sk | set(sol_idx))]
            alt = {}

            def note(f):
                for key, val in f.items():
                    if val != base[key]:
                        alt.setdefault(key, {dumps(cr.jv(base[key]))}).add(dumps(cr.jv(val)))

            for kind, i in pert.run(nitems, tau):
                note(fields(sr, nsr, d, T, lunar=True, solar=False))
            for kind, i in pert.run(sitems, tau):
                note(fields(sr, nsr, d, T, lunar=(i in lun_sk), solar=True))
            for i in sol_idx:   # sun events (sunrise, sunset, cut-off) of the sankranti days nudged too
                for nd in sd.nudges(skt[i], lvl):
                    sd.nudge = nd
                    note(fields(sr, nsr, d, T, lunar=False, solar=True))
                sd.nudge = None
            for T2 in {int(x) for x in (tith[n, 1:3] if lvl == "P" else tith[n, 3:5])} - {T}:
                note({**w.lunar_fields(sr, T2), "tithi": T2})
            frag[lvl] = {k: [json.loads(x) for x in sorted(v)] for k, v in sorted(alt.items())}
        sv = [base["shaka"], base["vikramChaitradi"], base["vikramKartikadi"], base["samvatsara"],
              base["samvatsaraTamil"], base["bangabda"], base["kollam"]]
        rowof[d] = [pr.date_str(d), sd.flags(d), sr, ss, nsr, T, cr.jv(base["amanta"]), cr.jv(base["purnimanta"]),
                         sv, base["ritu"], base["ayana"], [cr.jv(base["solar" + r]) for r in cr.REGIONS],
                         frag["P"], frag["B"]]
        # solar month start dates are ISO strings in the fixture
        rowof[d][11] = [[x[0], x[1], pr.date_str(x[2])] for x in rowof[d][11]]
        for fld in (frag["P"], frag["B"]):
            for k, alts in fld.items():
                if k.startswith("solar"):
                    alts[:] = [[a[0], a[1], pr.date_str(a[2])] for a in alts]
    for d in alld:
        day_rows.append(rowof.get(d) or [pr.date_str(d), sd.flags(d), None, None, None, None, None, None, None, None,
                                         None, None, {}, {}])
    for r in day_rows:
        anyP += bool(r[12])
        anyB += bool(r[13])
        for k in r[12]:
            fstat[k]["fragP"] += 1
        for k in r[13]:
            fstat[k]["fragB"] += 1
    flagdays = {c: sum(1 for d in set(alld) if sd.t[d][c]) for c in "FXSU"}
    return dict(ci=ci, name=name, off=off, solar=solar_rows, days=day_rows, sstat=sstat, fstat=fstat,
                anyP=anyP, anyB=anyB, flags=flagdays, dups=len(alld) - len(set(alld)))


# ---------------------------------------------------------------------------------------------- generate
def generate():
    t_start = time.time()
    _, ksha = oc.load_kernel()
    with Pool(12) as pool:
        nm, fm, sk = cr.build_world(pool)
        print(f"world: {len(nm)} new moons, {len(fm)} full moons, {len(sk)} sankrantis ({time.time() - t_start:.0f} s)")
        skt, ski = [x[1] for x in sk], [x[0] for x in sk]
        cities = pool.map(city_task, [(ci, nm, skt, ski) for ci in range(len(cr.CITY_NAMES))], chunksize=1)
    w = cr.World([x[0] for x in nm], skt, ski)
    sens = {lvl: cr.sens_flags(w, cr.TAU_S[lvl]) for lvl in ("P", "B")}
    lrows = lunation_rows(nm, fm, sk, w, sens)
    bad = []
    n_adh = sum(1 for r in lrows if r[6])
    kshaya = [r for r in lrows if r[7] is not None]
    lfragP, lfragB = sum(r[8] for r in lrows), sum(r[9] for r in lrows)
    print(f"lunations {len(lrows)}: adhika {n_adh}, kshaya {len(kshaya)}, fragile P {lfragP} B {lfragB} "
          f"({100 * lfragB / len(lrows):.3f} % of cap {100 * FRAGILE_CAP:.0f} %)")
    adhika = [(cr.MASA[r[5]], pr.date_str(r[0] // DAY_MS)) for r in lrows
              if r[6] and 2024 <= int(pr.date_str(r[0] // DAY_MS)[:4]) <= 2043]
    print("adhika masa 2024-2043 (masa, new moon UTC date): " + "; ".join(f"{m} {d}" for m, d in adhika))
    print("kshaya lunations: " + "; ".join(f"{cr.MASA[r[5]]}+{cr.MASA[r[7]]} {pr.date_str(r[0] // DAY_MS)}"
                                           for r in kshaya))
    if n_adh < MIN_ADHIKA:
        bad.append(f"adhika {n_adh} < {MIN_ADHIKA}")
    if len(kshaya) < MIN_KSHAYA:
        bad.append(f"kshaya {len(kshaya)} < {MIN_KSHAYA}")
    if lfragB / len(lrows) > FRAGILE_CAP:
        bad.append("lunations fragB share above cap")
    n_days = sum(len(c["days"]) for c in cities)
    n_solar = sum(len(c["solar"]) for c in cities)
    anyB = sum(c["anyB"] for c in cities)
    anyP = sum(c["anyP"] for c in cities)
    print(f"city-days {n_days}: fragile rows P {anyP} ({100 * anyP / n_days:.3f} %) B {anyB} "
          f"({100 * anyB / n_days:.3f} % of cap {100 * FRAGILE_CAP:.0f} %)")
    ftot = {f: [sum(c["fstat"][f]["fragP"] for c in cities), sum(c["fstat"][f]["fragB"] for c in cities)]
            for f in FIELDS}
    print("  per field (P, B): " + ", ".join(f"{f} {v[0]}/{v[1]}" for f, v in ftot.items()))
    # Cap per field (the analogue of "rule type"): a shifted solar month start marks every day of that month, so
    # the any-field row share (printed above) is the sum over the four regions and is reported, not capped.
    for f, v in ftot.items():
        if v[1] / n_days > FRAGILE_CAP:
            bad.append(f"days field {f} fragB share above cap")
    stot = {r: {k: sum(c["sstat"][r][k] for c in cities) for k in ("rows", "fragP", "fragB", "near", "altCutP", "altCutB")}
            for r in cr.REGIONS}
    near = sum(v["near"] for v in stot.values())
    print(f"solar rows {n_solar}: non-fragP rows within 600 s of the cut-off {near}")
    for r, v in stot.items():
        share = v["fragB"] / v["rows"]
        print(f"  {r:9s} rows {v['rows']} fragP {v['fragP']} fragB {v['fragB']} ({100 * share:.3f} %) near {v['near']} altCut P {v['altCutP']} B {v['altCutB']}")
        if share > FRAGILE_CAP:
            bad.append(f"solar {r} fragB share above cap")
    if near < MIN_NEAR_CUTOFF:
        bad.append(f"cut-off near-miss rows {near} < {MIN_NEAR_CUTOFF}")
    if n_days != 15610:
        bad.append(f"city-days {n_days} != 15610")
    for c in cities:
        print(f"  {c['name']:9s} sun-day flags {c['flags']} duplicate dates {c['dups']}")
    if bad:
        oc.die("; ".join(bad) + "; nothing written")
    cmeta = [{"name": cr.CITY_NAMES[i], "lat": cr.CITY_COORDS[cr.CITY_NAMES[i]][0],
              "lon": cr.CITY_COORDS[cr.CITY_NAMES[i]][1], "offsetMin": cr.CITY_OFFSETS[i], "seed": SEED_BASE + i,
              "cityDays": len(cities[i]["days"]), "duplicateDates": cities[i]["dups"], "solarRows": len(cities[i]["solar"]),
              "flagDays": cities[i]["flags"]} for i in range(len(cities))]
    common = {"genVersion": GEN_VERSION, "schema": 1, "versions": oc.versions(), "kernelSha256": ksha,
              "seedBase": SEED_BASE, "ayanamsa": "TRUE_LAHIRI", "boundaryMethod": "12 h grid + bisection to <= 2 ms",
              "tauSeconds": {"P": 30, "B": 600}, "tauTithiSeconds": {"P": 30, "B": 150, "BonFSdays": 600},
              "fragileCap": FRAGILE_CAP, "masaOrder": cr.MASA, "rateUnit": "arcsec/s"}
    lm_meta = dict(common, generator="gen_calendar.py", lunations=len(lrows), adhika=n_adh, kshaya=len(kshaya),
                   fragilePB=[lfragP, lfragB], worldNewMoons=len(nm), worldSankrantis=len(sk),
                   datesSha256=sha(",".join(pr.date_str(r[0] // DAY_MS) for r in lrows)),
                   adhika2024_2043=[list(x) for x in adhika],
                   kshayaLunations=[[cr.MASA[r[5]], cr.MASA[r[7]], pr.date_str(r[0] // DAY_MS)] for r in kshaya],
                   labelRule="purnimanta Krishna half: adhika -> (masa, adhika, null); else (masa of next nija "
                             "lunation, nija, null); Shukla half = amanta label (incl. kshaya)",
                   lunationRow="[newMoonMs, newMoonRate, fullMoonMs, fullMoonRate, [[into, ms, v]], masa, adhika, "
                               "kshaya|null, fragP, fragB]; into/masa/kshaya = Rashi/Masa ordinals (0 = MESHA/CHAITRA); "
                               "rates = elongation (new/full moon) and sidereal Sun (v) rate, arcsec/s; fragP/fragB: "
                               "label of the lunation changes when one new moon or sankranti moves by tau (30 s/600 s)")
    solar_dates = ",".join(r[4] for c in cities for r in c["solar"])
    day_dates = ",".join(r[0] for c in cities for r in c["days"])
    dm = dict(common, generator="gen_calendar.py", cityDays=n_days, cities=cmeta, datesSha256=sha(day_dates),
              regions=cr.REGIONS, ritu=cr.RITU, ayana=cr.AYANA,
              fragileRowsPB=[anyP, anyB], fragilePerField=ftot,
              dayRow="[date, flags(F,X,S,U as panchang_windows), srMs, ssMs, nextSrMs, tithiAtSunrise, "
                     "amanta[masa,adhika,kshaya|null], purnimanta[masa,adhika,kshaya|null], samvat[shaka, "
                     "vikramChaitradi, vikramKartikadi, samvatsara 1..60, samvatsaraTamil, bangabda, kollam], "
                     "ritu ordinal, ayana ordinal, solar[[month rashi ordinal, day, monthStartDate] x TAMIL, "
                     "MALAYALAM, BENGALI, ODIA], fragP, fragB]; frag = {field: [all values seen incl. base]} for "
                     "fields that change when one input moves (new moons/sankrantis by tauS, tithi boundary at "
                     "sunrise by tauL); fields: " + ",".join(FIELDS),
              datesPerCity="every 5th day 2024-01-01..2043-12-31 (1,461) + 100 draws of default_rng(20261118 + "
                           "cityIndex).integers(1900-01-02, 2099-12-30 inclusive), sorted, duplicates kept")
    sm = dict(common, generator="gen_calendar.py", solarRows=n_solar, cities=cmeta, datesSha256=sha(solar_dates),
              regions=cr.REGIONS, regionStats=stot, nearCutoffNonFragP=near,
              rules="TAMIL before_sunset (cutoff sunset), MALAYALAM before_three_fifths (3/5 of daylight), BENGALI "
                    "before_midnight_next (cutoff night midpoint), ODIA civil (civil date of the sankranti at the offset, cutoff null)",
              solarRow="[region, into (Rashi ordinal), sankrantiMs, cutoffMs|null, monthStartDate, fragP, fragB, "
                       "alt dates, altCutP[ms], altCutB[ms] (neighbouring-day cut-offs when a nudge changes which day holds the sankranti; not fragile)]; one row per sankranti 1950-01-01..2050-12-31 and region; fragility = date "
                       "changes when the sankranti moves by tau (30 s / 600 s) or a sunrise, sunset or cut-off of the days around it moves by tau (30 s / 150 s, 600 s on F/S sun days); alt includes the date")
    lt = '{"meta":' + dumps(lm_meta) + ',"lunations":[\n' + ",\n".join(dumps(r) for r in lrows) + "\n]}\n"
    dt = '{"meta":' + dumps(dm) + ',"cities":[\n' + ",\n".join(
        '{"name":"%s","offsetMin":%d,"days":[\n' % (c["name"], c["off"]) + ",\n".join(dumps(r) for r in c["days"])
        + "\n]}" for c in cities) + "\n]}\n"
    st = '{"meta":' + dumps(sm) + ',"cities":[\n' + ",\n".join(
        '{"name":"%s","offsetMin":%d,"rows":[\n' % (c["name"], c["off"]) + ",\n".join(dumps(r) for r in c["solar"])
        + "\n]}" for c in cities) + "\n]}\n"
    print(f"run time {time.time() - t_start:.0f} s")
    return {"calendar_lunations.json": lt, "calendar_days.json": dt, "calendar_solar.json": st}


def main():
    check = "--check" in sys.argv[1:]
    out = generate()
    bad = 0
    for name, text in out.items():
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
        raise SystemExit(f"gen_calendar --check: {bad} fixture(s) differ from the generator output")


if __name__ == "__main__":
    main()
