"""Panchang reference maths (docs/specs/M03.md sections 3.1-3.5). Dev tool only.

Independent of the Kotlin engine and of rules/*.json: the weekday tables below are typed copies of the spec
(3.3/3.4). Limb functions use geocentric apparent true-of-date ecliptic longitudes from Skyfield and the true
Lahiri ayanamsa of jyotish_ref.py. Boundaries are found by bisection (not Brent). All instants are integer
UTC epoch milliseconds.
"""
import datetime
import sys
from pathlib import Path

import numpy as np
from skyfield.framelib import ecliptic_frame

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_riseset as gr  # noqa: E402
import jyotish_ref as jr  # noqa: E402
import oracle_common as oc  # noqa: E402

KINDS = ["TITHI", "KARANA", "NAKSHATRA", "YOGA", "MOON_RASHI"]
DIVISIONS = [30, 60, 27, 27, 12]
ONE_BASED = [True, True, False, False, False]
GRID_MS = 3_600_000
DAY_MS = 86_400_000
HOUR_MS = 3_600_000
CHUNK = 40_000

# ---------------------------------------------------------------------------------------- weekday tables
VAAR = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"]
VAAR_LORD = ["SUN", "MOON", "MARS", "MERCURY", "JUPITER", "VENUS", "SATURN"]
RAHU_PART = [8, 2, 7, 5, 6, 4, 3]
YAMA_PART = [5, 4, 3, 2, 1, 7, 6]
GULIKA_PART = [7, 6, 5, 4, 3, 2, 1]
HORA_ORDER = ["SUN", "VENUS", "MERCURY", "MOON", "SATURN", "JUPITER", "MARS"]
CHOG = ["UDVEG", "CHAR", "LABH", "AMRIT", "KAAL", "SHUBH", "ROG"]
CHOG_LORD = ["SUN", "VENUS", "MERCURY", "MOON", "SATURN", "JUPITER", "MARS"]
# (night, muhurta) per weekday, VERIFY (spec 3.4)
DURMUHURTA = [[(False, 14)], [(False, 9), (False, 12)], [(False, 4), (True, 7)], [(False, 8)],
              [(False, 6), (False, 12)], [(False, 4), (False, 9)], [(False, 1), (False, 2)]]
KIND_ORDER = ["RAHU_KAAL", "YAMAGANDA", "GULIKA", "ABHIJIT", "BRAHMA_MUHURTA", "DURMUHURTA", "VARJYAM",
              "AMRIT_KAAL", "BHADRA", "PANCHAK", "CHOGHADIYA", "HORA", "GOWRI"]
PRINTED_KINDS = ["RAHU_KAAL_PRINTED", "YAMAGANDA_PRINTED", "GULIKA_PRINTED"]


def night_choghadiya(weekday):
    n0 = CHOG_LORD.index(VAAR_LORD[(weekday + 4) % 7])
    return [CHOG[(n0 - 2 * p) % 7] for p in range(8)]


def day_choghadiya(weekday):
    c0 = CHOG_LORD.index(VAAR_LORD[weekday])
    return [CHOG[(c0 + p) % 7] for p in range(8)]


# the two anchors of spec 3.4, asserted at import
assert night_choghadiya(0) == ["SHUBH", "AMRIT", "CHAR", "ROG", "KAAL", "LABH", "UDVEG", "SHUBH"]
assert night_choghadiya(2) == ["KAAL", "LABH", "UDVEG", "SHUBH", "AMRIT", "CHAR", "ROG", "KAAL"]


def weekday_of(days):
    """0 = Sunday for days since 1970-01-01 (a Thursday)."""
    return (days + 4) % 7


def date_str(days):
    return datetime.date.fromordinal(days + 719163).isoformat()


def part_point(a, b, n, i):
    """a + (b - a) * i / n in integer ms, half up (engine uses exact ns; difference < 1 ms)."""
    return a + (2 * (b - a) * i + n) // (2 * n)


