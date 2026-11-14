"""Lunar nodes oracle (docs/specs/M01.md section 3.6, formulas) using Skyfield. Dev tool only."""
import numpy as np
from skyfield.framelib import mean_equator_and_equinox_of_date

ARCSEC = 1.0 / 3600.0


def _t_cent(t):
    return (t.tt - 2451545.0) / 36525.0


def nutation_dpsi_deg(t):
    """IAU 2000A nutation in longitude (Skyfield), degrees."""
    return np.degrees(t._nutation_angles_radians[0])


def rahu_mean_deg(t):
    x = _t_cent(t)
    omega = 125.04455501 - (6962890.5431 * x - 7.4722 * x**2 - 0.007702 * x**3 + 0.00005939 * x**4) * ARCSEC
    return (omega + nutation_dpsi_deg(t)) % 360.0


def rahu_true_deg(t, earth, moon):
    """Osculating node of the geometric geocentric Moon state (no light-time, no aberration),
    rotated into the mean ecliptic of date (Skyfield precession + bias, mean obliquity), plus dpsi."""
    geo = (moon - earth).at(t)
    r = geo.position.km
    v = geo.velocity.km_per_s
    M = mean_equator_and_equinox_of_date.rotation_at(t)  # ICRS -> mean equator of date, (3,3,n)
    eps = t._mean_obliquity_radians
    ce, se = np.cos(eps), np.sin(eps)
    # R1(eps): equator -> ecliptic
    def rot(vec):
        e = np.einsum("ij...,j...->i...", M, vec)
        return np.array([e[0], ce * e[1] + se * e[2], -se * e[1] + ce * e[2]])
    re, ve = rot(r), rot(v)
    h = np.cross(re, ve, axis=0)
    lam = np.degrees(np.arctan2(h[0], -h[1]))
    return (lam + nutation_dpsi_deg(t)) % 360.0
