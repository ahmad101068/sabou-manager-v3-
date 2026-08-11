#!/usr/bin/env bash
set -euo pipefail

SOURCE_ZIP="${SABOU_SOURCE_ZIP:-Sabou-Restaurant-ERP-Alpha162.1-Enterprise-Core-Completion-SOURCE.zip}"
EXPECTED_PATCH_GZ_SHA="43188d217ce3d35ee6e3303d7b200ebb84e1b3980bc588ffcdfb227844ebaf87"
EXPECTED_PATCH_SHA="5d43cc0184587fd4ec255f4e54455861c6a6b623d914e633bd70021e11078bef"
EXPECTED_HOTFIX_GIT_BLOB="a776e9e0572565dd0a6a27d762192df92662bdc8"
EXPECTED_MIGRATION_HOTFIX_GIT_BLOB="17af7ce60cef17acf107ac2dc1f4ce3b893d5597"
SCHEMA_DIR_NAME="ir.sabou.inventory.data.db.AppDatabase"

cd "$GITHUB_WORKSPACE"
test -f "$SOURCE_ZIP" || { echo "Missing source ZIP: $SOURCE_ZIP"; exit 1; }
test -f _ci/alpha1621_compilefix.patch || { echo "Missing verified compile-fix patch"; exit 1; }
test -f _ci/alpha1621-final-hotfix.patch || { echo "Missing final readiness hotfix patch"; exit 1; }
test -f _ci/alpha1621-migration-proof-hotfix.patch || { echo "Missing direct migration proof hotfix"; exit 1; }
ACTUAL_HOTFIX_GIT_BLOB="$(git hash-object _ci/alpha1621-final-hotfix.patch)"
test "$ACTUAL_HOTFIX_GIT_BLOB" = "$EXPECTED_HOTFIX_GIT_BLOB" || {
  echo "Final readiness hotfix identity mismatch: expected=$EXPECTED_HOTFIX_GIT_BLOB actual=$ACTUAL_HOTFIX_GIT_BLOB" >&2
  exit 1
}
ACTUAL_MIGRATION_HOTFIX_GIT_BLOB="$(git hash-object _ci/alpha1621-migration-proof-hotfix.patch)"
test "$ACTUAL_MIGRATION_HOTFIX_GIT_BLOB" = "$EXPECTED_MIGRATION_HOTFIX_GIT_BLOB" || {
  echo "Migration proof hotfix identity mismatch: expected=$EXPECTED_MIGRATION_HOTFIX_GIT_BLOB actual=$ACTUAL_MIGRATION_HOTFIX_GIT_BLOB" >&2
  exit 1
}
echo "FINAL_HOTFIX_GIT_BLOB=$ACTUAL_HOTFIX_GIT_BLOB"
echo "MIGRATION_HOTFIX_GIT_BLOB=$ACTUAL_MIGRATION_HOTFIX_GIT_BLOB"

cat > /tmp/final-binary-parts.sha256 <<'EOF'
ae648ae7e41c8e808fe1f6a94e315c68ba04236e4424ba74155a7ddf578546f3  _ci/final-source-code.parts/part-00.bin
7563de2cce4e42e6a4f01874d4ef22f4294b010e933fe3df74e32a9f617cd921  _ci/final-source-code.parts/part-01.bin
1e480966adbae7d3d62c872792794930c75c6a03ca0f7fe41a47de903f216050  _ci/final-source-code.parts/part-02.bin
36503d1131b5e822d2cf3d0e6d1a35eda409463229d7c918f3f24794b40e6143  _ci/final-source-code.parts/part-03.bin
dff280c481f5ad8bacf66bdc622d45a7580abf8aae1ecd866a36499cc5c7dbf7  _ci/final-source-code.parts/part-04.bin
4e82f6cd530b0f7743b0de0d070e2d510912baa223f0ab7127ef1052a33813ef  _ci/final-source-code.parts/part-05.bin
EOF
sha256sum -c /tmp/final-binary-parts.sha256
cat _ci/final-source-code.parts/part-*.bin > /tmp/alpha1621-final-source.patch.gz
echo "$EXPECTED_PATCH_GZ_SHA  /tmp/alpha1621-final-source.patch.gz" | sha256sum -c -
gzip -dc /tmp/alpha1621-final-source.patch.gz > /tmp/alpha1621-final-source.patch
echo "$EXPECTED_PATCH_SHA  /tmp/alpha1621-final-source.patch" | sha256sum -c -

rm -rf _final_project
mkdir -p _final_project
unzip -q "$SOURCE_ZIP" -d _final_project
PROJECT_DIR="$(find _final_project -maxdepth 2 -name settings.gradle.kts -printf '%h\n' | head -n1)"
test -n "$PROJECT_DIR" || { echo "Android project not found"; exit 1; }

cd "$PROJECT_DIR"
patch --forward --batch -p1 < "$GITHUB_WORKSPACE/_ci/alpha1621_compilefix.patch"
patch --forward --batch -p1 < /tmp/alpha1621-final-source.patch
patch --forward --batch -p1 < "$GITHUB_WORKSPACE/_ci/alpha1621-final-hotfix.patch"
patch --forward --batch -p1 < "$GITHUB_WORKSPACE/_ci/alpha1621-migration-proof-hotfix.patch"

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
