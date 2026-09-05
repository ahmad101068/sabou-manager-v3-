#!/usr/bin/env python3
"""Host-side execution test for the real schema-43 -> schema-44 HR/Payroll SQL."""

from __future__ import annotations

import re
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "app/src/main/java/ir/restaurant/management/data/db/migration/HrPayrollMigrations.kt"
GUARDS = ROOT / "app/src/main/java/ir/restaurant/management/data/db/HrPayrollGuards.kt"


def function_text(source: str, name: str) -> str:
    start_match = re.search(
        rf"^(?:private|internal) fun {re.escape(name)}\([^\n]*\)\s*\{{",
        source,
        flags=re.MULTILINE,
    )
    if not start_match:
        raise AssertionError(f"function not found: {name}")
    next_match = re.search(
        r"^(?:private|internal) fun [A-Za-z0-9_]+\(",
        source[start_match.end() :],
        flags=re.MULTILINE,
    )
    end = len(source) if not next_match else start_match.end() + next_match.start()
    return source[start_match.end() : end]


def sql_literals(block: str) -> list[str]:
    tokens = re.finditer(r'"""([\s\S]*?)"""|"((?:\\.|[^"\\])*)"', block)
    statements: list[str] = []
    for token in tokens:
        raw = token.group(1)
        if raw is None:
            raw = bytes(token.group(2), "utf-8").decode("unicode_escape")
        sql = raw.strip()
        if sql.upper().startswith(("ALTER ", "CREATE ", "UPDATE ", "INSERT ", "DELETE ")):
            statements.append(sql)
    return statements


def production_statements() -> list[str]:
    migration_source = MIGRATION.read_text(encoding="utf-8")
    guard_source = GUARDS.read_text(encoding="utf-8")
    order = (
        "upgradeEmployeeMasterColumns",
        "upgradeLeaveWorkflowColumns",
        "upgradePayrollPolicyColumns",
        "createHrPayrollTables",
        "createHrPayrollIndexes",
        "migrateEmployeeMaster",
        "migrateContracts",
        "migrateLegacyPayrollDocuments",
    )
    statements: list[str] = []
    for function in order:
        statements.extend(sql_literals(function_text(migration_source, function)))
    statements.extend(sql_literals(function_text(guard_source, "installHrPayrollGuards")))
    if len(statements) < 150:
        raise AssertionError(f"unexpectedly few migration statements: {len(statements)}")
    return statements


SCHEMA_43 = (
    """CREATE TABLE employees(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,fatherName TEXT NOT NULL DEFAULT '',
        employeeCode TEXT,jobTitle TEXT NOT NULL,branchName TEXT NOT NULL DEFAULT '',phone TEXT NOT NULL DEFAULT '',
        nationalId TEXT,birthEpochDay INTEGER,hireEpochDay INTEGER,insuranceNumber TEXT,bankCard TEXT,
        address TEXT NOT NULL DEFAULT '',emergencyContact TEXT NOT NULL DEFAULT '',monthlySalaryRial INTEGER NOT NULL,
        leaveBalanceMicros INTEGER NOT NULL,status TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL,
        updatedAtEpochMillis INTEGER NOT NULL)""",
    "CREATE UNIQUE INDEX index_employees_employeeCode ON employees(employeeCode)",
    """CREATE TABLE attendance(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,workEpochDay INTEGER NOT NULL,
        status TEXT NOT NULL,checkInMinute INTEGER,checkOutMinute INTEGER,lateMinutes INTEGER NOT NULL,
        overtimeMinutes INTEGER NOT NULL,notes TEXT NOT NULL)""",
    """CREATE TABLE leaves(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,startEpochDay INTEGER NOT NULL,
        endEpochDay INTEGER NOT NULL,daysMicros INTEGER NOT NULL,leaveType TEXT NOT NULL,status TEXT NOT NULL,
        notes TEXT NOT NULL,requestedBy TEXT NOT NULL,reviewedBy TEXT,reviewNotes TEXT NOT NULL,
        reviewedAtEpochMillis INTEGER,cancelledAtEpochMillis INTEGER,createdAtEpochMillis INTEGER NOT NULL,
        updatedAtEpochMillis INTEGER NOT NULL)""",
    """CREATE TABLE payroll_policies(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title TEXT NOT NULL,effectiveFromEpochDay INTEGER NOT NULL,
        effectiveToEpochDay INTEGER,overtimeHourlyRateRial INTEGER NOT NULL,absenceDailyDeductionRial INTEGER NOT NULL,
        lateMinuteDeductionRial INTEGER NOT NULL,createdBy TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL)""",
    """CREATE TABLE employee_contracts(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,contractType TEXT NOT NULL,
        startEpochDay INTEGER NOT NULL,endEpochDay INTEGER,baseSalaryRial INTEGER NOT NULL,
        dailyWorkMinutes INTEGER NOT NULL,weeklyWorkDays INTEGER NOT NULL,status TEXT NOT NULL,notes TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,updatedAtEpochMillis INTEGER NOT NULL)""",
    """CREATE TABLE employee_advances(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,amountRial INTEGER NOT NULL,
        advanceEpochDay INTEGER NOT NULL,paymentMethod TEXT NOT NULL,journalEntryId INTEGER NOT NULL,
        settledAmountRial INTEGER NOT NULL,status TEXT NOT NULL,notes TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,updatedAtEpochMillis INTEGER NOT NULL)""",
    """CREATE TABLE payroll_runs(
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,periodYear INTEGER NOT NULL,
        periodMonth INTEGER NOT NULL,revisionNo INTEGER NOT NULL,baseSalaryRial INTEGER NOT NULL,
        overtimeRial INTEGER NOT NULL,bonusRial INTEGER NOT NULL,deductionsRial INTEGER NOT NULL,
        advanceDeductionRial INTEGER NOT NULL,periodStartEpochDay INTEGER NOT NULL,periodEndEpochDay INTEGER NOT NULL,
        payrollPolicyId INTEGER,automaticOvertimeRial INTEGER NOT NULL,attendanceDeductionRial INTEGER NOT NULL,
        insuranceRial INTEGER NOT NULL,taxRial INTEGER NOT NULL,netPayRial INTEGER NOT NULL,
        paymentEpochDay INTEGER NOT NULL,paymentMethod TEXT NOT NULL,journalEntryId INTEGER NOT NULL,
        status TEXT NOT NULL,approvedBy TEXT,approvedAtEpochMillis INTEGER,reversalEpochDay INTEGER,
        reversalReason TEXT NOT NULL,reversalJournalEntryId INTEGER,reversedBy TEXT,notes TEXT NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,globalId TEXT NOT NULL,createdByActorId INTEGER,
        approvedByActorId INTEGER,correlationId TEXT NOT NULL)""",
)


