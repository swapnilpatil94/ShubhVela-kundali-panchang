"""Festival reference evaluator (docs/specs/M04.md sections 3.3-3.7, 3.10, task B3). Dev tool only.

Independent of the Kotlin engine and of engine output: the rule files are read by the validator below (its own
code, not a port of the Kotlin parser) and the evaluation order, kala windows, tie rules, Ekadashi and solar rules
are restated from the spec text. World inputs are integer UTC epoch milliseconds: tithi boundaries TB, nakshatra
boundaries NB (panchang_ref.find_boundaries), sankrantis (calendar_ref.world_slice), sunrise/sunset per local civil
day (calendar_ref.SunDays) and moonrise (gr.refine). Dates are days since 1970-01-01 (ints).

Fragility by perturbation (spec 3.10): every decision is recomputed with ONE input instant moved by +-tau; a change
of (date, label, flags) marks the row fragile and the dates seen form `alt`.
"""
import bisect
import json
import re
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import calendar_ref as cr  # noqa: E402
import gen_riseset as gr  # noqa: E402
import panchang_ref as pr  # noqa: E402

DAY = 86_400_000
HOUR = 3_600_000
ARUNODAYA_MS = 5_760_000
DAY_KALAS = {"sunrise", "arunodaya", "madhyahna", "aparahna"}
RASHI = ["MESHA", "VRISHABHA", "MITHUNA", "KARKA", "SIMHA", "KANYA", "TULA", "VRISHCHIKA", "DHANU", "MAKARA",
         "KUMBHA", "MEENA"]
MASA_LC = [m.lower() for m in cr.MASA]
KALAS = ["sunrise", "arunodaya", "pradosh", "nishita", "madhyahna", "aparahna", "moonrise"]
TIES = ["first", "second", "max_coverage"]
REGIONS_OK = {"all", "north", "west", "mr", "gu", "south", "ta", "ml", "bn", "or", "te", "kn"}
SANK_DAYS = ["civil", "before_sunset", "before_three_fifths", "before_midnight_next"]
SANK_REGION = {"before_sunset": "TAMIL", "before_three_fifths": "MALAYALAM", "before_midnight_next": "BENGALI"}
ID_RE = re.compile(r"[a-z0-9_]{1,40}\Z")
MAX_CHARS = 262_144
TAU_S = cr.TAU_S                      # {"P": 30 s, "B": 600 s}
TAU_L = {"P": 30_000, "B": 150_000}   # Base 600 s on F/S days (spec 3.10)
TAU_L_BAD_B = 600_000
LEAP = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


# ------------------------------------------------------------------------------------------------ validator
class RuleError(ValueError):
    pass


def _bad(code, path):
    raise RuleError(f"{code} {path}")


def _keys(o, allowed, path, required=()):
    if not isinstance(o, dict):
        _bad("NOT_OBJECT", path)
    for k in o:
        if k not in allowed:
            _bad("UNKNOWN_KEY", f"{path}.{k}")
    for k in required:
        if k not in o:
            _bad("MISSING_FIELD", f"{path}.{k}")


def _enum(v, allowed, path):
    if not isinstance(v, str) or v not in allowed:
        _bad("BAD_VALUE", path)
    return v


def _int(v, lo, hi, path):
    if isinstance(v, bool) or not isinstance(v, int):
        _bad("BAD_TYPE", path)
    if not lo <= v <= hi:
        _bad("OUT_OF_RANGE", path)
    return v


def _masa(v, path):
    return None if v == "any" else MASA_LC.index(_enum(v, MASA_LC, path))


def _bhadra(v, path):
    """None (absent) or a non-empty list of steps: ("free_in", kala) | ("after",) | ("next", ghatis)."""
    if v is None:
        return None
    if not isinstance(v, list) or not v:
        _bad("BAD_LENGTH", path)
    out = []
    for i, st in enumerate(v):
        p = f"{path}[{i}]"
        if st == "after_bhadra_before_midnight":
            out.append(("after",))
        elif isinstance(st, dict) and set(st) == {"free_in"}:
            out.append(("free_in", _enum(st["free_in"], KALAS, p + ".free_in")))
        elif isinstance(st, dict) and set(st) == {"next_day_min_ghatis"}:
            out.append(("next", _int(st["next_day_min_ghatis"], 1, 60, p + ".next_day_min_ghatis")))
        else:
            _bad("BAD_VALUE", p)
    return out


