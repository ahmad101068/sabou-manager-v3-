#!/usr/bin/env python3
from pathlib import Path

PERM = Path("app/src/main/java/ir/sabou/inventory/domain/security/Permission.kt")
E2E = Path("app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt")

perm = PERM.read_text(encoding="utf-8")
manager_start = perm.index('    MANAGER(')
manager_end = perm.index('    CASHIER(', manager_start)
manager_block = perm[manager_start:manager_end]
needle = '            Permission.PAYROLL_REVIEW,\n            Permission.PAYROLL_APPROVE,\n            Permission.JOURNAL_REVERSE,'
replacement = '            Permission.PAYROLL_REVIEW,\n            Permission.JOURNAL_REVERSE,'
if needle not in manager_block:
    raise SystemExit("FIX14_MANAGER_APPROVE_TARGET_MISSING")
manager_block = manager_block.replace(needle, replacement, 1)
perm = perm[:manager_start] + manager_block + perm[manager_end:]
PERM.write_text(perm, encoding="utf-8")

text = E2E.read_text(encoding="utf-8")
old = '''        val owner = requireNotNull(security.currentUser.first())
        val manager = ensureTestManager()
        val nonce = System.nanoTime().toString().takeLast(8)'''
new = '''        val owner = requireNotNull(security.currentUser.first())
        val manager = ensureTestManager()
        val payrollApprover = ensureTestPayrollApprover()
        val nonce = System.nanoTime().toString().takeLast(8)'''
if old not in text:
    raise SystemExit("FIX14_PAYROLL_APPROVER_SEED_TARGET_MISSING")
text = text.replace(old, new, 1)
old = '        PayrollFixture(employeeId, periodId, batchId, manager.id)'
new = '        PayrollFixture(employeeId, periodId, batchId, payrollApprover.id)'
if old not in text:
    raise SystemExit("FIX14_PAYROLL_FIXTURE_TARGET_MISSING")
text = text.replace(old, new, 1)

anchor = '''    private suspend fun ensureTestManager() = app.container.securityRepository.users.first()
        .firstOrNull { it.username == TEST_MANAGER_USERNAME }'''
helper = '''    private suspend fun ensureTestPayrollApprover() = app.container.securityRepository.users.first()
        .firstOrNull { it.username == TEST_PAYROLL_APPROVER_USERNAME }
        ?: run {
            app.container.securityRepository.save(
                id = null,
                draft = UserDraft(
                    username = TEST_PAYROLL_APPROVER_USERNAME,
                    displayName = "مالک مستقل تأیید حقوق Alpha162",
                    pin = TEST_MANAGER_PIN,
                    role = UserRole.OWNER,
                    recoveryCode = TEST_PAYROLL_APPROVER_RECOVERY,
                ),
            )
            app.container.securityRepository.users.first().first { it.username == TEST_PAYROLL_APPROVER_USERNAME }
        }

'''
if helper not in text:
    if anchor not in text:
        raise SystemExit("FIX14_APPROVER_HELPER_ANCHOR_MISSING")
    text = text.replace(anchor, helper + anchor, 1)

const_anchor = '''        const val TEST_MANAGER_USERNAME = "e2emanager"
        const val TEST_MANAGER_PIN = "654321"
        const val TEST_MANAGER_RECOVERY = "24681357"'''
const_new = '''        const val TEST_MANAGER_USERNAME = "e2emanager"
        const val TEST_MANAGER_PIN = "654321"
        const val TEST_MANAGER_RECOVERY = "24681357"
        const val TEST_PAYROLL_APPROVER_USERNAME = "e2epayrollapprover"
        const val TEST_PAYROLL_APPROVER_RECOVERY = "97531864"'''
if const_new not in text:
    if const_anchor not in text:
        raise SystemExit("FIX14_APPROVER_CONSTANT_ANCHOR_MISSING")
    text = text.replace(const_anchor, const_new, 1)

E2E.write_text(text, encoding="utf-8")

perm_check = PERM.read_text(encoding="utf-8")
manager_block = perm_check[perm_check.index('    MANAGER('):perm_check.index('    CASHIER(')]
if 'Permission.PAYROLL_APPROVE,' in manager_block:
    raise SystemExit("FIX14_VERIFY_FAIL:manager_still_approves")
if 'role = UserRole.OWNER' not in E2E.read_text(encoding="utf-8") or 'TEST_PAYROLL_APPROVER_USERNAME' not in E2E.read_text(encoding="utf-8"):
    raise SystemExit("FIX14_VERIFY_FAIL:independent_owner_approver_missing")
print("DASHBOARD_UX2_FIX14_PAYROLL_SOD=PASS manager_approve=0 independent_owner_approver=1")
