# Restaurant Management ERP

Canonical pre-Phase-3 source baseline for restaurant back-office operations. The application covers branch management, Daily Sales, accounting, receivables and collections, inventory, procurement, recipes and food cost, personnel/payroll, assets, management controls, tasks/checklists, audit, backup, and optional fail-closed cloud synchronization.

## Canonical build metadata

The following exact fields are the documentation source read by `scripts/verify-documentation.py` and must match source code exactly.

```text
applicationId = ir.restaurant.management
compileSdk = 36
targetSdk = 36
minSdk = 23
versionCode = 209
versionName = 1.0.0
Room version = 55
Gradle = 8.13
AGP = 8.13.0
Kotlin = 2.1.20
```

## Database contract

Room schema version is **55**. Historical migrations are product upgrade-contract code and remain registered from 1 through 55. The current schema evidence is the Room/KSP-generated file `app/schemas/ir.restaurant.management.data.db.AppDatabase/55.json`; historical `54.json` is preserved, and neither schema may be hand-authored, copied, renamed, or edited to manufacture a pass.

Run the truth gate with:

```bash
python3 scripts/verify-room-schema.py
```

CI regenerates Room schema evidence and runs `git diff --exit-code -- app/schemas`; schema drift is a hard failure.

## Canonical architecture rules

- Branch identity is numeric `branchId`; branch text is display/snapshot/search/legacy-compatibility metadata only.
- No business path may default a missing or unknown branch to branch ID 1.
- The existing accounting Journal is the only accounting core. Accounting scopes are `BRANCH`, `ORGANIZATION`, and `UNASSIGNED_LEGACY`.
- Tax is not revenue; collection of an old receivable is not new sales revenue.
- Receivable balance/aging, Inventory Ledger, Food Cost semantics, and P&L formulas each have one canonical truth path.
- Historical branch text resolution remains only where required for the v1→v55 upgrade/compatibility contract.

See `ARCHITECTURE-CURRENT.md` and `ARCHITECTURE-FREEZE.md`.

## Fresh install branch setup

An authorized user with `BRANCH_MANAGE` can open **مدیریت شعب** and list branches, create the first branch using a user-provided name, rename it, and activate/deactivate it. The application does not synthesize a default branch ID.

## Verification

Static/readiness gates:

```bash
python3 scripts/verify-room-schema.py
python3 scripts/verify-documentation.py
python3 scripts/test-verify-documentation.py
python3 scripts/verify-repository-hygiene.py
python3 scripts/verify-security.py
python3 scripts/verify-code-quality.py
python3 scripts/verify-pre-phase3-standardization.py
bash scripts/verify-foundation.sh
```

Runtime/business/instrumentation test execution is preserved in `.github/workflows/tests.yml` and deferred to the next phase. It is not a production-readiness false-green bypass.

## Security and release

The source keeps SQLCipher, Android Keystore AES-256-GCM wrapping, `SecureRandom`, `allowBackup=false`, `usesCleartextTraffic=false`, HTTPS-only sync transport with automatic redirects disabled, no committed signing secrets, and fail-closed partial signing configuration. The currently stored credential/portable-backup PBKDF2-HMAC-SHA1 formats are compatibility-sensitive and are documented as test-backed security work rather than silently changed here.

Signed release publication requires owner-provided signing secrets. See `docs/SECURITY.md` and `docs/RELEASE_GUIDE.md`.

## Current documentation

- `ARCHITECTURE-CURRENT.md` — canonical domain map and legacy compatibility boundaries.
- `ARCHITECTURE-FREEZE.md` — frozen semantics before Phase 3.
- `PRODUCT-TERMINOLOGY.md` — stable product language.
- `UI-STANDARDS.md` — current UI/state/formatting conventions.
- `UI-COMPONENT-INVENTORY.md` — reusable UI inventory; no Phase-3 redesign component is introduced.
- `docs/SECURITY.md`, `docs/TESTING.md`, `docs/RELEASE_GUIDE.md`, `docs/MAINTENANCE.md`, `docs/HANDOVER.md`, `docs/KNOWN_ISSUES.md`, `docs/DECISIONS.md`, `docs/DEPENDENCIES.md`, `docs/PRIVACY.md`, `docs/GOOGLE_PLAY_DATA_SAFETY.md`.
- `docs/PAYROLL-CALCULATION-SPEC.md` is retained because it is a current domain specification, not an audit-history report.
- `docs/history/` contains development/audit reports retained only for traceability; current tools and runtime do not depend on them.

`README.md` is the single canonical metadata source. A second localized README is intentionally not maintained to avoid metadata divergence.
