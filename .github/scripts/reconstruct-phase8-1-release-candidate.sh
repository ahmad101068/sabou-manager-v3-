#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-1-source}"
root="${workspace}/${target}"
overlay="${workspace}/phase8-1-remediation/release-tests"
release_fixes="${workspace}/phase8-1-remediation/release-fixes"
release_fix6="${release_fixes}/phase8-1-release-fix-06.patch"
release_fix7="${release_fixes}/phase8-1-release-fix-07.patch"
release_fix8="${release_fixes}/phase8-1-release-fix-08.patch"
release_fix9="${release_fixes}/phase8-1-release-fix-09.patch"
release_fix10="${release_fixes}/phase8-1-release-fix-10.patch"
release_fix11="${release_fixes}/phase8-1-release-fix-11.patch"
expected_release_fix6_sha="26b3a1af42b77aab959558bd93b9ba7ea0548ce3615cdcbd5f3d7b6240f29e46"
expected_release_fix7_sha="06195430ffe396996fa6e1e7c84890b30d31ed716e96ae8c44aff703eef5951f"
expected_release_fix8_sha="d374f5a68435670fb5df09a2b41dcd3f9db48a64dd332832fee3ce25d1a30ac0"
expected_release_fix9_sha="bbd1142e20061390c6b49cd85fd2b3d16ba34cdc4087a7e4bb4ffc6341995a8c"
expected_release_fix10_sha="3004d6613705abebd6c6c546b628ff86959532f8a2cb9746fcc9d40f72b5ae33"
expected_release_fix11_sha="777277ffc3c3c1683deea7536ae374bf86badad36106f7aaac58cfe15c19a892"

apply_release_patch() {
  local file="$1" expected="$2" label="$3"
  test -s "$file" || { echo "::error::missing ${label}" >&2; exit 1; }
  local actual="$(sha256sum "$file" | awk '{print $1}')"
  echo "::notice::${label} sha256=${actual}" >&2
  test "$actual" = "$expected" || { echo "::error::${label} digest mismatch: $actual" >&2; exit 1; }
  patch --dry-run --batch --forward -p1 -d "$root" -i "$file" >/dev/null
  patch --batch --forward -p1 -d "$root" -i "$file" >/dev/null
  printf '%s' "$actual"
}

bash "${workspace}/.github/scripts/reconstruct-phase8-1-candidate.sh" "$target"
actual_release_fix6_sha="$(apply_release_patch "$release_fix6" "$expected_release_fix6_sha" 'Phase8.1 release-fix-06')"
actual_release_fix7_sha="$(apply_release_patch "$release_fix7" "$expected_release_fix7_sha" 'Phase8.1 release-fix-07')"
actual_release_fix8_sha="$(apply_release_patch "$release_fix8" "$expected_release_fix8_sha" 'Phase8.1 release-fix-08')"
actual_release_fix9_sha="$(apply_release_patch "$release_fix9" "$expected_release_fix9_sha" 'Phase8.1 release-fix-09')"
actual_release_fix10_sha="$(apply_release_patch "$release_fix10" "$expected_release_fix10_sha" 'Phase8.1 release-fix-10')"
actual_release_fix11_sha="$(apply_release_patch "$release_fix11" "$expected_release_fix11_sha" 'Phase8.1 release-fix-11')"
grep -Fq 'role.allows(Permission.DAILY_BRIEF_VIEW)' "$root/app/src/main/java/ir/restaurant/management/ui/DashboardViewModel.kt"
grep -Fq 'normalizedInvoiceNo = no.trim().uppercase()' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/DashboardBranchFilteringIntegrationTest.kt"
grep -Fq 'trackLot = false' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryLedgerIntegrationTest.kt"
grep -Fq 'branch payroll must post a positive salary expense' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/BranchPayrollPostingIntegrationTest.kt"
grep -Fq 'assertEquals(postedPayrollRial, brief.profitability.payrollRial)' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt"
grep -Fq 'role?.allows(Permission.PERSONNEL_VIEW) == true' "$root/app/src/main/java/ir/restaurant/management/ui/RestaurantManagementApp.kt"
grep -Fq 'role?.allows(Permission.INVENTORY_VIEW) == true' "$root/app/src/main/java/ir/restaurant/management/ui/InventoryWorkspaceViewModel.kt"
grep -Fq 'branchId = requireNotNull(mainLocation.branchId)' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryLedgerIntegrationTest.kt"
grep -Fq 'arrayOf<Any?>' "$root/app/src/main/java/ir/restaurant/management/data/db/migration/Phase81ProductionClosureMigration.kt"
grep -Fq 'Reactive reads are fail-closed across logout/role transitions.' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAlertRepository.kt"
grep -Fq 'SharingStarted.WhileSubscribed()' "$root/app/src/main/java/ir/restaurant/management/ui/AlertViewModel.kt"
grep -Fq 'canObserveOperationalAlerts()' "$root/app/src/main/java/ir/restaurant/management/ui/AppScreenAccess.kt"
grep -Fq 'performScrollToNode(hasTestTag(moduleTag))' "$root/app/src/androidTest/java/ir/restaurant/management/ui/DashboardNavigationSettingsUx2ComposeTest.kt"
grep -Fq 'label = "انبار $prefix"' "$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
python3 - "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalAlertRepository.kt" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text(encoding='utf-8')
start = text.index('    override fun alerts() = flow {')
end = text.index('    override suspend fun refresh(', start)
body = text[start:end]
required = [
    'authorizer.actorIdentity()',
    'requireAnyAlertDomainPermission()',
    'val permissions = permittedSourceTypes()',
    'if (permissions.isEmpty())',
    'emptyList()',
]
missing = [token for token in required if token not in body]
if missing:
    raise SystemExit(f'alert data-boundary/reactive fail-closed invariant missing: {missing}')