def _rule(r, path):
    if not isinstance(r, dict) or "type" not in r:
        _bad("MISSING_FIELD" if isinstance(r, dict) else "NOT_OBJECT", path + ".type")
    t = r["type"]
    if t in ("tithi_at", "weekday_tithi_combo"):
        extra = ["weekday"] if t == "weekday_tithi_combo" else []
        _keys(r, {"type", "tithi", "masa", "system", "kala", "tie", "none", "bhadra"} | set(extra), path,
              ["tithi", "masa", "kala"] + extra)
        tith = r["tithi"] if isinstance(r["tithi"], list) else [r["tithi"]]
        if not tith:
            _bad("BAD_LENGTH", path + ".tithi")
        tith = [_int(x, 1, 30, path + ".tithi") for x in tith]
        if len(set(tith)) != len(tith):
            _bad("DUPLICATE", path + ".tithi")
        tie, prefer = r.get("tie", "first"), None
        if isinstance(tie, dict):
            _keys(tie, {"prefer_nakshatra", "else"}, path + ".tie", ["prefer_nakshatra", "else"])
            prefer = _int(tie["prefer_nakshatra"], 0, 26, path + ".tie.prefer_nakshatra")
            tie = tie["else"]
        out = {"type": "tithi_at", "tithi": tith, "masa": _masa(r["masa"], path + ".masa"),
               "system": _enum(r.get("system", "amanta"), ["amanta", "purnimanta"], path + ".system").upper(),
               "kala": _enum(r["kala"], KALAS, path + ".kala"), "tie": _enum(tie, TIES, path + ".tie"),
               "prefer": prefer, "none": _enum(r.get("none", "containing_start"), ["containing_start", "sunrise", "skip"],
                                               path + ".none"), "bhadra": _bhadra(r.get("bhadra"), path + ".bhadra")}
        if extra:
            out["weekday"] = pr.VAAR.index(_enum(r["weekday"], pr.VAAR, path + ".weekday"))
        return out
    if t == "ekadashi":
        _keys(r, {"type", "tradition", "masa", "system", "paksha"}, path, ["tradition", "masa", "paksha"])
        return {"type": "ekadashi", "tradition": _enum(r["tradition"], ["smarta", "vaishnava"], path + ".tradition"),
                "masa": _masa(r["masa"], path + ".masa"),
                "system": _enum(r.get("system", "amanta"), ["amanta", "purnimanta"], path + ".system").upper(),
                "paksha": _enum(r["paksha"], ["shukla", "krishna", "both"], path + ".paksha")}
    if t == "relative":
        _keys(r, {"type", "to", "days"}, path, ["to", "days"])
        n = _int(r["days"], -3, 3, path + ".days")
        if n == 0:
            _bad("OUT_OF_RANGE", path + ".days")
        if not isinstance(r["to"], str) or not ID_RE.match(r["to"]):
            _bad("BAD_VALUE", path + ".to")
        return {"type": "relative", "to": r["to"], "days": n}
    if t == "sankranti":
        _keys(r, {"type", "into", "day"}, path, ["into", "day"])
        into = None if r["into"] == "any" else RASHI.index(_enum(r["into"], RASHI, path + ".into"))
        return {"type": "sankranti", "into": into, "day": _enum(r["day"], SANK_DAYS, path + ".day")}
    if t == "nakshatra_in_solar_month":
        _keys(r, {"type", "nakshatra", "month", "region", "kala", "occurrence", "preceded_in_month_by"}, path,
              ["nakshatra", "month", "region", "kala", "occurrence"])
        return {"type": t, "nakshatra": _int(r["nakshatra"], 0, 26, path + ".nakshatra"),
                "month": RASHI.index(_enum(r["month"], RASHI, path + ".month")),
                "region": _enum(r["region"], ["tamil", "malayalam", "bengali", "odia"], path + ".region").upper(),
                "kala": _enum(r["kala"], KALAS + ["ahoratra"], path + ".kala"),
                "occurrence": _enum(r["occurrence"], ["first", "last", "nearest_full_moon"], path + ".occurrence"),
                "preceded": None if "preceded_in_month_by" not in r
                else _int(r["preceded_in_month_by"], 0, 26, path + ".preceded_in_month_by")}
    if t == "fixed_gregorian":
        _keys(r, {"type", "month", "day"}, path, ["month", "day"])
        m = _int(r["month"], 1, 12, path + ".month")
        return {"type": t, "month": m, "day": _int(r["day"], 1, LEAP[m - 1], path + ".day")}
    _bad("BAD_VALUE", path + ".type")


