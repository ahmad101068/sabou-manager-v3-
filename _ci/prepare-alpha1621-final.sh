#!/usr/bin/env bash
set -euo pipefail

SOURCE_ZIP="${SABOU_SOURCE_ZIP:-Sabou-Restaurant-ERP-Alpha162.1-Enterprise-Core-Completion-SOURCE.zip}"
EXPECTED_PATCH_SHA="5d43cc0184587fd4ec255f4e54455861c6a6b623d914e633bd70021e11078bef"
SCHEMA_DIR_NAME="ir.sabou.inventory.data.db.AppDatabase"

cd "$GITHUB_WORKSPACE"
test -f "$SOURCE_ZIP" || { echo "Missing source ZIP: $SOURCE_ZIP"; exit 1; }
test -f _ci/alpha1621_compilefix.patch || { echo "Missing verified compile-fix patch"; exit 1; }

cat _ci/final-source-patch/part-*.txt > /tmp/alpha1621-final-source.patch
echo "$EXPECTED_PATCH_SHA  /tmp/alpha1621-final-source.patch" | sha256sum -c -

rm -rf _final_project
mkdir -p _final_project
unzip -q "$SOURCE_ZIP" -d _final_project
PROJECT_DIR="$(find _final_project -maxdepth 2 -name settings.gradle.kts -printf '%h\n' | head -n1)"
test -n "$PROJECT_DIR" || { echo "Android project not found"; exit 1; }

cd "$PROJECT_DIR"
patch --forward --batch -p1 < "$GITHUB_WORKSPACE/_ci/alpha1621_compilefix.patch"
patch --forward --batch -p1 < /tmp/alpha1621-final-source.patch

OFFICIAL_SCHEMA_ROOT="$GITHUB_WORKSPACE/_official_schemas/$SCHEMA_DIR_NAME"
test -f "$OFFICIAL_SCHEMA_ROOT/45.json"
test -f "$OFFICIAL_SCHEMA_ROOT/46.json"
test -f "$OFFICIAL_SCHEMA_ROOT/47.json"
mkdir -p "app/schemas/$SCHEMA_DIR_NAME"
cp "$OFFICIAL_SCHEMA_ROOT/45.json" "app/schemas/$SCHEMA_DIR_NAME/45.json"
cp "$OFFICIAL_SCHEMA_ROOT/46.json" "app/schemas/$SCHEMA_DIR_NAME/46.json"
cp "$OFFICIAL_SCHEMA_ROOT/47.json" "app/schemas/$SCHEMA_DIR_NAME/47.json"

echo "90596a2556d8e8832da7cbee4251b0da3caaa37d4b5d3ded447ac65ae2001a5d  app/schemas/$SCHEMA_DIR_NAME/45.json" | sha256sum -c -
echo "52359d307cd6206bc6391734dabad7ba5b5295d4bf71350fd24149e858ca03ff  app/schemas/$SCHEMA_DIR_NAME/46.json" | sha256sum -c -
echo "547a76548e796d26cfdf17af00200660fc74596b78f61cf7fb43df0c3c336c07  app/schemas/$SCHEMA_DIR_NAME/47.json" | sha256sum -c -

chmod +x gradlew
python3 scripts/verify-alpha162-room-schemas.py
python3 scripts/verify-alpha162-code-quality.py

if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "PROJECT_DIR=$PROJECT_DIR" >> "$GITHUB_ENV"
fi
printf 'FINAL_SOURCE_PREPARED=%s\n' "$PROJECT_DIR"
