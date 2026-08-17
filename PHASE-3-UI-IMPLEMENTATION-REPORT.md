# PHASE 3 — UI IMPLEMENTATION REPORT

## Scope
Only test-proven UI/navigation/synchronization defects were changed. No large UI feature or business-flow redesign was introduced.

## Implemented corrections
- Home/Dashboard keeps role-aware KPI filtering and now exposes stable Compose test hooks without changing business visibility rules.
- Primary KPI role tags are verified through the unmerged semantics tree where the clickable hero Card merges descendants.
- Management Tasks remains a child of CONTROL; its canonical visible title is `وظایف مدیریتی`.
- Inventory Count E2E respects authenticated-session ViewModelStore recreation and re-enters Counts through the real UI after a manager switch.
- Inventory Count post-submit verification now keeps the existing 10-second bound while synchronizing to both PENDING_APPROVAL service state and the real Close-action semantics node before interaction; no timeout increase or business-flow bypass was introduced.
- Security user rows expose stable item-root test tags and a users-loaded marker so tests synchronize to real state publication.
- Logout verification waits for both the public login surface and repository session teardown within the existing timeout.
- Canonical branch selector now uses a bounded Dialog/LazyColumn instead of lazy content inside DropdownMenu, preventing the UAT-proven popup measurement crash and keeping active branchId as identity.
- Home no longer silently selects a single active branch; management KPIs and the real seven-day revenue chart require explicit branchId selection and show a clear no-branch state otherwise.
- Procurement renders a compact/expanded workflow stepper, requires a rejection reason, disables unauthorized approval actions, and communicates owner-only final approval while preserving the domain permission boundary.
- Purchase success feedback is sourced from the persisted/allocated internal document number rather than a pre-validation placeholder.
- Full-suite UX2 fixtures create a test branch only when the Fresh Install fixture has none; application behavior is not modified to satisfy tests.

## Preserved contracts
- Owner management information remains available according to permissions.
- Cashier does not gain sensitive financial KPIs without explicit permission.
- Inventory role keeps inventory context and does not gain unauthorized financial KPIs.
- Authentication, authorization, protected graph teardown and permission boundaries are not bypassed.
- Inventory Count record → submit → approve → post workflow and exact-balance assertion remain unchanged.

## Final verification rule
UI implementation is considered verified only if the same-commit 177-test API23, API35 and 16KB connected suites all pass with zero required failures/skips. Otherwise `PHASE 3 PART 3B NOT COMPLETE`.
