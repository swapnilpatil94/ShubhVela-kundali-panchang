"""Ashtakoota reference (docs/specs/M12.md rules 1-10). Dev tool only; Python stdlib only.

Independent of the Kotlin engine: every table is transcribed here from the spec text and the standard
nakshatra attribute lists (gana, yoni, nadi), never read from rules/*.json and never from engine output.
The algorithms are written differently on purpose (exact Fractions for longitudes, a nadi zig-zag formula,
ordered view tables for Graha Maitri, a groom-to-bride count for Bhakoot). Scores are in quarter-points (Q).
"""
from fractions import Fraction

CH, MA, JA, VA, KE = range(5)          # vashya classes, Bond Code order
VASHYA_LETTERS = "CMJVK"

# ---------------------------------------------------------------- nakshatra attributes (standard lists)
DEVA, MANUSHYA, RAKSHASA = range(3)
_DEVA_NAKS = {0, 4, 6, 7, 12, 14, 16, 21, 26}          # Ashwini Mrigashira Punarvasu Pushya Hasta Swati
_RAKSHASA_NAKS = {2, 8, 9, 13, 15, 17, 18, 22, 23}     # Anuradha Shravana Revati | Krittika Ashlesha Magha
#                                                        Chitra Vishakha Jyeshtha Mula Dhanishta Shatabhisha


def gana(n):
    if n in _DEVA_NAKS:
        return DEVA
    if n in _RAKSHASA_NAKS:
        return RAKSHASA
    return MANUSHYA


def nadi(n):
    """Adi 0, Madhya 1, Antya 2: the nadis run 1-2-3-3-2-1 along the nakshatras."""
    return (0, 1, 2, 2, 1, 0)[n % 6]


YONI_ORDER = ["HORSE", "ELEPHANT", "SHEEP", "SERPENT", "DOG", "CAT", "RAT", "COW", "BUFFALO", "TIGER", "DEER",
              "MONKEY", "MONGOOSE", "LION"]
_YONI_OF_NAK = [
    "HORSE", "ELEPHANT", "SHEEP", "SERPENT", "SERPENT", "DOG", "CAT", "SHEEP", "CAT",      # Ashwini..Ashlesha
    "RAT", "RAT", "COW", "BUFFALO", "TIGER", "BUFFALO", "TIGER", "DEER", "DEER",           # Magha..Jyeshtha
    "DOG", "MONKEY", "MONGOOSE", "MONKEY", "LION", "HORSE", "LION", "COW", "ELEPHANT",     # Mula..Revati
]


def yoni(n):
    return YONI_ORDER.index(_YONI_OF_NAK[n])


# ---------------------------------------------------------------- spec tables (points; Q = 4 x points)
# Rule 4, rows in YONI_ORDER, transcribed from the spec text.
_YONI_ROWS = """
H 4 2 2 3 2 2 2 1 0 1 3 3 2 1
E 2 4 3 3 2 2 2 2 3 1 2 3 2 0
Sh 2 3 4 2 1 2 1 3 3 1 2 0 3 1
Se 3 3 2 4 2 1 1 1 1 2 2 2 0 2
D 2 2 1 2 4 2 1 2 2 1 0 2 1 1
C 2 2 2 1 2 4 0 2 2 1 3 3 2 1
R 2 2 1 1 1 0 4 2 2 2 2 2 1 2
Co 1 2 3 1 2 2 2 4 3 0 3 2 2 1
B 0 3 3 1 2 2 2 3 4 1 2 2 2 1
T 1 1 1 2 1 1 2 0 1 4 1 1 2 1
De 3 2 2 2 0 3 2 3 2 1 4 2 2 1
M 3 3 0 2 2 3 2 2 2 1 2 4 3 2
Mo 2 2 3 0 1 2 1 2 2 2 2 3 4 2
L 1 0 1 2 1 1 2 1 1 1 1 2 2 4
"""
YONI_POINTS = [[int(x) for x in line.split()[1:]] for line in _YONI_ROWS.strip().splitlines()]
assert len(YONI_POINTS) == 14 and all(len(r) == 14 for r in YONI_POINTS)
assert all(YONI_POINTS[i][j] == YONI_POINTS[j][i] for i in range(14) for j in range(14)), "yoni not symmetric"
assert sum(1 for i in range(14) for j in range(i + 1, 14) if YONI_POINTS[i][j] == 0) == 7

# Rule 2, rows groom, cols bride, order Ch Ma Ja Va Ke.
VASHYA_POINTS = [
    [Fraction(2), Fraction(1), Fraction(1), Fraction(1, 2), Fraction(1)],
    [Fraction(1), Fraction(2), Fraction(1, 2), Fraction(0), Fraction(1)],
    [Fraction(1), Fraction(1, 2), Fraction(2), Fraction(1), Fraction(1)],
    [Fraction(1, 2), Fraction(0), Fraction(1), Fraction(2), Fraction(0)],
    [Fraction(1), Fraction(1), Fraction(1), Fraction(0), Fraction(2)],
]

# Rule 6, rows groom, cols bride, order Deva Manushya Rakshasa.
GANA_POINTS = [[6, 6, 1], [5, 6, 0], [1, 0, 6]]

# Rule 1: varna rank by sign, written as element groups (fire 3, earth 2, air 1, water 4).
_VARNA_BY_ELEMENT = (3, 2, 1, 4)        # sign s has element s mod 4: fire, earth, air, water


def varna_rank(r):
    return _VARNA_BY_ELEMENT[r % 4]


