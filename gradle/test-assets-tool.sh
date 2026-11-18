#!/usr/bin/env bash
# Self-test for gradle/assets.gradle (exportAssets / importAssets), run against throw-away fake projects:
#   gradle/test-assets-tool.sh
# Needs bash, python3 (to craft damaged bundles) and the Gradle wrapper. Prints PASS/FAIL per scenario, exit 1 on any FAIL.
set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail=0
gw() { # runs gradle, shows its output (minus the JAVA_TOOL_OPTIONS notice), returns gradle's own exit code
  local log="$WORK/gradle.log" status
  (cd "$REPO" && ./gradlew --offline -q --no-daemon "$@") >"$log" 2>&1; status=$?
  grep -v JAVA_TOOL_OPTIONS "$log"; return $status; }
check() { if [ "$2" = "0" ]; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
expect_fail() { # description, command...
  local d="$1"; shift
  if "$@" >"$WORK/out.txt" 2>&1; then check "$d (should have been refused)" 1
  else check "$d" 0; grep -m1 -A2 "What went wrong" "$WORK/out.txt" | tail -1 | sed 's/^/        reason: /'; fi; }

SRC="$WORK/src"; DST="$WORK/dst"
mkdir -p "$SRC/assets/data" "$SRC/assets/images" "$SRC/lwjgl3/icons" "$SRC/lwjgl3/src/main/resources" "$DST"
echo "wolf: {}" > "$SRC/assets/data/pieces.yaml"; echo png-1 > "$SRC/assets/images/wolf.png"
echo ico > "$SRC/lwjgl3/icons/logo.png"; echo ico > "$SRC/lwjgl3/src/main/resources/icon-128.png"
echo generated > "$SRC/assets/assets.txt"; echo junk > "$SRC/assets/images/wolf.png:Zone.Identifier"

gw exportAssets -PassetsRoot="$SRC" -Pout="$WORK/a.zip" >/dev/null; check "export creates a bundle" $?
unzip -l "$WORK/a.zip" | grep -q "assets.txt\|Zone.Identifier" && check "generated/junk files stay out" 1 || check "generated/junk files stay out" 0

gw importAssets -PassetsRoot="$DST" -Pzip="$WORK/a.zip" -PdryRun=true >/dev/null; check "dry run succeeds" $?
[ -z "$(find "$DST" -type f)" ]; check "dry run writes nothing" $?

gw importAssets -PassetsRoot="$DST" -Pzip="$WORK/a.zip" >/dev/null; check "import into an empty checkout" $?
diff -r "$SRC/assets/data" "$DST/assets/data" >/dev/null && cmp -s "$SRC/assets/images/wolf.png" "$DST/assets/images/wolf.png" \
  && cmp -s "$SRC/lwjgl3/icons/logo.png" "$DST/lwjgl3/icons/logo.png"; check "imported files are identical" $?
gw importAssets -PassetsRoot="$DST" -Pzip="$WORK/a.zip" | grep -q "0 new, 4 already identical"; check "second import changes nothing" $?

echo mine > "$DST/assets/extra.txt"; echo edited > "$DST/assets/images/wolf.png"
gw importAssets -PassetsRoot="$DST" -Pzip="$WORK/a.zip" -PdryRun=true | grep -q "assets/images/wolf.png"; check "dry run lists a differing file and still succeeds" $?
expect_fail "a differing existing file is refused" gw importAssets -PassetsRoot="$DST" -Pzip="$WORK/a.zip"
[ "$(cat "$DST/assets/images/wolf.png")" = "edited" ]; check "refused import wrote nothing" $?
gw importAssets -PassetsRoot="$DST" -Pzip="$WORK/a.zip" -Pforce=true >/dev/null; check "force replaces it" $?
[ "$(cat "$DST/assets/images/wolf.png")" = "png-1" ]; check "forced file now matches the bundle" $?
find "$REPO/build/asset-import-backup" -path '*assets/images/wolf.png' 2>/dev/null | xargs grep -l edited >/dev/null 2>&1; check "old file was backed up" $?
[ "$(cat "$DST/assets/extra.txt")" = "mine" ]; check "files not in the bundle are left alone" $?

python3 - "$WORK" <<'PY'
import sys, zipfile
w = sys.argv[1]
def rewrite(name, edit):
    zin = zipfile.ZipFile(f"{w}/a.zip")
    with zipfile.ZipFile(f"{w}/{name}.zip", "w") as zout:
        for item in zin.infolist():
            zout.writestr(*edit(item.filename, zin.read(item.filename)))
    zin.close()
rewrite("tampered", lambda n, d: (n, b"png-X" if n == "assets/images/wolf.png" else d))
def slip(n, d):
    if n == "ASSETS-MANIFEST.txt":
        d = d.replace(b"assets/images/wolf.png", b"assets/../../evil.txt")
        return (n, d)
    return ("assets/../../evil.txt" if n == "assets/images/wolf.png" else n, d)
rewrite("slip", slip)
def outside(n, d):
    if n == "ASSETS-MANIFEST.txt":
        return (n, d.replace(b"assets/images/wolf.png", b"etc/evil.txt"))
    return ("etc/evil.txt" if n == "assets/images/wolf.png" else n, d)
rewrite("outside", outside)
def unlisted(n, d): return (n, d)
rewrite("unlisted", unlisted)
z = zipfile.ZipFile(f"{w}/unlisted.zip", "a"); z.writestr("assets/sneaky.txt", b"x"); z.close()
open(f"{w}/notabundle.zip", "wb").write(b"PK\x05\x06" + b"\0" * 18)
PY
FRESH="$WORK/fresh"; mkdir -p "$FRESH"
expect_fail "a tampered file is refused (checksum)" gw importAssets -PassetsRoot="$FRESH" -Pzip="$WORK/tampered.zip"
expect_fail "'..' in an entry name is refused" gw importAssets -PassetsRoot="$FRESH" -Pzip="$WORK/slip.zip"
expect_fail "an entry outside the asset folders is refused" gw importAssets -PassetsRoot="$FRESH" -Pzip="$WORK/outside.zip"
expect_fail "an unlisted entry is refused" gw importAssets -PassetsRoot="$FRESH" -Pzip="$WORK/unlisted.zip"
expect_fail "a zip with no manifest is refused" gw importAssets -PassetsRoot="$FRESH" -Pzip="$WORK/notabundle.zip"
expect_fail "a missing bundle is refused" gw importAssets -PassetsRoot="$FRESH" -Pzip="$WORK/none.zip"
[ -z "$(find "$FRESH" "$WORK" -maxdepth 1 -name 'evil.txt' ; find "$FRESH" -type f)" ]; check "none of the refused bundles wrote anything" $?
expect_fail "exporting where there are no assets is refused" gw exportAssets -PassetsRoot="$WORK/empty-nothing" -Pout="$WORK/b.zip"

[ $fail = 0 ] && echo "ALL PASSED" || { echo "SOME FAILED"; exit 1; }
