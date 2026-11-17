#!/usr/bin/env python3
"""Bond Code v1 vectors (docs/specs/M12.md rule 13). Dev tool only; Python stdlib only; seeded.

Run:   python3 tools/oracle/gen_bondcode.py            (writes core/share/src/test/resources/bondcode_vectors.json)
Check: python3 tools/oracle/gen_bondcode.py --check    (regenerates, byte-compares, exits non-zero on a diff)
Independent of the Kotlin codec: own CRC-8, base64 (stdlib), NFC (unicodedata); Moon facts from koota_ref.py.
50 valid vectors (traits -> code, g/y/nd recomputed from mn) and labelled invalid codes, each broken in one way
and re-checksummed where the defect is not the checksum, so the named check is the one that must fire.
Synthetic names only.
"""
import base64
import hashlib
import json
import random
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import koota_ref as kr  # noqa: E402

OUT = Path(__file__).resolve().parent.parent.parent / "core" / "share" / "src" / "test" / "resources" / "bondcode_vectors.json"
SEED = 0x5EED
NAMES = ["Asha", "Ravi", "A.K.", "Meera", "Jo", "Priya S", "Tanvi R", "Kabir", "Zoe", "Nikhil",
         "प्रिया", "रवि", "அருண்",
         "José", "Renée", "कृष्णा दास", "Ana \U0001F642",
         "അനു", "অমিত", "ABCDEFGHIJKL", "नमस्तेअआइईउऊ"]


def crc8(data):
    crc = 0
    for x in data:
        crc ^= x
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc


def b64(data):
    return base64.urlsafe_b64encode(bytes(data)).decode("ascii").rstrip("=")


def vashya_choices(n, p):
    a = kr.pada_start(n, p)
    out = [kr.vashya_at(a)]
    v1 = kr.vashya_at(a + kr.PADA_SPAN - kr.Fraction(1, 10 ** 9))
    return out + ([v1] if v1 != out[0] else [])


def body(t):
    name = unicodedata.normalize("NFC", t["name"]).encode("utf-8")
    has = 0 if t["lg"] is None else 1
    lg = 0xF if t["lg"] is None else t["lg"]
    b = [1, t["mn"], (t["mp"] - 1) << 6 | t["mars"] << 4 | has << 3 | t["vs"], t["mr"] << 4 | lg,
         t["ven"] << 4 | t["sun"], len(name)] + list(name)
    return b


def seal(b):
    return b + [crc8(b)]


def traits(rnd, name):
    n, p = rnd.randrange(27), rnd.randrange(1, 5)
    return {"name": name, "mn": n, "mp": p, "mr": kr.rashi_of(n, p),
            "lg": None if rnd.random() < 0.3 else rnd.randrange(12), "mars": rnd.randrange(3),
            "ven": rnd.randrange(12), "sun": rnd.randrange(12), "vs": rnd.choice(vashya_choices(n, p)),
            "g": kr.gana(n), "y": kr.yoni(n), "nd": kr.nadi(n)}