SEED_43 = (
    """INSERT INTO employees(
        id,name,fatherName,employeeCode,jobTitle,branchName,phone,nationalId,birthEpochDay,
        hireEpochDay,insuranceNumber,bankCard,address,emergencyContact,monthlySalaryRial,
        leaveBalanceMicros,status,createdAtEpochMillis,updatedAtEpochMillis
    ) VALUES(1,'علی رضایی','حسن',NULL,'آشپز','مرکزی','09120000000','0012345678',10000,
        19000,'INS-1','6037997512344321','تهران','09121111111',30000000,5000000,
        'ACTIVE',1000,2000)""",
    "INSERT INTO attendance VALUES(1,1,20010,'PRESENT',480,960,0,0,'legacy daily fact')",
    """INSERT INTO leaves VALUES(
        1,1,20020,20020,1000000,'ANNUAL','PENDING','legacy leave','manager',NULL,'',NULL,NULL,1000,1000)""",
    """INSERT INTO payroll_policies VALUES(
        1,'Legacy policy',19000,NULL,100000,1000000,1000,'SYSTEM',1000)""",
    """INSERT INTO employee_contracts VALUES(
        1,1,'FIXED_TERM',19000,20100,30000000,480,6,'ACTIVE','legacy A',1000,1000)""",
    """INSERT INTO employee_contracts VALUES(
        2,1,'FIXED_TERM',20000,NULL,35000000,480,6,'ACTIVE','legacy overlapping B',1100,1100)""",
    """INSERT INTO employee_advances VALUES(
        1,1,10000000,19900,'BANK',700,2000000,'OPEN','legacy advance',1000,1000)""",
    """INSERT INTO payroll_runs VALUES(
        1,1,1404,12,1,30000000,2000000,100000,500000,1000000,20000,20029,1,
        2000000,0,1500000,500000,29600000,20035,'BANK',701,'PAID','مالک',3000,
        NULL,'',NULL,NULL,'legacy payroll',2000,'legacy:payroll_run:1',2,3,'legacy:payroll_run:1')""",
)


def scalar(db: sqlite3.Connection, sql: str):
    return db.execute(sql).fetchone()[0]


def assert_rejected(db: sqlite3.Connection, sql: str) -> None:
    try:
        db.execute(sql)
    except sqlite3.DatabaseError:
        return
    raise AssertionError(f"statement unexpectedly accepted: {sql[:80]}")


def migrate_once(statements: list[str]) -> tuple[sqlite3.Connection, int]:
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for sql in SCHEMA_43:
        db.execute(sql)
    for sql in SEED_43:
        db.execute(sql)
    for position, sql in enumerate(statements, start=1):
        try:
            db.execute(sql)
        except sqlite3.DatabaseError as error:
            raise AssertionError(f"migration SQL #{position} failed:\n{sql}\n{error}") from error
    db.commit()
    return db, len(statements)


