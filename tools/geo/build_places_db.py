#!/usr/bin/env python3
"""Build places.db (FTS4) from GeoNames files the owner has already downloaded.

This script never downloads anything and never opens a network connection.
Usage: build_places_db.py --input-dir DIR [--output FILE] [--langs en,hi,mr,gu]
"""
import argparse
import csv
import hashlib
import os
import sqlite3
import sys
import zoneinfo

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import namekey  # shared normaliser (task B2)

name_key = getattr(namekey, "name_key", None) or getattr(namekey, "normalize")

SCHEMA_VERSION = "1"
ATTRIBUTION_ID = "geonames-cc-by-4.0"
MAX_BYTES = 6_000_000
PAGE_SIZE = 4096
DROP_STAGES = (0, 15_000, 50_000)  # alt names dropped below these populations
BAD_ALT_CODES = {"link", "post", "iata", "icao", "faac", "abbr", "wkdt"}
HISTORIC_CC = {"IN", "PK", "BD"}
DEFAULT_OUT = os.path.join(HERE, "..", "..", "core", "geo", "src", "main", "assets", "geo", "places.db")


class BuildError(Exception):
    pass


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def tsv_rows(path):
    with open(path, encoding="utf-8", newline="") as f:
        rd = csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE)
        next(rd, None)  # header
        for row in rd:
            if row and any(c.strip() for c in row):
                yield row


def check_zone(zone):
    try:
        zoneinfo.ZoneInfo(zone)
    except Exception:
        raise BuildError("invalid IANA zone in input")


def to_int(s):
    s = s.strip()
    return int(float(s)) if s else None


def load_admin1(path):
    out = {}
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(row) >= 2:
                out[row[0]] = row[1]
    return out


def load_cities(path, admin1):
    places = {}
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(row) < 18:
                raise BuildError("cities file: bad column count")
            elev = to_int(row[15])
            if elev is None:
                dem = to_int(row[16])
                elev = None if dem is None or dem == -9999 else dem
            zone = row[17]
            check_zone(zone)
            cc = row[8]
            places[int(row[0])] = {
                "id": int(row[0]), "name": row[1],
                "admin1": admin1.get(cc + "." + row[10]) if row[10] else None,
                "cc": cc, "lat_e5": round(float(row[4]) * 1e5), "lon_e5": round(float(row[5]) * 1e5),
                "elev": elev, "pop": to_int(row[14]) or 0, "zone": zone, "overlay": None,
                "alts": set(),
            }
    return places


def load_alternates(path, places, langs):
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(row) < 8:
                continue
            gid = int(row[1]) if row[1].isdigit() else None
            p = places.get(gid)
            if p is None:
                continue
            code, name = row[2], row[3]
            if code in BAD_ALT_CODES or row[6] == "1" or not name:
                continue
            if code in langs or (row[7] == "1" and p["cc"] in HISTORIC_CC):
                p["alts"].add(name)


def load_diaspora(path, places):
    for row in tsv_rows(path):
        if len(row) != 12:
            raise BuildError("diaspora.tsv: bad column count")
        pid = int(row[0])
        check_zone(row[8])
        places[pid] = {
            "id": pid, "name": row[1], "admin1": row[2] or None, "cc": row[3],
            "lat_e5": round(float(row[4]) * 1e5), "lon_e5": round(float(row[5]) * 1e5),
            "elev": to_int(row[6]), "pop": to_int(row[7]) or 0, "zone": row[8],
            "overlay": row[9] or None, "alts": {a for a in row[10].split("|") if a},
        }


def apply_renames(path, places):
    idx = {}
    for p in places.values():
        idx.setdefault((p["cc"], p["name"]), []).append(p)
    for row in tsv_rows(path):
        if len(row) < 3:
            raise BuildError("renames.tsv: bad column count")
        for p in idx.get((row[0], row[1]), []):
            p["alts"].update(a for a in row[2].split("|") if a)


def apply_tags(path, places):
    idx = {}
    for p in places.values():
        idx.setdefault((p["cc"], p["name"]), []).append(p)
    for row in tsv_rows(path):
        if len(row) < 3:
            raise BuildError("overlay_tags.tsv: bad column count")
        for p in idx.get((row[0], row[1]), []):
            p["overlay"] = row[2]