if body.index('requireAnyAlertDomainPermission()') > body.index('emitAll('):
    raise SystemExit('alert hard authorization must run before reactive emitAll')
print('PHASE8_1_ALERT_BOUNDARY_HYBRID_AUTH=PASS')
PY

copy_test() {
  local rel="$1" expected="$2"
  local src="${overlay}/${rel}" dst="${root}/app/src/androidTest/java/ir/restaurant/management/${rel}"
  test -s "$src" || { echo "::error::missing release test $rel"; exit 1; }
  local actual="$(sha256sum "$src" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::release test hash mismatch $rel: $actual"; exit 1; }
  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
}
copy_test 'data/db/Phase81RecentMigrationMatrixTest.kt' '0a88f127bc20256df5ccb05dce3c6e0c45f5f49094b11b6456a0f755cbde67a8'
copy_test 'data/repository/Phase81AuditIntegrityIntegrationTest.kt' '919ce761d439ddf0a8197077ff6f5cb8a118313709fc716883cb74ada2d596a6'
copy_test 'data/security/Phase81ForensicIntegrityLedgerIntegrationTest.kt' '4838dd0c5ba1d9ab03f515987f71c3e0280d9ca2eeaec184872005313e9a575b'
copy_test 'data/repository/Phase81UserOptimisticConcurrencyIntegrationTest.kt' '25a8f1cf66e22543a2c67ad9134f8ab35e6fa28d4ef6c09e232c7580ede8c0b6'
copy_test 'data/repository/Phase81LargeDataPerformanceIntegrationTest.kt' 'd99a149c624e1c678b50efe6cbd554c192a765752a7566b39f15e1ec835b6078'
if grep -R -nE '@Ignore|@Disabled' "$root/app/src"; then
  echo '::error::ignored/disabled tests are forbidden'
  exit 1
fi
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then
  echo '::error::destructive migration fallback found in release candidate'
  exit 1
fi
echo PHASE8_1_RELEASE_FIX6_SHA256=$actual_release_fix6_sha
echo PHASE8_1_RELEASE_FIX7_SHA256=$actual_release_fix7_sha
echo PHASE8_1_RELEASE_FIX8_SHA256=$actual_release_fix8_sha
echo PHASE8_1_RELEASE_FIX9_SHA256=$actual_release_fix9_sha
echo PHASE8_1_RELEASE_FIX10_SHA256=$actual_release_fix10_sha
echo PHASE8_1_RELEASE_FIX11_SHA256=$actual_release_fix11_sha
echo PHASE8_1_RELEASE_TEST_OVERLAYS=5
echo PHASE8_1_RELEASE_RECONSTRUCTION=PASS
