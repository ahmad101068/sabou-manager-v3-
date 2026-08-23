#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-1-source}"
root="${workspace}/${target}"
overlay="${workspace}/phase8-1-remediation/release-tests"

bash "${workspace}/.github/scripts/reconstruct-phase8-1-candidate.sh" "$target"
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
copy_test 'data/repository/Phase81LargeDataPerformanceIntegrationTest.kt' 'fe7a8620601a4537b85ed8140bef9d40f70ea0f873fbca36e45f60d00fe7a528'
if grep -R -nE '@Ignore|@Disabled' "$root/app/src"; then
  echo '::error::ignored/disabled tests are forbidden'
  exit 1
fi
echo PHASE8_1_RELEASE_TEST_OVERLAYS=5
echo PHASE8_1_RELEASE_RECONSTRUCTION=PASS
