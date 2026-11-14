# Third-party notices: place data

The root `THIRD_PARTY_NOTICES.md` lists software components. This file covers data used by the offline place search
(M05) and is not repeated there.

## GeoNames (CC BY 4.0)

The optional full place database (`places.db`, built by `tools/geo/build_places_db.py`) is derived from the GeoNames
geographical database, https://www.geonames.org, which is published under the Creative Commons Attribution 4.0
International licence (https://creativecommons.org/licenses/by/4.0/). The database is rebuilt into a compact form
(selected columns, normalised search keys, a few added names); it is not endorsed by GeoNames. Everyone may reuse
the original data under the same licence.

Required attribution line (also string `geo_attribution_geonames`):

> Place data from GeoNames (geonames.org), licensed under CC BY 4.0.

The built-in seed list and the supplement files in `tools/geo/` are written by hand and contain no GeoNames data.
