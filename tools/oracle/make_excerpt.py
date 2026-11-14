#!/usr/bin/env python
"""Builds the committed DE440 excerpt (docs/specs/M01.md section 3.4 / 3.9). Dev tool only.

Run: tools/oracle/.venv/bin/python tools/oracle/make_excerpt.py
Needs $PP_EPHEM_DIR/de440.bsp (fetch_kernel.sh). Writes, under engine-golden/src/main/resources:
  ephem/pp-de440-1900-2100.bin, ephem/pp-de440-1900-2100.bin.sha256, ephem/NOTICE-DE440.txt,
  golden/de440_states.json
Deterministic: fixed seeds, sorted output, no timestamps.
"""
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path

import numpy as np
from jplephem.spk import SPK

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
RES = REPO / "engine-golden" / "src" / "main" / "resources"
EPHEM_DIR = Path(os.environ.get("PP_EPHEM_DIR", str(Path.home() / ".cache/parivaar-panchang/ephem")))
FULL = EPHEM_DIR / "de440.bsp"
EXCERPT = EPHEM_DIR / "de440-excerpt.bsp"
BIN = RES / "ephem" / "pp-de440-1900-2100.bin"

J2000 = 2451545.0
SEGMENTS = [(0, 1), (0, 2), (0, 3), (0, 4), (0, 5), (0, 6), (0, 10), (3, 301)]  # (center, target)
EMRAT_EXPECTED = 81.3005682
EMRAT_TOL = 1e-6
CAP_BYTES = 16 * 1024 * 1024
SEED = 20261113
ATTRIBUTION = b"Derived from JPL DE440 (NAIF); modified, renamed 1900-2100 excerpt; not an official NAIF kernel."
assert len(ATTRIBUTION) <= 128
VALID_LO, VALID_HI = 2415020.5, 2488069.5  # engine validRange (TT)
HEADER_SIZE = 168
ENTRY_SIZE = 40


def die(msg):
    print("make_excerpt: FAIL: " + msg, file=sys.stderr)
    sys.exit(1)


def seg_name(c, t):
    return f"{c}->{t}"


