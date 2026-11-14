"""Jyotish reference maths (docs/specs/M02.md sections 3.2-3.4, 3.7, 3.10). Dev tool only.

Independent of the Kotlin engine and of rules/*.json: every formula below is restated from the spec.
Ayanamsa: Skyfield nutation (IAU 2000A) and mean obliquity. Ascendant: vector intersection of horizon and
ecliptic. MC and Placidus: bisection on the definitions. Dashas: fractions.Fraction. Vargas: closed forms.
"""
import math
from fractions import Fraction

import numpy as np
from skyfield.framelib import mean_equator_and_equinox_of_date

ARCSEC = 1.0 / 3600.0
J2000 = 2451545.0


def wrap360(x):
    return x % 360.0


def wrap180(x):
    return (x + 180.0) % 360.0 - 180.0


# ----------------------------------------------------------------------------------------------- ayanamsa
def dpsi_deg(t):
    """IAU 2000A nutation in longitude from Skyfield, degrees (array)."""
    return np.degrees(t._nutation_angles_radians[0])


def deps_deg(t):
    return np.degrees(t._nutation_angles_radians[1])


def eps_true_deg(t):
    return np.degrees(t._mean_obliquity_radians) + deps_deg(t)


def pa_arcsec(T):
    """IAU 1976 general precession in longitude, arcsec (Lieske et al. 1977)."""
    return 5029.0966 * T + 1.11113 * T**2 - 0.000006 * T**3


LAHIRI_T0_JD = 2435553.5
LAHIRI_T0_ARCSEC = 83700.658  # 23 deg 15' 00.658"


def lahiri(ts, tt_whole, tt_frac):
    """(mean, true) Lahiri ayanamsa in degrees at TT = whole + frac (arrays)."""
    t = ts.tt_jd(np.asarray(tt_whole, dtype=float), np.asarray(tt_frac, dtype=float))
    t0 = ts.tt_jd(LAHIRI_T0_JD)
    T = (np.asarray(tt_whole) - J2000 + np.asarray(tt_frac)) / 36525.0
    T0 = (LAHIRI_T0_JD - J2000) / 36525.0
    d0 = float(dpsi_deg(t0)) * 3600.0
    mean = (LAHIRI_T0_ARCSEC - d0 + pa_arcsec(T) - pa_arcsec(T0)) / 3600.0
    return mean, mean + dpsi_deg(t)


def dpsi_t0_arcsec(ts):
    return float(dpsi_deg(ts.tt_jd(LAHIRI_T0_JD))) * 3600.0


# Spica, HIP 65474, ICRS J2000.0 (spec 3.2; typed without catalogue access, TODO(owner) confirm).
SPICA_RA = math.radians(201.2982474)
SPICA_DEC = math.radians(-11.1613195)
SPICA_PMRA = -42.35  # mas/yr, times cos(dec)
SPICA_PMDEC = -30.67
MAS = math.radians(1.0 / 3600.0 / 1000.0)


def chitra(ts, tt_whole, tt_frac):
    """(mean, true) True-Chitra ayanamsa in degrees (arrays): Spica at sidereal 180 degrees."""
    whole = np.asarray(tt_whole, dtype=float)
    frac = np.asarray(tt_frac, dtype=float)
    t = ts.tt_jd(whole, frac)
    y = (whole - J2000 + frac) / 365.25
    ca, sa, cd, sd = math.cos(SPICA_RA), math.sin(SPICA_RA), math.cos(SPICA_DEC), math.sin(SPICA_DEC)
    p0 = np.array([cd * ca, cd * sa, sd])
    ea = np.array([-sa, ca, 0.0])
    ed = np.array([-sd * ca, -sd * sa, cd])
    mua, mud = SPICA_PMRA * MAS, SPICA_PMDEC * MAS
    p = p0[:, None] + y[None, :] * (mua * ea[:, None] + mud * ed[:, None])
    p = p / np.linalg.norm(p, axis=0)
    M = mean_equator_and_equinox_of_date.rotation_at(t)  # ICRS -> mean equator and equinox of date
    e = np.einsum("ij...,j...->i...", M, p)
    eps = t._mean_obliquity_radians
    ce, se = np.cos(eps), np.sin(eps)
    ey = ce * e[1] + se * e[2]
    lam = np.degrees(np.arctan2(ey, e[0]))
    mean = (lam - 180.0) % 360.0
    return mean, mean + dpsi_deg(t)


# -------------------------------------------------------------------------------------------------- houses
def asc_vec(theta, phi, eps):
    """Rising ecliptic point by vectors in the equator of date (degrees in, degrees out [0,360))."""
    th, ph, ep = math.radians(theta), math.radians(phi), math.radians(eps)
    k = (0.0, -math.sin(ep), math.cos(ep))  # ecliptic pole
    z = (math.cos(th) * math.cos(ph), math.sin(th) * math.cos(ph), math.sin(ph))  # zenith
    d = (k[1] * z[2] - k[2] * z[1], k[2] * z[0] - k[0] * z[2], k[0] * z[1] - k[1] * z[0])
    east = (-math.sin(th), math.cos(th), 0.0)
    if d[0] * east[0] + d[1] * east[1] + d[2] * east[2] < 0:
        d = (-d[0], -d[1], -d[2])
    x = d[0]
    y = math.cos(ep) * d[1] + math.sin(ep) * d[2]
    return math.degrees(math.atan2(y, x)) % 360.0


