# tools/geo

`build_places_db.py` builds the offline place-search database (SQLite FTS4) for `:core:geo`. It never downloads anything.

TODO(owner): download `cities5000.zip`, `admin1CodesASCII.txt` and (optional) `alternateNamesV2.zip` from GeoNames
(CC BY 4.0) into a folder outside the repo, unzip, then run:

    tools/oracle/.venv/bin/python tools/geo/build_places_db.py --input-dir <folder>

Output: `core/geo/src/main/assets/geo/places.db` (git-ignored). Size gate 6,000,000 bytes: alternate names of places
under 15,000, then under 50,000 population are dropped; place rows never are; still too big means exit 1.
`--langs` defaults to `en,hi,mr,gu` (VERIFY: extend to all 12 locales). Supplements `diaspora.tsv`, `renames.tsv`,
`overlay_tags.tsv` are hand-written from general knowledge, every row VERIFY. Tests use only `testdata/` (invented rows):

    tools/oracle/.venv/bin/python -m unittest discover -s tools/geo -p 'test_*.py'

Name keys come from `namekey.py` (shared with Kotlin `NameKey`).
