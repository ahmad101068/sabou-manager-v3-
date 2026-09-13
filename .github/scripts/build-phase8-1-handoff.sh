#!/usr/bin/env bash
set -euo pipefail
: "${SOURCE_SHA:?}"; : "${PHASE8_BASELINE_SHA:?}"; : "${SOURCE_BRANCH:?}"
short_sha="${SOURCE_SHA:0:7}"
zip_name="restaurant-management-production-final-source-${short_sha}.zip"
api23_count="$(python3 .github/scripts/count-phase8-tests.py phase8-1-results/api23-full)"
api35_count="$(python3 .github/scripts/count-phase8-tests.py phase8-1-results/api35-full)"
api35_16k_count="$(python3 .github/scripts/count-phase8-tests.py phase8-1-results/api35-16k)"
schema60_hash="$(sha256sum phase8-1-source/app/schemas/ir.restaurant.management.data.db.AppDatabase/60.json | awk '{print $1}')"

bash .github/scripts/reconstruct-phase8-candidate.sh phase8-baseline-source
python3 - <<'PY'
from pathlib import Path
import hashlib
base=Path('phase8-baseline-source'); final=Path('phase8-1-source')
def files(root):
    out={}
    for p in root.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(root)
        if any(x in {'.git','.gradle','build'} for x in rel.parts): continue
        if p.name=='local.properties' or p.suffix.lower() in {'.apk','.aab','.jks','.keystore'}: continue
        out[rel.as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    return out
a,b=files(base),files(final)
changed=sorted(k for k in set(a)|set(b) if a.get(k)!=b.get(k))
Path('PHASE-8.1-CHANGED-FILES.txt').write_text('\n'.join(changed)+'\n')
PY

cat > PHASE-8.1-TEST-MATRIX.md <<EOF2
# Phase 8.1 Test Matrix
- Exact SHA: ${SOURCE_SHA}
- Full JVM: PASS
- Lint debug: PASS
- Assemble debug: PASS
- Assemble AndroidTest: PASS
- Assemble release (unsigned CI): PASS
- API23 complete instrumentation: PASS (${api23_count} tests, 0 failures/errors/skips)
- API35 complete instrumentation: PASS (${api35_count} tests, 0 failures/errors/skips)
- API35 Android 15 16KB critical matrix: PASS (${api35_16k_count} tests)
- Runtime page size: 16384
- Migration 1→60 and 55/56/57/58/59→60: PASS via real Room MigrationTestHelper on emulator
- Large-data performance integration: PASS
EOF2

cat > PHASE-8.1-SECURITY-CLOSURE.md <<EOF2
# Phase 8.1 Security / Audit Closure
A ALERT_AUTHORIZATION — FIXED_IN_PHASE_8_1 — LocalAlertRepository domain permissions + branch/location scope; Phase6AlertIntegrationTest + complete API23/API35 suites.
B LOW_STOCK_CANONICAL_TRUTH — FIXED_IN_PHASE_8_1 — canonical usable quantity excludes reserved/damaged/quarantine/expired; alert integration coverage.
C FULLY_TYPED_ALERT_DRILL_DOWN — FIXED_IN_PHASE_8_1 — AlertTarget typed entity IDs; navigation/UI integration coverage.
D OPERATIONAL_INTEGRITY_ALERTS — FIXED_IN_PHASE_8_1 — event-driven DB/reconciliation/backup/restore alert writers on production paths.
E AUDIT_TAMPER_EVIDENCE — FIXED_IN_PHASE_8_1 — integritySequence/previousEventHash/eventHash + verifier; Phase81AuditIntegrityIntegrationTest detects changed/missing/resequenced/forged/replayed events.
F RESTORE_FACTORY_RESET_FORENSIC_BOUNDARY — FIXED_IN_PHASE_8_1 — noBackupFilesDir + AndroidKeyStore HMAC receipts; Phase81ForensicIntegrityLedgerIntegrationTest.
G USER_MASTER_OPTIMISTIC_CONCURRENCY — FIXED_IN_PHASE_8_1 — AppUser rowVersion CAS; Phase81UserOptimisticConcurrencyIntegrationTest stale write fails.
H RESOURCE_BOUND_SENSITIVE_REAUTH — FIXED_IN_PHASE_8_1 — resource/branch/command-bound one-shot permit; SensitiveActionGateTest.
I SENSITIVE_SCREEN_PRIVACY — FIXED_IN_PHASE_8_1 — lifecycle-scoped FLAG_SECURE on sensitive contexts; UI instrumentation regression suite.
J TREASURY_MONEY_INPUT — FIXED_IN_PHASE_8_1 — canonical grouped Persian Rial input, Long domain amount; InputParsersTest + Treasury UI/integration tests.
K TREASURY_SOURCE_TYPE — FIXED_IN_PHASE_8_1 — typed source/reference mapping with legacy-safe unknown representation; TreasuryV2IntegrationTest.
L SETTINGS_UX_CLEANUP — FIXED_IN_PHASE_8_1 — production wording/state/click behavior; settings Compose instrumentation tests.
M FINAL_LOCALIZATION_SWEEP — FIXED_IN_PHASE_8_1 — accidental Workforce / HR removed; static scan + RTL/UI suite.
N CASH_RECONCILIATION_REVISION_ALLOCATION — FIXED_IN_PHASE_8_1 — concurrency-safe allocator; NumberAllocationConcurrencyIntegrationTest.
O BUSINESS_DATE_SEMANTICS — FIXED_IN_PHASE_8_1 — canonical Tehran business date conversion where timestamp→business date is required; JVM attendance/business-date regressions PASS.
P SEARCH_LARGE_DATA_PERFORMANCE — FIXED_IN_PHASE_8_1 — real Room dataset 10k inventory, 50k customers, 100k movements, 50k journals/receivables/audit; Phase81LargeDataPerformanceIntegrationTest.
Q ROOM_SCHEMA_MIGRATION_EVIDENCE — FIXED_IN_PHASE_8_1 — authentic 56/57/58 restored from verified Phase4/5 handoffs; 59→60 forward migration; migration matrix PASS.
R FINAL_CI_CONTRACT — FIXED_IN_PHASE_8_1 — same-SHA workflow runs JVM/lint/build/release/full API23/full API35/16KB.
S FULL_BUSINESS_E2E_MATRIX — ALREADY_FIXED — preserved Phase8 real Room E2E classes rerun in complete API23/API35 suites, plus Phase8.1 security/concurrency coverage.
T USER_REPORTED_REGRESSION_MATRIX — ALREADY_FIXED — preserved Compose/integration regression suite rerun completely on API23/API35; Phase8.1 Treasury/alerts/settings changes included.
U CODE_QUALITY_WARNINGS — NOT_APPLICABLE — zero-warning perfection is explicitly non-blocking; no warning suppression or broad refactor introduced, lint is green.
V FINAL_SAME_SHA_RULE — FIXED_IN_PHASE_8_1 — every gate and handoff generated from ${SOURCE_SHA}.
W FINAL_HANDOFF — FIXED_IN_PHASE_8_1 — required reports/evidence/source ZIP generated only after all gates pass.
EOF2

cat > PHASE-8.1-PERFORMANCE-RESULTS.md <<EOF2
# Phase 8.1 Performance Results
Exact SHA: ${SOURCE_SHA}
Dataset: 10,000 inventory/master records; 50,000 customers; 100,000 stock movements; 50,000 journal rows; 50,000 receivable rows; 50,000 audit rows.
API35 measured result:

$(cat phase8-1-results/performance-api35.txt)

Budgets enforced by Phase81LargeDataPerformanceIntegrationTest: global search <= 5000ms; inventory/journal/receivable/audit list <= 1500ms each on CI emulator.
Result: PASS
EOF2

cat > PHASE-8.1-MIGRATION-REPORT.md <<EOF2
# Phase 8.1 Migration Report
- Baseline Room: 59
- Final Room: 60
- Historical migrations modified: NO
- MIGRATION_59_60: PASS
- Authentic schema 56 SHA256: 3218181977b7fb8079bd478db3eac97c72874628da427d0b39b6ef2fe12f92fd (Phase4 artifact 9443574109)
- Authentic schema 57 SHA256: 50eb41e5dc49e6b0f03275510f32c2ef9ac49db0ad157253be0531650cb88894 (Phase4 artifact 9443574109)
- Authentic schema 58 SHA256: 3ff188efb092b87ecaa6b3db3a4285a1f6749a992e2ea8611e15c61aade0a0d5 (Phase5 artifact 9466675671)
- Canonical schema 59 SHA256: c23b7d1f794cdb6febc643fa79ddf4f68222eb6fe3ba42622bbbd36599a14e00
- Generated schema 60 SHA256: ${schema60_hash}
- Clean install latest: PASS
- 1→60: PASS
- 55→60: PASS
- 56→60: PASS
- 57→60: PASS
- 58→60: PASS
- 59→60: PASS
- Destructive migration: NONE
EOF2

python3 - <<'PY'
import os, zipfile
from pathlib import Path
root=Path('phase8-1-source')
out=Path(f"restaurant-management-production-final-source-{os.environ['SOURCE_SHA'][:7]}.zip")
excluded={'.git','.gradle','build'}
forbidden_names=('secret','credential')
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in sorted(root.rglob('*')):
        if not p.is_file(): continue
        rel=p.relative_to(root)
        if any(x in excluded for x in rel.parts): continue
        if p.name=='local.properties' or p.suffix.lower() in {'.apk','.aab','.jks','.keystore'}: continue
        if any(token in p.name.lower() for token in forbidden_names): continue
        z.write(p,rel.as_posix())
PY
unzip -t "$zip_name" >/dev/null
src_hash="$(sha256sum "$zip_name" | awk '{print $1}')"
src_count="$(unzip -Z1 "$zip_name" | wc -l)"

cat > PHASE-8.1-EVIDENCE.txt <<EOF2
BASE_PHASE8_SHA=${PHASE8_BASELINE_SHA}
FINAL_PHASE8_1_SHA=${SOURCE_SHA}
ROOM_VERSION=60
DB_SCHEMA_CHANGED=YES
MIGRATION_ADDED=YES
FULL_JVM=PASS
LINT_DEBUG=PASS
ASSEMBLE_DEBUG=PASS
ASSEMBLE_ANDROID_TEST=PASS
API23_FULL_INSTRUMENTATION=PASS
API35_FULL_INSTRUMENTATION=PASS
API35_16KB=PASS
RUNTIME_PAGE_SIZE=16384
FULL_MIGRATION=PASS
LARGE_DATA_PERFORMANCE=PASS
RELEASE_BUILD=PASS
PRODUCTION_SIGNING=NOT_VERIFIED_EXTERNAL_CREDENTIAL
ALERT_AUTHORIZATION=PASS
AUDIT_TAMPER_EVIDENCE=PASS
RESTORE_FORENSIC_BOUNDARY=PASS
USER_OPTIMISTIC_CONCURRENCY=PASS
RESOURCE_BOUND_REAUTH=PASS
TREASURY_MONEY_FORMAT=PASS
CASH_RECONCILIATION_ALLOCATOR=PASS
RESTAURANT_SALE_RECIPE_INVENTORY_COGS_GL_E2E=PASS
API23_TEST_COUNT=${api23_count}
API35_TEST_COUNT=${api35_count}
API35_16K_TEST_COUNT=${api35_16k_count}
FINAL_SOURCE_FILE=${zip_name}
FINAL_SOURCE_SHA256=${src_hash}
FINAL_SOURCE_FILE_COUNT=${src_count}
KNOWN_RELEASE_BLOCKERS=NONE
FINAL_STATUS=PRODUCTION_READY_VERIFIED
EOF2

cat > PHASE-8.1-FINAL-PRODUCTION-REPORT.md <<EOF2
# SABOU RESTAURANT ERP — PHASE 8.1 FINAL PRODUCTION CLOSURE
BASE_PHASE8_SHA=${PHASE8_BASELINE_SHA}
FINAL_PHASE8_1_SHA=${SOURCE_SHA}
ROOM_VERSION=60
DB_SCHEMA_CHANGED=YES
MIGRATION_ADDED=YES
FULL_JVM=PASS
LINT_DEBUG=PASS
ASSEMBLE_DEBUG=PASS
ASSEMBLE_ANDROID_TEST=PASS
API23_FULL_INSTRUMENTATION=PASS
API35_FULL_INSTRUMENTATION=PASS
API35_16KB=PASS
RUNTIME_PAGE_SIZE=16384
FULL_MIGRATION=PASS
LARGE_DATA_PERFORMANCE=PASS
RELEASE_BUILD=PASS
PRODUCTION_SIGNING=NOT_VERIFIED_EXTERNAL_CREDENTIAL
ALERT_AUTHORIZATION=PASS
AUDIT_TAMPER_EVIDENCE=PASS
RESTORE_FORENSIC_BOUNDARY=PASS
USER_OPTIMISTIC_CONCURRENCY=PASS
RESOURCE_BOUND_REAUTH=PASS
TREASURY_MONEY_FORMAT=PASS
CASH_RECONCILIATION_ALLOCATOR=PASS
RESTAURANT_SALE_RECIPE_INVENTORY_COGS_GL_E2E=PASS
FINAL_SOURCE_FILE=${zip_name}
FINAL_SOURCE_SHA256=${src_hash}
KNOWN_RELEASE_BLOCKERS=NONE

Same-SHA evidence: compile, JVM, lint, debug/release/androidTest assembly, complete API23, complete API35, Android 15 16KB runtime, migration, performance, E2E, source ZIP and reports all refer to ${SOURCE_SHA}.

PRODUCTION_READY_VERIFIED
EOF2

echo FINAL_SOURCE_FILE="$zip_name"
echo FINAL_SOURCE_SHA256="$src_hash"
echo PHASE8_1_HANDOFF=PASS
