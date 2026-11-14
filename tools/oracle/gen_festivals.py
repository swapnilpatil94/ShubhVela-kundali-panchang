#!/usr/bin/env python
"""Festival oracle (docs/specs/M04.md sections 3.3-3.7 and 3.10, task B3). Dev tool only; offline; byte-reproducible.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_festivals.py            (writes festivals_oracle.json)
Check: tools/oracle/.venv/bin/python tools/oracle/gen_festivals.py --check    (regenerates, byte-compares, exits
       non-zero on any difference or a missing file)
Debug: ... --only delhi,london   (prints the gates for those cities, writes nothing)
Reads rules/festivals/*.json with its own validator (festival_ref.py); never reads Kotlin or engine output.
Synthetic places and dates only. Exits non-zero, writing nothing, if the fragile (Base) share exceeds 2 % for a
rule type, or a coverage minimum fails. One worker process per city.
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
import festival_ref as fr  # noqa: E402
import oracle_common as oc  # noqa: E402
import panchang_ref as pr  # noqa: E402
import timescale as tsm  # noqa: E402

GOLD = oc.RES / "golden"
RULES = oc.REPO / "rules" / "festivals"
OUT = "festivals_oracle.json"
GEN_VERSION = 1
FRAGILE_CAP = 0.02
MIN_FLAG_ROWS = 20
MIN_BHADRA_SHIFT = 5
DAY = fr.DAY
D_FIRST = tsm._days_from_civil(2024, 1, 1)
D_LAST = tsm._days_from_civil(2043, 12, 31)
LIMB_G0 = (tsm._days_from_civil(2023, 9, 1) * DAY) // pr.GRID_MS
LIMB_G1 = (tsm._days_from_civil(2044, 5, 1) * DAY) // pr.GRID_MS
SK_G0 = (tsm._days_from_civil(2022, 12, 1) * DAY - cr.WORLD_START) // cr.GRID_MS
SK_G1 = (tsm._days_from_civil(2045, 1, 1) * DAY - cr.WORLD_START) // cr.GRID_MS
K_START_MS = tsm._days_from_civil(2023, 11, 20) * DAY
K_END_MS = tsm._days_from_civil(2044, 2, 10) * DAY


def dumps(o):
    return json.dumps(o, sort_keys=True, separators=(",", ":"))


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ------------------------------------------------------------------------------------------------ world
def limb_slice(args):
    g0, g1 = args
    eph, _ = oc.load_kernel()
    sky = pr.Sky(eph, oc.timescale())
    f = pr.find_boundaries(sky, np.arange(g0, g1 + 1, dtype=np.int64))
    return {k: (f[k][0].tolist(), f[k][1].tolist()) for k in (0, 1, 2)}


def sank_slice(args):
    return cr.world_slice(args)[2]


def build_world(pool):
    n = LIMB_G1 - LIMB_G0
    cuts = [LIMB_G0 + n * i // 48 for i in range(49)]
    res = pool.map(limb_slice, [(cuts[i], cuts[i + 1]) for i in range(48)])
    tt = [t for r in res for t in r[0][0]]
    ti = [i for r in res for i in r[0][1]]
    nt = [t for r in res for t in r[2][0]]
    ni = [i for r in res for i in r[2][1]]
    kt = [t for r in res for t in r[1][0]]
    ki = [i for r in res for i in r[1][1]]
    p0 = ti.index(1)
    tt, ti = tt[p0:], ti[p0:]
    nlun = (len(tt) - 1) // 30
    tt, ti = tt[:30 * nlun + 1], ti[:30 * nlun + 1]
    assert all(b > a for a, b in zip(tt, tt[1:])) and all(b > a for a, b in zip(nt, nt[1:]))
    assert all(ti[i] == i % 30 + 1 for i in range(len(ti))), "tithi sequence broken"
    assert all(ni[i + 1] == (ni[i] + 1) % 27 for i in range(len(ni) - 1)), "nakshatra sequence broken"
    assert all(b > a for a, b in zip(kt, kt[1:])), "karana boundaries not increasing"
    assert all(ki[i + 1] == ki[i] % 60 + 1 for i in range(len(ki) - 1)), "karana sequence broken"
    m = SK_G1 - SK_G0
    cuts = [SK_G0 + m * i // 24 for i in range(25)]
    sk = [x for part in pool.map(sank_slice, [(cuts[i], cuts[i + 1]) for i in range(24)]) for x in part]
    sk.sort(key=lambda x: x[1])
    assert all(b[1] > a[1] and b[0] == (a[0] + 1) % 12 for a, b in zip(sk, sk[1:])), "sankranti sequence broken"
    return tt, nt, ni, [x[0] for x in sk], [x[1] for x in sk], kt, ki


# ------------------------------------------------------------------------------------------ perturbation
def label_targets(ctx, k, tau):
    W = ctx.W
    lo = bisect.bisect_left(W.SKT, W.NM[k] - tau)
    hi = bisect.bisect_right(W.SKT, W.NM[k + 3] + tau)
    out = []
    for j in range(lo, hi):
        for i in range(k, k + 4):
            if abs(W.SKT[j] - W.NM[i]) <= tau:
                for t in ((W.SKT, j, "S"), (W.NM, i, "S")):
                    if t not in out:
                        out.append(t)
    return out


def vishti_targets(ctx, t0, t1):
    kb = ctx.KB
    lo, hi = bisect.bisect_left(kb, t0 - 2 * DAY), bisect.bisect_right(kb, t1 + 2 * DAY)
    out = []
    for i in range(max(0, lo - 1), min(len(kb) - 1, hi + 1)):
        if ctx.is_vishti(ctx.KBI[i]):
            out += [(kb, i, "L"), (kb, i + 1, "L")]
    return out


def nak_targets(ctx, n, t0, t1):
    nb = ctx.NB
    lo, hi = bisect.bisect_left(nb, t0 - 2 * DAY), bisect.bisect_right(nb, t1 + 2 * DAY)
    out = []
    for i in range(max(0, lo - 1), min(len(nb), hi + 1)):
        if ctx.NBI[i] == n:
            out += [(nb, i, "L")] + ([(nb, i + 1, "L")] if i + 1 < len(nb) else [])
    return out


class Unit:
    """One decision: `run()` gives the record or None; `targets(lvl)` the one-at-a-time inputs (container, key, kind)."""

    def __init__(self, run, k=None, tb=(), nak=(), sk=(), moon=False, vb=None):
        self.run, self.k, self.tb, self.nak, self.sk, self.moon, self.vb = run, k, tb, nak, sk, moon, vb


def perturb(ctx, unit, base_dec, used, results):
    """results[lvl] = (fragile, seen dates) for a unit with a base record; runs one input at a time."""
    sd = ctx.sd
    near = range(base_dec[0] - 2, base_dec[0] + 3)       # S/U sun days around the decision make it fragile
    sunbad = any(sd.sunflag_any(d, "S") or sd.sunflag_any(d, "U") for d in near if d in used)
    fall = any(sd.sunflag_any(d, c) for d in used for c in "FSU")
    out = {}
    for lvl in ("P", "B"):
        tau_s = cr.TAU_S[lvl]
        tau_l = fr.TAU_L[lvl] if not (lvl == "B" and fall) else fr.TAU_L_BAD_B
        tg = []
        if unit.k is not None:
            tg += label_targets(ctx, unit.k, tau_s)
        tg += [(ctx.W.SKT, j, "S") for j in unit.sk]
        tg += [(ctx.TB, i, "L") for i in unit.tb]
        for nk in unit.nak:
            tg += nak_targets(ctx, *nk)
        if unit.vb is not None:
            tg += vishti_targets(ctx, *unit.vb)
        if unit.moon:
            tg += [(sd.moonc, d, "L") for d in sorted(used) if sd.moonc.get(d) is not None]
        frag, seen = False, set()
        for cont, key, kind in tg:
            orig = cont[key]
            tau = tau_s if kind == "S" else tau_l
            for sg in (-tau, tau):
                cont[key] = orig + sg
                try:
                    rec = unit.run()
                finally:
                    cont[key] = orig
                dec = fr.decision(rec)
                if dec != base_dec:
                    frag = True
                if rec is not None:
                    seen.add(rec["date"])
        if sunbad:
            frag = True
            seen |= {x for d in {base_dec[0]} for x in (d - 1, d, d + 1)}
        out[lvl] = (frag, seen)
    return out


def phantom_dates(ctx, unit):
    """Dates a base-None unit takes when a label input moves by tau (adhika / kshaya flips)."""
    out = {"P": set(), "B": set()}
    for lvl in ("P", "B"):
        for cont, key, kind in label_targets(ctx, unit.k, cr.TAU_S[lvl]):
            orig = cont[key]
            for sg in (-cr.TAU_S[lvl], cr.TAU_S[lvl]):
                cont[key] = orig + sg
                try:
                    rec = unit.run()
                finally:
                    cont[key] = orig
                if rec is not None:
                    out[lvl].add(rec["date"])
    return out


# ------------------------------------------------------------------------------------------ city worker
def make_units(ctx, df, lo, hi):
    r, adh = df["rule"], df["adhika"]
    t = r["type"]
    units = []
    kmin = next(k for k in range(ctx.nlun) if ctx.TB[30 * k] >= K_START_MS)
    kmax = max(k for k in range(ctx.nlun - 4) if ctx.TB[30 * k + 30] <= K_END_MS)
    if t in ("tithi_at", "weekday_tithi_combo"):
        wd = r.get("weekday")
        for k in range(kmin, kmax + 1):
            for T in r["tithi"]:
                def run(k=k, T=T):
                    rec = ctx.tithi_unit(r, adh, k, T)
                    if rec is not None and wd is not None and pr.weekday_of(rec["date"]) != wd:
                        return None
                    return rec
                x0, x1 = ctx.TB[30 * k + T - 1], ctx.TB[30 * k + T]
                units.append(Unit(run, k=k, tb=(30 * k + T - 1, 30 * k + T),
                                  nak=[(r["prefer"], x0, x1)] if r["prefer"] is not None else [],
                                  moon=r["kala"] == "moonrise", vb=(x0, x1) if r["bhadra"] else None))
    elif t == "ekadashi":
        for k in range(kmin, kmax + 1):
            for pk in (("shukla", "krishna") if r["paksha"] == "both" else (r["paksha"],)):
                T = 11 if pk == "shukla" else 26
                units.append(Unit(lambda k=k, pk=pk: ctx.ekadashi_unit(r, adh, k, pk), k=k,
                                  tb=tuple(range(30 * k + T - 1, 30 * k + T + 2))))
    elif t == "sankranti":
        W = ctx.W
        for j in range(len(W.SKT)):
            if (r["into"] is None or W.SKI[j] == r["into"]) and \
                    lo - 3 <= ctx.civil(W.SKT[j]) <= hi + 3:
                units.append(Unit(lambda j=j: ctx.sankranti_unit(r, j), sk=(j,)))
    elif t == "nakshatra_in_solar_month":
        W = ctx.W
        for j in range(len(W.SKT) - 1):
            if W.SKI[j] == r["month"] and lo - 40 <= ctx.civil(W.SKT[j]) <= hi + 3:
                nk = [(r["nakshatra"], W.SKT[j], W.SKT[j + 1])]
                if r["preceded"] is not None:
                    nk.append((r["preceded"], W.SKT[j] - 30 * DAY, W.SKT[j + 1]))
                fm = ()
                if r["occurrence"] == "nearest_full_moon":
                    k0 = max(0, bisect.bisect_left(ctx.TB, W.SKT[j] - 40 * DAY) // 30)
                    k1 = min(ctx.nlun - 1, bisect.bisect_right(ctx.TB, W.SKT[j + 1] + 40 * DAY) // 30 + 1)
                    fm = tuple(30 * k + 15 for k in range(k0, k1 + 1))
                units.append(Unit(lambda j=j: ctx.nakshatra_unit(r, j), sk=(j, j + 1), nak=nk, tb=fm))
    return units


def fixed_rows(df, lo, hi):
    out = []
    r = df["rule"]
    import datetime
    for y in range(2023, 2045):
        try:
            d = datetime.date(y, r["month"], r["day"]).toordinal() - 719163
        except ValueError:
            continue
        if lo <= d <= hi:
            out.append(dict(date=d, label=None, tithi=None, kala=None, kw=None, muh=None, par=None, flags=set(),
                            fragP=False, fragB=False, altP={d}, altB={d}))
    return out


def eval_def(ctx, df, by_id, lo=D_FIRST, hi=D_LAST):
    t = df["rule"]["type"]
    if t == "fixed_gregorian":
        return fixed_rows(df, lo, hi), 0
    if t == "relative":
        r = df["rule"]
        base, orph = eval_def(ctx, by_id[r["to"]], by_id, lo - r["days"], hi - r["days"])
        out = []
        for b in base:   # label and flags of the base occurrence; no kala, windows or tithi
            n = r["days"]
            out.append(dict(date=b["date"] + n, label=b["label"], tithi=None, kala=None, kw=None, muh=None,
                            par=None, flags=set(b["flags"]), fragP=b["fragP"], fragB=b["fragB"],
                            altP={x + n for x in b["altP"]}, altB={x + n for x in b["altB"]}))
        return out, orph
    recs, phantoms = [], {"P": set(), "B": set()}
    for u in make_units(ctx, df, lo, hi):
        ctx.sd.used.clear()
        rec = u.run()
        used = set(ctx.sd.used)
        if rec is None:
            if u.k is not None:
                ph = phantom_dates(ctx, u)
                for lvl in ph:
                    phantoms[lvl] |= {x for x in ph[lvl] if lo <= x <= hi}
            continue
        if not lo <= rec["date"] <= hi:
            continue
        res = perturb(ctx, u, fr.decision(rec), used, None)
        rec["fragP"], rec["altP"] = res["P"][0], res["P"][1] | {rec["date"]}
        rec["fragB"], rec["altB"] = res["B"][0], res["B"][1] | {rec["date"]}
        recs.append(rec)
    seen, rows = set(), []
    for rec in recs:           # dedupe (id, date): the first unit in time order wins
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
    ci, defs, tb, nbm, nbi, sk_i, sk_t, kb, kbi = args
    eph, _ = oc.load_kernel()
    ts = oc.timescale()
    name, off = cr.CITY_NAMES[ci], cr.CITY_OFFSETS[ci]
    sd = fr.City(eph, ts, name, off)
    sd.sunflag_any = lambda d, c: bool(sd.t.get(d) and sd.t[d][c])
    t0 = time.time()
    sd.ensure(range(D_FIRST - 110, D_LAST + 70))
    nlun = (len(tb) - 1) // 30
    w = cr.World([tb[30 * k] for k in range(nlun + 1)], sk_t, sk_i)
    ctx = fr.Ctx(sd, list(tb), list(nbm), list(nbi), w, list(kb), list(kbi))
    ctx.sunflag = lambda d, c: sd.sunflag_any(d, c)
    # moonrise days (every def with kala moonrise): around each span of its tithis
    need = set()
    for df in defs:
        r = df["rule"]
        if r["type"] in ("tithi_at", "weekday_tithi_combo") and r["kala"] == "moonrise":
            for k in range(nlun - 1):
                for T in r["tithi"]:
                    if K_START_MS <= tb[30 * k] <= K_END_MS:
                        need.update(range(sd.containing(tb[30 * k + T - 1]) - 3, sd.containing(tb[30 * k + T]) + 4))
    sd.ensure(need)
    sd.moon_batch(need)
    prep = time.time() - t0
    per_def, orphans = {}, 0
    by_id = {d["id"]: d for d in defs}
    for df in defs:
        rows, o = eval_def(ctx, df, by_id)
        per_def[df["id"]] = rows
        orphans += o
    flagdays = {c: sum(1 for d in range(D_FIRST, D_LAST + 1) if sd.t.get(d) and sd.t[d][c]) for c in "FXSU"}
    return dict(ci=ci, rows=per_def, orphans=orphans, prep=prep, total=time.time() - t0, flags=flagdays)


# ---------------------------------------------------------------------------------------------- generate
def row_of(df, name, rec):
    lab = rec["label"]
    kw, muh, par = rec["kw"], rec["muh"], rec["par"]
    return [df["id"], pr.date_str(rec["date"]), None if lab is None else [lab[0], lab[1], lab[2]], rec["tithi"],
            rec["kala"], kw[0] if kw else None, kw[1] if kw else None,
            muh[0] if muh else None, muh[1] if muh else None, muh[2] if muh else None, muh[3] if muh else None,
            par[0] if par else None, par[1] if par else None, par[2] if par else None, par[3] if par else None,
            sorted(rec["flags"]), rec["fragP"], rec["fragB"], [pr.date_str(x) for x in sorted(rec["altP"])],
            [pr.date_str(x) for x in sorted(rec["altB"])]]


def generate(only=None):
    t_start = time.time()
    defs, hashes = fr.load_rules(RULES)
    _, ksha = oc.load_kernel()
    cities = [i for i, n in enumerate(cr.CITY_NAMES) if only is None or n in only]
    with Pool(12) as pool:
        tb, nbm, nbi, ski, skt, kb, kbi = build_world(pool)
        print(f"world: {(len(tb) - 1) // 30} lunations, {len(nbm)} nakshatra boundaries, {len(skt)} sankrantis "
              f"({time.time() - t_start:.0f} s)")
        res = pool.map(city_task, [(ci, defs, tb, nbm, nbi, ski, skt, kb, kbi) for ci in cities], chunksize=1)
    by_type = {}
    for df in defs:
        by_type.setdefault(df["rule"]["type"], []).append(df["id"])
    type_of = {df["id"]: df["rule"]["type"] for df in defs}
    # a relative row repeats the fragility of its base occurrence (no input of its own): it is counted under the
    # base def's rule type for the cap, and reported on its own below
    for df in defs:
        if df["rule"]["type"] == "relative":
            type_of[df["id"]] = type_of[df["rule"]["to"]]
    rel = {df["id"]: df["rule"]["to"] for df in defs if df["rule"]["type"] == "relative"}
    relstat = {i: [0, 0, 0] for i in rel}
    bad, rows_out, all_rows = [], [], []
    stat = {t: [0, 0, 0] for t in by_type}
    flagc = {}
    per_dcy = {}
    for c in res:
        name = cr.CITY_NAMES[c["ci"]]
        lines = []
        for df in defs:
            for rec in c["rows"][df["id"]]:
                row = row_of(df, name, rec)
                lines.append((rec["date"], df["id"], row))
                if df["id"] in relstat:
                    relstat[df["id"]][0] += 1
                    relstat[df["id"]][1] += rec["fragP"]
                    relstat[df["id"]][2] += rec["fragB"]
                s = stat[type_of[df["id"]]]
                s[0] += 1
                s[1] += rec["fragP"]
                s[2] += rec["fragB"]
                for f in rec["flags"]:
                    flagc[f] = flagc.get(f, 0) + 1
                per_dcy[(df["id"], name, pr.date_str(rec["date"])[:4])] = per_dcy.get((df["id"], name, pr.date_str(rec["date"])[:4]), 0) + 1
        lines.sort(key=lambda x: (x[0], x[1]))
        rows_out.append((name, c["ci"], [x[2] for x in lines]))
        print(f"  {name:9s} rows {len(lines)} orphan phantoms {c['orphans']} sun-day flags {c['flags']} "
              f"(prep {c['prep']:.0f} s, total {c['total']:.0f} s)")
    n_rows = sum(len(r[2]) for r in rows_out)
    print(f"rows {n_rows}; flag counts {dict(sorted(flagc.items()))}")
    for t, (n, fp, fb) in stat.items():
        share = fb / n if n else 0.0
        print(f"  rule type {t:26s} rows {n} fragP {fp} fragB {fb} ({100 * share:.3f} % of cap {100 * FRAGILE_CAP:.0f} %)")
        if share > FRAGILE_CAP:
            bad.append(f"rule type {t} fragB share above cap")
    for i, (n, fp, fb) in relstat.items():
        print(f"  relative {i} (inherits {rel[i]}): rows {n} fragP {fp} fragB {fb} ({100 * fb / n:.3f} %)")
    for f in ("TIE_BROKEN", "KSHAYA_TITHI", "DASHAMI_VIDDHA", "ADHIKA_MASA"):
        if flagc.get(f, 0) < MIN_FLAG_ROWS:
            bad.append(f"{f} rows {flagc.get(f, 0)} < {MIN_FLAG_ROWS}")
    shifts = {}
    for _n, _ci, rr in rows_out:
        for x in rr:
            if "BHADRA_SHIFT" in x[15]:
                shifts[x[0]] = shifts.get(x[0], 0) + 1
    print(f"BHADRA_SHIFT rows {shifts}")
    if only is None:
        for fid in ("raksha_bandhan", "holika_dahan"):
            if shifts.get(fid, 0) < MIN_BHADRA_SHIFT:
                bad.append(f"BHADRA_SHIFT {fid} rows {shifts.get(fid, 0)} < {MIN_BHADRA_SHIFT}")
    soft = GOLD / "festivals_published_soft.json"
    delhi_rows = next((r for r in rows_out if r[0] == "delhi"), None)
    if soft.is_file() and delhi_rows is not None:
        entries = json.loads(soft.read_text())["entries"]
        have = {(x[0], x[1]) for x in delhi_rows[2]}
        miss = [(i, d) for i, d in entries if (i, d) not in have]
        print(f"published soft list (Delhi, information only): {len(entries) - len(miss)}/{len(entries)} match")
        for i, d in miss:
            near = sorted(x[1] for x in delhi_rows[2] if x[0] == i and x[1][:4] == d[:4])
            print(f"  mismatch {i} published {d} oracle {near}")
    if only is None:
        two = sum(1 for v in per_dcy.values() if v >= 2)
        zero = len(defs) * len(cities) * 20 - len(per_dcy)
        print(f"(def, city, year) with 2+ occurrences {two}; with 0 occurrences {zero}")
        if two < 1 or zero < 1:
            bad.append("coverage: need a (def, city, year) with 2 and one with 0 occurrences")
    for c in res:
        if c["orphans"]:
            bad.append("orphan phantom occurrences")
            break
    delhi = next((r for r in rows_out if r[0] == "delhi"), None)
    if delhi:
        keys = {"diwali_lakshmi_puja", "holi", "krishna_janmashtami", "maha_shivratri", "ganesh_chaturthi",
                "rama_navami", "makar_sankranti", "vijayadashami", "raksha_bandhan"}
        for y in ("2024", "2025", "2026"):
            print("  delhi " + y + ": " + ", ".join(f"{x[0]} {x[1]}" for x in delhi[2]
                                                  if x[0] in keys and x[1].startswith(y)))
    if bad:
        oc.die("; ".join(bad) + "; nothing written")
    cmeta = [{"name": cr.CITY_NAMES[ci], "lat": cr.CITY_COORDS[cr.CITY_NAMES[ci]][0],
              "lon": cr.CITY_COORDS[cr.CITY_NAMES[ci]][1], "offsetMin": cr.CITY_OFFSETS[ci],
              "rows": len(next(r for r in rows_out if r[1] == ci)[2])} for ci in cities]
    dates = "|".join(f"{n}:{x[0]}:{x[1]}" for n, _, rr in rows_out for x in rr)
    meta = {"genVersion": GEN_VERSION, "schema": 1, "generator": "gen_festivals.py", "versions": oc.versions(),
            "kernelSha256": ksha, "ayanamsa": "TRUE_LAHIRI", "range": "2024-01-01..2043-12-31",
            "rulesSha256": hashes, "datesSha256": sha(dates), "rows": n_rows, "defs": [d["id"] for d in defs],
            "cities": cmeta, "fragileCap": FRAGILE_CAP, "tauSeconds": {"P": 30, "B": 600},
            "tauTithiSeconds": {"P": 30, "B": 150, "BonFSdays": 600},
            "fragileByRuleType": {t: {"rows": s[0], "fragP": s[1], "fragB": s[2]} for t, s in stat.items()},
            "flagCounts": dict(sorted(flagc.items())),
            "labels": "[masa ordinal 0 = CHAITRA, adhika, kshaya masa|null]; purnimanta Krishna half has kshaya null",
            "row": "[id, date (panchang day), label|null, tithi|null, kala|null, kalaStartMs, kalaEndMs (point kala: "
                   "equal), muhuratStartMs, muhuratEndMs, muhuratStartSrc, muhuratEndSrc (L = limb boundary, S = "
                   "sun-derived), paranaStartMs, paranaEndMs, paranaStartSrc, paranaEndSrc, flags (sorted OccFlag "
                   "names), fragP, fragB, altP, altB]; the kala of an Ekadashi row is the kala of the fast day "
                   "(sunrise smarta, arunodaya vaishnava); moonrise kala instants are Moon-derived; sankranti rows "
                   "have kala null and kalaStart = kalaEnd = the sankranti instant; muhurat only for a non-empty "
                   "interval kala; nakshatra rows: kala of the rule",
            "fragility": "one input at a time +-tau: tithi/nakshatra boundaries and moonrise tauL (30 s | 150 s, "
                         "600 s on F/S sun days), sankrantis and new moons tauS (30 s | 600 s); fragile = (date, label, "
                         "flags) changes; alt = every date seen incl. the base date; sun-day S/U flags within 2 days of the date "
                         "mark the row fragile with date +-1 added; a unit absent in the base run whose label flips "
                         "adds its date to the nearest fragile row of the same id"}
    text = '{"meta":' + dumps(meta) + ',"cities":[\n' + ",\n".join(
        '{"name":"%s","offsetMin":%d,"rows":[\n' % (n, cr.CITY_OFFSETS[ci]) + ",\n".join(dumps(x) for x in rr)
        + "\n]}" for n, ci, rr in rows_out) + "\n]}\n"
    print(f"run time {time.time() - t_start:.0f} s")
    return {OUT: text}


def main():
    argv = sys.argv[1:]
    check = "--check" in argv
    only = None
    if "--only" in argv:
        only = set(argv[argv.index("--only") + 1].split(","))
    out = generate(only)
    if only:
        print("--only: nothing written")
        return
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
        raise SystemExit("gen_festivals --check: fixture differs from the generator output")


if __name__ == "__main__":
    main()
