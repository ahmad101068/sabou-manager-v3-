#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase5-source}"
root="${workspace}/${target}"
patch1_b64="${workspace}/.phase5-hotfix-01.patch.xz.b64"
patch1_file="${workspace}/.phase5-hotfix-01.patch"
patch2_b64="${workspace}/.phase5-hotfix-02.patch.xz.b64"
patch2_file="${workspace}/.phase5-hotfix-02.patch"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file" || { echo "::error::${label} missing"; return 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::${label} digest mismatch: $actual"; return 1; }
}

bash "${workspace}/.github/scripts/reconstruct-phase4-candidate-v2.sh" "$target"

chunks1=(
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.00"
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.01"
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.02"
  "phase5-remediation/phase5-hotfix-01.patch.xz.b64.03"
)
: > "$patch1_b64"
for rel in "${chunks1[@]}"; do
  test -s "${workspace}/${rel}" || { echo "::error::${rel} missing"; exit 1; }
  cat "${workspace}/${rel}" >> "$patch1_b64"
done
base64 --decode "$patch1_b64" | xz --decompress > "$patch1_file"
verify_sha "$patch1_file" "f4427011155fc4a55a3ef40572a34179f215efc4010d2d521ccdf4b747c90edc" "Phase-5 hotfix-01 decoded patch"
git -C "$workspace" apply --check --directory="$target" "$patch1_file"
git -C "$workspace" apply --directory="$target" "$patch1_file"

hotfix2_rel="phase5-remediation/phase5-hotfix-02.patch.xz.b64.00"
verify_sha "${workspace}/${hotfix2_rel}" "1ce3739805581339ecbf99271ec8f4c50b3d1a56347653c02da9e81d8c60918c" "Phase-5 hotfix-02 encoded chunk"
cat "${workspace}/${hotfix2_rel}" > "$patch2_b64"
verify_sha "$patch2_b64" "1ce3739805581339ecbf99271ec8f4c50b3d1a56347653c02da9e81d8c60918c" "Phase-5 hotfix-02 encoded stream"
base64 --decode "$patch2_b64" | xz --decompress > "$patch2_file"
verify_sha "$patch2_file" "9087124a7f2ebf9a19f16d648a9aeddb248d5d1c565fb39d74105897d08d33ff" "Phase-5 hotfix-02 decoded patch"
git -C "$workspace" apply --check --directory="$target" "$patch2_file"
git -C "$workspace" apply --directory="$target" "$patch2_file"

migration_test="$root/app/src/androidTest/java/ir/restaurant/management/data/db/Migration57To58Test.kt"
asset_test="$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AssetLifecycleIntegrationTest.kt"
sales_test="$root/app/src/androidTest/java/ir/restaurant/management/data/repository/DailySalesReversalIntegrationTest.kt"
grep -Fq "PH5-MIG-10" "$migration_test"
grep -Fq "PH5-MIG-11" "$migration_test"
grep -Fq 'BranchEntity(id = 102L' "$asset_test"
grep -Fq 'toBranchId = 102L' "$asset_test"
if grep -Fq 'BranchEntity(id = 1L, globalId = "test:asset-branch:1"' "$asset_test"; then
  echo '::error::stale duplicate seeded asset branch fixture remains'
  exit 1
fi
if grep -Fq 'BranchEntity(id = 1L, globalId = "test:branch:1"' "$sales_test"; then
  echo '::error::stale duplicate seeded daily-sales branch fixture remains'
  exit 1
fi

grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 58' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Rq 'MIGRATION_57_58' "$root/app/src/main/java/ir/restaurant/management/data/db/migration"
grep -Fq 'RecipeMaterialResolver(database).resolve' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
grep -Fq 'effectiveSubstitutions' "$root/app/src/main/java/ir/restaurant/management/data/repository/RecipeMaterialResolver.kt"
grep -Fq 'override suspend fun reverseDepreciation' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'depreciationByCommandId' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'current.location == valid.location' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'valid.acquisitionSource != AssetAcquisitionSource.OWNER_CAPITAL' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt"
grep -Fq 'asset_depreciation_reversal_reason' "$root/app/src/main/java/ir/restaurant/management/ui/AssetScreens.kt"
test -s "$migration_test"
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
echo 'HOTFIX_02_SHA256=9087124a7f2ebf9a19f16d648a9aeddb248d5d1c565fb39d74105897d08d33ff'
