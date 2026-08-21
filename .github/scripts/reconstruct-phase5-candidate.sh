#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase5-source}"
root="${workspace}/${target}"
patch_b64="${workspace}/.phase5-hotfix-01.patch.xz.b64"
patch_file="${workspace}/.phase5-hotfix-01.patch"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file" || { echo "::error::${label} missing"; return 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::${label} digest mismatch: $actual"; return 1; }
}

bash "${workspace}/.github/scripts/reconstruct-phase4-candidate-v2.sh" "$target"

chunks=(
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.00:8b89beb4b0fa72cc68696fc4521b4487d41e3ebb482c3cb2b933eb396c22f506"
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.01:2d7b934588903e0f1d1a09c41eb85871e942954ee4e98a52699c936db738d15d"
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.02:4588503432518d83e9e0a3d5c16740e32b7f846322933aea8165b6a0083474b0"
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.03:fe18279c547ab90f63488cd3309e4505d573ab3428131cc9c248eb276aa6feb9"
)
: > "$patch_b64"
for spec in "${chunks[@]}"; do
  rel="${spec%%:*}"; expected="${spec##*:}"
  verify_sha "${workspace}/${rel}" "$expected" "$rel"
  cat "${workspace}/${rel}" >> "$patch_b64"
done
verify_sha "$patch_b64" "773469065c58da061ed8f28fe7b04d239f39049a67a9a7091b68eaf953e70852" "Phase-5 hotfix-01 encoded stream"
base64 --decode "$patch_b64" | xz --decompress > "$patch_file"
verify_sha "$patch_file" "f4427011155fc4a55a3ef40572a34179f215efc4010d2d521ccdf4b747c90edc" "Phase-5 hotfix-01 decoded patch"
git -C "$workspace" apply --check --directory="$target" "$patch_file"
git -C "$workspace" apply --directory="$target" "$patch_file"

# Fail-closed acceptance probes before Gradle compilation.
grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 58' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Rq 'MIGRATION_57_58' "$root/app/src/main/java/ir/restaurant/management/data/db/migration"
grep -Fq 'RecipeMaterialResolver(database).resolve' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
grep -Fq 'effectiveSubstitutions' "$root/app/src/main/java/ir/restaurant/management/data/repository/RecipeMaterialResolver.kt"
grep -Fq 'override suspend fun reverseDepreciation' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'depreciationByCommandId' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'current.location == valid.location' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'valid.acquisitionSource != AssetAcquisitionSource.OWNER_CAPITAL' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'asset_depreciation_reversal_reason' "$root/app/src/main/java/ir/restaurant/management/ui/AssetScreens.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/db/Migration57To58Test.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/RecipeMaterialResolverIntegrationTest.kt"

if grep -R -n -E 'MAX\([^)]+\)[^\n]*\+[[:space:]]*1' \
  "$root/app/src/main/java/ir/restaurant/management/data/db/RecipeDao.kt" \
  "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalRecipeRepository.kt"; then
  echo '::error::Unsafe MAX()+1 recipe revision allocation detected'
  exit 1
fi
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then
  echo '::error::Destructive migration fallback detected'
  exit 1
fi

echo 'PHASE5_RECONSTRUCTION=PASS'
echo 'PHASE4_BASELINE_SHA=8f01f91fd025fe2935360976b455680787caa277'
echo 'ROOM_VERSION=58'
echo 'SCHEMA_CHANGED=YES'
echo 'MIGRATION_ADDED=YES'
echo 'HOTFIX_01_SHA256=f4427011155fc4a55a3ef40572a34179f215efc4010d2d521ccdf4b747c90edc'
