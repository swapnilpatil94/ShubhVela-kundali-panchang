"""Python copy of the engine time rules (docs/specs/M01.md section 3.2). Dev tool only.

Instants are integer/float seconds since the Unix epoch (UTC, no leap seconds in the count, as
kotlin.time.Instant). Keep in lock step with engine TimeScales/TimeTables; the golden tests prove it.
"""
import math

J2000 = 2451545.0
UNIX_EPOCH_JD = 2440587.5

# (UTC instant of 00:00 on the date, TAI-UTC seconds), spec 3.2.
_LEAP_DATES = [
    ((1972, 1, 1), 10), ((1972, 7, 1), 11), ((1973, 1, 1), 12), ((1974, 1, 1), 13), ((1975, 1, 1), 14),
    ((1976, 1, 1), 15), ((1977, 1, 1), 16), ((1978, 1, 1), 17), ((1979, 1, 1), 18), ((1980, 1, 1), 19),
    ((1981, 7, 1), 20), ((1982, 7, 1), 21), ((1983, 7, 1), 22), ((1985, 7, 1), 23), ((1988, 1, 1), 24),
    ((1990, 1, 1), 25), ((1991, 1, 1), 26), ((1992, 7, 1), 27), ((1993, 7, 1), 28), ((1994, 7, 1), 29),
    ((1996, 1, 1), 30), ((1997, 7, 1), 31), ((1999, 1, 1), 32), ((2006, 1, 1), 33), ((2009, 1, 1), 34),
    ((2012, 7, 1), 35), ((2015, 7, 1), 36), ((2017, 1, 1), 37),
]


def _days_from_civil(y, m, d):
    y -= m <= 2
    era = (y if y >= 0 else y - 399) // 400
    yoe = y - era * 400
    doy = (153 * (m + (-3 if m > 2 else 9)) + 2) // 5 + d - 1
    doe = yoe * 365 + yoe // 4 - yoe // 100 + doy
    return era * 146097 + doe - 719468


def _epoch(y, m, d):
    return _days_from_civil(y, m, d) * 86400


LEAP_TABLE = [(_epoch(*ymd), n) for ymd, n in _LEAP_DATES]
# VERIFY / TODO(owner): end of the period covered by the latest IERS Bulletin C. Must equal
# TimeTables.DEFAULT.validThrough in the engine (written by C1); B3/B4 assert nothing else about it.
VALID_THROUGH = _epoch(2027, 1, 1)
FIRST_LEAP = LEAP_TABLE[0][0]
# Fable review M2: after VALID_THROUGH, TT-UT1 is the table value at VALID_THROUGH (TAI-UTC + 32.184 s, DUT1
# ignored) growing by 0.1 s per Julian year of 365.25 days, so the rule has no step at the horizon. Source
# assumption (VERIFY / TODO(owner)): mean excess length of day about +0.27 ms over the coming decades. Must match
# TimeScales.FUTURE_SLOPE_SECONDS_PER_YEAR in the engine (the golden tests prove it).
GROWTH_PER_DAY_AFTER_HORIZON = 0.1 / 365.25


def jd_utc(epoch_s):
    return UNIX_EPOCH_JD + epoch_s / 86400.0


def delta_t_seconds(y):
    """Espenak-Meeus polynomials, 1900 onwards (earlier rows are not needed by the 1900-2100 oracle)."""
    if y < 1900:
        raise ValueError("delta_t_seconds: year < 1900 not implemented in the oracle")
    if y < 1920:
        t = y - 1900
        return -2.79 + 1.494119 * t - 0.0598939 * t**2 + 0.0061966 * t**3 - 0.000197 * t**4
    if y < 1941:
        t = y - 1920
        return 21.20 + 0.84493 * t - 0.076100 * t**2 + 0.0020936 * t**3
    if y < 1961:
        t = y - 1950
        return 29.07 + 0.407 * t - t**2 / 233 + t**3 / 2547
    if y < 1986:
        t = y - 1975
        return 45.45 + 1.067 * t - t**2 / 260 - t**3 / 718
    if y < 2005:
        t = y - 2000
        return (63.86 + 0.3345 * t - 0.060374 * t**2 + 0.0017275 * t**3 + 0.000651814 * t**4
                + 0.00002373599 * t**5)
    if y < 2050:
        t = y - 2000
        return 62.92 + 0.32217 * t + 0.005589 * t**2
    u = (y - 1820) / 100
    if y < 2150:
        return -20 + 32 * u**2 - 0.5628 * (2150 - y)
    return -20 + 32 * u**2


def _decimal_year(jd_ut):
    return 2000 + (jd_ut - 2451544.5) / 365.2425


def tai_minus_utc(epoch_s):
    n = 0
    for t0, v in LEAP_TABLE:
        if epoch_s >= t0:
            n = v
    return n


def utc_to_tt_ut1(epoch_s):
    """Returns (tt_jd_parts, ut1_jd_parts); each is (whole, fraction) with whole + fraction = JD.

    In-table instants (1972-01-01 .. VALID_THROUGH): TT = UTC + TAI-UTC + 32.184 s, UT1 = UTC.
    Otherwise the instant is read as UT1: before the table TT = UT1 + deltaT(y); after it
    TT = UT1 + (TT-UT1 at VALID_THROUGH) + GROWTH_PER_DAY_AFTER_HORIZON * days since VALID_THROUGH."""
    if FIRST_LEAP <= epoch_s <= VALID_THROUGH:
        tt_s = epoch_s + tai_minus_utc(epoch_s) + 32.184
        return _split(tt_s), _split(epoch_s)
    ut1 = _split(epoch_s)
    if epoch_s > VALID_THROUGH:
        held = tai_minus_utc(VALID_THROUGH) + 32.184
        dt = held + GROWTH_PER_DAY_AFTER_HORIZON * ((epoch_s - VALID_THROUGH) / 86400.0)
    else:
        dt = delta_t_seconds(_decimal_year(ut1[0] + ut1[1]))
    return _split(epoch_s + dt), ut1


def _split(epoch_s):
    days = epoch_s / 86400.0
    whole = math.floor(days)
    return (UNIX_EPOCH_JD + whole, days - whole)


def tt_jd(parts):
    return parts[0] + parts[1]


def tdb_minus_tt_seconds(tt):
    g = math.radians(357.53 + 0.98560028 * (tt - J2000))
    return 0.001657 * math.sin(g) + 0.000014 * math.sin(2 * g)


def tt_to_tdb_jd(tt):
    return tt + tdb_minus_tt_seconds(tt) / 86400.0