def asc_formula(theta, phi, eps):
    """The spec 3.3 closed form (cross-check only; not written to fixtures)."""
    th, ph, ep = math.radians(theta), math.radians(phi), math.radians(eps)
    y = math.cos(th) * math.cos(ph)
    x = -(math.sin(th) * math.cos(ep) * math.cos(ph) + math.sin(ph) * math.sin(ep))
    return math.degrees(math.atan2(y, x)) % 360.0


def ra_deg(lam, eps):
    """Right ascension of the ecliptic point (lam, 0), degrees [0,360)."""
    l, e = math.radians(lam), math.radians(eps)
    return math.degrees(math.atan2(math.sin(l) * math.cos(e), math.cos(l))) % 360.0


def dec_rad(lam, eps):
    return math.asin(math.sin(math.radians(eps)) * math.sin(math.radians(lam)))


def bisect(f, lo, hi, iters=100):
    """Root of f in [lo, hi] with f(lo) and f(hi) of opposite sign; fixed iteration count (deterministic)."""
    flo = f(lo)
    for _ in range(iters):
        mid = 0.5 * (lo + hi)
        fm = f(mid)
        if (fm < 0) == (flo < 0) and fm != 0.0:
            lo, flo = mid, fm
        else:
            hi = mid
    return 0.5 * (lo + hi)


def mc_bisect(theta, eps):
    """Ecliptic point whose right ascension is theta."""
    return wrap360(bisect(lambda l: wrap180(ra_deg(l, eps) - theta), theta - 6.0, theta + 6.0))


def _semi_arc_deg(lam, phi, eps):
    x = -math.tan(math.radians(phi)) * math.tan(dec_rad(lam, eps))
    return math.degrees(math.acos(max(-1.0, min(1.0, x))))


def _single_root(f, lo, hi, step=1.0):
    """Scan [lo, hi] at `step` degrees for sign changes; exactly one is required; bisect it."""
    n = max(1, int(math.ceil((hi - lo) / step)))
    pts = [lo + (hi - lo) * i / n for i in range(n + 1)]
    vals = [f(p) for p in pts]
    roots = [i for i in range(n) if (vals[i] < 0) != (vals[i + 1] < 0)]
    if len(roots) != 1:
        raise ValueError(f"placidus: {len(roots)} sign changes")
    return bisect(f, pts[roots[0]], pts[roots[0] + 1])


def placidus(theta, phi, eps, asc, mc):
    """12 cusps (c1..c12) by bisection on the semi-arc definitions, or None where |phi| > 66.0."""
    if abs(phi) > 66.0:
        return None
    A = wrap360(asc - mc)
    if A > 180.0:
        raise ValueError("placidus: asc - mc > 180")

    def above(frac):
        return lambda l: wrap180(ra_deg(l, eps) - theta) - frac * _semi_arc_deg(l, phi, eps)

    def below(k):
        return lambda l: (ra_deg(l, eps) - theta) % 360.0 - 180.0 + k * (180.0 - _semi_arc_deg(l, phi, eps))

    c11 = wrap360(_single_root(above(1.0 / 3.0), mc, mc + A))
    c12 = wrap360(_single_root(above(2.0 / 3.0), mc, mc + A))
    c2 = wrap360(_single_root(below(2.0 / 3.0), mc + A, mc + 180.0))
    c3 = wrap360(_single_root(below(1.0 / 3.0), mc + A, mc + 180.0))
    c = [0.0] * 12
    c[0], c[9], c[10], c[11], c[1], c[2] = asc, mc, c11, c12, c2, c3
    c[3] = wrap360(mc + 180.0)
    c[4], c[5] = wrap360(c11 + 180.0), wrap360(c12 + 180.0)
    c[6] = wrap360(asc + 180.0)
    c[7], c[8] = wrap360(c2 + 180.0), wrap360(c3 + 180.0)
    return c


def sripati(asc, mc):
    """(cusps c1..c12 as bhava madhya, starts_i = sandhi before cusp i), all wrap360."""
    A = wrap360(asc - mc)
    c = [0.0] * 12
    c[9], c[10], c[11] = mc, mc + A / 3.0, mc + 2.0 * A / 3.0
    c[0], c[1], c[2] = asc, asc + (180.0 - A) / 3.0, asc + 2.0 * (180.0 - A) / 3.0
    for i in range(3):
        c[3 + i] = c[9 + i] + 180.0  # c4, c5, c6 = c10, c11, c12 + 180
        c[6 + i] = c[i] + 180.0      # c7, c8, c9 = c1, c2, c3 + 180
    c = [wrap360(v) for v in c]
    starts = [wrap360(c[i - 1] + wrap360(c[i] - c[i - 1]) / 2.0) for i in range(12)]
    return c, starts


