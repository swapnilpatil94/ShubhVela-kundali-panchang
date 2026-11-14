#!/usr/bin/env python
"""Positions oracle (docs/specs/M01.md section 3.9). Dev tool only.

Run: tools/oracle/.venv/bin/python tools/oracle/gen_positions.py
Writes engine-golden/src/main/resources/golden/positions.json. Deterministic: fixed seed, sorted
epochs, no timestamps. Bodies: SUN, MOON, MERCURY, VENUS, MARS, JUPITER, SATURN, RAHU_MEAN, RAHU_TRUE.
"""
import json
import sys
from pathlib import Path

import numpy as np
from skyfield.api import load  # noqa: F401
from skyfield.framelib import ecliptic_frame

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nodes  # noqa: E402
import oracle_common as oc  # noqa: E402

N = 10_000
H = 0.005
LO, HI = 2415020.5, 2488069.5
BODIES = ["SUN", "MOON", "MERCURY", "VENUS", "MARS", "JUPITER", "SATURN", "RAHU_MEAN", "RAHU_TRUE"]
KEYS = ["sun", "moon", "mercury", "venus", "mars barycenter", "jupiter barycenter", "saturn barycenter"]
OUT = oc.RES / "golden" / "positions.json"


def wrap180(x):
    return (x + 180.0) % 360.0 - 180.0


def lons_lats(eph, ts, whole, frac, with_lat):
    """Returns (lon[9][n], lat[9][n]) at TT = whole + frac."""
    t = oc.time_from_tt(ts, whole, frac)
    earth = eph["earth"]
    lon, lat = [], []
    for key in KEYS:
        la, lo, _ = earth.at(t).observe(eph[key]).apparent().frame_latlon(ecliptic_frame)
        lon.append(lo.degrees % 360.0)
        lat.append(la.degrees)
    lon.append(nodes.rahu_mean_deg(t))
    lon.append(nodes.rahu_true_deg(t, earth, eph["moon"]))
    zeros = np.zeros_like(lon[0])
    lat += [zeros, zeros]
    return lon, lat


def main():
    eph, ksha = oc.load_kernel()
    ts = oc.timescale()
    rng = np.random.default_rng(oc.SEED)
    tt = np.sort(rng.uniform(LO, HI, N))
    whole = np.floor(tt)
    frac = tt - whole  # exact split
    lon0, lat0 = lons_lats(eph, ts, whole, frac, True)
    lon_m, _ = lons_lats(eph, ts, whole, frac - H, False)
    lon_p, _ = lons_lats(eph, ts, whole, frac + H, False)
    speed = [wrap180(p - m) / (2 * H) for p, m in zip(lon_p, lon_m)]
    lines = []
    for i in range(N):
        row = [[round(float(lon0[b][i]), 12), round(float(lat0[b][i]), 12), round(float(speed[b][i]), 12)]
               for b in range(9)]
        lines.append("[" + repr(float(tt[i])) + "," + json.dumps(row, separators=(",", ":")) + "]")
    meta = {"schema": 1, "seed": oc.SEED, "versions": oc.versions(), "kernelSha256": ksha, "bodies": BODIES}
    text = '{"meta":' + json.dumps(meta, sort_keys=True, separators=(",", ":")) + ',"v":[\n' + ",\n".join(lines) + "\n]}\n"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text)
    print(f"wrote {OUT} ({len(text)} bytes, {N} epochs)")


if __name__ == "__main__":
    main()
