#!/usr/bin/env python
"""Rise/set oracle (docs/specs/M01.md sections 3.8, 3.9). Dev tool only.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_riseset.py
Check: tools/oracle/.venv/bin/python tools/oracle/gen_riseset.py --check  (regenerates in memory, byte-compares
       with the committed file, exits non-zero on any difference or a missing file; writes nothing)
Writes engine-golden/src/main/resources/golden/riseset.json (deterministic: fixed seeds, sorted output, no
timestamps). 10 cities x 400 UTC dates; window = date 00:00 UTC + 24 h; samples every 600 s (145 samples);
events refined by bisection on integer milliseconds (bracket 1 ms; the reported instant is the first
millisecond on the far side of the crossing). Altitude: geometric, airless, body centre, topocentric
(Skyfield altaz without temperature/pressure). Exits non-zero (and writes nothing) if more than 1 % of
entries are fragile.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from skyfield.toposlib import wgs84

sys.path.insert(0, str(Path(__file__).resolve().parent))
import oracle_common as oc  # noqa: E402
import timescale as tsm  # noqa: E402

OUT = oc.RES / "golden" / "riseset.json"
CITIES = [
    ("mumbai", 19.0760, 72.8777), ("delhi", 28.6139, 77.2090), ("kolkata", 22.5726, 88.3639),
    ("chennai", 13.0827, 80.2707), ("ujjain", 23.1765, 75.7885), ("london", 51.5074, -0.1278),
    ("newyork", 40.7128, -74.0060), ("auckland", -36.8485, 174.7633), ("suva", -18.1416, 178.4419),
    ("tromso", 69.6492, 18.9553),
]
CONVS = ["UPPER_LIMB_REFRACTED", "CENTER_REFRACTED", "CENTER_GEOMETRIC"]
BODIES = ["sun", "moon"]
R_REFR_DEG = 34.0 / 60.0           # exactly 34' at P = 1010 hPa, T = 10 C (spec 3.8)
SUN_SEMI_DEG = 16.0 / 60.0
R_MOON_KM = 1737.4
STEP_MS = 600_000
NS = 6 * 24 + 1                     # samples per window
DAY_MS = 86_400_000
N_BROAD, N_RECENT = 200, 200
FRAGILE_SAMPLE_DEG = 60.0 / 3600.0
FRAGILE_RATE = 0.5                  # arcsec/s
FRAGILE_MAX_FRACTION = 0.01
CHUNK = 4000


def h0(body, conv, dist_km):
    """Altitude threshold in degrees for the body centre (spec 3.8). Arrays allowed for dist_km."""
    if conv == "CENTER_GEOMETRIC":
        return np.zeros_like(dist_km)
    if conv == "CENTER_REFRACTED":
        return np.full_like(dist_km, -R_REFR_DEG)
    semi = SUN_SEMI_DEG if body == "sun" else np.degrees(np.arcsin(R_MOON_KM / dist_km))
    return -(R_REFR_DEG + semi) + np.zeros_like(dist_km)


class Site:
    def __init__(self, eph, ts, lat, lon):
        self.eph, self.ts = eph, ts
        self.obs = eph["earth"] + wgs84.latlon(lat, lon, 0.0)

    def alt(self, body, ms):
        """(altitude deg, topocentric distance km) at integer UTC epoch milliseconds."""
        ms = np.asarray(ms, dtype=np.int64)
        alts, ds = [], []
        for i in range(0, ms.size, CHUNK):
            t = oc.time_from_utc_ms(self.ts, ms[i:i + CHUNK])
            a, _, d = self.obs.at(t).observe(self.eph[body]).apparent().altaz()
            alts.append(a.degrees)
            ds.append(d.km)
        return np.concatenate(alts), np.concatenate(ds)


def make_days(city_index):
    rng = np.random.default_rng(oc.SEED + city_index)
    d0, d1 = tsm._days_from_civil(1900, 1, 1), tsm._days_from_civil(2099, 12, 31)
    e0, e1 = tsm._days_from_civil(2024, 1, 1), tsm._days_from_civil(2030, 12, 31)
    broad = rng.integers(d0, d1 + 1, N_BROAD)
    recent = rng.integers(e0, e1 + 1, N_RECENT)
    return np.sort(np.concatenate([broad, recent])).astype(np.int64)


def fragile_samples(f):
    """Clause (b), amended spec 3.9: a sample with |f_k| < 60 arcsec that is not a plain crossing."""
    thr = FRAGILE_SAMPLE_DEG
    for k in np.nonzero(np.abs(f) < thr)[0]:
        plain = (0 < k < len(f) - 1 and (f[k - 1] >= 0) != (f[k + 1] >= 0)
                 and abs(f[k - 1]) >= thr and abs(f[k + 1]) >= thr)
        if not plain:
            return True
    return False


def city_entries(eph, ts, city_index):
    name, lat, lon = CITIES[city_index]
    site = Site(eph, ts, lat, lon)
    days = make_days(city_index)
    nd = days.size
    starts = days * DAY_MS
    samples = (starts[:, None] + np.arange(NS, dtype=np.int64)[None, :] * STEP_MS)  # (nd, NS)
    entries = {}  # (body, conv) -> list per day of dict
    clauses = {}  # (body, conv, day) -> (clause a, clause b)
    for body in BODIES:
        alt, dist = site.alt(body, samples.ravel())
        alt, dist = alt.reshape(nd, NS), dist.reshape(nd, NS)
        # collect brackets
        br = []  # (day, conv_index, kind, lo_ms, hi_ms)
        info = {}  # (day, conv) -> (noEvent or None, fragile_sample)
        for ci, conv in enumerate(CONVS):
            f = alt - h0(body, conv, dist)
            fl, fh = f[:, :-1], f[:, 1:]
            rise = (fl < 0) & (fh >= 0)
            sset = (fl >= 0) & (fh < 0)
            for d in range(nd):
                frag = fragile_samples(f[d])
                ks = np.nonzero(rise[d] | sset[d])[0]
                if ks.size == 0:
                    info[(d, ci)] = ("above" if f[d, 0] >= 0 else "below", frag)
                else:
                    info[(d, ci)] = (None, frag)
                    for k in ks:
                        br.append((d, ci, 0 if rise[d, k] else 1, int(samples[d, k]), int(samples[d, k + 1])))
        events = refine(site, body, br)
        per = {}
        for (d, ci, kind, ms, rate) in events:
            per.setdefault((d, ci), []).append((kind, ms, rate))
        for ci, conv in enumerate(CONVS):
            lst = []
            for d in range(nd):
                noev, frag = info[(d, ci)]
                rate_frag = False
                ev = sorted(per.get((d, ci), []), key=lambda e: e[1])
                if noev is not None:
                    e = {"noEvent": noev}
                else:
                    e = {"events": [["RISE" if k == 0 else "SET", ms, round(rate, 6)] for k, ms, rate in ev]}
                    rate_frag = any(abs(rate) < FRAGILE_RATE for _, _, rate in ev)
                if frag or rate_frag:
                    e["fragile"] = True
                clauses[(body, conv, d)] = (rate_frag, frag)
                lst.append(e)
            entries[(body, conv)] = lst
    return name, starts, entries, clauses


def refine(site, body, br):
    """Vectorised bisection on integer ms. Returns (day, conv, kind, ms, rate arcsec/s)."""
    if not br:
        return []
    day = np.array([b[0] for b in br]); conv = np.array([b[1] for b in br])
    kind = np.array([b[2] for b in br]); lo = np.array([b[3] for b in br], dtype=np.int64)
    hi = np.array([b[4] for b in br], dtype=np.int64)

    def f_at(ms):
        a, d = site.alt(body, ms)
        out = np.empty_like(a)
        for ci, c in enumerate(CONVS):
            m = conv == ci
            out[m] = a[m] - h0(body, c, d[m])
        return out

    while True:
        active = (hi - lo) > 1
        if not active.any():
            break
        mid = (lo + hi) // 2
        fm = f_at(mid)
        is_rise = kind == 0
        go_hi = ((fm >= 0) == is_rise) & active  # mid on the high side of the crossing
        hi = np.where(go_hi, mid, hi)
        lo = np.where(active & ~go_hi, mid, lo)
    f_lo, f_hi = f_at(lo), f_at(hi)
    rate = (f_hi - f_lo) * 3600.0 / ((hi - lo) / 1000.0)
    return [(int(day[i]), int(conv[i]), int(kind[i]), int(hi[i]), float(rate[i])) for i in range(len(br))]


def main():
    eph, ksha = oc.load_kernel()
    ts = oc.timescale()
    rows = []   # (city, start_ms, {body: {conv: entry}})
    total = frag = events = 0
    ca = cb = cboth = 0
    per_city = {}
    for ci in range(len(CITIES)):
        name, starts, entries, clauses = city_entries(eph, ts, ci)
        pc = [0, 0, 0, 0]  # entries, fragile, clause a, clause b
        for d in range(starts.size):
            body_map = {b: {c: entries[(b, c)][d] for c in CONVS} for b in BODIES}
            for b in BODIES:
                for c in CONVS:
                    e = body_map[b][c]
                    ra, rb = clauses[(b, c, d)]
                    total += 1
                    events += len(e.get("events", []))
                    frag += bool(e.get("fragile"))
                    ca += ra; cb += rb; cboth += ra and rb
                    pc[0] += 1; pc[1] += bool(e.get("fragile")); pc[2] += ra; pc[3] += rb
            rows.append((name, int(starts[d]), body_map))
        per_city[name] = pc
    frac = frag / total
    print(f"entries {total}, events {events}, city-days {len(rows)}")
    print(f"fragile {frag} ({100 * frac:.3f} %): clause a (rate) {ca}, clause b (samples) {cb}, both {cboth}")
    for n, pc in per_city.items():
        print(f"  {n:8s} entries {pc[0]} fragile {pc[1]} (a {pc[2]}, b {pc[3]})")
    if frac > FRAGILE_MAX_FRACTION:
        oc.die(f"fragile fraction {100 * frac:.3f} % > {100 * FRAGILE_MAX_FRACTION:.0f} % gate; nothing written")
    rows.sort(key=lambda r: ([c[0] for c in CITIES].index(r[0]), r[1]))
    meta = {
        "schema": 1, "seed": oc.SEED, "versions": oc.versions(), "kernelSha256": ksha,
        "cities": [{"name": n, "lat": la, "lon": lo, "elevationM": 0} for n, la, lo in CITIES],
        "conventions": CONVS, "bodies": BODIES, "windowHours": 24, "stepSeconds": STEP_MS // 1000,
        "fragileEntries": frag, "entries": total, "events": events,
    }
    lines = [json.dumps([n, s, bm], sort_keys=True, separators=(",", ":")) for n, s, bm in rows]
    text = '{"meta":' + json.dumps(meta, sort_keys=True, separators=(",", ":")) + ',"d":[\n' + ",\n".join(lines) + "\n]}\n"
    if "--check" in sys.argv[1:]:
        same = OUT.is_file() and OUT.read_bytes() == text.encode("utf-8")
        print(f"{'ok  ' if same else 'DIFF'} {OUT.name}")
        if not same:
            raise SystemExit("gen_riseset --check: fixture differs from the generator output")
        return
    OUT.write_text(text)
    print(f"wrote {OUT} ({len(text)} bytes)")


if __name__ == "__main__":
    main()
