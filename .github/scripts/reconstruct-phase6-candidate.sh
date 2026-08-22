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
hotfix_04="${workspace}/phase6-remediation/phase6-hotfix-04.py"
hotfix_05="${workspace}/phase6-remediation/phase6-hotfix-05.py"
hotfix_06="${workspace}/phase6-remediation/phase6-hotfix-06.py"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file" || { echo "::error::${label} missing"; return 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::${label} digest mismatch: $actual"; return 1; }
}

require_contains() {
  local token="$1" file="$2" label="$3"
  grep -Fq "$token" "$file" || { echo "::error::missing invariant ${label}: ${token}"; return 1; }
}

require_file() {
  local file="$1" label="$2"
  test -s "$file" || { echo "::error::missing required file ${label}: ${file}"; return 1; }
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

verify_sha "$hotfix_01" "16c9ea3919d705d60e101e7ce602d4433387960d517a59cb2c9aa4d54c716d52" "Phase-6 hotfix-01"
python3 "$hotfix_01" "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt"

verify_sha "$hotfix_02" "7d2e21fe26a822396371e2a99fdeb480941d08fb1e4a5e776b30e113d542cce6" "Phase-6 hotfix-02"
python3 "$hotfix_02" "$root"

verify_sha "$hotfix_03" "056aa6d451889dfaeec9812ebd479eee194e780efc1c7c8a3afc3f3f1006a8b9" "Phase-6 hotfix-03"
python3 "$hotfix_03" "$root"

verify_sha "$hotfix_04" "df44d303eec00ab769160a9803cf6ff77d3efe13cc98771ed4feb62ea91e7c74" "Phase-6 hotfix-04"
python3 "$hotfix_04" "$root"

verify_sha "$hotfix_05" "ed3fa9c771d1511c1a258a7be23380ecf5bee69e72c77f1185682062bdcff8f3" "Phase-6 hotfix-05"
python3 "$hotfix_05" "$root"

verify_sha "$hotfix_06" "8e0b0f2ca054100a993a2689e6e501860b64324f47c8f851316e0057c045d331" "Phase-6 hotfix-06"
python3 "$hotfix_06" "$root"

require_contains 'AppScreen.PURCHASES' "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt" 'canonical purchase route'
if grep -Fq 'AppScreen.PROCUREMENT' "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt"; then
  echo '::error::obsolete AppScreen.PROCUREMENT remains after hotfix-01'
  exit 1
fi
if grep -Fq 'grantedAtEpochMillis' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt"; then
  echo '::error::legacy scope fixture column remains after hotfix-02'
  exit 1
fi
for test_file in AlertStateIntegrationTest.kt AlertReceivableIntegrationTest.kt Phase6AlertIntegrationTest.kt; do
  path="$root/app/src/androidTest/java/ir/restaurant/management/data/repository/$test_file"
  if grep -Fq 'LocalAlertRepository(database, authorizer, clock' "$path"; then
    echo "::error::unsupported LocalAlertRepository clock remains in $test_file"
    exit 1
  fi
  if grep -Eq 'AlertDrillDownType|\.drillDownType' "$path"; then
    echo "::error::obsolete alert drill-down API remains in $test_file"
    exit 1
  fi
done
p6_alert_test="$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6AlertIntegrationTest.kt"
if grep -Fq 'SessionAuthorizer(database, clock' "$p6_alert_test"; then
  echo '::error::unsupported SessionAuthorizer clock remains in Phase6AlertIntegrationTest'
  exit 1
fi
require_contains 'authorizer = SessionAuthorizer(database)' "$p6_alert_test" 'canonical SessionAuthorizer fixture'
require_contains 'AlertDrillDownTarget.RECEIVABLE' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt" 'receivable typed drill-down'
require_contains 'AlertDrillDownTarget.INVENTORY_ITEM' "$p6_alert_test" 'low-stock typed drill-down'
require_contains 'System.currentTimeMillis() + 120_000L' "$p6_alert_test" 'real future snooze deadline'
if grep -Eq 'SalesInvoiceEntity|CustomerReceivableLedgerEntity|insertCreditInvoice|insertLedger' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt"; then
  echo '::error::legacy receivable fixture remains'
  exit 1
fi

require_contains 'APP_DATABASE_SCHEMA_VERSION = 59' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt" 'Room schema version 59'
grep -Rq 'MIGRATION_58_59' "$root/app/src/main/java/ir/restaurant/management/data/db/migration" || { echo '::error::MIGRATION_58_59 missing'; exit 1; }
require_contains 'actorRoleSnapshot' "$root/app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt" 'audit actor role snapshot'
require_contains 'snoozedUntilEpochMillis' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertEntities.kt" 'durable alert snooze'
require_contains 'completedByUserId' "$root/app/src/main/java/ir/restaurant/management/data/db/BusinessOperationsEntities.kt" 'management maker-checker completion actor'
require_contains 'LocalDataScopeService(database, authorizer)' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalManagementWorkflowService.kt" 'management data scope'
require_contains 'FROM receivables r' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertDao.kt" 'canonical receivable alert query'
require_contains 'FROM inventory_balances b' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertDao.kt" 'canonical inventory alert query'
require_file "$root/app/src/androidTest/java/ir/restaurant/management/data/db/Migration58To59Test.kt" 'Migration58To59Test'
require_file "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt" 'Phase6SecurityManagementIntegrationTest'
require_file "$p6_alert_test" 'Phase6AlertIntegrationTest'
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then
  echo '::error::destructive migration fallback'
  exit 1
fi

echo PHASE6_RECONSTRUCTION=PASS
echo PHASE5_BASELINE_SHA=5465031036dbe4514a93f34ff9208230fb864e38
echo ROOM_VERSION=59
echo PATCH_SHA256=$expected
echo HOTFIX_01_SHA256=16c9ea3919d705d60e101e7ce602d4433387960d517a59cb2c9aa4d54c716d52
echo HOTFIX_02_SHA256=7d2e21fe26a822396371e2a99fdeb480941d08fb1e4a5e776b30e113d542cce6
echo HOTFIX_03_SHA256=056aa6d451889dfaeec9812ebd479eee194e780efc1c7c8a3afc3f3f1006a8b9
echo HOTFIX_04_SHA256=df44d303eec00ab769160a9803cf6ff77d3efe13cc98771ed4feb62ea91e7c74
echo HOTFIX_05_SHA256=ed3fa9c771d1511c1a258a7be23380ecf5bee69e72c77f1185682062bdcff8f3
echo HOTFIX_06_SHA256=8e0b0f2ca054100a993a2689e6e501860b64324f47c8f851316e0057c045d331
