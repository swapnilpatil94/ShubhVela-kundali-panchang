"""Family dates reference evaluator (docs/specs/M10.md rule 8, task C2). Dev tool only.

Independent of the Kotlin engine and of engine output. Restates from the spec text:
  * janma facts of an instant: tithi 1..30 and nakshatra 0..26 by the floor rule on the elongation and the
    sidereal Moon (true Lahiri), the sidereal Sun's rashi, and the amanta / purnimanta labels of the lunation
    (calendar_ref.World over new moons and sankrantis);
  * the family definitions of the rule-8 table as festival_ref rule dicts (tithi_at, nakshatra_in_solar_month);
  * the new rule type nakshatra_in_lunar_month (FamCtx.lunar_unit) on top of festival_ref.Ctx.
Every instant is an integer UTC epoch millisecond; dates are days since 1970-01-01.
"""
import bisect
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import calendar_ref as cr  # noqa: E402
import festival_ref as fr  # noqa: E402

DAY = 86_400_000
TAU_L = {"P": 30_000, "B": 150_000}   # tithi / nakshatra boundary (spec M04 3.10)
TAU_S = {"P": 30_000, "B": 600_000}   # sankranti / new moon
REGIONS = ["TAMIL", "MALAYALAM", "BENGALI", "ODIA"]
# variant v of a birth: (kind, basis, system); def id = "fam_" + str(10 * birth + v)
VARIANTS = [("TITHI_BIRTHDAY", None, "AMANTA"), ("TITHI_BIRTHDAY", None, "PURNIMANTA"),
            ("NAKSHATRA_BIRTHDAY", "SOLAR", None), ("NAKSHATRA_BIRTHDAY", "LUNAR", "AMANTA"),
            ("NAKSHATRA_BIRTHDAY", "LUNAR", "PURNIMANTA"), ("SHRADDHA", None, "AMANTA"),
            ("SHRADDHA", None, "PURNIMANTA")]


# ------------------------------------------------------------------------------------------------ janma
def indices(sky, ms):
    """(tithi 1..30, nakshatra 0..26, sun rashi 0..11) arrays at UTC ms (floor rule on the true-of-date F)."""
    ls, lm, ay = sky.lons(np.asarray(ms, dtype=np.int64))
    el = (lm - ls) % 360.0
    tithi = np.minimum(29, np.floor(el * 30.0 / 360.0)).astype(np.int64) + 1
    nak = np.minimum(26, np.floor(((lm - ay) % 360.0) * 27.0 / 360.0)).astype(np.int64)
    rashi = np.minimum(11, np.floor(((ls - ay) % 360.0) / 30.0)).astype(np.int64)
    return tithi, nak, rashi


def labels(w, ms, tithi):
    k = w.lun_at(ms)
    return w.lun(k), w.purnimanta(k, tithi)


def janma_rows(sky, w, births):
    """births = [(ms, tag)]; returns [(ms, tag, tithi, am, pm, nak, rashi, fragP, fragB)].

    Fragile (per level) if a fact changes when the instant moves by +-tauL (tithi, nakshatra, labels) or +-tauS
    (sun rashi), or when one new moon or sankranti near the lunation moves by +-tauS (labels)."""
    offs = [0, -TAU_L["P"], TAU_L["P"], -TAU_L["B"], TAU_L["B"], -TAU_S["B"], TAU_S["B"]]
    probe = np.array([b[0] + o for b in births for o in offs], dtype=np.int64)
    ti, nk, ra = indices(sky, probe)
    out = []
    for bi, (ms, tag) in enumerate(births):
        base = bi * len(offs)
        t0, n0, r0 = int(ti[base]), int(nk[base]), int(ra[base])
        am, pm = labels(w, ms, t0)
        frag = {}
        for lvl, (lo, hi), (so, sh) in (("P", (1, 2), (1, 2)), ("B", (3, 4), (5, 6))):
            f = False
            for j in (lo, hi):
                tj = int(ti[base + j])
                if tj != t0 or int(nk[base + j]) != n0 or labels(w, ms + offs[j], tj) != (am, pm):
                    f = True
            for j in (so, sh):
                if int(ra[base + j]) != r0:
                    f = True
            tau = TAU_S[lvl]
            k = w.lun_at(ms)
            lists = [(w.NM, i) for i in range(max(0, k - 1), min(len(w.NM), k + 4))]
            s0, s1 = bisect.bisect_left(w.SKT, w.NM[max(0, k - 1)] - tau), \
                bisect.bisect_right(w.SKT, w.NM[min(len(w.NM) - 1, k + 4)] + tau)
            lists += [(w.SKT, j) for j in range(s0, s1)]
            for cont, key in lists:
                orig = cont[key]
                for sg in (-tau, tau):
                    cont[key] = orig + sg
                    try:
                        if labels(w, ms, t0) != (am, pm):
                            f = True
                    finally:
                        cont[key] = orig
            frag[lvl] = f
        out.append((ms, tag, t0, am, pm, n0, r0, frag["P"], frag["B"]))
    return out