def write_db(out_path, places, meta, min_alt_pop):
    tmp = out_path + ".tmp"
    if os.path.exists(tmp):
        os.remove(tmp)
    con = sqlite3.connect(tmp)
    try:
        con.execute("PRAGMA page_size=%d" % PAGE_SIZE)
        with open(os.path.join(HERE, "schema.sql"), encoding="utf-8") as f:
            con.executescript(f.read())
        for pid in sorted(places):
            p = places[pid]
            con.execute("INSERT INTO place VALUES (?,?,?,?,?,?,?,?,?,?)",
                        (pid, p["name"], p["admin1"], p["cc"], p["lat_e5"], p["lon_e5"],
                         p["elev"], p["pop"], p["zone"], p["overlay"]))
            names = [p["name"]]
            if p["pop"] >= min_alt_pop:
                names += sorted(p["alts"])
            seen = set()
            for disp in names:
                key = name_key(disp)
                if not key or (key, disp) in seen:
                    continue
                seen.add((key, disp))
                con.execute("INSERT INTO place_name(key, display, place_id) VALUES (?,?,?)", (key, disp, pid))
        con.execute("INSERT INTO place_name(place_name) VALUES ('optimize')")
        for k in sorted(meta):
            con.execute("INSERT INTO meta VALUES (?,?)", (k, meta[k]))
        con.commit()
        con.execute("VACUUM")
    finally:
        con.close()
    size = os.path.getsize(tmp)
    return tmp, size


def build(input_dir, output, langs=("en", "hi", "mr", "gu"), max_bytes=MAX_BYTES, supplements_dir=HERE):
    """Returns (size_bytes, stage_index). Raises BuildError when it cannot meet the gate."""
    try:
        con = sqlite3.connect(":memory:")
        con.execute("CREATE VIRTUAL TABLE t USING fts4(a)")
        con.close()
    except sqlite3.Error:
        raise BuildError("this sqlite3 has no FTS4")
    cities = os.path.join(input_dir, "cities5000.txt")
    admin = os.path.join(input_dir, "admin1CodesASCII.txt")
    alts = os.path.join(input_dir, "alternateNamesV2.txt")
    for need in (cities, admin):
        if not os.path.isfile(need):
            raise BuildError("missing input file: " + os.path.basename(need))
    sup = {n: os.path.join(supplements_dir, n) for n in ("diaspora.tsv", "renames.tsv", "overlay_tags.tsv")}
    places = load_cities(cities, load_admin1(admin))
    have_alts = os.path.isfile(alts)
    if have_alts:
        load_alternates(alts, places, set(langs))
    load_diaspora(sup["diaspora.tsv"], places)
    apply_renames(sup["renames.tsv"], places)
    apply_tags(sup["overlay_tags.tsv"], places)
    meta = {
        "schema_version": SCHEMA_VERSION,
        "attribution": ATTRIBUTION_ID,
        "sha256_cities5000": sha256(cities),
        "sha256_admin1CodesASCII": sha256(admin),
        "sha256_alternateNamesV2": sha256(alts) if have_alts else "absent",
        "sha256_diaspora": sha256(sup["diaspora.tsv"]),
        "sha256_renames": sha256(sup["renames.tsv"]),
        "sha256_overlay_tags": sha256(sup["overlay_tags.tsv"]),
    }
    os.makedirs(os.path.dirname(os.path.abspath(output)), exist_ok=True)
    for stage, min_pop in enumerate(DROP_STAGES):
        tmp, size = write_db(output, places, meta, min_pop)
        if size <= max_bytes:
            os.replace(tmp, output)
            return size, stage
        os.remove(tmp)
    raise BuildError("database exceeds the size gate even without small-place alternate names")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--input-dir", required=True, help="folder holding the GeoNames files (owner supplies them)")
    ap.add_argument("--output", default=DEFAULT_OUT)
    ap.add_argument("--langs", default="en,hi,mr,gu")
    ap.add_argument("--max-bytes", type=int, default=MAX_BYTES)
    a = ap.parse_args(argv)
    try:
        size, stage = build(a.input_dir, a.output, tuple(x for x in a.langs.split(",") if x), a.max_bytes)
    except BuildError as e:
        sys.stderr.write("error: %s\n" % e)
        return 1
    sys.stderr.write("ok: %d bytes, drop stage %d\n" % (size, stage))
    return 0


if __name__ == "__main__":
    sys.exit(main())