def main():
    if not FULL.is_file():
        die(f"{FULL} missing (run fetch_kernel.sh)")
    # lock check
    lock = dict(l.strip().split("=", 1) for l in (HERE / "kernel.lock").read_text().splitlines() if "=" in l)
    h = hashlib.sha256()
    with open(FULL, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    if h.hexdigest() != lock.get("sha256") or str(FULL.stat().st_size) != lock.get("bytes"):
        die("de440.bsp does not match kernel.lock")
    kernel_sha = h.hexdigest()

    # (1) excerpt with jplephem
    if EXCERPT.exists():
        EXCERPT.unlink()
    subprocess.run(
        [sys.executable, "-m", "jplephem", "excerpt", "1899/12/1", "2100/2/1", str(FULL), str(EXCERPT)],
        check=True, stdout=subprocess.DEVNULL,
    )

    # (2) convert
    spk = SPK.open(str(EXCERPT))
    full = SPK.open(str(FULL))
    entries = []
    blobs = []
    jd_start, jd_end = -np.inf, np.inf
    for c, t in SEGMENTS:
        seg = spk[c, t]
        if seg.data_type != 2:
            die(f"segment {seg_name(c, t)} is SPK type {seg.data_type}, not 2")
        init_jd, interval_days, coef = seg.load_array()  # coef[comp, rec, k]
        ncomp, nrec, ncoef = coef.shape
        if ncomp != 3:
            die("component count")
        # the file's INIT and INTLEN (seconds from J2000 TDB)
        init_s, intlen_s, _ = seg._data
        jd_init = J2000 + init_s / 86400.0
        interval = intlen_s / 86400.0
        entries.append((t, c, jd_init, interval, ncoef, nrec))
        blobs.append(np.ascontiguousarray(coef.astype("<f8")))  # (3, nrec, ncoef)
        jd_start = max(jd_start, jd_init)
        jd_end = min(jd_end, jd_init + nrec * interval)
    if jd_start > VALID_LO or jd_end < VALID_HI:
        die(f"excerpt coverage {jd_start}..{jd_end} does not cover {VALID_LO}..{VALID_HI}")

    # (3) EMRAT from the full kernel at TDB J2000
    moon = np.array(full[3, 301].compute(J2000))
    emb_earth = np.array(full[3, 399].compute(J2000))
    emrat = float(np.linalg.norm(moon) / np.linalg.norm(emb_earth))
    if abs(emrat - EMRAT_EXPECTED) > EMRAT_TOL:
        die(f"EMRAT {emrat!r} differs from {EMRAT_EXPECTED} by more than {EMRAT_TOL}")

    # write bin
    data_off = HEADER_SIZE + len(SEGMENTS) * ENTRY_SIZE
    out = bytearray()
    out += b"PPDE440\0"
    out += struct.pack("<II", 1, len(SEGMENTS))
    out += struct.pack("<d", emrat)
    out += struct.pack("<dd", jd_start, jd_end)
    out += ATTRIBUTION.ljust(128, b"\0")
    assert len(out) == HEADER_SIZE
    layout = []
    off = data_off
    for (target, center, jd_init, interval, ncoef, nrec), blob in zip(entries, blobs):
        out += struct.pack("<iiddiiq", target, center, jd_init, interval, ncoef, nrec, off)
        layout.append((target, center, jd_init, interval, ncoef, nrec, off))
        off += nrec * 3 * ncoef * 8
    assert len(out) == data_off
    # record-major layout: blob is (3, nrec, ncoef); the file needs (nrec, 3, ncoef)
    for blob in blobs:
        out += np.ascontiguousarray(np.transpose(blob, (1, 0, 2))).tobytes()
    if len(out) != off:
        die("size accounting")
    if len(out) > CAP_BYTES:
        die(f"bin is {len(out)} bytes, over the {CAP_BYTES} cap")
    BIN.parent.mkdir(parents=True, exist_ok=True)
    BIN.write_bytes(bytes(out))
    bin_sha = hashlib.sha256(bytes(out)).hexdigest()

    # (4) re-read the bin in Python (spec reader) vs jplephem, 1000 seeded points
    rng = np.random.default_rng(SEED)
    raw = BIN.read_bytes()
    worst = 0.0
    for n in range(1000):
        si = int(rng.integers(0, len(layout)))
        target, center, jd_init, interval, ncoef, nrec, off = layout[si]
        jd = float(rng.uniform(jd_start, jd_end))
        r, v = read_state(raw, layout[si], jd)
        ref = exact_eval(full[center, target], jd)
        ref_r, ref_v = np.array(ref[0]), np.array(ref[1])
        tol_r = 1e-12 * np.linalg.norm(ref_r) + 1e-6
        tol_v = 1e-12 * np.linalg.norm(ref_v) + 1e-6
        er, ev = np.abs(r - ref_r).max(), np.abs(v - ref_v).max()
        worst = max(worst, er / tol_r, ev / tol_v)
        if er > tol_r or ev > tol_v:
            die(f"re-read mismatch seg {seg_name(center, target)} jd {jd!r}: dr={er} dv={ev}")

    # (5) de440_states.json: 200 points
    rng2 = np.random.default_rng(SEED + 1)
    pts = []
    for n in range(200):
        si = n % len(layout)
        target, center = layout[si][0], layout[si][1]
        jd = float(rng2.uniform(jd_start, jd_end))
        pr, pv = exact_eval(full[center, target], jd)
        pts.append({"segment": seg_name(center, target), "jdTdb": jd,
                    "r": [float(x) for x in pr], "v": [float(x) for x in pv]})
    pts.sort(key=lambda p: (p["segment"], p["jdTdb"]))
    states = {"meta": {"schema": 1, "seed": SEED + 1, "kernelSha256": kernel_sha,
                       "units": "r km, v km/day, jdTdb TDB", "emrat": emrat},
              "v": pts}
    gold = RES / "golden"
    gold.mkdir(parents=True, exist_ok=True)
    (gold / "de440_states.json").write_text(json.dumps(states, sort_keys=True, separators=(",", ":")) + "\n")

    # (6) sha + notice
    (BIN.parent / "pp-de440-1900-2100.bin.sha256").write_text(f"{bin_sha}  pp-de440-1900-2100.bin\n")
    (BIN.parent / "NOTICE-DE440.txt").write_text(
        "pp-de440-1900-2100.bin is derived from the JPL Development Ephemeris DE440\n"
        "(NASA/JPL/NAIF, file de440.bsp, https://naif.jpl.nasa.gov/pub/naif/generic_kernels/spk/planets/).\n"
        "Modified and renamed excerpt (Chebyshev records for 1899-12 to 2100-02, segments\n"
        "0->1..6, 0->10, 3->301 re-packed into a private binary layout).\n"
        "Not an official NAIF kernel. Source kernel SHA-256: " + kernel_sha + "\n"
        "Reference: Park, R. S. et al. 2021, The Astronomical Journal 161, 105 (DE440 and DE441).\n"
    )
    print(f"OK bin bytes={len(out)} sha256={bin_sha} emrat={emrat!r} jd={jd_start}..{jd_end} "
          f"worst_ratio_to_tol={worst:.3g}")


def exact_eval(segment, jd):
    """jplephem evaluation without its float-seconds rounding (about 3e-5 s at JD 2.4e6, i.e. ~1e-6 km
    for the Moon). The JD is split exactly into a whole-day part (integer days from J2000, so
    whole*86400 is exact) and a fraction, which jplephem keeps strictly separate."""
    n = np.floor(jd - J2000)
    whole = J2000 + n
    frac = jd - whole  # exact
    r, v = segment.compute_and_differentiate(whole, frac)
    return np.array(r), np.array(v)


def read_state(raw, entry, jd):
    """Spec 3.4 reader: floor record, Clenshaw position, T' recursion velocity."""
    target, center, jd_init, interval, ncoef, nrec, off = entry
    r = int(np.floor((jd - jd_init) / interval))
    r = min(max(r, 0), nrec - 1)
    s = 2.0 * ((jd - jd_init) - r * interval) / interval - 1.0
    rec = np.frombuffer(raw, dtype="<f8", count=3 * ncoef, offset=off + r * 3 * ncoef * 8).reshape(3, ncoef)
    pos, vel = [], []
    for comp in range(3):
        c = rec[comp]
        b1 = b2 = 0.0
        for k in range(ncoef - 1, 0, -1):
            b1, b2 = 2.0 * s * b1 - b2 + c[k], b1
        pos.append(c[0] + s * b1 - b2)
        t = [1.0, s]
        dt = [0.0, 1.0]
        for k in range(2, ncoef):
            t.append(2.0 * s * t[k - 1] - t[k - 2])
            dt.append(2.0 * t[k - 1] + 2.0 * s * dt[k - 1] - dt[k - 2])
        vel.append(sum(c[k] * dt[k] for k in range(ncoef)) * 2.0 / interval)
    return np.array(pos), np.array(vel)


if __name__ == "__main__":
    main()
