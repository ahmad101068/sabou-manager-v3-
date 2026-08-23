#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-1-source}"
root="${workspace}/${target}"
base_phase8_sha="38b2b13883bcb806796c9de41ac8914a8974b016"
hotfix="${workspace}/phase8-1-remediation/phase8-1-hotfix-chunked.py"
expected_patch_sha="865b2d29bad1ee39b116fd6e1e201cd40663f4e7aaa4254af664e255400284f4"

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

echo PHASE8_1_RECONSTRUCTION=PASS
echo BASE_PHASE8_SHA=$base_phase8_sha
echo ROOM_VERSION=60
