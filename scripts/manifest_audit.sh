#!/usr/bin/env bash
# Manifest audit (MASTER Part 5 "Platform" and "Security tests"). No network, no build.
# Usage: scripts/manifest_audit.sh [repo-root]
# Audits app/src/main and app/src/debug manifests, plus every merged manifest under app/build that is not older
# than the sources (stale merged manifests are skipped with a note; run assembleDebug/bundleRelease for a full audit).
# Exits 1 with one "FAIL" line per drift.
set -u
ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
PKG="app.parivaar.panchang"
MAIN="$ROOT/app/src/main/AndroidManifest.xml"
DEBUG="$ROOT/app/src/debug/AndroidManifest.xml"
FAILS=0
fail() { echo "manifest_audit: FAIL: $1"; FAILS=$((FAILS + 1)); }
note() { echo "manifest_audit: note: $1"; }

REQUIRED_PERMS="android.permission.ACCESS_COARSE_LOCATION android.permission.POST_NOTIFICATIONS android.permission.RECEIVE_BOOT_COMPLETED"
# Exported components allowed everywhere (MASTER Part 5): launcher, 3 widget providers, Quick Settings tile.
EXPORT_ALL="$PKG.MainActivity $PKG.platform.widget.TodayWidget $PKG.platform.widget.NextRahuWidget $PKG.platform.widget.FestivalWidget $PKG.platform.tile.PanchangTileService"
EXPORT_LIB="androidx.profileinstaller.ProfileInstallReceiver"        # library receiver, guarded by DUMP
EXPORT_DEBUG_SRC="$PKG.DebugGateReceiver $PKG.DebugRemindersReceiver" # debug source set only
EXPORT_TOOLING="androidx.compose.ui.tooling.PreviewActivity"         # debug-only tooling library
DEBUG_ONLY="$EXPORT_DEBUG_SRC $EXPORT_TOOLING"