def load_rules(rule_dir):
    """Own validator of rules/festivals/*.json. Returns (defs, {file: sha256 of the bytes})."""
    import hashlib
    defs, hashes, seen = [], {}, set()
    for p in sorted(Path(rule_dir).glob("*.json")):
        raw = p.read_bytes()
        hashes[p.name] = hashlib.sha256(raw).hexdigest()
        if len(raw.decode("utf-8")) > MAX_CHARS:
            _bad("TOO_LARGE", p.name)
        root = json.loads(raw)
        _keys(root, {"version", "status", "sourceId", "festivals"}, p.name, ["version", "status", "sourceId",
                                                                              "festivals"])
        if root["version"] != 1 or isinstance(root["version"], bool):
            _bad("OUT_OF_RANGE", p.name + ".version")
        _enum(root["status"], ["APPROVED", "VERIFY"], p.name + ".status")
        if not isinstance(root["sourceId"], str) or not root["sourceId"].strip():
            _bad("BLANK", p.name + ".sourceId")
        file_ids = {}
        for i, d in enumerate(root["festivals"]):
            path = f"{p.name}.festivals[{i}]"
            _keys(d, {"id", "name", "status", "sourceId", "regions", "rule", "adhika", "vratRules"}, path,
                  ["id", "name", "status", "sourceId", "regions", "rule"])
            if not isinstance(d["id"], str) or not ID_RE.match(d["id"]):
                _bad("BAD_VALUE", path + ".id")
            if d["id"] in seen:
                _bad("DUPLICATE", path + ".id")
            seen.add(d["id"])
            if d["name"] != "term:fest_" + d["id"]:
                _bad("BAD_VALUE", path + ".name")
            _enum(d["status"], ["APPROVED", "VERIFY"], path + ".status")
            if not isinstance(d["sourceId"], str) or not d["sourceId"].strip():
                _bad("BLANK", path + ".sourceId")
            if not d["regions"] or any(x not in REGIONS_OK for x in d["regions"]):
                _bad("BAD_VALUE", path + ".regions")
            rule = _rule(d["rule"], path + ".rule")
            anym = rule["type"] in ("tithi_at", "weekday_tithi_combo", "ekadashi") and rule["masa"] is None
            adh = _enum(d.get("adhika", "both" if anym else "nija"), ["nija", "adhika", "both"], path + ".adhika")
            file_ids[d["id"]] = rule["type"]
            defs.append({"id": d["id"], "rule": rule, "adhika": adh.upper(), "regions": d["regions"],
                         "file": p.name})
        for i, d in enumerate(d for d in defs if d["file"] == p.name):
            if d["rule"]["type"] == "relative" and file_ids.get(d["rule"]["to"], "relative") == "relative":
                _bad("BAD_VALUE", f"{p.name}.{d['id']}.rule.to")
    return defs, hashes


