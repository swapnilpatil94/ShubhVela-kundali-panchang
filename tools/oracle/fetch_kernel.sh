#!/usr/bin/env bash
# Downloads JPL DE440 (planetary SPK) from NAIF into $PP_EPHEM_DIR (outside the repo) and
# verifies it against kernel.lock. Sends no project or user identifiers. Never commit the .bsp.
set -euo pipefail
URL="https://naif.jpl.nasa.gov/pub/naif/generic_kernels/spk/planets/de440.bsp"
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="${PP_EPHEM_DIR:-$HOME/.cache/parivaar-panchang/ephem}"
FILE="$DIR/de440.bsp"
mkdir -p "$DIR"
if [ ! -f "$FILE" ]; then
  curl -fsSL --retry 3 -o "$FILE.part" "$URL"
  mv "$FILE.part" "$FILE"
fi
# kernel.lock: lines "name=...", "bytes=...", "sha256=..."
want_bytes="$(sed -n 's/^bytes=//p' "$HERE/kernel.lock")"
want_sha="$(sed -n 's/^sha256=//p' "$HERE/kernel.lock")"
got_bytes="$(wc -c < "$FILE" | tr -d ' ')"
got_sha="$(shasum -a 256 "$FILE" | cut -d' ' -f1)"
if [ -n "$want_sha" ] && { [ "$got_bytes" != "$want_bytes" ] || [ "$got_sha" != "$want_sha" ]; }; then
  echo "kernel.lock mismatch: bytes $got_bytes sha256 $got_sha" >&2
  exit 1
fi
echo "OK $FILE bytes=$got_bytes sha256=$got_sha"
