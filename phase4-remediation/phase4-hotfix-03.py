#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase4-hotfix-03.py <phase4-source-root>')

root = Path(sys.argv[1]).resolve()
changes = {
    root / 'app/src/main/java/ir/restaurant/management/data/db/PersonnelDao.kt': [
        '''    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM employees")\n    suspend fun nextEmployeeSequence(): Long\n\n''',
        '''    @Query("SELECT COALESCE(MAX(revisionNo), 0) + 1 FROM payroll_runs WHERE employeeId = :employeeId AND periodYear = :year AND periodMonth = :month")\n    suspend fun nextPayrollRevision(employeeId: Long, year: Int, month: Int): Int\n\n''',
    ],
    root / 'app/src/main/java/ir/restaurant/management/data/db/HrPayrollDao.kt': [
        '''    @Query("SELECT COALESCE(MAX(revisionNo),0)+1 FROM payroll_payslips WHERE employeeId=:employeeId AND periodId=:periodId")\n    suspend fun nextPayslipRevision(employeeId: Long, periodId: Long): Int\n\n''',
    ],
}

for path, blocks in changes.items():
    text = path.read_text(encoding='utf-8')
    updated = text
    for block in blocks:
        count = updated.count(block)
        if count == 0:
            method = block.split('suspend fun ', 1)[1].split('(', 1)[0]
            if method in updated:
                raise SystemExit(f'{path}: allocator block changed shape for {method}')
            continue
        if count != 1:
            raise SystemExit(f'{path}: expected one allocator block, found {count}')
        updated = updated.replace(block, '')
    path.write_text(updated, encoding='utf-8')

print('PHASE4_HOTFIX_03=APPLIED_OR_ALREADY_CLEAN')
