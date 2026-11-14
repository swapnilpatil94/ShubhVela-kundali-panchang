-- Places database schema (M05 B7). page_size is set by build_places_db.py before this runs.
CREATE TABLE place (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  admin1 TEXT,
  cc TEXT NOT NULL,
  lat_e5 INTEGER NOT NULL,
  lon_e5 INTEGER NOT NULL,
  elev_m INTEGER,
  population INTEGER NOT NULL,
  zone TEXT NOT NULL,
  overlay TEXT
);
CREATE INDEX place_lat ON place(lat_e5);
CREATE VIRTUAL TABLE place_name USING fts4(key, display, place_id, notindexed=display, notindexed=place_id, prefix="2,3", tokenize=simple);
CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT NOT NULL);
