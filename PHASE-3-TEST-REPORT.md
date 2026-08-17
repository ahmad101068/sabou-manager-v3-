# PHASE 3 — TEST REPORT

## Validity
Fail-closed: the final values below are verified only if `Phase 3 Part 3B Runtime and Business Tests` completes successfully on the exact same source commit that contains this report. Otherwise this report's status is `PHASE 3 PART 3B NOT COMPLETE`.

## Final required result
API 23:
- TOTAL=173
- PASS=173
- FAIL=0
- SKIP=0

API 35:
- TOTAL=173
- PASS=173
- FAIL=0
- SKIP=0

16KB:
- PAGE_SIZE=16384
- TOTAL=173
- PASS=173
- FAIL=0
- SKIP=0

JVM:
- TOTAL=342
- PASS=342
- FAIL=0

## Targeted regression evidence before final full gate
Eight remaining-failure regressions were executed on API 23, API 35 and real 16KB runtime. The last targeted run before removal of the temporary targeted workflow completed successfully in all three environments. The targeted set covered Actual Food Cost/Waste, three Dashboard role cases, Control navigation, Inventory Count record/approve/post, owner payroll-auth boundary and cashier logout/protected-graph teardown.

## Bug records
### Actual Food Cost / Waste
BUG_EVIDENCE=Expected ACTUAL_LEDGER_ESTIMATE after independent waste evidence but received ACTUAL_NOT_AVAILABLE; first fixture correction also exposed InsufficientStock when the sale lot was incorrectly moved away from canonical MAIN.
FAILING_TEST=Phase2CorrectionIntegrationTest.actualFoodCostIsUnavailableWithoutIndependentEvidenceAndChangesWithWaste
ROOT_CAUSE=Waste evidence was recorded on organization-wide MAIN (branchId null); the sale flow itself consumes from canonical MAIN.
MINIMAL_FIX=Create explicit Branch 1 test storage location only for waste evidence; keep sale lot on MAIN.
REGRESSION_TEST=Same named integration test.
FILES_CHANGED=Phase2CorrectionIntegrationTest.kt
RESULT_AFTER_FIX=Targeted regression PASS on API23/API35/16KB.

### Dashboard role-aware semantics
BUG_EVIDENCE=Owner/Cashier/Inventory Dashboard tests timed out locating role KPI semantics.
FAILING_TEST=ownerHome_hasFourPrimarySections_andNoAuditOrModuleDump; cashierHome_filtersSensitiveKpisAndActionsBeforeRendering; inventoryHome_showsInventoryContext_withoutFinancialKpis
ROOT_CAUSE=Clickable Card merges descendant Compose semantics; role KPI tag existed on an inner child and was absent from the default merged test tree.
MINIMAL_FIX=Keep stable item hook and query role KPI semantics in unmerged tree; do not alter permission filtering or KPI business rules.
REGRESSION_TEST=The three existing DashboardNavigationSettingsUx2ComposeTest cases.
FILES_CHANGED=DashboardScreen.kt; DashboardNavigationSettingsUx2ComposeTest.kt
RESULT_AFTER_FIX=Targeted regression PASS on API23/API35/16KB.

### Control / Management Tasks
BUG_EVIDENCE=Control child navigation reached the screen but canonical expected title was not rendered.
FAILING_TEST=DashboardNavigationSettingsUx2ComposeTest.controlChildren_renderAndKeepControlSelected
ROOT_CAUSE=Production copy used `وظایف` instead of canonical `وظایف مدیریتی`.
MINIMAL_FIX=Restore canonical title only; keep CONTROL destination and selection.
REGRESSION_TEST=Existing controlChildren_renderAndKeepControlSelected.
FILES_CHANGED=ManagementWorkflowScreens.kt
RESULT_AFTER_FIX=Targeted regression PASS.

### Inventory Count session transition
BUG_EVIDENCE=Count list disappeared after switching from owner to manager during approve/post E2E.
FAILING_TEST=EnterpriseCoreComposeE2ETest.inventoryCount_uiRecordApproveAndPost_reachesPostedWithoutChangingExactBalance
ROOT_CAUSE=Authenticated identity change intentionally recreates protected ViewModelStore and returns Inventory to Overview.
MINIMAL_FIX=After verified manager switch, re-enter Counts through real UI before approve/post; no lifecycle bypass.
REGRESSION_TEST=Existing Inventory Count E2E.
FILES_CHANGED=EnterpriseCoreComposeE2ETest.kt
RESULT_AFTER_FIX=Targeted regression PASS.

### Authentication / user-list publication / logout
BUG_EVIDENCE=API23/16KB could scroll before the Lazy user list was published; 16KB logout UI could reappear before repository currentUser reached null.
FAILING_TEST=StartupAuthenticationBoundaryComposeTest owner/cashier boundary tests.
ROOT_CAUSE=Asynchronous Compose/state publication timing, not authorization policy.
MINIMAL_FIX=Add stable user-card/users-loaded test hooks; wait within existing 10-second bound for user list and repository logout state.
REGRESSION_TEST=Existing ownerWithPayrollPermission and cashierWithoutPayrollPermission tests.
FILES_CHANGED=SecurityScreens.kt; StartupAuthenticationBoundaryComposeTest.kt
RESULT_AFTER_FIX=Targeted regression PASS on API23/API35/16KB.

## Non-blocking warnings
ExperimentalCoroutinesApi opt-in warnings, deprecated AutoMirrored icons, deprecated status/navigation bar APIs and deprecated Compose test APIs remain NON_BLOCKING_WARNING unless a final required gate proves otherwise.
