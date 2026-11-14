#!/usr/bin/env python
"""PBKDF2-HMAC-SHA256 oracle (docs/specs/M06.md section 6). Dev tool only, standard library only.

Run:   tools/oracle/.venv/bin/python -I tools/oracle/gen_pbkdf2.py [--check]
Writes core/data/src/test/resources/vectors/pbkdf2_sha256.json using hashlib.pbkdf2_hmac.
The password bytes are UTF-8 of unicodedata.normalize("NFC", password), exactly as the Kotlin
side derives export keys (spec section 4, D6). Deterministic: no timestamps, fixed key order,
ASCII-only JSON (non-ASCII passwords are \\u-escaped). --check regenerates in memory and compares
byte for byte.

The NFC pair uses U+0958 (DEVANAGARI LETTER QA, a composition exclusion) versus U+0915 U+093C
(KA + NUKTA). Both normalise to U+0915 U+093C, so their keys must be equal.
"""
import hashlib
import json
import sys
import unicodedata
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
OUT = REPO / "core" / "data" / "src" / "test" / "resources" / "vectors" / "pbkdf2_sha256.json"

NFC_PRECOMPOSED = "\u0958abcdefghij"
NFC_DECOMPOSED = "\u0915\u093cabcdefghij"

# (name, password, salt bytes, iterations, dkLen)
CASES = [
    ("rfc7914_1", "passwd", b"salt", 1, 64),
    ("rfc7914_2", "Password", b"NaCl", 80000, 64),
    ("pp_export", "pp-test-passphrase", bytes(range(16)), 600000, 32),
    ("nfc_precomposed", NFC_PRECOMPOSED, bytes(16), 1000, 32),
    ("nfc_decomposed", NFC_DECOMPOSED, bytes(16), 1000, 32),
]


def fail(msg):
    sys.stderr.write("gen_pbkdf2: " + msg + "\n")
    sys.exit(1)


def build():
    if NFC_PRECOMPOSED == NFC_DECOMPOSED:
        fail("NFC pair inputs must differ before normalisation")
    rows = []
    for name, password, salt, iterations, dklen in CASES:
        p = unicodedata.normalize("NFC", password).encode("utf-8")
        dk = hashlib.pbkdf2_hmac("sha256", p, salt, iterations, dklen)
        rows.append({
            "name": name,
            "password": password,
            "passwordNfcUtf8Hex": p.hex(),
            "saltHex": salt.hex(),
            "iterations": iterations,
            "dkLen": dklen,
            "dkHex": dk.hex(),
        })
    by_name = {r["name"]: r for r in rows}
    if by_name["nfc_precomposed"]["dkHex"] != by_name["nfc_decomposed"]["dkHex"]:
        fail("NFC pair keys differ")
    return {"algorithm": "PBKDF2-HMAC-SHA256", "normalization": "NFC", "encoding": "UTF-8", "cases": rows}


def render(doc):
    head = {k: doc[k] for k in ("algorithm", "normalization", "encoding")}
    lines = [json.dumps(r, ensure_ascii=True, separators=(",", ":")) for r in doc["cases"]]
    return ("{\n" + json.dumps(head, ensure_ascii=True, separators=(",", ":"))[1:-1]
            + ',\n"cases":[\n' + ",\n".join(lines) + "\n]\n}\n")


def main():
    data = render(build()).encode("ascii")
    if "--check" in sys.argv[1:]:
        if not OUT.is_file() or OUT.read_bytes() != data:
            fail("pbkdf2_sha256.json is not byte-identical to a fresh run")
        print("ok sha256=" + hashlib.sha256(data).hexdigest()[:8])
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data)
    print("wrote %d bytes sha256=%s" % (len(data), hashlib.sha256(data).hexdigest()[:8]))


if __name__ == "__main__":
    main()
