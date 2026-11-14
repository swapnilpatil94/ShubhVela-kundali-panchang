#!/usr/bin/env python
"""Name-key parity fixture (docs/specs/M05.md 4.1, 5). Dev tool only, stdlib only.

Run:   tools/oracle/.venv/bin/python tools/geo/gen_namekey_fixture.py          (writes the fixture)
Check: tools/oracle/.venv/bin/python tools/geo/gen_namekey_fixture.py --check  (byte-identical or exit 1)
Inputs are hand-written strings below; the output is deterministic (no timestamps).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from namekey import name_key  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "engine-golden/src/main/resources/golden/geo_namekey.json"

INPUTS = [
    "Prayagraj", "Allahabad", "allahabad ", "  Mumbai  ", "BOMBAY", "Kolkata", "Calcutta",
    "Zürich", "São Paulo", "Montréal", "Bogotá", "Reykjavík", "Kraków", "Łódź",
    "Curaçao", "Malmö", "Tromsø", "Tromso", "Ålesund", "Ho Chi Minh City", "Saint-Denis",
    "St. John's", "O'Fallon", "Port-of-Spain", "New York, NY", "Dar es Salaam", "Kuala Lumpur (KL)",
    "प्रयागराज", "इलाहाबाद",
    "मुंबई", "कोलकाता", "क़राची",
    "फ़ैज़ाबाद", "ड़ोंगरगढ़",
    "क्षिप्रा", "श्रीनगर",
    "कर्‍नाटक", "क्‌ष", "क्‍ष",
    "अहमदाबाद", "દિલહી", "અમદાવાદ",
    "વડોદરા", "સુરત", "ಬೆಂಗಳೂರು",
    "சென்னை", "హైದರಾಬಾದ್",
    "القاهرة", "東京", "ＴＯＫＹＯ",
    "ﬁnd ① route 66", "Sector 21-B", "2nd Avenue", "A", "  ", "", "---", " Nairobi ",
    "ǅungla", "İstanbul", "Málaga", "Chennai/Madras", "Dhaka_Division", "Kāthmāṇḍū",
]


def render() -> str:
    rows = [{"in": s, "key": name_key(s)} for s in INPUTS]
    return json.dumps({"schema": 1, "rule": "docs/specs/M05.md 4.1", "rows": rows},
                      ensure_ascii=True, indent=1, sort_keys=True) + "\n"


def main() -> int:
    text = render()
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_bytes() != text.encode("ascii"):
            sys.stderr.write("geo_namekey.json differs from generator output\n")
            return 1
        return 0
    OUT.write_bytes(text.encode("ascii"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
