#!/usr/bin/env python3
"""Ashtakoota golden fixtures (docs/specs/M12.md, Tests). Dev tool only; Python stdlib only; no randomness.

Run:   python3 tools/oracle/gen_koota.py            (writes koota_pairs.csv and koota_symmetric.csv)
Check: python3 tools/oracle/gen_koota.py --check    (regenerates, byte-compares, exits non-zero on any
       difference or a missing file)
Scores come from koota_ref.py only (tables transcribed from the spec; never rules/koota.json, never engine code
or output). koota_pairs.csv: every ordered pair (groom, bride) of the 110 inputs = 12,100 rows.
koota_symmetric.csv: every unordered pair (i <= j) = 6,105 rows of matchSymmetric.
Columns: groom/bride (or a/b) nakshatra 0..26, pada 1..4, vashya 0..4 (Ch Ma Ja Va Ke), the eight Q values in
koota order, the total Q, and the note ids of all kootas in koota order joined by ';'.
"""
import hashlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import koota_ref as kr  # noqa: E402

GOLD = Path(__file__).resolve().parent.parent.parent / "engine-golden" / "src" / "main" / "resources" / "golden"
HEAD = "vashya,tara,yoni,graha_maitri,gana,bhakoot,nadi,total,notes"


def row(x, y, scores):
    qs = [q for q, _ in scores]
    notes = ";".join(n for _, ns in scores for n in ns)
    cells = [*x, *y, *qs, sum(qs)]
    return ",".join(str(c) for c in cells) + "," + notes


def hand_cases():
    """The three spec hand cases (exact Q)."""
    ash1, bha1, has1 = (0, 1, kr.CH), (1, 1, kr.CH), (12, 1, kr.MA)
    s = kr.score(ash1, ash1)
    assert [q for q, _ in s] == [4, 8, 12, 16, 20, 24, 28, 0] and sum(q for q, _ in s) == 112
    s = kr.score(ash1, bha1)
    assert [q for q, _ in s] == [4, 8, 12, 8, 20, 24, 28, 32] and sum(q for q, _ in s) == 136
    s = kr.score(ash1, has1)
    q = [x for x, _ in s]
    assert (q[0], q[2], q[3], q[4], q[5], q[6], q[7]) == (4, 6, 0, 2, 24, 0, 0)
    assert "koota.bhakoot.dosha" in s[6][1] and "koota.nadi.same" in s[7][1]


def build():
    hand_cases()
    ins = kr.inputs()
    pairs = ["gn,gp,gv,bn,bp,bv,varna," + HEAD]
    for g in ins:
        for b in ins:
            pairs.append(row(g, b, kr.score(g, b)))
    sym = ["an,ap,av,bn,bp,bv,varna," + HEAD]
    for i, a in enumerate(ins):
        for b in ins[i:]:
            sym.append(row(a, b, kr.score_symmetric(a, b)))
    assert len(pairs) == 1 + 12_100 and len(sym) == 1 + 6_105
    return {"koota_pairs.csv": "\n".join(pairs) + "\n", "koota_symmetric.csv": "\n".join(sym) + "\n"}


def main():
    check = "--check" in sys.argv[1:]
    out = build()
    bad = False
    for name, text in out.items():
        path = GOLD / name
        data = text.encode("utf-8")
        if check:
            if not path.exists() or path.read_bytes() != data:
                print(f"DIFF {name}")
                bad = True
            else:
                print(f"OK   {name} sha256 {hashlib.sha256(data).hexdigest()}")
        else:
            path.write_bytes(data)
            print(f"wrote {name} rows {text.count(chr(10)) - 1} sha256 {hashlib.sha256(data).hexdigest()}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
