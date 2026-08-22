#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase6-source}"
root="${workspace}/${target}"
patch_b64="${workspace}/.phase6-final.patch.xz.b64"
patch_file="${workspace}/.phase6-final.patch"
hotfix_01="${workspace}/phase6-remediation/phase6-hotfix-01.py"
hotfix_02="${workspace}/phase6-remediation/phase6-hotfix-02.py"
hotfix_03="${workspace}/phase6-remediation/phase6-hotfix-03.py"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file" || { echo "::error::${label} missing"; return 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::${label} digest mismatch: $actual"; return 1; }
}

bash "${workspace}/.github/scripts/reconstruct-phase5-candidate.sh" "$target"

chunks=(
  phase6-remediation/phase6-final.patch.xz.b64.00
  phase6-remediation/phase6-final.patch.xz.b64.01
  phase6-remediation/phase6-final.patch.xz.b64.02
  phase6-remediation/phase6-final.patch.xz.b64.03
  phase6-remediation/phase6-final.patch.xz.b64.04
  phase6-remediation/phase6-final.patch.xz.b64.05
)
: > "$patch_b64"
for rel in "${chunks[@]}"; do
  test -s "${workspace}/${rel}" || { echo "::error::missing ${rel}"; exit 1; }
  cat "${workspace}/${rel}" >> "$patch_b64"
done
base64 --decode "$patch_b64" | xz --decompress > "$patch_file"
actual="$(sha256sum "$patch_file" | awk '{print $1}')"
expected="331389fd31e7fddaa2bd1b6806e29905615b7691fccc9aeb9ea7a9588017b937"
test "$actual" = "$expected" || { echo "::error::Phase6 patch digest mismatch $actual"; exit 1; }
git -C "$workspace" apply --check --directory="$target" "$patch_file"
git -C "$workspace" apply --directory="$target" "$patch_file"

# CI root-cause hotfix 01: canonical purchase route. Fail closed on source drift.
verify_sha "$hotfix_01" "16c9ea3919d705d60e101e7ce602d4433387960d517a59cb2c9aa4d54c716d52" "Phase-6 hotfix-01"
python3 "$hotfix_01" "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt"
grep -Fq 'AppScreen.PURCHASES' "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt"
if grep -Fq 'AppScreen.PROCUREMENT' "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt"; then
  echo '::error::obsolete AppScreen.PROCUREMENT remains after hotfix-01'
  exit 1
fi

# CI root-cause hotfix 02: security fixture schema compatibility. Production semantics unchanged.
verify_sha "$hotfix_02" "7d2e21fe26a822396371e2a99fdeb480941d08fb1e4a5e776b30e113d542cce6" "Phase-6 hotfix-02"
python3 "$hotfix_02" "$root"
if grep -Fq 'grantedAtEpochMillis' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt"; then
  echo '::error::legacy scope fixture column remains after hotfix-02'
  exit 1
fi

# CI root-cause hotfix 03: explicitly pin the alert repository test clock and move the
# receivable alert fixture to the canonical receivables source of truth used by Phase 6.
verify_sha "$hotfix_03" "056aa6d451889dfaeec9812ebd479eee194e780efc1c7c8a3afc3f3f1006a8b9" "Phase-6 hotfix-03"
python3 "$hotfix_03" "$root"
grep -Fq 'repository = LocalAlertRepository(database, authorizer, clock = { now })' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertStateIntegrationTest.kt"
grep -Fq 'canonicalReceivableMaster_excludesSettled_andAlertsOnlyPartialOutstanding' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt"
grep -Fq 'database.businessOperationsDao().insertReceivable' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt"
if grep -Eq 'SalesInvoiceEntity|CustomerReceivableLedgerEntity|insertCreditInvoice|insertLedger' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt"; then
  echo '::error::legacy receivable alert fixture remains after hotfix-03'
  exit 1
fi

grep -Fq 'APP_DATABASE_SCHEMA_VERSION = 59' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Rq 'MIGRATION_58_59' "$root/app/src/main/java/ir/restaurant/management/data/db/migration"
grep -Fq 'actorRoleSnapshot' "$root/app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt"
grep -Fq 'snoozedUntilEpochMillis' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertEntities.kt"
grep -Fq 'completedByUserId' "$root/app/src/main/java/ir/restaurant/management/data/db/BusinessOperationsEntities.kt"
grep -Fq 'LocalDataScopeService(database, authorizer)' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalManagementWorkflowService.kt"
grep -Fq 'FROM receivables r' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertDao.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/db/Migration58To59Test.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6AlertIntegrationTest.kt"
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then echo '::error::destructive migration fallback'; exit 1; fi

echo PHASE6_RECONSTRUCTION=PASS
echo PHASE5_BASELINE_SHA=5465031036dbe4514a93f34ff9208230fb864e38
echo ROOM_VERSION=59
echo PATCH_SHA256=$expected
echo HOTFIX_01_SHA256=16c9ea3919d705d60e101e7ce602d4433387960d517a59cb2c9aa4d54c716d52
echo HOTFIX_02_SHA256=7d2e21fe26a822396371e2a99fdeb480941d08fb1e4a5e776b30e113d542cce6
echo HOTFIX_03_SHA256=056aa6d451889dfaeec9812ebd479eee194e780efc1c7c8a3afc3f3f1006a8b9
