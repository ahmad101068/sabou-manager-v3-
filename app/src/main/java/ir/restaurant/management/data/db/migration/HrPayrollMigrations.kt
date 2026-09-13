package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * HR/Payroll 2.0 document-ledger migration.
 *
 * Legacy aggregates are preserved and linked. No attendance clock event, contract fact, policy
 * version, component breakdown or treasury payment is fabricated when the source did not store it.
 */
internal val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        upgradeEmployeeMasterColumns(db)
        upgradeLeaveWorkflowColumns(db)
        upgradePayrollPolicyColumns(db)
        createHrPayrollTables(db)
        createHrPayrollIndexes(db)
        migrateEmployeeMaster(db)
        migrateContracts(db)
        migrateLegacyPayrollDocuments(db)
        installHrPayrollGuards(db)
    }
}

private fun upgradePayrollPolicyColumns(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN versionNo INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN overtimeMultiplierBasisPoints INTEGER NOT NULL DEFAULT 10000")
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN insuranceBasisPoints INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN taxBasisPoints INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN createdByActorId INTEGER")
    db.execSQL("ALTER TABLE payroll_policies ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
    db.execSQL("UPDATE payroll_policies SET correlationId='legacy:payroll_policy:' || id")
}

private fun upgradeLeaveWorkflowColumns(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE leaves ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE leaves ADD COLUMN idempotencyKey TEXT")
    db.execSQL("ALTER TABLE leaves ADD COLUMN requestedByActorId INTEGER")
    db.execSQL("ALTER TABLE leaves ADD COLUMN reviewedByActorId INTEGER")
    db.execSQL("ALTER TABLE leaves ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
    db.execSQL("UPDATE leaves SET globalId='legacy:leave:' || id, correlationId='legacy:leave:' || id")
    db.execSQL("UPDATE leaves SET status='SUBMITTED' WHERE status='PENDING'")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_leaves_globalId ON leaves(globalId)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_leaves_idempotencyKey ON leaves(idempotencyKey)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_leaves_employeeId_status_startEpochDay_endEpochDay ON leaves(employeeId,status,startEpochDay,endEpochDay)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_leaves_correlationId ON leaves(correlationId)")
}

private fun upgradeEmployeeMasterColumns(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE employees ADD COLUMN firstName TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE employees ADD COLUMN lastName TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE employees ADD COLUMN displayName TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE employees ADD COLUMN department TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE employees ADD COLUMN locationId INTEGER")
    db.execSQL("ALTER TABLE employees ADD COLUMN managerId INTEGER")
    db.execSQL("ALTER TABLE employees ADD COLUMN email TEXT")
    db.execSQL("ALTER TABLE employees ADD COLUMN terminationEpochDay INTEGER")
    db.execSQL("ALTER TABLE employees ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE employees ADD COLUMN createdByActorId INTEGER")
    db.execSQL("ALTER TABLE employees ADD COLUMN updatedByActorId INTEGER")
    db.execSQL("UPDATE employees SET displayName=name, department='UNASSIGNED'")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_name ON employees(name)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_displayName ON employees(displayName)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_phone ON employees(phone)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_jobTitle ON employees(jobTitle)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_department ON employees(department)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_locationId ON employees(locationId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_managerId ON employees(managerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_terminationEpochDay ON employees(terminationEpochDay)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_advances_employeeId_status ON employee_advances(employeeId,status)")
}

private fun createHrPayrollTables(db: SupportSQLiteDatabase) {
    val statements = listOf(
        """CREATE TABLE IF NOT EXISTS employee_private_profiles (
            employeeId INTEGER NOT NULL,
            nationalId TEXT,
            insuranceNumber TEXT,
            bankName TEXT,
            bankAccountLast4 TEXT,
            ibanLast4 TEXT,
            accountHolder TEXT,
            emergencyContact TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            updatedByActorId INTEGER,
            PRIMARY KEY(employeeId),
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS employment_assignments (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            employeeId INTEGER NOT NULL,
            effectiveFromEpochDay INTEGER NOT NULL,
            effectiveToEpochDay INTEGER,
            jobTitle TEXT NOT NULL,
            department TEXT NOT NULL,
            branchName TEXT NOT NULL,
            locationId INTEGER,
            managerId INTEGER,
            reason TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            createdByActorId INTEGER,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS employment_contract_versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            employeeId INTEGER NOT NULL,
            contractNumber TEXT NOT NULL,
            versionNo INTEGER NOT NULL,
            replacesContractId INTEGER,
            contractType TEXT NOT NULL,
            effectiveFromEpochDay INTEGER NOT NULL,
            effectiveToEpochDay INTEGER,
            baseSalaryRial INTEGER NOT NULL,
            standardDailyMinutes INTEGER NOT NULL,
            standardWeeklyMinutes INTEGER NOT NULL,
            overtimePolicyId INTEGER,
            payrollPolicyId INTEGER,
            jobTitleSnapshot TEXT NOT NULL,
            departmentSnapshot TEXT NOT NULL,
            branchSnapshot TEXT NOT NULL,
            status TEXT NOT NULL,
            notes TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            createdByActorId INTEGER,
            approvedAtEpochMillis INTEGER,
            approvedByActorId INTEGER,
            correlationId TEXT NOT NULL,
            source TEXT NOT NULL,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS attendance_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            globalId TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            employeeId INTEGER NOT NULL,
            eventType TEXT NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            timestampEpochMillis INTEGER NOT NULL,
            minuteOfDay INTEGER NOT NULL,
            source TEXT NOT NULL,
            deviceId TEXT,
            locationId INTEGER,
            createdByActorId INTEGER,
            reason TEXT,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS attendance_corrections (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            employeeId INTEGER NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            idempotencyKey TEXT NOT NULL,
            beforeSnapshot TEXT NOT NULL,
            afterSnapshot TEXT NOT NULL,
            reason TEXT NOT NULL,
            status TEXT NOT NULL,
            requestedByActorId INTEGER NOT NULL,
            approvedByActorId INTEGER,
            requestedAtEpochMillis INTEGER NOT NULL,
            approvedAtEpochMillis INTEGER,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS leave_ledger_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            globalId TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            employeeId INTEGER NOT NULL,
            leaveType TEXT NOT NULL,
            entryType TEXT NOT NULL,
            amountMicros INTEGER NOT NULL,
            leaveId INTEGER,
            businessEpochDay INTEGER NOT NULL,
            reason TEXT NOT NULL,
            createdByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(leaveId) REFERENCES leaves(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_periods (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            periodKey TEXT NOT NULL,
            startEpochDay INTEGER NOT NULL,
            endEpochDay INTEGER NOT NULL,
            paymentDueEpochDay INTEGER,
            status TEXT NOT NULL,
            openedByActorId INTEGER,
            openedAtEpochMillis INTEGER NOT NULL,
            closedAtEpochMillis INTEGER,
            reopenedAtEpochMillis INTEGER,
            rowVersion INTEGER NOT NULL,
            source TEXT NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_batches (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            documentNumber TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            periodId INTEGER NOT NULL,
            scope TEXT NOT NULL,
            branchName TEXT,
            department TEXT,
            status TEXT NOT NULL,
            createdByActorId INTEGER,
            calculatedByActorId INTEGER,
            calculatedAtEpochMillis INTEGER,
            reviewedByActorId INTEGER,
            reviewedAtEpochMillis INTEGER,
            approvedByActorId INTEGER,
            approvedAtEpochMillis INTEGER,
            correlationId TEXT NOT NULL,
            notes TEXT NOT NULL,
            rowVersion INTEGER NOT NULL,
            accrualJournalEntryId INTEGER,
            reversalJournalEntryId INTEGER,
            source TEXT NOT NULL,
            FOREIGN KEY(periodId) REFERENCES payroll_periods(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_payslips (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            globalId TEXT NOT NULL,
            batchId INTEGER NOT NULL,
            periodId INTEGER NOT NULL,
            employeeId INTEGER NOT NULL,
            employeeCodeSnapshot TEXT NOT NULL,
            employeeNameSnapshot TEXT NOT NULL,
            revisionNo INTEGER NOT NULL,
            replacesPayslipId INTEGER,
            legacyPayrollRunId INTEGER,
            contractId INTEGER,
            status TEXT NOT NULL,
            grossPayRial INTEGER NOT NULL,
            totalDeductionsRial INTEGER NOT NULL,
            netPayRial INTEGER NOT NULL,
            paidAmountRial INTEGER NOT NULL,
            remainingAmountRial INTEGER NOT NULL,
            componentDetailComplete INTEGER NOT NULL,
            calculatedAtEpochMillis INTEGER NOT NULL,
            approvedAtEpochMillis INTEGER,
            paidAtEpochMillis INTEGER,
            correlationId TEXT NOT NULL,
            source TEXT NOT NULL,
            rowVersion INTEGER NOT NULL,
            accrualJournalEntryId INTEGER,
            reversalJournalEntryId INTEGER,
            reversalReason TEXT,
            reversalEpochDay INTEGER,
            reversedAtEpochMillis INTEGER,
            FOREIGN KEY(batchId) REFERENCES payroll_batches(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(periodId) REFERENCES payroll_periods(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_snapshots (
            payslipId INTEGER NOT NULL,
            employeeId INTEGER NOT NULL,
            employeeCode TEXT NOT NULL,
            employeeDisplayName TEXT NOT NULL,
            contractId INTEGER,
            contractNumber TEXT,
            contractVersionNo INTEGER,
            baseSalaryRial INTEGER,
            standardPeriodMinutes INTEGER,
            eligiblePeriodMinutes INTEGER,
            actualWorkMinutes INTEGER,
            overtimeMinutes INTEGER,
            absenceMinutes INTEGER,
            lateMinutes INTEGER,
            paidLeaveMinutes INTEGER,
            unpaidLeaveMinutes INTEGER,
            payrollPolicyId INTEGER,
            payrollPolicyVersion INTEGER,
            overtimeRateRialPerHour INTEGER,
            overtimeMultiplierBasisPoints INTEGER,
            insuranceBasisPoints INTEGER,
            taxBasisPoints INTEGER,
            grossPayRial INTEGER NOT NULL,
            totalDeductionsRial INTEGER NOT NULL,
            netPayRial INTEGER NOT NULL,
            calculationVersion TEXT,
            calculationParameters TEXT NOT NULL,
            snapshotHash TEXT NOT NULL,
            capturedAtEpochMillis INTEGER NOT NULL,
            detailComplete INTEGER NOT NULL,
            PRIMARY KEY(payslipId),
            FOREIGN KEY(payslipId) REFERENCES payroll_payslips(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_components (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            payslipId INTEGER NOT NULL,
            componentType TEXT NOT NULL,
            description TEXT NOT NULL,
            quantity INTEGER,
            rateRial INTEGER,
            amountRial INTEGER NOT NULL,
            direction TEXT NOT NULL,
            sourceType TEXT NOT NULL,
            sourceId INTEGER,
            manualOverride INTEGER NOT NULL,
            overrideReason TEXT,
            createdByActorId INTEGER,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(payslipId) REFERENCES payroll_payslips(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_manual_adjustments (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            globalId TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            employeeId INTEGER NOT NULL,
            periodId INTEGER NOT NULL,
            componentType TEXT NOT NULL,
            direction TEXT NOT NULL,
            amountRial INTEGER NOT NULL,
            reason TEXT NOT NULL,
            attachmentMetadata TEXT,
            status TEXT NOT NULL,
            createdByActorId INTEGER NOT NULL,
            approvedByActorId INTEGER,
            createdAtEpochMillis INTEGER NOT NULL,
            approvedAtEpochMillis INTEGER,
            consumedByPayslipId INTEGER,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(periodId) REFERENCES payroll_periods(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_approval_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            batchId INTEGER NOT NULL,
            payslipId INTEGER,
            eventType TEXT NOT NULL,
            fromStatus TEXT NOT NULL,
            toStatus TEXT NOT NULL,
            actorId INTEGER NOT NULL,
            reason TEXT NOT NULL,
            snapshotHash TEXT,
            createdAtEpochMillis INTEGER NOT NULL,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(batchId) REFERENCES payroll_batches(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(payslipId) REFERENCES payroll_payslips(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_payments (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            globalId TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            payslipId INTEGER NOT NULL,
            amountRial INTEGER NOT NULL,
            treasuryAccountId TEXT NOT NULL,
            channel TEXT NOT NULL,
            paymentEpochDay INTEGER NOT NULL,
            paymentReference TEXT NOT NULL,
            status TEXT NOT NULL,
            treasuryTransactionId TEXT NOT NULL,
            journalEntryId INTEGER,
            reversalOfPaymentId INTEGER,
            createdByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            reversedAtEpochMillis INTEGER,
            reversalReason TEXT,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(payslipId) REFERENCES payroll_payslips(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_advance_allocations_v2 (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            payslipId INTEGER NOT NULL,
            advanceId INTEGER NOT NULL,
            amountRial INTEGER NOT NULL,
            status TEXT NOT NULL,
            createdByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            reversedAtEpochMillis INTEGER,
            reversalReason TEXT,
            correlationId TEXT NOT NULL,
            FOREIGN KEY(payslipId) REFERENCES payroll_payslips(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(advanceId) REFERENCES employee_advances(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS payroll_exceptions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            batchId INTEGER NOT NULL,
            payslipId INTEGER,
            employeeId INTEGER,
            code TEXT NOT NULL,
            blocking INTEGER NOT NULL,
            detail TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            resolvedAtEpochMillis INTEGER,
            resolvedByActorId INTEGER,
            resolutionNote TEXT,
            FOREIGN KEY(batchId) REFERENCES payroll_batches(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(payslipId) REFERENCES payroll_payslips(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS hr_payroll_migration_anomalies (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            entityType TEXT NOT NULL,
            entityId INTEGER NOT NULL,
            code TEXT NOT NULL,
            detail TEXT NOT NULL,
            detectedAtEpochMillis INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS hr_payroll_command_receipts (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            commandType TEXT NOT NULL,
            payloadHash TEXT NOT NULL,
            resultEntityType TEXT NOT NULL,
            resultEntityId INTEGER NOT NULL,
            resultDetail TEXT NOT NULL,
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            correlationId TEXT NOT NULL
        )""",
    )
    statements.forEach(db::execSQL)
}

private fun createHrPayrollIndexes(db: SupportSQLiteDatabase) {
    val statements = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_employee_private_profiles_nationalId ON employee_private_profiles(nationalId)",
        "CREATE INDEX IF NOT EXISTS index_employment_assignments_employeeId_effectiveFromEpochDay ON employment_assignments(employeeId,effectiveFromEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_employment_assignments_employeeId_effectiveToEpochDay ON employment_assignments(employeeId,effectiveToEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_employment_assignments_department ON employment_assignments(department)",
        "CREATE INDEX IF NOT EXISTS index_employment_assignments_branchName ON employment_assignments(branchName)",
        "CREATE INDEX IF NOT EXISTS index_employment_assignments_locationId ON employment_assignments(locationId)",
        "CREATE INDEX IF NOT EXISTS index_employment_assignments_managerId ON employment_assignments(managerId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_employment_contract_versions_contractNumber ON employment_contract_versions(contractNumber)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_employeeId_effectiveFromEpochDay ON employment_contract_versions(employeeId,effectiveFromEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_employeeId_effectiveToEpochDay ON employment_contract_versions(employeeId,effectiveToEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_employeeId_status ON employment_contract_versions(employeeId,status)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_replacesContractId ON employment_contract_versions(replacesContractId)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_payrollPolicyId ON employment_contract_versions(payrollPolicyId)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_overtimePolicyId ON employment_contract_versions(overtimePolicyId)",
        "CREATE INDEX IF NOT EXISTS index_employment_contract_versions_correlationId ON employment_contract_versions(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_events_globalId ON attendance_events(globalId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_events_idempotencyKey ON attendance_events(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_attendance_events_employeeId_businessEpochDay_timestampEpochMillis ON attendance_events(employeeId,businessEpochDay,timestampEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_attendance_events_employeeId_eventType_businessEpochDay ON attendance_events(employeeId,eventType,businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_attendance_events_correlationId ON attendance_events(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_corrections_idempotencyKey ON attendance_corrections(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_attendance_corrections_employeeId_businessEpochDay ON attendance_corrections(employeeId,businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_attendance_corrections_status ON attendance_corrections(status)",
        "CREATE INDEX IF NOT EXISTS index_attendance_corrections_correlationId ON attendance_corrections(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_leave_ledger_entries_globalId ON leave_ledger_entries(globalId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_leave_ledger_entries_idempotencyKey ON leave_ledger_entries(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_leave_ledger_entries_employeeId_leaveType_businessEpochDay ON leave_ledger_entries(employeeId,leaveType,businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_leave_ledger_entries_leaveId ON leave_ledger_entries(leaveId)",
        "CREATE INDEX IF NOT EXISTS index_leave_ledger_entries_correlationId ON leave_ledger_entries(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_periods_periodKey ON payroll_periods(periodKey)",
        "CREATE INDEX IF NOT EXISTS index_payroll_periods_startEpochDay_endEpochDay ON payroll_periods(startEpochDay,endEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_payroll_periods_status ON payroll_periods(status)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_batches_documentNumber ON payroll_batches(documentNumber)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_batches_idempotencyKey ON payroll_batches(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_payroll_batches_periodId_status ON payroll_batches(periodId,status)",
        "CREATE INDEX IF NOT EXISTS index_payroll_batches_branchName ON payroll_batches(branchName)",
        "CREATE INDEX IF NOT EXISTS index_payroll_batches_department ON payroll_batches(department)",
        "CREATE INDEX IF NOT EXISTS index_payroll_batches_correlationId ON payroll_batches(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_payslips_globalId ON payroll_payslips(globalId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_payslips_employeeId_periodId_revisionNo ON payroll_payslips(employeeId,periodId,revisionNo)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payslips_batchId_status ON payroll_payslips(batchId,status)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payslips_employeeId_periodId ON payroll_payslips(employeeId,periodId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payslips_periodId ON payroll_payslips(periodId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payslips_replacesPayslipId ON payroll_payslips(replacesPayslipId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payslips_contractId ON payroll_payslips(contractId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payslips_correlationId ON payroll_payslips(correlationId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_snapshots_contractId ON payroll_snapshots(contractId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_snapshots_payrollPolicyId ON payroll_snapshots(payrollPolicyId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_components_payslipId_direction ON payroll_components(payslipId,direction)",
        "CREATE INDEX IF NOT EXISTS index_payroll_components_sourceType_sourceId ON payroll_components(sourceType,sourceId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_components_componentType ON payroll_components(componentType)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_manual_adjustments_globalId ON payroll_manual_adjustments(globalId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_manual_adjustments_idempotencyKey ON payroll_manual_adjustments(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_payroll_manual_adjustments_employeeId_periodId_status ON payroll_manual_adjustments(employeeId,periodId,status)",
        "CREATE INDEX IF NOT EXISTS index_payroll_manual_adjustments_periodId ON payroll_manual_adjustments(periodId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_manual_adjustments_consumedByPayslipId ON payroll_manual_adjustments(consumedByPayslipId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_manual_adjustments_correlationId ON payroll_manual_adjustments(correlationId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_approval_events_batchId_createdAtEpochMillis ON payroll_approval_events(batchId,createdAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_payroll_approval_events_payslipId_createdAtEpochMillis ON payroll_approval_events(payslipId,createdAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_payroll_approval_events_correlationId ON payroll_approval_events(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_payments_globalId ON payroll_payments(globalId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_payments_idempotencyKey ON payroll_payments(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payments_payslipId_status_paymentEpochDay ON payroll_payments(payslipId,status,paymentEpochDay)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_payments_reversalOfPaymentId ON payroll_payments(reversalOfPaymentId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payments_treasuryTransactionId ON payroll_payments(treasuryTransactionId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_payments_correlationId ON payroll_payments(correlationId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_advance_allocations_v2_idempotencyKey ON payroll_advance_allocations_v2(idempotencyKey)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_advance_allocations_v2_payslipId_advanceId ON payroll_advance_allocations_v2(payslipId,advanceId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_advance_allocations_v2_advanceId_status ON payroll_advance_allocations_v2(advanceId,status)",
        "CREATE INDEX IF NOT EXISTS index_payroll_advance_allocations_v2_correlationId ON payroll_advance_allocations_v2(correlationId)",
        "CREATE INDEX IF NOT EXISTS index_payroll_exceptions_batchId_resolvedAtEpochMillis ON payroll_exceptions(batchId,resolvedAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_payroll_exceptions_employeeId_code ON payroll_exceptions(employeeId,code)",
        "CREATE INDEX IF NOT EXISTS index_payroll_exceptions_payslipId ON payroll_exceptions(payslipId)",
        "CREATE INDEX IF NOT EXISTS index_hr_payroll_migration_anomalies_entityType_entityId ON hr_payroll_migration_anomalies(entityType,entityId)",
        "CREATE INDEX IF NOT EXISTS index_hr_payroll_migration_anomalies_code ON hr_payroll_migration_anomalies(code)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_hr_payroll_command_receipts_idempotencyKey ON hr_payroll_command_receipts(idempotencyKey)",
        "CREATE INDEX IF NOT EXISTS index_hr_payroll_command_receipts_commandType_resultEntityType_resultEntityId ON hr_payroll_command_receipts(commandType,resultEntityType,resultEntityId)",
        "CREATE INDEX IF NOT EXISTS index_hr_payroll_command_receipts_correlationId ON hr_payroll_command_receipts(correlationId)",
    )
    statements.forEach(db::execSQL)
}

private fun migrateEmployeeMaster(db: SupportSQLiteDatabase) {
    db.execSQL(
        """INSERT INTO hr_payroll_migration_anomalies(entityType,entityId,code,detail,detectedAtEpochMillis)
        SELECT 'EMPLOYEE',id,'EMPLOYEE_CODE_BACKFILLED','Legacy employee did not have a stable employee code',updatedAtEpochMillis
        FROM employees WHERE employeeCode IS NULL OR TRIM(employeeCode)=''""",
    )
    db.execSQL(
        """INSERT INTO hr_payroll_migration_anomalies(entityType,entityId,code,detail,detectedAtEpochMillis)
        SELECT 'EMPLOYEE',id,'MISSING_HIRE_DATE','Employment assignment could not be backfilled without a proven hire date',updatedAtEpochMillis
        FROM employees WHERE hireEpochDay IS NULL OR hireEpochDay<=0""",
    )
    db.execSQL("UPDATE employees SET employeeCode='EMP-' || printf('%06d',id) WHERE employeeCode IS NULL OR TRIM(employeeCode)=''")
    db.execSQL(
        """UPDATE employees SET status=CASE
            WHEN UPPER(TRIM(status))='ACTIVE' THEN 'ACTIVE'
            WHEN UPPER(TRIM(status)) IN ('INACTIVE','DEACTIVATED') THEN 'ARCHIVED'
            WHEN UPPER(TRIM(status)) IN ('APPLICANT','ON_LEAVE','SUSPENDED','TERMINATED','ARCHIVED') THEN UPPER(TRIM(status))
            ELSE 'ARCHIVED' END""",
    )
    db.execSQL(
        """INSERT INTO employee_private_profiles(
            employeeId,nationalId,insuranceNumber,bankName,bankAccountLast4,ibanLast4,accountHolder,
            emergencyContact,createdAtEpochMillis,updatedAtEpochMillis,updatedByActorId
        ) SELECT id,nationalId,insuranceNumber,NULL,
                 CASE WHEN bankCard IS NULL OR LENGTH(TRIM(bankCard))<4 THEN NULL ELSE SUBSTR(TRIM(bankCard),-4) END,
                 NULL,NULL,emergencyContact,createdAtEpochMillis,updatedAtEpochMillis,NULL
          FROM employees""",
    )
    db.execSQL("UPDATE employees SET nationalId=NULL, insuranceNumber=NULL, bankCard=NULL")
    db.execSQL(
        """INSERT INTO employment_assignments(
            employeeId,effectiveFromEpochDay,effectiveToEpochDay,jobTitle,department,branchName,
            locationId,managerId,reason,createdAtEpochMillis,createdByActorId,correlationId
        ) SELECT id,hireEpochDay,NULL,jobTitle,'UNASSIGNED',branchName,NULL,NULL,
                 'LEGACY_MIGRATION',createdAtEpochMillis,NULL,'legacy:employee_assignment:' || id
          FROM employees WHERE hireEpochDay IS NOT NULL AND hireEpochDay>0""",
    )
}

private fun migrateContracts(db: SupportSQLiteDatabase) {
    db.execSQL(
        """INSERT INTO employment_contract_versions(
            employeeId,contractNumber,versionNo,replacesContractId,contractType,effectiveFromEpochDay,
            effectiveToEpochDay,baseSalaryRial,standardDailyMinutes,standardWeeklyMinutes,
            overtimePolicyId,payrollPolicyId,jobTitleSnapshot,departmentSnapshot,branchSnapshot,status,
            notes,createdAtEpochMillis,createdByActorId,approvedAtEpochMillis,approvedByActorId,correlationId,source
        ) SELECT employeeId,'LEG-CTR-' || printf('%08d',id),1,NULL,
                 CASE UPPER(TRIM(contractType))
                    WHEN 'PERMANENT' THEN 'PERMANENT'
                    WHEN 'FIXED_TERM' THEN 'FIXED_TERM'
                    WHEN 'PART_TIME' THEN 'PART_TIME'
                    WHEN 'HOURLY' THEN 'HOURLY'
                    WHEN 'PROBATION' THEN 'PROBATION'
                    ELSE 'LEGACY_UNKNOWN' END,
                 startEpochDay,endEpochDay,baseSalaryRial,dailyWorkMinutes,dailyWorkMinutes*weeklyWorkDays,
                 NULL,NULL,'UNKNOWN_LEGACY','UNKNOWN_LEGACY','UNKNOWN_LEGACY','LEGACY',notes,
                 createdAtEpochMillis,NULL,NULL,NULL,'legacy:employee_contract:' || id,'LEGACY_MIGRATION'
          FROM employee_contracts""",
    )
    db.execSQL(
        """INSERT INTO hr_payroll_migration_anomalies(entityType,entityId,code,detail,detectedAtEpochMillis)
        SELECT 'CONTRACT',id,'INVALID_EFFECTIVE_RANGE','Legacy contract end date is before start date',updatedAtEpochMillis
          FROM employee_contracts WHERE endEpochDay IS NOT NULL AND endEpochDay<startEpochDay""",
    )
    db.execSQL(
        """INSERT INTO hr_payroll_migration_anomalies(entityType,entityId,code,detail,detectedAtEpochMillis)
        SELECT 'CONTRACT',a.id,'OVERLAPPING_LEGACY_CONTRACT','Overlaps legacy contract ' || b.id,
               CASE WHEN a.updatedAtEpochMillis>b.updatedAtEpochMillis THEN a.updatedAtEpochMillis ELSE b.updatedAtEpochMillis END
          FROM employee_contracts a
          JOIN employee_contracts b ON b.employeeId=a.employeeId AND b.id>a.id
         WHERE a.startEpochDay<=COALESCE(b.endEpochDay,9223372036854775807)
           AND COALESCE(a.endEpochDay,9223372036854775807)>=b.startEpochDay""",
    )
}

private fun migrateLegacyPayrollDocuments(db: SupportSQLiteDatabase) {
    db.execSQL(
        """INSERT INTO payroll_periods(
            periodKey,startEpochDay,endEpochDay,paymentDueEpochDay,status,openedByActorId,
            openedAtEpochMillis,closedAtEpochMillis,reopenedAtEpochMillis,rowVersion,source
        ) SELECT 'LEGACY-' || printf('%04d',periodYear) || '-' || printf('%02d',periodMonth),
                 COALESCE(MIN(CASE WHEN periodStartEpochDay>0 THEN periodStartEpochDay END),0),
                 COALESCE(MAX(CASE WHEN periodEndEpochDay>0 THEN periodEndEpochDay END),0),
                 MAX(paymentEpochDay),'LEGACY',NULL,MIN(createdAtEpochMillis),NULL,NULL,0,'LEGACY_MIGRATION'
          FROM payroll_runs GROUP BY periodYear,periodMonth""",
    )
    db.execSQL(
        """INSERT INTO payroll_batches(
            documentNumber,idempotencyKey,periodId,scope,branchName,department,status,createdByActorId,
            calculatedByActorId,calculatedAtEpochMillis,reviewedByActorId,reviewedAtEpochMillis,approvedByActorId,
            approvedAtEpochMillis,correlationId,notes,rowVersion,accrualJournalEntryId,reversalJournalEntryId,source
        ) SELECT 'LEG-PAY-' || SUBSTR(p.periodKey,8),
                 'legacy:payroll_batch:' || SUBSTR(p.periodKey,8),p.id,'ALL',NULL,NULL,'LEGACY',NULL,
                 NULL,p.openedAtEpochMillis,NULL,NULL,NULL,NULL,'legacy:payroll_batch:' || p.id,
                 'Migrated legacy payroll batch; source rows had no batch document',0,NULL,NULL,'LEGACY_MIGRATION'
          FROM payroll_periods p WHERE p.source='LEGACY_MIGRATION'""",
    )
    db.execSQL(
        """INSERT INTO payroll_payslips(
            globalId,batchId,periodId,employeeId,employeeCodeSnapshot,employeeNameSnapshot,revisionNo,
            replacesPayslipId,legacyPayrollRunId,contractId,status,grossPayRial,totalDeductionsRial,
            netPayRial,paidAmountRial,remainingAmountRial,componentDetailComplete,calculatedAtEpochMillis,
            approvedAtEpochMillis,paidAtEpochMillis,correlationId,source,rowVersion,
            accrualJournalEntryId,reversalJournalEntryId,reversalReason,reversalEpochDay,reversedAtEpochMillis
        ) SELECT CASE WHEN TRIM(r.globalId)='' THEN 'legacy:payroll_run:' || r.id ELSE r.globalId END,
                 b.id,p.id,r.employeeId,e.employeeCode,e.name,r.revisionNo,NULL,r.id,NULL,
                 CASE UPPER(TRIM(r.status))
                    WHEN 'PAID' THEN 'PAID'
                    WHEN 'REVERSED' THEN 'REVERSED'
                    WHEN 'PENDING_APPROVAL' THEN 'UNDER_REVIEW'
                    ELSE 'LEGACY' END,
                 r.baseSalaryRial+r.overtimeRial+r.bonusRial,
                 CASE WHEN r.baseSalaryRial+r.overtimeRial+r.bonusRial>=r.netPayRial
                      THEN r.baseSalaryRial+r.overtimeRial+r.bonusRial-r.netPayRial ELSE 0 END,
                 r.netPayRial,
                 CASE WHEN UPPER(TRIM(r.status)) IN ('PAID','REVERSED') THEN r.netPayRial ELSE 0 END,
                 CASE WHEN UPPER(TRIM(r.status)) IN ('PAID','REVERSED') THEN 0 ELSE r.netPayRial END,
                 0,r.createdAtEpochMillis,r.approvedAtEpochMillis,NULL,
                 CASE WHEN TRIM(r.correlationId)='' THEN 'legacy:payroll_run:' || r.id ELSE r.correlationId END,
                 'LEGACY_MIGRATION',0,r.journalEntryId,r.reversalJournalEntryId,
                 NULLIF(r.reversalReason,''),r.reversalEpochDay,NULL
          FROM payroll_runs r
          JOIN employees e ON e.id=r.employeeId
          JOIN payroll_periods p ON p.periodKey='LEGACY-' || printf('%04d',r.periodYear) || '-' || printf('%02d',r.periodMonth)
          JOIN payroll_batches b ON b.periodId=p.id AND b.source='LEGACY_MIGRATION'""",
    )
    db.execSQL(
        """INSERT INTO payroll_snapshots(
            payslipId,employeeId,employeeCode,employeeDisplayName,contractId,contractNumber,contractVersionNo,
            baseSalaryRial,standardPeriodMinutes,eligiblePeriodMinutes,actualWorkMinutes,overtimeMinutes,
            absenceMinutes,lateMinutes,paidLeaveMinutes,unpaidLeaveMinutes,payrollPolicyId,
            payrollPolicyVersion,overtimeRateRialPerHour,overtimeMultiplierBasisPoints,
            insuranceBasisPoints,taxBasisPoints,grossPayRial,totalDeductionsRial,netPayRial,
            calculationVersion,calculationParameters,snapshotHash,capturedAtEpochMillis,detailComplete
        ) SELECT p.id,p.employeeId,p.employeeCodeSnapshot,p.employeeNameSnapshot,NULL,NULL,NULL,r.baseSalaryRial,
                 NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,r.payrollPolicyId,NULL,NULL,NULL,NULL,NULL,
                 p.grossPayRial,p.totalDeductionsRial,p.netPayRial,NULL,
                 'LEGACY_SOURCE_TOTALS_ONLY;NO_COMPONENT_OR_INPUT_DETAIL',
                 'legacy:payroll_snapshot:' || r.id,r.createdAtEpochMillis,0
          FROM payroll_payslips p
          JOIN payroll_runs r ON r.id=p.legacyPayrollRunId
         WHERE p.source='LEGACY_MIGRATION'""",
    )
    db.execSQL(
        """INSERT INTO hr_payroll_migration_anomalies(entityType,entityId,code,detail,detectedAtEpochMillis)
        SELECT 'PAYROLL_RUN',r.id,'LEGACY_PAYSLIP_INCOMPLETE',
               'Totals preserved; component, attendance, leave, contract and payment detail was not fabricated',
               r.createdAtEpochMillis FROM payroll_runs r""",
    )
    db.execSQL(
        """INSERT INTO hr_payroll_migration_anomalies(entityType,entityId,code,detail,detectedAtEpochMillis)
        SELECT 'PAYROLL_RUN',r.id,'LEGACY_NET_EXCEEDS_GROSS','Legacy net pay is greater than stored gross inputs',r.createdAtEpochMillis
          FROM payroll_runs r WHERE r.netPayRial>r.baseSalaryRial+r.overtimeRial+r.bonusRial""",
    )
}
