"""Eclipse reference model (docs/specs/M04.md section 3.9). Dev tool only; offline; independent of the Kotlin engine.

Geometry from Skyfield + DE440 apparent vectors (light time, deflection, aberration), spherical-Earth shadow
model with 1/85 atmospheric enlargement. Instants are integer UTC epoch milliseconds (time rule: timescale.py).
Vectorised helpers: bisection to 1 ms (contacts, horizon crossings), golden-section to ~0.1 s (greatest).
"""
import math

import numpy as np
from skyfield.api import wgs84

import gen_riseset as gr
import oracle_common as oc

R_E, R_M, R_S, AU_KM = 6378.137, 1737.4, 695_700.0, 149_597_870.7
ENLARGE = 1.0 + 1.0 / 85.0
BETA_MAX_DEG = 2.0
MS_S = 1000
MIN_MS = 60_000
HOUR_MS = 3_600_000
GRID_MS = 600_000            # 10-min grid for greatest and contacts
VIS_GRID_MS = 300_000        # 5-min grid for horizon visibility
RAD_ARCSEC = 206264.80624709636
CHUNK = 4000
GOLDEN_ITERS = 22
LUNAR_HALF_H, SOLAR_HALF_H = 6, 4
CONTACT_RATE_ARCSEC = 0.05   # contact fragile if |rate| below this (arcsec/s)
SIDE = "UPPER_LIMB_REFRACTED"


def ang(a, b):
    """Angle between row vectors (N,3), radians (atan2: exact for tiny and near-pi angles)."""
    return np.arctan2(np.linalg.norm(np.cross(a, b), axis=1), np.einsum("ij,ij->i", a, b))


def wrap180(x):
    return (x + 180.0) % 360.0 - 180.0


def chunks(n):
    for i in range(0, n, CHUNK):
        yield slice(i, min(n, i + CHUNK))


def golden_min(fun, a, b, iters=GOLDEN_ITERS):
    """Vectorised golden-section minimum of fun(ms int array) on [a, b] (ms); returns int ms."""
    inv = 0.6180339887498949
    a = np.asarray(a, dtype=float).copy()
    b = np.asarray(b, dtype=float).copy()
    for _ in range(iters):
        c, e = b - (b - a) * inv, a + (b - a) * inv
        fc = fun(np.rint(c).astype(np.int64))
        fe = fun(np.rint(e).astype(np.int64))
        left = fc <= fe
        b = np.where(left, e, b)
        a = np.where(left, a, c)
    return np.rint((a + b) / 2).astype(np.int64)


def bisect_root(gfun, lo, hi, codes, neg_lo):
    """Vectorised bisection to 1 ms. g(lo) has sign neg_lo (True = negative); returns hi (first ms on the far side)."""
    lo = np.asarray(lo, dtype=np.int64).copy()
    hi = np.asarray(hi, dtype=np.int64).copy()
    neg_lo = np.asarray(neg_lo, dtype=bool)
    while True:
        act = (hi - lo) > 1
        if not act.any():
            break
        mid = (lo + hi) // 2
        neg = gfun(mid, codes) < 0
        go_hi = (neg != neg_lo) & act
        hi = np.where(go_hi, mid, hi)
        lo = np.where(act & ~go_hi, mid, lo)
    return hi


def rate_arcsec(gfun, t, codes):
    """|dg/dt| in arcsec/s by a +-30 s central difference of g (radians)."""
    t = np.asarray(t, dtype=np.int64)
    return np.abs(gfun(t + 30_000, codes) - gfun(t - 30_000, codes)) / 60.0 * RAD_ARCSEC


# ----------------------------------------------------------------------------------------------------- geocentric
class Geo:
    """Geocentric apparent Sun/Moon and the global shadow quantities."""

    def __init__(self, eph, ts):
        self.eph, self.ts = eph, ts
        self.earth, self.sun, self.moon = eph["earth"], eph["sun"], eph["moon"]

    def _vec(self, ms):
        sv, mv = [], []
        for sl in chunks(ms.size):
            t = oc.time_from_utc_ms(self.ts, ms[sl])
            e = self.earth.at(t)
            sv.append(e.observe(self.sun).apparent().position.km.T)
            mv.append(e.observe(self.moon).apparent().position.km.T)
        return np.concatenate(sv), np.concatenate(mv)

    def ecliptic(self, ms):
        """(lambda_S, lambda_M, beta_M) degrees, apparent, ecliptic of date."""
        ls, lm, bm = [], [], []
        for sl in chunks(ms.size):
            t = oc.time_from_utc_ms(self.ts, ms[sl])
            e = self.earth.at(t)
            _, ln_s, _ = e.observe(self.sun).apparent().ecliptic_latlon(epoch="date")
            b_m, ln_m, _ = e.observe(self.moon).apparent().ecliptic_latlon(epoch="date")
            ls.append(ln_s.degrees)
            lm.append(ln_m.degrees)
            bm.append(b_m.degrees)
        return np.concatenate(ls), np.concatenate(lm), np.concatenate(bm)

    def vals(self, ms):
        ms = np.asarray(ms, dtype=np.int64).ravel()
        sv, mv = self._vec(ms)
        dS, dM = np.linalg.norm(sv, axis=1), np.linalg.norm(mv, axis=1)
        return dict(gl=ang(mv, -sv), gs=ang(mv, sv), pM=np.arcsin(R_E / dM), pS=np.arcsin(R_E / dS),
                    sS=np.arcsin(R_S / dS), sM=np.arcsin(R_M / dM))

    @staticmethod
    def rho(v):
        base = ENLARGE * v["pM"] + v["pS"]
        return base - v["sS"], base + v["sS"]          # rho_u, rho_p

    def gamma_lunar(self, ms):
        return self.vals(ms)["gl"]

    def gamma_solar(self, ms):
        return self.vals(ms)["gs"]

    def g_lunar(self, ms, codes):
        """g = gamma - threshold; codes 0: P (rho_p + s_M), 1: U outer (rho_u + s_M), 2: U inner (rho_u - s_M)."""
        v = self.vals(ms)
        ru, rp = self.rho(v)
        thr = np.choose(np.asarray(codes), [rp + v["sM"], ru + v["sM"], ru - v["sM"]])
        return v["gl"] - thr

    def syzygies(self, start_ms, end_ms, full):
        """Instants (ms, 1 ms) where the apparent elongation (ecliptic of date) crosses 0 (new) or 180 (full)."""
        step = 6 * HOUR_MS
        t = np.arange(start_ms, end_ms + step, step, dtype=np.int64)
        ls, lm, _ = self.ecliptic(t)
        f = wrap180(lm - ls - (180.0 if full else 0.0))
        k = np.nonzero((f[:-1] < 0) & (f[1:] >= 0) & ((f[1:] - f[:-1]) < 90.0))[0]

        def gf(ms, _codes):
            a, b, _ = self.ecliptic(np.asarray(ms, dtype=np.int64))
            return wrap180(b - a - (180.0 if full else 0.0))

        hi = bisect_root(gf, t[k], t[k + 1], np.zeros(k.size, dtype=int), np.ones(k.size, dtype=bool))
        return hi


