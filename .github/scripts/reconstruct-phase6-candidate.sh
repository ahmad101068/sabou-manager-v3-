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

require_recursive() {
  local token="$1" dir="$2" label="$3"
  grep -Rq "$token" "$dir" || { echo "::error::missing invariant ${label}: ${token}"; return 1; }
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
require_contains 'AppScreen.PURCHASES' "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt" 'canonical purchase route'
if grep -Fq 'AppScreen.PROCUREMENT' "$root/app/src/main/java/ir/restaurant/management/ui/ManagementRoutes.kt"; then
  echo '::error::obsolete AppScreen.PROCUREMENT remains after hotfix-01'
  exit 1
fi

verify_sha "$hotfix_02" "7d2e21fe26a822396371e2a99fdeb480941d08fb1e4a5e776b30e113d542cce6" "Phase-6 hotfix-02"
python3 "$hotfix_02" "$root"
if grep -Fq 'grantedAtEpochMillis' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt"; then
  echo '::error::legacy scope fixture column remains after hotfix-02'
  exit 1
fi

verify_sha "$hotfix_03" "056aa6d451889dfaeec9812ebd479eee194e780efc1c7c8a3afc3f3f1006a8b9" "Phase-6 hotfix-03"
python3 "$hotfix_03" "$root"
require_contains 'repository = LocalAlertRepository(database, authorizer, clock = { now })' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertStateIntegrationTest.kt" 'alert state deterministic clock'
require_contains 'canonicalReceivableMaster_excludesSettled_andAlertsOnlyPartialOutstanding' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt" 'canonical receivable test'
require_contains 'database.businessOperationsDao().insertReceivable' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt" 'canonical receivable fixture insert'
if grep -Eq 'SalesInvoiceEntity|CustomerReceivableLedgerEntity|insertCreditInvoice|insertLedger' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt"; then
  echo '::error::legacy receivable alert fixture remains after hotfix-03'
  exit 1
fi

verify_sha "$hotfix_04" "df44d303eec00ab769160a9803cf6ff77d3efe13cc98771ed4feb62ea91e7c74" "Phase-6 hotfix-04"
python3 "$hotfix_04" "$root"

echo 'PHASE6_ALERT_API_CONTRACT_BEGIN'
sed -n '1,220p' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAlertRepository.kt"
find "$root/app/src/main/java/ir/restaurant/management" -type f -name '*Alert*.kt' -print | sort
while IFS= read -r model_file; do
  echo "--- ${model_file#${root}/} ---"
  grep -nE 'data class|sealed|enum class|class .*Alert|destination|Destination|route|Route|intent|Intent|drill|Drill|snooze|Snooze' "$model_file" || true
done < <(find "$root/app/src/main/java/ir/restaurant/management" -type f -name '*Alert*.kt' | sort)
echo 'PHASE6_ALERT_API_CONTRACT_END'
echo '::error::PHASE6_DIAGNOSTIC_SNAPSHOT_COMPLETE'
exit 1
