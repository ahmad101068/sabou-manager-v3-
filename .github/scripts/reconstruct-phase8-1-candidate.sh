#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-1-source}"
root="${workspace}/${target}"
base_phase8_sha="38b2b13883bcb806796c9de41ac8914a8974b016"
hotfix="${workspace}/phase8-1-remediation/phase8-1-hotfix-chunked.py"
overlay="${workspace}/phase8-1-remediation/overlay"
followup="${workspace}/phase8-1-remediation/followup/phase8-1-followup-01.patch"
followup2="${workspace}/phase8-1-remediation/followup/phase8-1-followup-02.py"
followup3="${workspace}/phase8-1-remediation/followup/phase8-1-followup-03.patch"
expected_patch_sha="865b2d29bad1ee39b116fd6e1e201cd40663f4e7aaa4254af664e255400284f4"
expected_followup_sha="3fba390239ed0206278e053a1cc79fabf429cf98f01527f051569ab7adfad283"
expected_followup2_sha="e9f97f7b34f1e96708a2a0830a7e239b7347f1e69fb43e1d5b6ef752a7f51e45"
expected_followup3_sha="1ad5d32997c25d06bd56bc4f4c7fac9ac5ff90821e4d8c357f8e33c169528127"

verify_copy() {
  local rel="$1"
  local expected="$2"
  local source="${overlay}/${rel}"
  local destination="${root}/${rel}"
  local actual
  test -s "$source" || { echo "::error::missing Phase8.1 overlay $rel"; exit 1; }
  actual="$(sha256sum "$source" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::Phase8.1 overlay digest mismatch $rel: $actual"; exit 1; }
  mkdir -p "$(dirname "$destination")"
  cp "$source" "$destination"
}

bash "${workspace}/.github/scripts/reconstruct-phase8-candidate.sh" "$target"
test -s "$hotfix"
for i in 00 01 02 03 04 05 06 07; do
  test -s "${workspace}/phase8-1-remediation/patch/phase8-1-patch.part${i}" || {
    echo "::error::missing Phase8.1 patch chunk ${i}"
    exit 1
  }
done
python3 "$hotfix" "$root" | tee "${workspace}/phase8-1-patch-apply.log"
grep -Fq "PHASE8_1_PATCH_SHA256=${expected_patch_sha}" "${workspace}/phase8-1-patch-apply.log"
grep -Fq 'PHASE8_1_PATCH_APPLIED=PASS' "${workspace}/phase8-1-patch-apply.log"

verify_copy 'app/src/main/java/ir/restaurant/management/core/BusinessCalendar.kt' '28c0d302cf768a562378666233c5a06a2fe27bcc14e90cdf68e10e13fa0b9321'
verify_copy 'app/src/main/java/ir/restaurant/management/data/repository/OperationalAlertWriter.kt' 'bbb79c23061bb6c036fe4cc87ac39431cbcae6704b5ad7382bb35a8104e04288'
verify_copy 'app/src/main/java/ir/restaurant/management/data/repository/AuditIntegrityVerifier.kt' '12b4b12d83ad47ad415daa66eabf000fd692bae87f0e02d99c7074d6eda40195'
verify_copy 'app/src/main/java/ir/restaurant/management/data/db/migration/Phase81ProductionClosureMigration.kt' '13af4513abfa26532f17ba91bebe51c6fbf90c8a3a31ca65cba6719a5fd810ef'
verify_copy 'app/src/main/java/ir/restaurant/management/data/security/AuditIntegrity.kt' 'e56b8cec658934b15d99cfacdc06b8004afeddbdb860f3c79bd982a643f037cc'
verify_copy 'app/src/main/java/ir/restaurant/management/data/security/ForensicIntegrityLedger.kt' '4c5360fe7f540e1660d94c0f9584644e790c4d0c55740df6d73b194735348ccc'

