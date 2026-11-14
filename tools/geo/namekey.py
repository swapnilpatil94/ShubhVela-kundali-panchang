"""Name-key normaliser (docs/specs/M05.md 4.1). Twin of core/geo internal/NameKey.kt; parity is pinned by
engine-golden/.../golden/geo_namekey.json. Stdlib only."""
import unicodedata

_KEEP = ("L", "M")


def name_key(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text)
    stripped = "".join(
        c for c in decomposed if not (0x0300 <= ord(c) <= 0x036F or ord(c) in (0x200C, 0x200D))
    )
    lower = stripped.lower()
    out = []
    for c in lower:
        cat = unicodedata.category(c)
        out.append(c if cat[0] in _KEEP or cat == "Nd" else " ")
    return " ".join("".join(out).split())


def tokens(key: str):
    return key.split() if key else []


def too_short(key: str) -> bool:
    return len(key.replace(" ", "")) < 2
