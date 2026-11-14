#!/usr/bin/env python
"""Timezone offset oracle (docs/specs/M05.md section 5). Dev tool only, standard library only.

Run:   tools/oracle/.venv/bin/python tools/oracle/gen_tz.py [--check]
Writes engine-golden/src/main/resources/golden/tz_offsets.json from the system tz database
(/usr/share/zoneinfo via zoneinfo.reset_tzpath; the tzdata pip package is refused). Deterministic: no
timestamps, sorted rows, compact arrays. --check regenerates in memory and compares byte for byte.

Rows (all epoch seconds, offsets in seconds):
  sampled     [zoneIdx, t, offset]                 monthly 1st 12:00Z before 2026-01-01; 1970-2025 all zones, 1900-1969 five South Asian zones
  transitions [zoneIdx, t, offsetBefore, offsetAfter]  every change found between samples, bisected to 1 s (t-1 and t checked)
  local       [zoneIdx, kind, localAsUtc, epochFold0, epochFold1|null]  kind in gap, overlap, midnight_gap, midnight_overlap
              (no midnight_gap for gaps of 24 h or more: the whole civil day is nonexistent);
              fold 0 = first occurrence (gap: offset before), fold 1 = second occurrence (overlap only).
              Transitions out of LMT are skipped: the resolver replaces a zone's LMT by round(lon*240) s (spec 4.4).
"""
import datetime as dt
import hashlib
import json
import sys
import zoneinfo
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
OUT = REPO / "engine-golden" / "src" / "main" / "resources" / "golden" / "tz_offsets.json"
TZ_ROOT = "/usr/share/zoneinfo"

SEED = REPO / "core" / "geo" / "src" / "main" / "assets" / "geo" / "places_seed.tsv"
BOUNDARY_ZONES = {
    "Pacific/Apia", "Pacific/Kiritimati", "America/Santiago", "America/Havana", "Asia/Beirut",
    "Australia/Lord_Howe", "Asia/Kathmandu",
}
# Sampled rows stop before both tzdb 2026b (host JDK) and 2026f (system) so later law cannot skew them.
SAMPLED_UNTIL = "2026-01-01T00:00:00Z"
EXPECTED_JDK_TZDB = "2026b"


def load_zones():
    if not SEED.is_file():
        fail("missing seed file")
    lines = SEED.read_text(encoding="utf-8").splitlines()
    header = lines[0].split("\t")
    col = header.index("zone")
    zones = {ln.split("\t")[col] for ln in lines[1:] if ln.strip()}
    return sorted(zones | BOUNDARY_ZONES)


ZONES = load_zones()
OLD_ZONES = {"Asia/Kolkata", "Asia/Karachi", "Asia/Dhaka", "Asia/Kathmandu", "Asia/Colombo"}
EPOCH = dt.datetime(1970, 1, 1, tzinfo=dt.timezone.utc)
SAMPLED_CUTOFF = 1767225600  # SAMPLED_UNTIL as epoch seconds
DAY = 86400


def fail(msg):
    sys.stderr.write("gen_tz: " + msg + "\n")
    sys.exit(1)


def tz_version():
    if "tzdata" in sys.modules:
        fail("the tzdata pip package must not be used")
    zoneinfo.reset_tzpath([TZ_ROOT])
    p = Path(TZ_ROOT) / "+VERSION"
    if not p.is_file():
        fail("missing " + str(p))
    v = p.read_text().strip()
    if not v:
        fail("empty tz version file")
    return v


def epoch(y, m, d, h=0):
    return int((dt.datetime(y, m, d, h, tzinfo=dt.timezone.utc) - EPOCH).total_seconds())


def offset_at(zi, t):
    d = (EPOCH + dt.timedelta(seconds=t)).astimezone(zi)
    return int(d.utcoffset().total_seconds())


def tzname_at(zi, t):
    return (EPOCH + dt.timedelta(seconds=t)).astimezone(zi).tzname()