# ----------------------------------------------------------------------------------------------------- site
class SiteEv:
    """Topocentric Sun/Moon at a place (WGS84, height 0) as in the M01 oracle."""

    def __init__(self, eph, ts, lat, lon):
        self.eph, self.ts = eph, ts
        self.obs = eph["earth"] + wgs84.latlon(lat, lon, 0.0)
        self.site = gr.Site(eph, ts, lat, lon)

    def vals(self, ms):
        ms = np.asarray(ms, dtype=np.int64).ravel()
        sv, mv = [], []
        for sl in chunks(ms.size):
            t = oc.time_from_utc_ms(self.ts, ms[sl])
            o = self.obs.at(t)
            sv.append(o.observe(self.eph["sun"]).apparent().position.km.T)
            mv.append(o.observe(self.eph["moon"]).apparent().position.km.T)
        sv, mv = np.concatenate(sv), np.concatenate(mv)
        dS, dM = np.linalg.norm(sv, axis=1), np.linalg.norm(mv, axis=1)
        return dict(sig=ang(mv, sv), sS=np.arcsin(R_S / dS), sM=np.arcsin(R_M / dM))

    def sigma(self, ms):
        return self.vals(ms)["sig"]

    def g_solar(self, ms, codes):
        """codes 0: outer (s'_S + s'_M), 1: inner |s'_M - s'_S|."""
        v = self.vals(ms)
        thr = np.choose(np.asarray(codes), [v["sS"] + v["sM"], np.abs(v["sM"] - v["sS"])])
        return v["sig"] - thr

    def horizon(self, ms, codes):
        """crossingDeg-style value in degrees: altitude minus the upper-limb refracted threshold; codes 0 Sun, 1 Moon."""
        ms = np.asarray(ms, dtype=np.int64)
        codes = np.asarray(codes)
        out = np.empty(ms.size)
        for c, body in ((0, "sun"), (1, "moon")):
            m = codes == c
            if m.any():
                alt, d = self.site.alt(body, ms[m])
                out[m] = alt - gr.h0(body, SIDE, d)
        return out


def visibility(ev, start, end, body_code):
    """First..last instant in [start, end] with horizon value >= 0. Returns (vs, ve, crossings, extreme_deg).

    crossings = ms of every horizon crossing inside the interval; extreme_deg = min f if visible throughout,
    max f if never visible (distance of the closest approach to the horizon, for the sliver-fragility test)."""
    n = max(2, int(math.ceil((end - start) / VIS_GRID_MS)) + 1)
    ts_ = np.unique(np.concatenate([start + np.arange(n - 1, dtype=np.int64) * VIS_GRID_MS, [end]]))
    f = ev.horizon(ts_, np.full(ts_.size, body_code))
    up = np.nonzero((f[:-1] < 0) & (f[1:] >= 0))[0]
    dn = np.nonzero((f[:-1] >= 0) & (f[1:] < 0))[0]
    cr = []
    for idx, rising in [(i, True) for i in up] + [(i, False) for i in dn]:
        h = bisect_root(ev.horizon, [ts_[idx]], [ts_[idx + 1]], np.array([body_code]), np.array([rising]))
        cr.append((int(h[0]), rising))
    cr.sort()
    if (f >= 0).sum() == 0:
        return None, None, [c for c, _ in cr], float(f.max())
    vs = int(ts_[0]) if f[0] >= 0 else next(c for c, r in cr if r)
    ve = int(ts_[-1]) if f[-1] >= 0 else [c for c, r in cr if not r][-1]
    return vs, ve, [c for c, _ in cr], float(f.min()) if (f >= 0).all() else float("nan")
