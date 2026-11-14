"""Shared helpers for the oracle generators (docs/specs/M01.md section 3.9). Dev tool only.

Kernel: skyfield.api.load_file on $PP_EPHEM_DIR/de440.bsp (no downloads), checked against kernel.lock.
Timescale: load.timescale(builtin=True) (no downloads). Time rule: timescale.py (spec 3.2).
Skyfield cannot take a UTC-rule delta-T through delta_t_function (that function maps TT -> dT, while the
spec rule is keyed on the UTC instant and has steps at table edges), so TT and UT1 are computed by
timescale.py and written into the Time objects: Time(ts, whole, tt_fraction) with `ut1_fraction`
(and `delta_t`) set explicitly. Skyfield derives GAST/ERA from `ut1_fraction`, so altaz() uses exactly
the engine's UT1. Skyfield's own TDB-TT series (not the engine's two-term formula) is kept.
"""
import hashlib
import os
from pathlib import Path

import numpy as np
import skyfield
import jplephem
from skyfield.api import load, load_file
from skyfield.timelib import Time

import timescale as tsm

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
RES = REPO / "engine-golden" / "src" / "main" / "resources"
EPHEM_DIR = Path(os.environ.get("PP_EPHEM_DIR", str(Path.home() / ".cache/parivaar-panchang/ephem")))
SEED = 20261113


def die(msg):
    raise SystemExit("oracle: FAIL: " + msg)


def kernel_lock():
    return dict(l.strip().split("=", 1) for l in (HERE / "kernel.lock").read_text().splitlines() if "=" in l)


def load_kernel():
    path = EPHEM_DIR / "de440.bsp"
    if not path.is_file():
        die(f"{path} missing (run fetch_kernel.sh)")
    lock = kernel_lock()
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    if h.hexdigest() != lock["sha256"] or str(path.stat().st_size) != lock["bytes"]:
        die("de440.bsp does not match kernel.lock")
    return load_file(str(path)), h.hexdigest()


def timescale():
    return load.timescale(builtin=True)


def versions():
    return {"jplephem": jplephem.__version__, "numpy": np.__version__, "skyfield": skyfield.__version__}


def time_from_tt(ts, whole, frac):
    """TT given as (whole, fraction) arrays; UT1 is not needed (ecliptic frame, planets)."""
    return ts.tt_jd(np.asarray(whole, dtype=float), np.asarray(frac, dtype=float))


def time_from_utc_ms(ts, ms):
    """Times from integer UTC epoch milliseconds, TT/UT1 by the spec rule (timescale.py)."""
    ms = np.asarray(ms, dtype=np.int64)
    n = ms.size
    tw = np.empty(n)
    tf = np.empty(n)
    uf = np.empty(n)
    for i, m in enumerate(ms.tolist()):
        (a, b), (c, d) = tsm.utc_to_tt_ut1(m / 1000.0)
        tw[i], tf[i] = a, b
        uf[i] = (c - a) + d  # integer day difference, exact
    t = Time(ts, tw, tf)
    t.ut1_fraction = uf
    t.delta_t = (tf - uf) * 86400.0
    return t