# ------------------------------------------------------------------------------------------ definitions
def defs_of(birth, facts, region):
    """The seven rule-8 definitions of one birth as festival_ref rule dicts (defaults: docs/specs/M10.md)."""
    _ms, _tag, tithi, am, pm, nak, rashi = facts[:7]
    out = []
    for v, (kind, basis, system) in enumerate(VARIANTS):
        fid = "fam_%d" % (10 * birth + v)
        lab = am if system == "AMANTA" else pm
        if kind in ("TITHI_BIRTHDAY", "SHRADDHA"):
            rule = {"type": "tithi_at", "tithi": [tithi], "masa": lab[0] if lab else None, "system": system,
                    "kala": "sunrise" if kind == "TITHI_BIRTHDAY" else "aparahna", "tie": "first", "prefer": None,
                    "none": "containing_start", "bhadra": None}
        elif basis == "SOLAR":
            rule = {"type": "nakshatra_in_solar_month", "nakshatra": nak, "month": rashi, "region": region,
                    "kala": "sunrise", "occurrence": "first", "preceded": None}
        else:
            rule = {"type": "nakshatra_in_lunar_month", "nakshatra": nak, "masa": lab[0], "system": system,
                    "kala": "sunrise", "occurrence": "first"}
        out.append({"id": fid, "rule": rule, "adhika": "NIJA", "kind": kind, "basis": basis, "system": system,
                    "region": region if basis == "SOLAR" else None, "birth": birth})
    return out


# ----------------------------------------------------------------------------- nakshatra_in_lunar_month
class FamCtx(fr.Ctx):
    """festival_ref.Ctx plus the lunar month rule (spec M10 rule 8)."""

    def half_label(self, k, h, system):
        return self.W.lun(k) if system == "AMANTA" else self.W.purnimanta(k, 1 if h == 0 else 16)

    def anchor(self, k, h, system):
        """The lunation that names the month of half h (0: tithis 1..15, 1: 16..30) of lunation k."""
        if system == "AMANTA" or h == 0 or self.W.lun(k)[1]:
            return k
        j = k + 1
        while self.W.lun(j)[1]:
            j += 1
        return j

    def lunar_unit(self, r, adh, a):
        """The occurrence of the month named by lunation a, or None (no matching day)."""
        sd, system = self.sd, r["system"]
        kept = []
        for k in (a - 2, a - 1, a):
            for h in (0, 1):
                if k < 0 or self.anchor(k, h, system) != a:
                    continue
                lab = self.half_label(k, h, system)
                flags = set()
                if r["masa"] != lab[0]:
                    if lab[2] is not None and r["masa"] == lab[2]:
                        flags.add("KSHAYA_MASA")
                    else:
                        continue
                if (adh == "NIJA" and lab[1]) or (adh == "ADHIKA" and not lab[1]):
                    continue
                if lab[1]:
                    flags.add("ADHIKA_MASA")
                kept.append((self.TB[30 * k + 15 * h], self.TB[30 * k + 15 * h + 15], lab, flags))
        cand = []
        for x0, x1, lab, flags in kept:
            d = sd.containing(x0)
            if sd.get(d)[0] < x0:
                d += 1
            while sd.get(d)[0] < x1:
                win = self.window(d, r["kala"])
                nc = self.nak_cover(win, r["nakshatra"])
                if nc is not None:
                    cand.append((d, win, nc, lab, flags))
                d += 1
        if not cand:
            return None
        d, win, nc, lab, flags = cand[0] if r["occurrence"] == "first" else cand[-1]
        muh = None
        if not win[2]:
            s, e = max(win[0], nc[1]), min(win[1], nc[2])
            if s < e:
                muh = (s, e, "L" if nc[1] > win[0] else "S", "L" if nc[2] < win[1] else "S")
        flags = set(flags) | ({"SUN_FALLBACK"} if self.sunflag(d, "F") else set())
        return {"date": d, "label": lab, "tithi": None, "kala": r["kala"], "kw": (win[0], win[1]), "muh": muh,
                "par": None, "flags": flags}


def tz_offset(tz, zone, t):
    """UTC offset (s) of IANA [zone] at epoch second t from the gen_tz fixture (transitions, else the first
    sampled offset)."""
    zi = tz["meta"]["zones"].index(zone)
    trs = [x for x in tz["transitions"] if x[0] == zi and x[1] <= t]
    if trs:
        return max(trs, key=lambda x: x[1])[3]
    return min((x for x in tz["sampled"] if x[0] == zi), key=lambda x: x[1])[2]


def local_to_ms(tz, zone, local_epoch_s):
    """Instant of a local wall time (seconds as if UTC) in [zone]: two-pass offset lookup (no gap or overlap)."""
    off = tz_offset(tz, zone, local_epoch_s - tz_offset(tz, zone, local_epoch_s))
    return (local_epoch_s - off) * 1000, off


def region_of(i):
    return REGIONS[i % 4]


def ms_of(y, m, d, h=0, mi=0):
    return (cr.tsm._days_from_civil(y, m, d) * 86_400 + h * 3600 + mi * 60) * 1000
