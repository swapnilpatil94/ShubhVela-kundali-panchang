"""Calendar reference maths (docs/specs/M04.md sections 3.1-3.3, 3.10). Dev tool only.

Independent of the Kotlin engine, of rules/*.json and of engine output. Reuses the M01-M03 Python helpers
(time scales, rise/set, true Lahiri, limb functions of panchang_ref.py). All instants are integer UTC epoch
milliseconds; dates are days since 1970-01-01 (ints) or ISO strings in the fixtures.

World = new moons (tithi 30 -> 1), full moons (tithi 15 -> 16) and Sun sidereal ingresses (true Lahiri), found
on a 12 h grid and refined by bisection to <= 2 ms. Masa, adhika, kshaya, samvat, solar months and ayana are
then pure functions of the world lists, so "fragile by perturbation" (spec 3.10) is evaluated by moving one
input at a time by +-tau and re-running the same functions.
"""
import bisect
import datetime
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_riseset as gr  # noqa: E402
import oracle_common as oc  # noqa: E402
import panchang_ref as pr  # noqa: E402
import timescale as tsm  # noqa: E402

# Lunations before 1900-01-01 are needed only to find the latest Chaitra/Kartika lunation (samvat years) of the
# first 1900 days. The Espenak-Meeus rows start in 1900 (timescale.py), so the world before 1900 uses deltaT(1900.0);
# an error of seconds cannot move a new moon across a year edge. VERIFY (engine reach before 1900: see DECISIONS).
_DELTA_T_1900 = tsm.delta_t_seconds
tsm.delta_t_seconds = lambda y: _DELTA_T_1900(max(y, 1900.0))

DAY_MS = 86_400_000
HOUR_MS = 3_600_000
GRID_MS = 43_200_000
TAU_S = {"P": 30_000, "B": 600_000}          # sankranti / new moon perturbation (spec 3.10)
TAU_L = {"P": 30_000, "B": 150_000}          # tithi boundary perturbation; Base 600 s on F/S days
TAU_L_BAD_B = 600_000

CITY_NAMES = ["delhi", "mumbai", "chennai", "kolkata", "newyork", "london", "sydney", "auckland", "suva", "tromso"]
CITY_OFFSETS = [330, 330, 330, 330, -300, 0, 600, 720, 720, 60]
CITY_COORDS = {n: (la, lo) for n, la, lo in gr.CITIES}
CITY_COORDS["sydney"] = (-33.8688, 151.2093)
REGIONS = ["TAMIL", "MALAYALAM", "BENGALI", "ODIA"]
MASA = ["CHAITRA", "VAISHAKHA", "JYESHTHA", "ASHADHA", "SHRAVANA", "BHADRAPADA", "ASHVINA", "KARTIKA",
        "MARGASHIRSHA", "PAUSHA", "MAGHA", "PHALGUNA"]
RITU = ["VASANTA", "GRISHMA", "VARSHA", "SHARAD", "HEMANTA", "SHISHIRA"]
AYANA = ["UTTARAYANA", "DAKSHINAYANA"]
UTTARA_RASHIS = {9, 10, 11, 0, 1, 2}
WORLD_START = tsm._days_from_civil(1898, 10, 1) * DAY_MS
WORLD_END = tsm._days_from_civil(2101, 6, 1) * DAY_MS
EPOCH = datetime.datetime(1970, 1, 1)


def utc_year(ms):
    return (EPOCH + datetime.timedelta(milliseconds=int(ms))).year


def date_year(days):
    return datetime.date.fromordinal(int(days) + 719163).year


def jv(x):
    """JSON-friendly copy (tuples -> lists)."""
    if isinstance(x, (tuple, list)):
        return [jv(i) for i in x]
    return x