def windows_for_day(days, off_min, sr, ss, nsr):
    """Spec 3.3 windows (without limb-derived kinds): [(kind, part, code, night, start, end)] sorted by
    (start, kind ordinal, part), then the three PRINTED_6AM windows appended in kind order."""
    w = weekday_of(days)
    out = []

    def parts(kind, a, b, n, night, code_of):
        for p in range(1, n + 1):
            out.append((kind, p, code_of(p), night, part_point(a, b, n, p - 1), part_point(a, b, n, p)))

    for kind, tab in (("RAHU_KAAL", RAHU_PART), ("YAMAGANDA", YAMA_PART), ("GULIKA", GULIKA_PART)):
        p = tab[w]
        out.append((kind, p, None, False, part_point(sr, ss, 8, p - 1), part_point(sr, ss, 8, p)))
    if w != 3:
        out.append(("ABHIJIT", 8, None, False, part_point(sr, ss, 15, 7), part_point(sr, ss, 15, 8)))
    out.append(("BRAHMA_MUHURTA", 14, None, True, part_point(ss, nsr, 15, 13), part_point(ss, nsr, 15, 14)))
    for night, m in DURMUHURTA[w]:
        a, b = (ss, nsr) if night else (sr, ss)
        out.append(("DURMUHURTA", m, None, night, part_point(a, b, 15, m - 1), part_point(a, b, 15, m)))
    dc, nc = day_choghadiya(w), night_choghadiya(w)
    parts("CHOGHADIYA", sr, ss, 8, False, lambda p: dc[p - 1])
    parts("CHOGHADIYA", ss, nsr, 8, True, lambda p: nc[p - 1])
    h0 = HORA_ORDER.index(VAAR_LORD[w])
    parts("HORA", sr, ss, 12, False, lambda p: HORA_ORDER[(h0 + p - 1) % 7])
    parts("HORA", ss, nsr, 12, True, lambda p: HORA_ORDER[(h0 + 12 + p - 1) % 7])
    out.sort(key=lambda r: (r[4], KIND_ORDER.index(r[0]), r[1]))
    ws = days * DAY_MS - off_min * 60_000
    a, b = ws + 6 * HOUR_MS, ws + 18 * HOUR_MS
    for kind, tab in zip(PRINTED_KINDS, (RAHU_PART, YAMA_PART, GULIKA_PART)):
        p = tab[w]
        out.append((kind, p, None, False, part_point(a, b, 8, p - 1), part_point(a, b, 8, p)))
    return out


# -------------------------------------------------------------------------------------------- limb maths
class Sky:
    """Geocentric apparent true-of-date longitudes of Sun and Moon and true Lahiri at UTC epoch ms."""

    def __init__(self, eph, ts):
        self.earth, self.sun, self.moon, self.ts = eph["earth"], eph["sun"], eph["moon"], ts

    def lons(self, ms):
        ms = np.asarray(ms, dtype=np.int64)
        ls, lm, ay = [], [], []
        for i in range(0, ms.size, CHUNK):
            t = oc.time_from_utc_ms(self.ts, ms[i:i + CHUNK])
            e = self.earth.at(t)
            ls.append(e.observe(self.sun).apparent().frame_latlon(ecliptic_frame)[1].degrees % 360.0)
            lm.append(e.observe(self.moon).apparent().frame_latlon(ecliptic_frame)[1].degrees % 360.0)
            ay.append(jr.lahiri(self.ts, t.whole, t.tt_fraction)[1])
        if not ls:
            z = np.zeros(0)
            return z, z, z
        return np.concatenate(ls), np.concatenate(lm), np.concatenate(ay)

    def f_all(self, ms):
        """(5, n) array of F per kind in KINDS order."""
        ls, lm, ay = self.lons(ms)
        return np.vstack([(lm - ls) % 360.0, (lm - ls) % 360.0, (lm - ay) % 360.0,
                          (lm + ls - 2 * ay) % 360.0, (lm - ay) % 360.0])

    def f_kind(self, kid, ms):
        f = self.f_all(ms)
        return f[kid, np.arange(f.shape[1])]


def division_index(kid, f):
    n = DIVISIONS[kid]
    return np.minimum(n - 1, np.floor(f * n / 360.0)).astype(np.int64)


def wrap180(x):
    return (x + 180.0) % 360.0 - 180.0