# Rule 5: sign lords and natural relations.
SU, MO, MAR, ME, JU, VE, SA = "Su", "Mo", "Ma", "Me", "Ju", "Ve", "Sa"
SIGN_LORD = [MAR, VE, ME, MO, SU, ME, VE, MAR, JU, SA, SA, JU]
_REL = {
    SU: ({MO, MAR, JU}, {ME}, {VE, SA}),
    MO: ({SU, ME}, {MAR, JU, VE, SA}, set()),
    MAR: ({SU, MO, JU}, {VE, SA}, {ME}),
    ME: ({SU, VE}, {MAR, JU, SA}, {MO}),
    JU: ({SU, MO, MAR}, {SA}, {ME, VE}),
    VE: ({ME, SA}, {MAR, JU}, {SU, MO}),
    SA: ({ME, VE}, {JU}, {SU, MO, MAR}),
}
for _g, (_f, _n, _e) in _REL.items():
    assert _f | _n | _e == set(_REL) - {_g} and not (_f & _n) and not (_f & _e) and not (_n & _e)


def view(of, other):
    f, n, _e = _REL[of]
    return "F" if other in f else ("N" if other in n else "E")


# Ordered (groom's view of the bride's lord, bride's view of the groom's lord) -> points.
_GM_VIEW_POINTS = {
    ("F", "F"): Fraction(5), ("F", "N"): Fraction(4), ("N", "F"): Fraction(4),
    ("N", "N"): Fraction(3), ("F", "E"): Fraction(1), ("E", "F"): Fraction(1),
    ("N", "E"): Fraction(1, 2), ("E", "N"): Fraction(1, 2), ("E", "E"): Fraction(0),
}

TARA_BAD = (3, 5, 7)
BHAKOOT_BAD_COUNTS = (2, 12, 5, 9, 6, 8)     # groom-to-bride sign count k; the pair (14-k, k) is a dosha pair

KOOTAS = ["VARNA", "VASHYA", "TARA", "YONI", "GRAHA_MAITRI", "GANA", "BHAKOOT", "NADI"]
MAX_Q = [4, 8, 12, 16, 20, 24, 28, 32]


# ---------------------------------------------------------------- Moon points
NAK_SPAN = Fraction(40, 3)
PADA_SPAN = Fraction(10, 3)


def pada_start(n, p):
    return NAK_SPAN * n + PADA_SPAN * (p - 1)


def rashi_of(n, p):
    return int(pada_start(n, p) // 30)


def vashya_at(lon):
    """Rule 2 by sidereal longitude (exact Fraction); exactly 15 deg is the second half."""
    r = int(lon // 30)
    second = lon - 30 * r >= 15
    fixed = {0: CH, 1: CH, 2: MA, 3: JA, 4: VA, 5: MA, 6: MA, 7: KE, 10: MA, 11: JA}
    if r == 8:
        return CH if second else MA
    if r == 9:
        return JA if second else CH
    return fixed[r]


def inputs():
    """The 110 golden inputs: 108 padas (vashya at the pada start) plus the alternate vashya of a pada that
    straddles 15 deg of Dhanu or Makara, listed right after its pada."""
    out = []
    for n in range(27):
        for p in range(1, 5):
            a = pada_start(n, p)
            b = a + PADA_SPAN
            v0 = vashya_at(a)
            out.append((n, p, v0))
            # the class just below the pada end (end is exclusive)
            v1 = vashya_at(b - Fraction(1, 10 ** 9))
            if v1 != v0:
                out.append((n, p, v1))
    assert len(out) == 110
    return out


# ---------------------------------------------------------------- scoring
def q_of(points):
    q = points * 4
    assert q.denominator == 1
    return int(q)


def score(g, b):
    """[(q, [note ids])] for the eight kootas, groom [g], bride [b]; each is (nakshatra, pada, vashya)."""
    gn, gp, gv = g
    bn, bp, bv = b
    rg, rb = rashi_of(gn, gp), rashi_of(bn, bp)
    out = []
    out.append((4 if varna_rank(rg) >= varna_rank(rb) else 0, []))
    out.append((q_of(VASHYA_POINTS[gv][bv]), []))

    def good(x, y):
        count = (x - y) % 27 + 1
        t = count % 9 or 9
        return t not in TARA_BAD
    n_good = good(gn, bn) + good(bn, gn)
    out.append(({2: 12, 1: 6, 0: 0}[n_good], []))
    out.append((4 * YONI_POINTS[yoni(gn)][yoni(bn)], []))
    lg, lb = SIGN_LORD[rg], SIGN_LORD[rb]
    gm = Fraction(5) if lg == lb else _GM_VIEW_POINTS[(view(lg, lb), view(lb, lg))]
    out.append((q_of(gm), []))
    out.append((4 * GANA_POINTS[gana(gn)][gana(bn)], []))
    k = (rb - rg) % 12 + 1
    if k in BHAKOOT_BAD_COUNTS:
        notes = ["koota.bhakoot.dosha"]
        if lg == lb or (view(lg, lb) == "F" and view(lb, lg) == "F"):
            notes.append("koota.bhakoot.exception_possible")
        out.append((0, notes))
    else:
        out.append((28, []))
    if nadi(gn) == nadi(bn):
        notes = ["koota.nadi.same"]
        if (rg == rb and gn != bn) or (gn == bn and gp != bp):
            notes.append("koota.nadi.exception_possible")
        out.append((0, notes))
    else:
        out.append((32, []))
    return out


def score_symmetric(a, b):
    x, y = score(a, b), score(b, a)
    out = []
    for (q1, n1), (q2, n2) in zip(x, y):
        assert (q1 + q2) % 2 == 0, "odd symmetric sum"
        out.append(((q1 + q2) // 2, n1 + [n for n in n2 if n not in n1]))
    return out