# ------------------------------------------------------------------------------------------------ world
def world_slice(args):
    """Worker: grid indices g0..g1 (inclusive) of the 12 h grid; every pair (g, g + 1) belongs to one slice."""
    g0, g1 = args
    eph, _ = oc.load_kernel()
    ts = oc.timescale()
    sky = pr.Sky(eph, ts)

    def ev(ms):
        ls, lm, ay = sky.lons(ms)
        return (lm - ls) % 360.0, (ls - ay) % 360.0

    ms = WORLD_START + np.arange(g0, g1 + 1, dtype=np.int64) * GRID_MS
    el, sid = ev(ms)
    ae, be = el[:-1], el[1:]
    cells = []  # (kind 0 nm / 1 fm / 2 sankranti, target deg, lo, hi, into)
    for i in np.nonzero(be < ae)[0]:
        cells.append((0, 0.0, int(ms[i]), int(ms[i + 1]), 0))
    for i in np.nonzero((ae < 180.0) & (be >= 180.0))[0]:
        cells.append((1, 180.0, int(ms[i]), int(ms[i + 1]), 0))
    j = np.floor(sid / 30.0).astype(np.int64)
    for i in np.nonzero(j[1:] != j[:-1])[0]:
        cells.append((2, float(j[i + 1]) * 30.0, int(ms[i]), int(ms[i + 1]), int(j[i + 1])))
    if not cells:
        return [], [], []
    kind = np.array([c[0] for c in cells]); tgt = np.array([c[1] for c in cells])
    lo = np.array([c[2] for c in cells], dtype=np.int64); hi = np.array([c[3] for c in cells], dtype=np.int64)
    into = np.array([c[4] for c in cells], dtype=np.int64)
    sel_el = kind < 2

    def f_at(t):
        e, s = ev(t)
        return np.where(sel_el, e, s)

    while True:
        act = np.nonzero(hi - lo > 2)[0]
        if act.size == 0:
            break
        mid = (lo[act] + hi[act]) // 2
        e, s = ev(mid)
        fm = np.where(sel_el[act], e, s)
        pos = pr.wrap180(fm - tgt[act]) >= 0.0
        hi[act] = np.where(pos, mid, hi[act])
        lo[act] = np.where(pos, lo[act], mid)
    t = (lo + hi) // 2
    fp, fm_ = f_at(t + 60_000), f_at(t - 60_000)
    rate = np.round(np.abs(pr.wrap180(fp - fm_)) * 3600.0 / 120.0, 4)
    nm, fm, sk = [], [], []
    for c in np.argsort(t, kind="stable"):
        row = (int(t[c]), float(rate[c]))
        if kind[c] == 0:
            nm.append(row)
        elif kind[c] == 1:
            fm.append(row)
        else:
            sk.append((int(into[c]), int(t[c]), float(rate[c])))
    return nm, fm, sk