def find_boundaries(sky, ks):
    """ks = sorted unique grid indices (t_k = k * 3600 s). Returns {kind: (ms[], indexAfter[], rate[])}."""
    ks = np.asarray(ks, dtype=np.int64)
    f = sky.f_all(ks * GRID_MS)
    pairs = np.nonzero(ks[1:] == ks[:-1] + 1)[0]
    found = {}
    cells = []  # (kid, B, lo, hi, index_after)
    for kid in range(len(KINDS)):
        j = division_index(kid, f[kid])
        ch = pairs[j[pairs + 1] != j[pairs]]
        n = DIVISIONS[kid]
        for c in ch:
            ja = int(j[c + 1])
            cells.append((kid, ja * 360.0 / n, int(ks[c]) * GRID_MS, int(ks[c + 1]) * GRID_MS,
                          ja + (1 if ONE_BASED[kid] else 0)))
    if not cells:
        return {k: (np.zeros(0, np.int64), np.zeros(0, np.int64), np.zeros(0)) for k in range(5)}
    kid = np.array([c[0] for c in cells]); bb = np.array([c[1] for c in cells])
    lo = np.array([c[2] for c in cells], dtype=np.int64); hi = np.array([c[3] for c in cells], dtype=np.int64)
    idx = np.array([c[4] for c in cells], dtype=np.int64)
    while True:
        act = np.nonzero(hi - lo > 2)[0]
        if act.size == 0:
            break
        mid = (lo[act] + hi[act]) // 2
        fm = sky.f_all(mid)[kid[act], np.arange(act.size)]
        pos = wrap180(fm - bb[act]) >= 0.0
        hi[act] = np.where(pos, mid, hi[act])
        lo[act] = np.where(pos, lo[act], mid)
    t = (lo + hi) // 2
    fp = sky.f_all(t + 60_000)[kid, np.arange(t.size)]
    fm_ = sky.f_all(t - 60_000)[kid, np.arange(t.size)]
    rate = np.abs(wrap180(fp - fm_)) * 3600.0 / 120.0
    for k in range(len(KINDS)):
        m = np.nonzero(kid == k)[0]
        order = m[np.argsort(t[m], kind="stable")]
        found[k] = (t[order], idx[order], np.round(rate[order], 4))
    return found


# --------------------------------------------------------------------------------------------- sun events
CONV = "UPPER_LIMB_REFRACTED"


def sun_windows(site, starts):
    """Per window start (ms): dict(events [(kind, ms)], fallback, noon, fragile). Spec 3.5."""
    starts = np.asarray(starts, dtype=np.int64)
    nd, ns = starts.size, gr.NS
    samples = starts[:, None] + np.arange(ns, dtype=np.int64)[None, :] * gr.STEP_MS
    alt, dist = site.alt("sun", samples.ravel())
    alt, dist = alt.reshape(nd, ns), dist.reshape(nd, ns)
    f = alt - gr.h0("sun", CONV, dist)
    fl, fh = f[:, :-1], f[:, 1:]
    rise, sset = (fl < 0) & (fh >= 0), (fl >= 0) & (fh < 0)
    br, frag = [], []
    for d in range(nd):
        frag.append(gr.fragile_samples(f[d]))
        for k in np.nonzero(rise[d] | sset[d])[0]:
            br.append((d, 0, 0 if rise[d, k] else 1, int(samples[d, k]), int(samples[d, k + 1])))
    per = {}
    for (d, _, kind, ms, rate) in gr.refine(site, "sun", br):
        per.setdefault(d, []).append((kind, ms, rate))
    res, fb = [], []
    for d in range(nd):
        ev = sorted(per.get(d, []), key=lambda e: e[1])
        real = any(e[0] == 0 for e in ev) and any(e[0] == 1 for e in ev)
        fr = bool(frag[d]) or any(abs(e[2]) < gr.FRAGILE_RATE for e in ev)
        res.append({"events": [("RISE" if k == 0 else "SET", ms) for k, ms, _ in ev], "fallback": not real,
                    "noon": None, "fragile": fr})
        if not real:
            fb.append(d)
    if fb:
        am = np.argmax(alt[fb], axis=1)
        a = np.array([samples[d, max(0, i - 1)] for d, i in zip(fb, am)], dtype=float)
        b = np.array([samples[d, min(ns - 1, i + 1)] for d, i in zip(fb, am)], dtype=float)
        inv = 0.6180339887498949
        for _ in range(40):
            c, e = b - (b - a) * inv, a + (b - a) * inv
            fc, _d = site.alt("sun", np.rint(c).astype(np.int64))
            fe, _d = site.alt("sun", np.rint(e).astype(np.int64))
            left = fc >= fe
            b = np.where(left, e, b)
            a = np.where(left, a, c)
        noon = np.rint((a + b) / 2).astype(np.int64)
        for d, nm in zip(fb, noon.tolist()):
            res[d]["noon"] = nm
            res[d]["events"] = [("RISE", nm - 6 * HOUR_MS), ("SET", nm + 6 * HOUR_MS)]
    return res