def verify(db: sqlite3.Connection) -> tuple[tuple, int]:
    checks = 0

    def check(condition: bool, label: str) -> None:
        nonlocal checks
        checks += 1
        if not condition:
            raise AssertionError(label)

    check(scalar(db, "SELECT employeeCode FROM employees WHERE id=1") == "EMP-000001", "employee code")
    check(scalar(db, "SELECT COUNT(*) FROM employee_private_profiles WHERE employeeId=1") == 1, "private profile")
    check(scalar(db, "SELECT bankAccountLast4 FROM employee_private_profiles WHERE employeeId=1") == "4321", "bank mask")
    check(scalar(db, "SELECT nationalId IS NULL AND insuranceNumber IS NULL AND bankCard IS NULL FROM employees WHERE id=1") == 1, "sensitive clear")
    check(scalar(db, "SELECT COUNT(*) FROM employment_assignments WHERE employeeId=1") == 1, "assignment")
    check(scalar(db, "SELECT COUNT(*) FROM employment_contract_versions WHERE employeeId=1") == 2, "contracts")
    check(scalar(db, "SELECT COUNT(*) FROM hr_payroll_migration_anomalies WHERE code='OVERLAPPING_LEGACY_CONTRACT'") > 0, "overlap anomaly")
    check(scalar(db, "SELECT COUNT(*) FROM attendance WHERE id=1") == 1, "attendance preserved")
    check(scalar(db, "SELECT COUNT(*) FROM attendance_events") == 0, "no fake events")
    check(scalar(db, "SELECT status FROM leaves WHERE id=1") == "SUBMITTED", "leave mapping")
    check(scalar(db, "SELECT settledAmountRial FROM employee_advances WHERE id=1") == 2_000_000, "advance preserved")
    check(scalar(db, "SELECT COUNT(*) FROM payroll_periods WHERE periodKey='LEGACY-1404-12'") == 1, "period")
    check(scalar(db, "SELECT COUNT(*) FROM payroll_batches WHERE documentNumber='LEG-PAY-1404-12'") == 1, "batch")
    check(scalar(db, "SELECT COUNT(*) FROM payroll_payslips WHERE legacyPayrollRunId=1 AND componentDetailComplete=0 AND source='LEGACY_MIGRATION'") == 1, "payslip marker")
    check(scalar(db, "SELECT detailComplete FROM payroll_snapshots WHERE payslipId=(SELECT id FROM payroll_payslips WHERE legacyPayrollRunId=1)") == 0, "snapshot marker")
    check(scalar(db, "SELECT COUNT(*) FROM payroll_components") == 0, "no fake components")
    check(scalar(db, "SELECT COUNT(*) FROM payroll_payments") == 0, "no fake payments")
    check(len(db.execute("PRAGMA foreign_key_check").fetchall()) == 0, "foreign keys")
    check(scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_payroll_payslips_employeeId_periodId_revisionNo'") == 1, "payslip index")
    check(scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_employees_name'") == 1, "employee index")
    check(scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_payroll_snapshot_no_update'") == 1, "snapshot guard")

    assert_rejected(db, "UPDATE payroll_snapshots SET netPayRial=1 WHERE payslipId=(SELECT id FROM payroll_payslips WHERE legacyPayrollRunId=1)")
    checks += 1
    assert_rejected(db, "DELETE FROM payroll_payslips WHERE legacyPayrollRunId=1")
    checks += 1
    assert_rejected(
        db,
        """INSERT INTO employment_contract_versions(
            employeeId,contractNumber,versionNo,replacesContractId,contractType,effectiveFromEpochDay,
            effectiveToEpochDay,baseSalaryRial,standardDailyMinutes,standardWeeklyMinutes,overtimePolicyId,
            payrollPolicyId,jobTitleSnapshot,departmentSnapshot,branchSnapshot,status,notes,
            createdAtEpochMillis,createdByActorId,approvedAtEpochMillis,approvedByActorId,correlationId,source
        ) VALUES(1,'CTR-CONFLICT',1,NULL,'FIXED_TERM',20025,20075,50000000,480,2880,NULL,NULL,
            'آشپز','آشپزخانه','مرکزی','ACTIVE','',1000,NULL,NULL,NULL,'test:contract:conflict','NATIVE')""",
    )
    checks += 1

    deterministic = (
        db.execute("SELECT periodKey,startEpochDay,endEpochDay,status,source FROM payroll_periods ORDER BY periodKey").fetchall(),
        db.execute("SELECT documentNumber,status,source FROM payroll_batches ORDER BY documentNumber").fetchall(),
        db.execute("SELECT globalId,employeeCodeSnapshot,revisionNo,grossPayRial,totalDeductionsRial,netPayRial,source FROM payroll_payslips ORDER BY globalId").fetchall(),
        db.execute("SELECT entityType,entityId,code,detail FROM hr_payroll_migration_anomalies ORDER BY entityType,entityId,code").fetchall(),
    )
    return deterministic, checks


def main() -> None:
    statements = production_statements()
    first, count = migrate_once(statements)
    second, _ = migrate_once(statements)
    try:
        first_result, checks = verify(first)
        second_result, second_checks = verify(second)
        if first_result != second_result:
            raise AssertionError("migration output is not deterministic")
        checks += second_checks + 1
    finally:
        first.close()
        second.close()
    print(f"HR_PAYROLL_SQL_MIGRATION_PASS statements={count} checks={checks} deterministic=1")


if __name__ == "__main__":
    main()