def build_world(pool, nslices=24):
    n = (WORLD_END - WORLD_START) // GRID_MS
    cuts = [n * i // nslices for i in range(nslices + 1)]
    res = pool.map(world_slice, [(cuts[i], cuts[i + 1]) for i in range(nslices)])
    nm = [r for s in res for r in s[0]]
    fm = [r for s in res for r in s[1]]
    sk = [r for s in res for r in s[2]]
    for a, b in zip(nm, nm[1:]):
        assert b[0] > a[0]
    for a, b in zip(sk, sk[1:]):
        assert b[1] > a[1] and b[0] == (a[0] + 1) % 12, "sankranti sequence broken"
    return nm, fm, sk


# ------------------------------------------------------------------------------------- masa, samvat, ayana
class World:
    """Mutable world lists (perturbed in place and restored by the caller)."""

    def __init__(self, nm, sk_t, sk_i):
        self.NM, self.SKT, self.SKI = list(nm), list(sk_t), list(sk_i)

    def lun(self, k):
        """(masa ordinal, adhika, kshaya ordinal|None) of the lunation [NM[k], NM[k+1]) (spec 3.1)."""
        lo = bisect.bisect_left(self.SKT, self.NM[k])
        hi = bisect.bisect_left(self.SKT, self.NM[k + 1])
        n = hi - lo
        if n == 1:
            return (self.SKI[lo], False, None)
        if n == 0:
            return (self.SKI[lo], True, None)
        if n == 2:
            return (self.SKI[lo], False, self.SKI[lo + 1])
        raise AssertionError("more than two sankrantis in a lunation")

    def lun_at(self, ms):
        return bisect.bisect_right(self.NM, ms) - 1

    def purnimanta(self, k, tithi):
        am = self.lun(k)
        if tithi <= 15:
            return am
        if am[1]:
            return (am[0], True, None)
        j = k + 1
        while True:
            lj = self.lun(j)
            if not lj[1]:
                return (lj[0], False, None)
            j += 1

    def latest_nija(self, k, masa):
        j = k
        while True:
            m, adh, _ = self.lun(j)
            if m == masa and not adh:
                return j
            j -= 1

    def lunar_fields(self, sr, tithi):
        k = self.lun_at(sr)
        am = self.lun(k)
        out = {"amanta": am, "purnimanta": self.purnimanta(k, tithi), "ritu": am[0] // 2}
        shaka = utc_year(self.NM[self.latest_nija(k, 0)] + 15 * DAY_MS) - 78
        out["shaka"] = shaka
        out["vikramChaitradi"] = shaka + 135
        out["vikramKartikadi"] = utc_year(self.NM[self.latest_nija(k, 7)] + 15 * DAY_MS) + 57
        out["samvatsara"] = (shaka + 11) % 60 + 1
        return out


def solar_need(w, nsr):
    """Indices of the sankrantis a day's solar fields can depend on (perturbed lists included)."""
    n = bisect.bisect_left(w.SKT, nsr + HOUR_MS)
    idx = set(range(max(0, n - 3), n))
    for rashi in (0, 4):
        c = 0
        for j in range(n - 1, -1, -1):
            if w.SKI[j] == rashi:
                idx.add(j)
                c += 1
                if c == 2:
                    break
    return n, sorted(idx)


class SunDays:
    """Sunrise, sunset, next sunrise of local civil days of one city (M03 windows), memoised."""

    def __init__(self, eph, ts, name, off_min):
        lat, lon = CITY_COORDS[name]
        self.site = gr.Site(eph, ts, lat, lon)
        self.off = off_min
        self.t = {}
        self.memo = {}
        self.nudge = None   # (day, 'sr'|'ss'|'cut', delta ms): one sun event moved for a fragility probe

    def ensure(self, days, chunk=1500):
        miss = sorted(d for d in set(days) if d not in self.t)
        for i in range(0, len(miss), chunk):
            self._compute(miss[i:i + chunk])

    def _compute(self, days):
        ws = {d: d * DAY_MS - self.off * 60_000 for d in days}
        starts = sorted(set(ws.values()) | {w + DAY_MS for w in ws.values()})
        sw = dict(zip(starts, pr.sun_windows(self.site, starts)))
        for d in days:
            ev, fv = sw[ws[d]], sw[ws[d] + DAY_MS]
            rises_e = [t for k, t in ev["events"] if k == "RISE"]
            rises_f = [t for k, t in fv["events"] if k == "RISE"]
            sr = rises_e[0] if rises_e else None
            ss = nsr = None
            if sr is not None:
                ss = next((t for k, t in sorted(ev["events"] + fv["events"], key=lambda e: e[1])
                           if k == "SET" and t > sr), None)
                nsr = rises_f[0] if rises_f else None
            unavailable = not (sr is not None and ss is not None and nsr is not None and sr < ss < nsr)
            extra = (not unavailable) and any(t > ss for t in rises_e)
            self.t[d] = dict(sr=None if unavailable else sr, ss=None if unavailable else ss,
                             nsr=None if unavailable else nsr, F=ev["fallback"] or fv["fallback"], X=extra,
                             S=ev["fragile"] or fv["fragile"], U=unavailable)

    def get(self, d):
        if d not in self.t:
            self._compute([d])
        e = self.t[d]
        if e["U"]:
            raise RuntimeError("sun day unavailable inside a calendar computation")
        sr, ss, nsr = e["sr"], e["ss"], e["nsr"]
        if self.nudge is not None:
            k, ev, dl = self.nudge
            if ev == "sr":
                sr += dl if d == k else 0
                nsr += dl if d + 1 == k else 0
            elif ev == "ss" and d == k:
                ss += dl
        return sr, ss, nsr

    def tau_sun(self, k, lvl):
        """Perturbation of a sun event of day k: Precision 30 s; Base 150 s, 600 s on F/S sun days (spec 3.10)."""
        if lvl == "P":
            return TAU_L["P"]
        e = self.t[k]
        return TAU_L_BAD_B if (e["F"] or e["S"]) else TAU_L["B"]

    def nudges(self, s, lvl):
        """Sun-event nudges (+-tau, one at a time: sunrise, sunset of the days around sankranti s, and the cut-off
        itself) that can change the month start date of s; pre-filtered by closeness (<= 2 tau) to any event or
        cut-off candidate, which cannot miss a change because cut-offs move by at most tau."""
        self.nudge = None
        p = self.containing(s)
        times = []
        for k in range(p - 1, p + 3):
            sr, ss, nsr = self.get(k)
            times += [sr, ss, pr.part_point(sr, ss, 5, 3), pr.part_point(ss, nsr, 2, 1)]
        out = []
        for k in range(p - 1, p + 3):
            tau = self.tau_sun(k, lvl)
            if any(abs(s - x) <= 2 * tau for x in times):
                for ev in ("sr", "ss") + (("cut",) if k == p else ()):
                    out += [(k, ev, -tau), (k, ev, tau)]
        return out

    def flags(self, d):
        e = self.t[d]
        return ("F" if e["F"] else "") + ("X" if e["X"] else "") + ("S" if e["S"] else "") + ("U" if e["U"] else "")

    def containing(self, x):
        d = (x + self.off * 60_000) // DAY_MS
        while True:
            sr, _, nsr = self.get(d)
            if x < sr:
                d -= 1
            elif x >= nsr:
                d += 1
            else:
                return d

    def start(self, region, s):
        """(month start date as days, cutoff ms|None) for sankranti s (spec 3.3)."""
        key = (region, s)
        r = None if self.nudge is not None else self.memo.get(key)
        if r is None and region == "ODIA":
            # Amendment 1: civil midnight-to-midnight date of the sankranti at the caller's (fixed) offset
            r = ((s + self.off * 60_000) // DAY_MS, None)
            if self.nudge is None:
                self.memo[key] = r
        elif r is None:
            p = self.containing(s)
            sr, ss, nsr = self.get(p)
            if region == "TAMIL":
                r = (p if s < ss else p + 1, ss)
            elif region == "MALAYALAM":
                cut = pr.part_point(sr, ss, 5, 3)
                r = (p if s < cut else p + 1, cut)
            elif region == "BENGALI":
                mid = pr.part_point(ss, nsr, 2, 1)
                r = (p + 1 if s < mid else p + 2, mid)
            if self.nudge is not None and self.nudge[1] == "cut" and self.nudge[0] == p and r[1] is not None:
                cut = r[1] + self.nudge[2]
                if region == "TAMIL":
                    r = (p if s < cut else p + 1, cut)
                elif region == "MALAYALAM":
                    r = (p if s < cut else p + 1, cut)
                else:
                    r = (p + 1 if s < cut else p + 2, cut)
            if self.nudge is None:
                self.memo[key] = r
        return r


def solar_fields(w, sd, d, sr, nsr):
    n, _ = solar_need(w, nsr)
    out = {}
    for r in REGIONS:
        for j in range(n - 1, n - 4, -1):
            date = sd.start(r, w.SKT[j])[0]
            if date <= d:
                break
        else:
            raise AssertionError("no solar month start found")
        out["solar" + r] = (w.SKI[j], d - date + 1, date)

    def year_of(r, rashi):
        for j in range(n - 1, -1, -1):
            if w.SKI[j] == rashi:
                date = sd.start(r, w.SKT[j])[0]
                if date <= d:
                    return date_year(date)
        raise AssertionError("no year start found")

    out["samvatsaraTamil"] = (year_of("TAMIL", 0) - 78 + 11) % 60 + 1
    out["bangabda"] = year_of("BENGALI", 0) - 593
    out["kollam"] = year_of("MALAYALAM", 4) - 824
    j = bisect.bisect_right(w.SKT, sr) - 1
    out["ayana"] = 0 if w.SKI[j] in UTTARA_RASHIS else 1
    return out


def sens_flags(w, tau):
    """Per new moon / sankranti: True if moving it by tau can change a lunation label or a year (spec 3.10)."""
    nm = np.array(w.NM, dtype=np.int64)
    sk = np.array(w.SKT, dtype=np.int64)

    def near(a, b):
        i = np.searchsorted(b, a - tau, side="left")
        ok = i < b.size
        out = np.zeros(a.size, dtype=bool)
        out[ok] = b[i[ok]] <= a[ok] + tau
        return out

    nsens = near(nm, sk)
    for i, x in enumerate(w.NM):
        y = utc_year(x + 15 * DAY_MS)
        if utc_year(x + 15 * DAY_MS - tau) != y or utc_year(x + 15 * DAY_MS + tau) != y:
            nsens[i] = True
    return nsens.tolist(), near(sk, nm).tolist()