in_list() { case " $2 " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

# Emits one record per relevant element: PERM|PERM23|PERMDEF|APP|COMP, tab separated.
records() {
    awk -v pkg="$PKG" '
    function attr(s, n,   i, r) {
        i = index(s, " " n "=\""); if (i == 0) return "-"
        r = substr(s, i + length(n) + 3); return substr(r, 1, index(r, "\"") - 1)
    }
    function full(n) { return (substr(n, 1, 1) == ".") ? pkg n : n }
    { buf = buf " " $0 }
    END {
        gsub(/[\t\r\n]/, " ", buf)
        while ((i = index(buf, "<!--")) > 0) {          # drop comments
            rest = substr(buf, i + 4); j = index(rest, "-->")
            buf = substr(buf, 1, i - 1) " " (j > 0 ? substr(rest, j + 3) : "")
        }
        n = split(buf, el, "<")
        for (k = 2; k <= n; k++) {
            e = el[k]; sub(/>.*/, "", e); e = " " e " "
            split(e, w, " "); tag = w[1]
            if (tag == "uses-permission") print "PERM\t" attr(e, "android:name")
            else if (tag ~ /^uses-permission-sdk/) print "PERM23\t" attr(e, "android:name")
            else if (tag == "permission") print "PERMDEF\t" attr(e, "android:name") "\t" attr(e, "android:protectionLevel")
            else if (tag == "application")
                print "APP\t" attr(e, "android:allowBackup") "\t" attr(e, "android:dataExtractionRules") "\t" \
                    attr(e, "android:fullBackupContent") "\t" attr(e, "android:intentMatchingFlags")
            else if (tag == "activity" || tag == "activity-alias" || tag == "receiver" || tag == "service" || tag == "provider")
                print "COMP\t" tag "\t" full(attr(e, "android:name")) "\t" attr(e, "android:exported") "\t" attr(e, "android:permission")
        }
    }' "$1"
}

# audit <file> <kind: main|debugsrc|debug|perf|release>
audit() {
    local f="$1" kind="$2" recs perms=""
    recs="$(records "$f")"
    local tab=$'\t'
    while IFS="$tab" read -r type a b c d e; do
        case "$type" in
        PERM)
            perms="$perms $a"
            case "$a" in
            android.permission.INTERNET) fail "$f: INTERNET is not allowed (offline-first)" ;;
            *EXACT_ALARM*) fail "$f: exact-alarm permission $a is not allowed" ;;
            android.permission.READ_* | android.permission.WRITE_*) fail "$f: storage/data permission $a is not allowed" ;;
            esac
            if [ "$kind" = debugsrc ]; then fail "$f: the debug source set must not add permissions ($a)"
            elif ! in_list "$a" "$REQUIRED_PERMS" && [ "$a" != "$PKG.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" ]; then
                fail "$f: permission $a is not in MASTER Part 5"
            fi ;;
        PERM23) fail "$f: uses-permission-sdk-23 $a is not allowed" ;;
        PERMDEF)
            [ "$a" = "$PKG.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" ] && [ "$b" = signature ] ||
                fail "$f: custom permission $a ($b) is not allowed" ;;
        APP)
            if [ "$kind" != debugsrc ]; then
                [ "$a" = true ] || fail "$f: android:allowBackup must be true (only the encrypted snapshot is included)"
                [ "$b" = "@xml/pp_data_extraction_rules" ] || fail "$f: android:dataExtractionRules missing or changed"
                [ "$c" = "@xml/pp_backup_rules" ] || fail "$f: android:fullBackupContent missing or changed"
                [ "$d" = enforceIntentFilter ] || fail "$f: android:intentMatchingFlags=\"enforceIntentFilter\" missing"
            fi ;;
        COMP)
            [ "$c" = "-" ] && fail "$f: $b ($a) has no explicit android:exported"
            if [ "$c" = true ]; then
                local ok=1
                if in_list "$b" "$EXPORT_ALL"; then ok=0
                elif [ "$kind" != main ] && [ "$kind" != debugsrc ] && in_list "$b" "$EXPORT_LIB"; then ok=0
                elif { [ "$kind" = debugsrc ] || [ "$kind" = debug ]; } && in_list "$b" "$EXPORT_DEBUG_SRC"; then ok=0
                elif { [ "$kind" = debug ] || [ "$kind" = perf ]; } && in_list "$b" "$EXPORT_TOOLING"; then ok=0
                fi
                [ $ok -eq 0 ] || fail "$f: exported component $b is not in the MASTER Part 5 allow-list for $kind"
                [ "$b" = "$PKG.platform.tile.PanchangTileService" ] && [ "$d" != android.permission.BIND_QUICK_SETTINGS_TILE ] &&
                    fail "$f: the tile service must require BIND_QUICK_SETTINGS_TILE"
                [ "$b" = "$EXPORT_LIB" ] && [ "$d" != android.permission.DUMP ] && fail "$f: $b must require DUMP"
            fi
            if { [ "$kind" = main ] || [ "$kind" = release ]; } && in_list "$b" "$DEBUG_ONLY"; then
                fail "$f: debug-only component $b must live only under app/src/debug"
            fi ;;
        esac
    done <<< "$recs"
    if [ "$kind" != debugsrc ]; then
        for p in $REQUIRED_PERMS; do in_list "$p" "$perms" || fail "$f: required permission $p is missing"; done
    fi
}

[ -f "$MAIN" ] || { echo "manifest_audit: FAIL: $MAIN not found"; exit 1; }
for x in pp_data_extraction_rules pp_backup_rules; do
    [ -f "$ROOT/core/data/src/main/res/xml/$x.xml" ] || fail "backup rules file $x.xml is missing"
done
if find "$ROOT/app/src/main" -name 'Debug*Receiver.kt' 2>/dev/null | grep -q .; then
    fail "debug receivers must live only under app/src/debug"
fi
audit "$MAIN" main
COUNT=1
if [ -f "$DEBUG" ]; then audit "$DEBUG" debugsrc; COUNT=$((COUNT + 1)); fi
for vt in debug:Debug perf:Perf release:Release; do
    v="${vt%%:*}"
    m="$ROOT/app/build/intermediates/merged_manifests/$v/process${vt##*:}Manifest/AndroidManifest.xml"
    [ -f "$m" ] || continue
    if [ "$m" -ot "$MAIN" ] || { [ -f "$DEBUG" ] && [ "$m" -ot "$DEBUG" ]; }; then
        note "skipping stale merged $v manifest (older than the sources)"
        continue
    fi
    audit "$m" "$v"
    COUNT=$((COUNT + 1))
done
if [ $FAILS -gt 0 ]; then
    echo "manifest_audit: $FAILS problem(s); update the manifest or MASTER Part 5 (and this script) together."
    exit 1
fi
echo "manifest_audit: OK ($COUNT manifests)"