test -s "$followup"
actual_followup_sha="$(sha256sum "$followup" | awk '{print $1}')"
test "$actual_followup_sha" = "$expected_followup_sha" || {
  echo "::error::Phase8.1 followup digest mismatch: $actual_followup_sha"
  exit 1
}
patch --dry-run --batch --forward -p1 -d "$root" -i "$followup"
patch --batch --forward -p1 -d "$root" -i "$followup"

test -s "$followup2"
actual_followup2_sha="$(sha256sum "$followup2" | awk '{print $1}')"
test "$actual_followup2_sha" = "$expected_followup2_sha" || {
  echo "::error::Phase8.1 followup-02 digest mismatch: $actual_followup2_sha"
  exit 1
}
python3 "$followup2" "$root" | tee "${workspace}/phase8-1-followup-02.log"
grep -Fq 'PHASE8_1_TEST_API_ALIGNMENT=PASS' "${workspace}/phase8-1-followup-02.log"
grep -Fq 'PRESERVED_RESOURCE_ID=1' "${workspace}/phase8-1-followup-02.log"

test -s "$followup3"
actual_followup3_sha="$(sha256sum "$followup3" | awk '{print $1}')"
test "$actual_followup3_sha" = "$expected_followup3_sha" || {
  echo "::error::Phase8.1 followup-03 digest mismatch: $actual_followup3_sha"
  exit 1
}
patch --dry-run --batch --forward -p1 -d "$root" -i "$followup3"
patch --batch --forward -p1 -d "$root" -i "$followup3"
grep -Fq 'Math.multiplyExact(businessEpochDay, 86_400_000L)' "$root/app/src/main/java/ir/restaurant/management/domain/personnel/AttendanceCalculationEngine.kt"
if grep -Fq 'BusinessCalendar.startOfDayEpochMillis(businessEpochDay)' "$root/app/src/main/java/ir/restaurant/management/domain/personnel/AttendanceCalculationEngine.kt"; then
  echo '::error::attendance engine illegally reinterprets an existing businessEpochDay through timezone conversion'
  exit 1
fi
grep -Fq 'dropLast(1)' "$root/app/src/test/java/ir/restaurant/management/ui/InputParsersTest.kt"
grep -Fq 'dropLast(2)' "$root/app/src/test/java/ir/restaurant/management/ui/InputParsersTest.kt"

grep -Fq 'APP_DATABASE_SCHEMA_VERSION = 60' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Fq 'MIGRATION_59_60' "$root/app/src/main/java/ir/restaurant/management/data/db/migration/AppMigrations.kt"
grep -Fq 'integritySequence' "$root/app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt"
grep -Fq 'rowVersion' "$root/app/src/main/java/ir/restaurant/management/data/db/SecurityEntities.kt"
grep -Fq 'ForensicIntegrityLedger' "$root/app/src/main/java/ir/restaurant/management/data/AppContainer.kt"
grep -Fq 'formatRialMoneyInput' "$root/app/src/main/java/ir/restaurant/management/ui/TreasuryScreen.kt"
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then
  echo '::error::destructive migration fallback found'
  exit 1
fi
if grep -R -nE '@Ignore|@Disabled' "$root/app/src"; then
  echo '::error::ignored/disabled test found in Phase8.1 candidate'
  exit 1
fi
if grep -R -nE 'MAX\(revisionNo\)|MAX\(integritySequence\)[[:space:]]*\+[[:space:]]*1' "$root/app/src/main/java"; then
  echo '::error::sensitive MAX()+1 allocation remains'
  exit 1
fi

echo PHASE8_1_OVERLAY_FILES=6
echo PHASE8_1_FOLLOWUP_SHA256=$actual_followup_sha
echo PHASE8_1_FOLLOWUP2_SHA256=$actual_followup2_sha
echo PHASE8_1_FOLLOWUP3_SHA256=$actual_followup3_sha
echo PHASE8_1_RECONSTRUCTION=PASS
echo BASE_PHASE8_SHA=$base_phase8_sha
echo ROOM_VERSION=60