# --------------------------------------------------------------------------------------------------- dashas
VIM = [("KETU", 7), ("VENUS", 20), ("SUN", 6), ("MOON", 10), ("MARS", 7), ("RAHU", 18), ("JUPITER", 16),
       ("SATURN", 19), ("MERCURY", 17)]
YOG = [("MANGALA", 1), ("PINGALA", 2), ("DHANYA", 3), ("BHRAMARI", 4), ("BHADRIKA", 5), ("ULKA", 6),
       ("SIDDHA", 7), ("SANKATA", 8)]
DAY_MS = 86_400_000


def round_half_up(fr):
    return math.floor(fr + Fraction(1, 2))


def dasha_tree(moon, year_days, kind):
    """Returns (order, maha) with maha = [(lord_idx_path, startDay, endDay)] as exact Fractions."""
    order = VIM if kind == "VIM" else YOG
    m = len(order)
    total = sum(y for _, y in order)
    x = Fraction(moon) * 27 / 360
    n = min(26, math.floor(x))
    f = x - n
    i0 = n % 9 if kind == "VIM" else (n + 3) % 8
    cycles = 1 if kind == "VIM" else 4
    s = -f * order[i0][1] * year_days
    maha = []
    for j in range(m * cycles):
        idx = (i0 + j) % m
        e = s + order[idx][1] * year_days
        maha.append(((idx,), s, e))
        s = e
    return order, total, maha


def children(order, total, period):
    path, s, e = period
    m = len(order)
    d = e - s
    out = []
    c = 0
    for k in range(m):
        idx = (path[-1] + k) % m
        cs = s + d * c / total
        c += order[idx][1]
        ce = e if k == m - 1 else s + d * c / total
        out.append((path + (idx,), cs, ce))
    return out


def chain_at(order, total, maha, xd, depth):
    cur = [p for p in maha if p[1] <= xd < p[2]]
    if len(cur) != 1:
        raise ValueError("dasha probe outside the maha list")
    ch = [cur[0]]
    for _ in range(depth - 1):
        kids = [p for p in children(order, total, ch[-1]) if p[1] <= xd < p[2]]
        if len(kids) != 1:
            raise ValueError("dasha probe not in exactly one child")
        ch.append(kids[0])
    return ch


# --------------------------------------------------------------------------------------------------- vargas
def _cls(s, by):
    return {"NONE": 0, "PARITY": s % 2, "MODALITY": s % 3, "ELEMENT": s % 4}[by]


# id -> (by, start list [("r"|"a", value)], step)
_CYCLIC = {
    "D1": ("NONE", [("r", 0)], 1), "D3": ("NONE", [("r", 0)], 4), "D4": ("NONE", [("r", 0)], 3),
    "D7": ("PARITY", [("r", 0), ("r", 6)], 1), "D9": ("MODALITY", [("r", 0), ("r", 8), ("r", 4)], 1),
    "D10": ("PARITY", [("r", 0), ("r", 8)], 1), "D12": ("NONE", [("r", 0)], 1),
    "D16": ("MODALITY", [("a", 0), ("a", 4), ("a", 8)], 1), "D20": ("MODALITY", [("a", 0), ("a", 8), ("a", 4)], 1),
    "D24": ("PARITY", [("a", 4), ("a", 3)], 1),
    "D27": ("ELEMENT", [("a", 0), ("a", 3), ("a", 6), ("a", 9)], 1),
    "D40": ("PARITY", [("a", 0), ("a", 6)], 1), "D45": ("MODALITY", [("a", 0), ("a", 4), ("a", 8)], 1),
    "D60": ("NONE", [("r", 0)], 1), "D3J": ("MODALITY", [("r", 0), ("r", 8), ("r", 4)], 4),
}
_N = {"D1": 1, "D2": 2, "D3": 3, "D3J": 3, "D4": 4, "D7": 7, "D9": 9, "D10": 10, "D12": 12, "D16": 16, "D20": 20,
      "D24": 24, "D27": 27, "D30": 30, "D40": 40, "D45": 45, "D60": 60}
_TABLE = {
    "D2": ([(15, 4), (30, 3)], [(15, 3), (30, 4)]),
    "D30": ([(5, 0), (10, 10), (18, 8), (25, 2), (30, 6)], [(5, 1), (12, 5), (20, 11), (25, 9), (30, 7)]),
}
VARGA_IDS = ["D1", "D2", "D3", "D4", "D7", "D9", "D10", "D12", "D16", "D20", "D24", "D27", "D30", "D40", "D45",
             "D60", "D3J"]


def varga_pos(vid, lon):
    """(rashi, part) of a sidereal longitude in [0, 360), Parashara rules; operation order per spec 3.7."""
    s = min(11, math.floor(lon / 30.0))
    d = lon - 30.0 * s
    if vid in _TABLE:
        rows = _TABLE[vid][s % 2]
        for i, (end, r) in enumerate(rows):
            if d < end:
                return r, i
        raise ValueError("varga table: no row")
    n = _N[vid]
    k = min(n - 1, math.floor(d * n / 30.0))
    by, starts, step = _CYCLIC[vid]
    kind, v = starts[_cls(s, by)]
    start = (s + v) if kind == "r" else v
    return (start + k * step) % 12, k