def local_epoch(zi, local_as_utc, fold):
    naive = dt.datetime(1970, 1, 1) + dt.timedelta(seconds=local_as_utc)
    off = int(naive.replace(tzinfo=zi, fold=fold).utcoffset().total_seconds())
    return local_as_utc - off


def month_starts(y0, y1):
    return [epoch(y, m, 1, 12) for y in range(y0, y1 + 1) for m in range(1, 13)]


def bisect(zi, lo, hi, olo, ohi, out):
    """Appends (t, before, after) for every offset change in (lo, hi]; offsets at lo / hi are olo / ohi."""
    if olo == ohi:
        return
    if hi - lo == 1:
        out.append((hi, olo, ohi))
        return
    mid = (lo + hi) // 2
    om = offset_at(zi, mid)
    bisect(zi, lo, mid, olo, om, out)
    bisect(zi, mid, hi, om, ohi, out)


def build():
    version = tz_version()
    sampled, transitions, local = [], [], []
    skipped_lmt = 0
    for idx, name in enumerate(ZONES):
        zi = zoneinfo.ZoneInfo(name)
        ts = month_starts(1900, 2035) if name in OLD_ZONES else month_starts(1970, 2035)
        offs = [offset_at(zi, t) for t in ts]
        for t, o in zip(ts, offs):
            if t < SAMPLED_CUTOFF:
                sampled.append([idx, t, o])
        found = []
        for i in range(len(ts) - 1):
            bisect(zi, ts[i], ts[i + 1], offs[i], offs[i + 1], found)
        for t, before, after in found:
            transitions.append([idx, t, before, after])
            if tzname_at(zi, t - 1) == "LMT":
                skipped_lmt += 1
                continue
            delta = after - before
            if delta > 0:
                lo, hi, kind, mkind = t + before, t + after, "gap", "midnight_gap"
                mid = lo + delta // 2
            else:
                lo, hi, kind, mkind = t + after, t + before, "overlap", "midnight_overlap"
                mid = lo + (-delta) // 2
            rows = [(kind, mid)]
            m = -(-lo // DAY) * DAY  # first local midnight >= lo
            if m < hi and not (delta >= DAY):  # a skipped whole day is Nonexistent, not a midnight case
                rows.append((mkind, m))
            for k, loc in rows:
                f0 = local_epoch(zi, loc, 0)
                f1 = local_epoch(zi, loc, 1) if delta < 0 else None
                local.append([idx, k, loc, f0, f1])
    sampled.sort()
    transitions.sort()
    local.sort(key=lambda r: (r[0], r[2], r[1]))
    meta = {
        "schema": 1, "tzdataVersion": version, "expectedJdkTzdb": EXPECTED_JDK_TZDB,
        "sampledUntilUtc": SAMPLED_UNTIL, "zones": ZONES,
        "sampledCount": len(sampled), "transitionCount": len(transitions),
        "transitionRowCount": 2 * len(transitions), "localCount": len(local),
        "skippedLmtTransitions": skipped_lmt,
    }
    return meta, sampled, transitions, local


def render(meta, sampled, transitions, local):
    def block(name, rows):
        body = ",\n".join(json.dumps(r, separators=(",", ":")) for r in rows)
        return '"%s":[\n%s\n]' % (name, body)
    head = '"meta":' + json.dumps(meta, separators=(",", ":"), sort_keys=True)
    return "{\n" + ",\n".join([head, block("sampled", sampled), block("transitions", transitions),
                               block("local", local)]) + "\n}\n"


def main():
    text = render(*build())
    data = text.encode("utf-8")
    if "--check" in sys.argv[1:]:
        if not OUT.is_file() or OUT.read_bytes() != data:
            fail("tz_offsets.json is not byte-identical to a fresh run")
        print("ok sha256=" + hashlib.sha256(data).hexdigest()[:8])
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data)
    print("wrote %d bytes sha256=%s" % (len(data), hashlib.sha256(data).hexdigest()[:8]))


if __name__ == "__main__":
    main()