# ------------------------------------------------------------------------------------------- world per city
class City(cr.SunDays):
    """SunDays whose `get` never raises: an unavailable sun day gets local 06:00/18:00/06:00 (flagged U)."""

    def __init__(self, eph, ts, name, off_min):
        super().__init__(eph, ts, name, off_min)
        self.used = set()
        self.moonc = {}

    def get(self, d):
        if d not in self.t:
            self._compute([d])
        self.used.add(d)
        e = self.t[d]
        if e["U"]:
            b = d * DAY - self.off * 60_000
            return b + 6 * HOUR, b + 18 * HOUR, b + 30 * HOUR
        return e["sr"], e["ss"], e["nsr"]

    def moon_batch(self, days):
        """First Moon rise in [sunrise, next sunrise) per day (upper limb, refracted), cached; None if none."""
        todo = sorted({d for d in days if d not in self.moonc})
        for i in range(0, len(todo), 600):
            self._moon(todo[i:i + 600])

    def _moon(self, days):
        flat = []
        for d in days:
            sr, _, nsr = self.get(d)
            n = (nsr - sr) // gr.STEP_MS + 2
            flat.append(np.minimum(sr + np.arange(n, dtype=np.int64) * gr.STEP_MS, nsr))
        allms = np.concatenate(flat)
        alt, dist = self.site.alt("moon", allms)
        f = alt - gr.h0("moon", "UPPER_LIMB_REFRACTED", dist)
        br, pos = [], 0
        for di, s in enumerate(flat):
            g = f[pos:pos + s.size]
            for k in np.nonzero((g[:-1] < 0) & (g[1:] >= 0))[0]:
                br.append((di, 0, 0, int(s[k]), int(s[k + 1])))
            pos += s.size
        first = {}
        for (di, _c, _k, ms, _r) in sorted(gr.refine(self.site, "moon", br), key=lambda e: (e[0], e[3])):
            nsr = int(flat[di][-1])
            if ms < nsr and di not in first:
                first[di] = ms
        for di, d in enumerate(days):
            self.moonc[d] = first.get(di)


def part(a, b, n, i):
    return pr.part_point(a, b, n, i)


def cover(win, s, e):
    a, b, pt = win
    if pt:
        return 1 if s <= a < e else 0
    return max(0, min(b, e) - max(a, s))


