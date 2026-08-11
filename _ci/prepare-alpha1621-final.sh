#!/usr/bin/env bash
set -euo pipefail

SOURCE_ZIP="${SABOU_SOURCE_ZIP:-Sabou-Restaurant-ERP-Alpha162.1-Enterprise-Core-Completion-SOURCE.zip}"
EXPECTED_PATCH_SHA="5d43cc0184587fd4ec255f4e54455861c6a6b623d914e633bd70021e11078bef"
SCHEMA_DIR_NAME="ir.sabou.inventory.data.db.AppDatabase"

cd "$GITHUB_WORKSPACE"
test -f "$SOURCE_ZIP" || { echo "Missing source ZIP: $SOURCE_ZIP"; exit 1; }
test -f _ci/alpha1621_compilefix.patch || { echo "Missing verified compile-fix patch"; exit 1; }

cat > /tmp/final-patch-parts.sha256 <<'EOF'
4b42dcc1b20355f5cbb294cd23bfbb169ce94b317921a6038e697279b20e9ffe  _ci/final-source-patch/part-00.txt
c122b448f20ac68e9decdfd85dbb07b9396d78fa4bf75dc4f0cc82e45da0b89b  _ci/final-source-patch/part-01.txt
7ba92dac36503b1f26f7ba292383916f5461d38c8c39e9be17ffae0589572261  _ci/final-source-patch/part-02.txt
5102af0fdd535dca6016f5abf7f9ec26a55983fb2646c8454d82186c15241116  _ci/final-source-patch/part-03.txt
c4603af5986684de03e4d21bc79750ab816f45401e2a530e32537d375dff10c2  _ci/final-source-patch/part-04.txt
bfa2ae64ae3b5b2f2d72e124cb00a97dc0fe8fe38e02ef6a23d4d82275323214  _ci/final-source-patch/part-05.txt
7abc44d00d8d5a11e516035c8157d6cb136414cd3e53844d1a57cadfc91557ac  _ci/final-source-patch/part-06.txt
EOF
sha256sum -c /tmp/final-patch-parts.sha256
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