def build():
    assert crc8(b"123456789") == 0xF4
    rnd = random.Random(SEED)
    valid = []
    for i in range(50):
        name = NAMES[i % len(NAMES)]
        assert 1 <= len(name) <= 12 and len(name.encode("utf-16-le")) // 2 <= 12
        assert len(name.encode("utf-8")) <= 36
        t = traits(rnd, name)
        if i == 0:   # the two straddling padas with their second-half class
            t.update(mn=19, mp=1, mr=8, vs=kr.CH, g=kr.gana(19), y=kr.yoni(19), nd=kr.nadi(19))
        if i == 1:
            t.update(mn=21, mp=2, mr=9, vs=kr.JA, g=kr.gana(21), y=kr.yoni(21), nd=kr.nadi(21))
        t["code"] = b64(seal(body(t)))
        valid.append(t)
    base = body(valid[2])
    inv = []

    def add(why, code):
        inv.append({"why": why, "code": code})

    def mut(why, f):
        b = list(base)
        f(b)
        add(why, b64(seal(b)))

    add("empty", "")
    add("too_long", "A" * 129)
    add("bad_char_plus", valid[3]["code"][:-1] + "+")
    add("padding", valid[3]["code"] + "=")
    add("space", " " + valid[3]["code"])
    good = seal(base)
    add("bad_crc", b64(good[:-1] + [good[-1] ^ 0x01]))
    raw = b64(good)
    # a trailing character whose unused low bits are set (only when the length leaves unused bits)
    alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    if len(good) % 3:
        add("non_canonical_bits", raw[:-1] + alpha[alpha.index(raw[-1]) | 1])
    add("length_mod4_is_1", raw + "A" * ((1 - len(raw)) % 4 or 4))
    mut("version_2", lambda b: b.__setitem__(0, 2))
    mut("version_0", lambda b: b.__setitem__(0, 0))
    mut("mn_27", lambda b: b.__setitem__(1, 27))
    mut("mr_mismatch", lambda b: b.__setitem__(3, (((b[3] >> 4) + 1) % 12) << 4 | (b[3] & 0xF)))
    mut("mr_12", lambda b: b.__setitem__(3, 12 << 4 | (b[3] & 0xF)))
    mut("mars_3", lambda b: b.__setitem__(2, b[2] | 0x30))
    mut("vs_5", lambda b: b.__setitem__(2, (b[2] & 0xF8) | 5))
    mut("vs_mismatch", lambda b: b.__setitem__(2, (b[2] & 0xF8) | ((b[2] & 7) + 1) % 5))
    mut("lagna_flag_without_lagna", lambda b: (b.__setitem__(2, b[2] | 0x08), b.__setitem__(3, b[3] | 0xF)))
    mut("lagna_without_flag", lambda b: (b.__setitem__(2, b[2] & ~0x08), b.__setitem__(3, (b[3] & 0xF0) | 3)))
    mut("lagna_12", lambda b: (b.__setitem__(2, b[2] | 0x08), b.__setitem__(3, (b[3] & 0xF0) | 12)))
    mut("venus_12", lambda b: b.__setitem__(4, 12 << 4 | (b[4] & 0xF)))
    mut("sun_15", lambda b: b.__setitem__(4, (b[4] & 0xF0) | 15))
    mut("length_short", lambda b: b.__setitem__(5, b[5] + 1))
    mut("length_long", lambda b: b.__setitem__(5, b[5] - 1))

    def with_name(why, raw_name):
        b = base[:5] + [len(raw_name)] + list(raw_name)
        add(why, b64(seal(b)))
    with_name("name_empty", b"")
    with_name("name_rlo", "Ab‮c".encode("utf-8"))
    with_name("name_lrm", "Ab‎c".encode("utf-8"))
    with_name("name_newline", b"Ab\nc")
    with_name("name_nul", b"Ab\x00c")
    with_name("name_not_nfc", "José".encode("utf-8"))
    with_name("name_bad_utf8", b"Ab\xc3")
    with_name("name_surrogate", b"Ab\xed\xa0\x80")
    with_name("name_13_code_points", b"ABCDEFGHIJKLM")
    with_name("name_37_bytes", ("क" * 12 + "a").encode("utf-8"))
    return {"version": 1, "crcCheck": "123456789", "crcCheckValue": 0xF4, "seed": SEED, "valid": valid,
            "invalid": inv}


def main():
    text = json.dumps(build(), sort_keys=True, ensure_ascii=True, indent=1) + "\n"
    data = text.encode("ascii")
    if "--check" in sys.argv[1:]:
        if not OUT.exists() or OUT.read_bytes() != data:
            print("DIFF bondcode_vectors.json")
            sys.exit(1)
        print(f"OK   bondcode_vectors.json sha256 {hashlib.sha256(data).hexdigest()}")
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data)
    print(f"wrote bondcode_vectors.json sha256 {hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
