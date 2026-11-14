import os
import shutil
import sqlite3
import tempfile
import unittest

import build_places_db as b

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "testdata")


def make_input(d):
    shutil.copy(os.path.join(DATA, "cities_sample.txt"), os.path.join(d, "cities5000.txt"))
    shutil.copy(os.path.join(DATA, "admin1CodesASCII_sample.txt"), os.path.join(d, "admin1CodesASCII.txt"))
    shutil.copy(os.path.join(DATA, "alternates_sample.txt"), os.path.join(d, "alternateNamesV2.txt"))


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.inp = os.path.join(self.tmp, "in")
        os.mkdir(self.inp)
        make_input(self.inp)
        self.out = os.path.join(self.tmp, "places.db")

    def build(self, **kw):
        return b.build(self.inp, self.out, **kw)

    def test_fts4_present(self):
        try:
            c = sqlite3.connect(":memory:")
            c.execute("CREATE VIRTUAL TABLE t USING fts4(a)")
        except sqlite3.Error:
            self.fail("sqlite3 build has no FTS4")

    def test_devanagari_prefix_and_alt_filters(self):
        size, stage = self.build()
        self.assertEqual(stage, 0)
        self.assertLess(size, b.MAX_BYTES)
        c = sqlite3.connect(self.out)
        hit = c.execute("SELECT place_id FROM place_name WHERE place_name MATCH ?", ("प्रय*",)).fetchall()
        self.assertEqual([int(r[0]) for r in hit], [9000000003])
        disp = {r[0] for r in c.execute("SELECT display FROM place_name")}
        self.assertIn("Allahabadtest", disp)          # en listed
        self.assertNotIn("Prayagraj-fr", disp)        # fr not in langs
        self.assertIn("Ancienne", disp)               # historic, cc IN
        self.assertNotIn("Zedfr", disp)               # historic but cc ZZ
        self.assertNotIn("http://example.invalid/x", disp)
        self.assertNotIn("CCU", disp)
        self.assertNotIn("Zedtown", disp)             # colloquial
        self.assertIn("Zedburg", disp)

    def test_renames_overlay_and_columns(self):
        self.build()
        c = sqlite3.connect(self.out)
        m = c.execute("SELECT overlay, elev_m, admin1, lat_e5 FROM place WHERE id=9000000001").fetchone()
        self.assertEqual(m, ("bombay", 14, "Testra Pradesh", 1907000))  # elev from dem
        self.assertEqual(c.execute("SELECT overlay FROM place WHERE id=9000000006").fetchone()[0], None)  # cc ZZ
        self.assertIsNone(c.execute("SELECT elev_m FROM place WHERE id=9000000003").fetchone()[0])  # -9999
        r = c.execute("SELECT place_id FROM place_name WHERE place_name MATCH 'bomb*'").fetchall()
        self.assertEqual([int(x[0]) for x in r], [9000000001])
        self.assertEqual(c.execute("SELECT overlay FROM place WHERE id=9000000002").fetchone()[0], "calcutta")
        self.assertEqual(c.execute("SELECT elev_m FROM place WHERE id=9000000002").fetchone()[0], 11)
        # diaspora rows added
        self.assertEqual(c.execute("SELECT count(*) FROM place WHERE id>=2000001001 AND id<=2000001018").fetchone()[0], 18)
        meta = dict(c.execute("SELECT k, v FROM meta"))
        self.assertEqual(meta["schema_version"], "1")
        self.assertEqual(len(meta["sha256_cities5000"]), 64)
        self.assertEqual(c.execute("PRAGMA page_size").fetchone()[0], 4096)

    def test_deterministic(self):
        self.build()
        c1 = sqlite3.connect(self.out)
        d1 = list(c1.iterdump())
        c1.close()
        self.build()
        c2 = sqlite3.connect(self.out)
        self.assertEqual(d1, list(c2.iterdump()))

    def _big_input(self):
        with open(os.path.join(self.inp, "cities5000.txt"), "w", encoding="utf-8") as f:
            for i in range(700):
                cols = [str(9100000000 + i), "Synth%d" % i, "", "", "10.0", "20.0", "P", "PPL", "ZZ", "", "",
                        "", "", "", "20000", "", "5", "Europe/London", "2020-01-01"]
                f.write("\t".join(cols) + "\n")
        with open(os.path.join(self.inp, "alternateNamesV2.txt"), "w", encoding="utf-8") as f:
            n = 0
            for i in range(700):
                for j in range(4):
                    n += 1
                    f.write("\t".join([str(n), str(9100000000 + i), "en", "Altname%d x%d" % (i, j), "", "", "", "", "", ""]) + "\n")

    def test_size_gate_drops_alts_then_fails(self):
        self._big_input()
        full, stage = self.build()
        self.assertEqual(stage, 0)
        size, stage = self.build(max_bytes=full - 1)
        self.assertEqual(stage, 2)  # pop 20000 survives <15000 stage, dropped at <50000
        self.assertLess(size, full)
        c = sqlite3.connect(self.out)
        self.assertEqual(c.execute("SELECT count(*) FROM place").fetchone()[0], 700 + 18 + 0)
        self.assertEqual(c.execute("SELECT count(*) FROM place_name WHERE display LIKE 'Altname%'").fetchone()[0], 0)
        with self.assertRaises(b.BuildError):
            self.build(max_bytes=1000)
        self.assertEqual(b.main(["--input-dir", self.inp, "--output", self.out, "--max-bytes", "1000"]), 1)

    def test_bad_zone_exit_1(self):
        p = os.path.join(self.inp, "cities5000.txt")
        with open(p, encoding="utf-8") as f:
            s = f.read().replace("Europe/London", "Not/AZone")
        with open(p, "w", encoding="utf-8") as f:
            f.write(s)
        self.assertEqual(b.main(["--input-dir", self.inp, "--output", self.out]), 1)

    def test_missing_input_exit_1(self):
        os.remove(os.path.join(self.inp, "cities5000.txt"))
        self.assertEqual(b.main(["--input-dir", self.inp, "--output", self.out]), 1)


if __name__ == "__main__":
    unittest.main()
