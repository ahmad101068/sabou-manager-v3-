#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"
archive="${workspace}/phase3-part3b-ci-source.zip"

cat "${workspace}"/phase3-ci-v2-parts/part-* > "${archive}"
unzip -t "${archive}" >/dev/null
rm -rf "${workspace}/${target}"
mkdir -p "${workspace}/${target}"
unzip -q "${archive}" -d "${workspace}/${target}"

patches=(
  phase3-junit-lifecycle.patch
  phase3-connected-followup.patch
  phase3-runtime-business-nav.patch
  phase3-runtime-ui-semantics.patch
  phase3-runtime-dashboard-auth-tests.patch
  phase3-runtime-e2e-scroll-tests.patch
  phase3-runtime-remaining.patch
)

for patch in "${patches[@]}"; do
  git -C "${workspace}" apply --check --directory="${target}" "${workspace}/${patch}"
  git -C "${workspace}" apply --directory="${target}" "${workspace}/${patch}"
done

source_root="${workspace}/${target}"
chmod +x "${source_root}/gradlew"

# Fail closed if any known pre-fix source remains or the current evidence-backed fixes are missing.
grep -Fq 'const val DATABASE_NAME = "restaurant_management.db"' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/BackupRestoreValidationIntegrationTest.kt"
grep -Fq 'QuantityMicros.positive(1_000_000L)' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/BranchPurchasePostingIntegrationTest.kt"
grep -Fq 'fun inventoryHome_showsInventoryContext_withoutFinancialKpis()' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/DashboardNavigationSettingsUx2ComposeTest.kt"
grep -Fq 'module_INVENTORY_موجودی' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'JOIN storage_locations sl ON sl.id=sm.locationId' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/BusinessOperationsDao.kt"
grep -Fq 'onClick = { onNavigate(destination) }' \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/ErpDashboardComponents.kt"
grep -Fq 'rowTestTag = { "crm_select_${it.id}" }' \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/CrmScreen.kt"
grep -Fq 'testTag("recipe_list")' \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/RecipeScreens.kt"
grep -Fq 'private var branchLocationId = 0L' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt"
grep -Fq 'testTag("home_kpi_section")' \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt"
grep -Fq 'testTag("security_user_${user.id}")' \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/SecurityScreens.kt"
grep -Fq '"وظایف مدیریتی", "اقدام، مسئول، سررسید و چرخه تأیید"' \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/ManagementWorkflowScreens.kt"
grep -Fq 'Counts workspace as the manager instead of relying on stale owner-session UI state.' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'app.container.securityRepository.currentUser.first() == null' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/StartupAuthenticationBoundaryComposeTest.kt"

if grep -Fq 'fun inventoryHome_showsInventoryKpis_withoutFinancialKpis()' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/DashboardNavigationSettingsUx2ComposeTest.kt"; then
  echo '::error::Old connected-test source remains after patch application'
  exit 1
fi

sha256sum \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/BusinessOperationsDao.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/CrmScreen.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/ErpDashboardComponents.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/SecurityScreens.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/ui/ManagementWorkflowScreens.kt" \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt" \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/DashboardNavigationSettingsUx2ComposeTest.kt" \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt" \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/ui/StartupAuthenticationBoundaryComposeTest.kt"