class Ctx:
    """Everything one city's evaluation reads. The lists are mutated (and restored) by the perturbation loop."""

    def __init__(self, sd, tb, nb, nbi, w, kb=(), kbi=()):
        self.sd, self.TB, self.NB, self.NBI, self.W = sd, tb, nb, nbi, w
        self.KB, self.KBI = kb, kbi
        self.off = sd.off
        self.nlun = (len(tb) - 1) // 30

    # -- kala windows (spec 3.4)
    def window(self, d, kala):
        sr, ss, nsr = self.sd.get(d)
        if kala == "sunrise":
            return (sr, sr, True)
        if kala == "arunodaya":
            return (sr - ARUNODAYA_MS, sr, False)
        if kala == "ahoratra":
            return (sr, nsr, False)
        if kala == "pradosh":
            return (ss, part(ss, nsr, 15, 3), False)
        if kala == "nishita":
            return (part(ss, nsr, 15, 7), part(ss, nsr, 15, 8), False)
        if kala == "madhyahna":
            return (part(sr, ss, 5, 2), part(sr, ss, 5, 3), False)
        if kala == "aparahna":
            return (part(sr, ss, 5, 3), part(sr, ss, 5, 4), False)
        if d not in self.sd.moonc:
            self.sd.moon_batch([d])
        m = self.sd.moonc[d]
        return None if m is None else (m, m, True)

    def nak_cover(self, win, n):
        """Best (coverage, start, end) of the nakshatra-n spans touching the window, or None."""
        if win is None:
            return None
        nb, nbi = self.NB, self.NBI
        i0 = bisect.bisect_right(nb, win[0]) - 1
        best = None
        for i in range(max(0, i0 - 1), min(len(nb) - 1, i0 + 3)):
            if nbi[i] == n:
                c = cover(win, nb[i], nb[i + 1])
                if c > 0 and (best is None or c > best[0]):
                    best = (c, nb[i], nb[i + 1])
        return best

    @staticmethod
    def is_vishti(idx):
        """Karana index 1..60 (1 = Kimstughna, 2..57 the 7-cycle Bava..Vishti, 58..60 fixed): Vishti = 8, 15, ..."""
        return 8 <= idx <= 57 and (idx - 8) % 7 == 0

    def vishti(self, t0, t1):
        """Vishti (bhadra) spans [s, e) overlapping [t0, t1), time order."""
        kb = self.KB
        i = max(0, bisect.bisect_right(kb, t0) - 1)
        out = []
        while i + 1 < len(kb) and kb[i] < t1:
            if self.is_vishti(self.KBI[i]) and kb[i + 1] > t0:
                out.append((kb[i], kb[i + 1]))
            i += 1
        return out

    def label(self, k, T, system):
        return self.W.lun(k) if system == "AMANTA" else self.W.purnimanta(k, T)

    def civil(self, ms):
        return (ms + self.off * 60_000) // DAY

    def sank_date(self, kind, s):
        if kind == "civil":
            return self.civil(s)
        return self.sd.start(SANK_REGION[kind], s)[0]

    def sunflag(self, d, c):
        e = self.sd.t.get(d)
        return bool(e and e[c])

    # -- TithiAt (spec 3.6)
    def tithi_unit(self, r, adh, k, T):
        flags = set()
        lab = self.label(k, T, r["system"])
        if r["masa"] is not None and r["masa"] != lab[0]:
            if lab[2] is not None and r["masa"] == lab[2]:
                flags.add("KSHAYA_MASA")
            else:
                return None
        if (adh == "NIJA" and lab[1]) or (adh == "ADHIKA" and not lab[1]):
            return None
        x0, x1 = self.TB[30 * k + T - 1], self.TB[30 * k + T]
        sd, kala = self.sd, r["kala"]
        dlo, dhi = sd.containing(x0) - 1, sd.containing(x1) + 1
        wins, cov = {}, {}
        for d in range(dlo, dhi + 1):
            w = self.window(d, kala)
            wins[d] = w
            cov[d] = 0 if w is None else cover(w, x0, x1)
            if w is None:
                flags.add("MOONRISE_NONE")
        cplus = [d for d in range(dlo, dhi + 1) if cov[d] > 0]
        none_touched = False
        if len(cplus) == 1:
            d = cplus[0]
        elif len(cplus) >= 2:
            flags.add("TIE_BROKEN")
            if kala == "sunrise":
                flags.add("VRIDDHI_TITHI")
            pool = cplus
            if r["prefer"] is not None:
                cprime = [x for x in cplus if self.nak_cover(wins[x], r["prefer"]) is not None]
                if len(cprime) == 1:
                    pool = None
                    d = cprime[0]
                elif cprime:
                    pool = cprime
            if pool is not None:
                if r["tie"] == "first":
                    d = pool[0]
                elif r["tie"] == "second":
                    d = pool[1]
                else:
                    d = min(pool, key=lambda x: (-cov[x], x))
        else:
            flags.add("NONE_TOUCHED")
            none_touched = True
            if kala == "sunrise":
                flags.add("KSHAYA_TITHI")
            if r["none"] == "skip":
                return None
            d = sd.containing(x0)
            if r["none"] == "sunrise":
                d = next((x for x in range(dlo, dhi + 1) if x0 <= sd.get(x)[0] < x1), d)
            if d not in wins:
                wins[d] = self.window(d, kala)
                if wins[d] is None:
                    flags.add("MOONRISE_NONE")
        if not none_touched and any(self.sunflag(x, "F") for x in cplus):
            flags.add("SUN_FALLBACK")
        if lab[1]:
            flags.add("ADHIKA_MASA")
        win = wins[d]
        muh = None
        if win is not None and not win[2]:
            s, e = max(win[0], x0), min(win[1], x1)
            if s < e:
                muh = (s, e, "L" if x0 > win[0] else "S", "L" if x1 < win[1] else "S")
        if r.get("bhadra"):
            d, muh = self.bhadra(r, d, x0, x1, flags)
            win = self.window(d, kala)
        kw = None if win is None else (win[0], win[1])
        return {"date": d, "label": lab, "tithi": T, "kala": kala, "kw": kw, "muh": muh, "par": None,
                "flags": flags}

    @staticmethod
    def subtract(iv, vs):
        """Free pieces (start, end, srcStart, srcEnd) of iv = (a, b, ta, tb) after removing the Vishti spans vs."""
        a, b, ta, tb = iv
        out, cur, cta = [], a, ta
        for vs_, ve in vs:
            if ve <= cur:
                continue
            if vs_ >= b:
                break
            if vs_ > cur:
                out.append((cur, vs_, cta, "L"))
            cur, cta = ve, "L"
            if cur >= b:
                break
        if cur < b:
            out.append((cur, b, cta, tb))
        return [p for p in out if p[0] < p[1]]

    def bhadra(self, r, d, x0, x1, flags):
        """Spec 3.6 bhadra steps after the day d*; returns (date, muhurat|None). Steps in order, first success wins."""
        sd, kala = self.sd, r["kala"]
        vs = self.vishti(x0 - DAY, x1 + DAY)
        for si, st in enumerate(r["bhadra"]):
            res = None
            if st[0] == "free_in":
                w = self.window(d, st[1])
                if w is not None and not w[2]:
                    piece = self.subtract((max(w[0], x0), min(w[1], x1), "L" if x0 > w[0] else "S",
                                           "L" if x1 < w[1] else "S"), vs) if max(w[0], x0) < min(w[1], x1) else []
                    if piece:
                        res = (d, piece[0])
            elif st[0] == "after":
                _, ss, nsr = sd.get(d)
                ov = [v for v in vs if v[0] < nsr and v[1] > ss]
                if ov:
                    b, mid = ov[0][1], part(ss, nsr, 2, 1)
                    if b < mid and b < x1:
                        res = (d, (b, min(mid, x1), "L", "S" if mid <= x1 else "L"))
            else:
                n = d + 1
                sr_n, ss_n, _ = sd.get(n)
                if x0 <= sr_n and x1 - sr_n >= st[1] * 1_440_000:
                    if kala in DAY_KALAS:
                        res = (n, (sr_n, min(x1, ss_n), "S", "L" if x1 < ss_n else "S"))
                    else:
                        w = self.window(n, kala)
                        res = (n, None if w is None else (w[0], w[1], "S", "S"))
            if res is not None:
                if si > 0:
                    flags.add("BHADRA_SHIFT")
                return res
        flags.add("BHADRA_UNAVOIDABLE")
        return d, None

    # -- Ekadashi + parana (spec 3.7)
    def ekadashi_unit(self, r, adh, k, paksha):
        T = 11 if paksha == "shukla" else 26
        lab = self.label(k, T, r["system"])
        flags = set()
        if r["masa"] is not None and r["masa"] != lab[0]:
            if lab[2] is not None and r["masa"] == lab[2]:
                flags.add("KSHAYA_MASA")
            else:
                return None
        if (adh == "NIJA" and lab[1]) or (adh == "ADHIKA" and not lab[1]):
            return None
        sd, TB = self.sd, self.TB
        e0, e1 = TB[30 * k + T - 1], TB[30 * k + T]
        w0, w1 = TB[30 * k + T], TB[30 * k + T + 1]
        cplus = [d for d in range(sd.containing(e0) - 1, sd.containing(e1) + 2) if e0 <= sd.get(d)[0] < e1]
        if cplus:
            d0 = min(cplus)
            if len(cplus) >= 2:
                flags.add("VRIDDHI_TITHI")
        else:
            d0 = sd.containing(e0)
            flags.add("KSHAYA_TITHI")
        f = d0
        if r["tradition"] == "vaishnava" and e0 > sd.get(d0)[0] - ARUNODAYA_MS:
            f = d0 + 1
            flags.add("DASHAMI_VIDDHA")
        sr_f = sd.get(f)[0]
        sr_n = sd.get(f + 1)[0]
        quarter = w0 + (w1 - w0) // 4
        p0 = max(sr_n, quarter)
        par = None
        if w1 > p0:
            par = (p0, w1, "S" if sr_n >= quarter else "L", "L")
        else:
            flags.add("PARANA_UNDEFINED")
        if any(self.sunflag(x, "F") for x in set(cplus) | {d0}):
            flags.add("SUN_FALLBACK")
        if lab[1]:
            flags.add("ADHIKA_MASA")
        kala = "sunrise" if r["tradition"] == "smarta" else "arunodaya"
        kw = (sr_f, sr_f) if kala == "sunrise" else (sr_f - ARUNODAYA_MS, sr_f)
        return {"date": f, "label": lab, "tithi": T, "kala": kala, "kw": kw, "muh": None, "par": par,
                "flags": flags}

    # -- solar rules
    def sankranti_unit(self, r, j):
        s = self.W.SKT[j]
        self.sd.used.clear()
        if r["day"] != "civil":
            # the flag reads the days start() touches: recompute, never take a memo filled by another def
            self.sd.memo.pop((SANK_REGION[r["day"]], s), None)
        d = self.sank_date(r["day"], s)
        flags = set()
        if r["day"] != "civil" and any(self.sunflag(x, "F") for x in self.sd.used):
            flags.add("SUN_FALLBACK")
        return {"date": d, "label": None, "tithi": None, "kala": None, "kw": (s, s), "muh": None, "par": None,
                "flags": flags}

    def nakshatra_unit(self, r, j):
        W = self.W
        a = self.sd.start(r["region"], W.SKT[j])[0]
        b = self.sd.start(r["region"], W.SKT[j + 1])[0]
        kala, n = r["kala"], r["nakshatra"]
        cand = []
        for d in range(a, b):
            win = self.window(d, kala)
            nc = self.nak_cover(win, n)
            if nc is not None:
                cand.append((d, win, nc))
        if r["preceded"] is not None:
            keep = []
            for c in cand:
                for e in range(c[0] - 1, c[0] - 31, -1):
                    if self.nak_cover(self.window(e, kala), r["preceded"]) is not None:
                        if e >= a:
                            keep.append(c)
                        break
            cand = keep
        if not cand:
            return None
        if r["occurrence"] == "first":
            d, win, nc = cand[0]
        elif r["occurrence"] == "last":
            d, win, nc = cand[-1]
        else:
            fms = self.full_moons_near(j, a, b)
            d, win, nc = min(cand, key=lambda c: (min(self.dist(f, max(c[1][0], c[2][1]), min(c[1][1], c[2][2]))
                                                      for f in fms), c[0]))
        muh = None
        if not win[2]:
            s, e = max(win[0], nc[1]), min(win[1], nc[2])
            if s < e:
                muh = (s, e, "L" if nc[1] > win[0] else "S", "L" if nc[2] < win[1] else "S")
        flags = {"SUN_FALLBACK"} if self.sunflag(d, "F") else set()
        return {"date": d, "label": None, "tithi": None, "kala": kala, "kw": (win[0], win[1]), "muh": muh,
                "par": None, "flags": flags}

    @staticmethod
    def dist(f, lo, hi):
        """Distance from the instant f to the interval [lo, hi] (0 inside)."""
        return 0 if lo <= f <= hi else min(abs(f - lo), abs(f - hi))

    def full_moons_near(self, j, a, b):
        """Full moons F with containing(F) in the solar month days [a, b); else the one nearest its midpoint."""
        W, tb = self.W, self.TB
        k0 = max(0, bisect.bisect_left(tb, W.SKT[j] - 40 * DAY) // 30)
        k1 = min(self.nlun - 1, bisect.bisect_right(tb, W.SKT[j + 1] + 40 * DAY) // 30 + 1)
        allf = [tb[30 * k + 15] for k in range(k0, k1 + 1)]
        inside = [f for f in allf if a <= self.sd.containing(f) < b]
        if inside:
            return inside
        mid = (W.SKT[j] + W.SKT[j + 1]) // 2
        return [min(allf, key=lambda f: (abs(f - mid), f))]


def decision(rec):
    return None if rec is None else (rec["date"], rec["label"], tuple(sorted(rec["flags"])), rec["tithi"])
