package ir.restaurant.management.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

internal val hrPayrollGuardNames = listOf(
    "trg_employees_code_required_insert",
    "trg_employees_code_stable_update",
    "trg_employees_status_validate_insert",
    "trg_employees_status_validate_update",
    "trg_employees_history_no_delete",
    "trg_contract_versions_overlap_insert",
    "trg_contract_versions_overlap_update",
    "trg_contract_versions_frozen_update",
    "trg_contract_versions_no_delete",
    "trg_attendance_events_no_update",
    "trg_attendance_events_no_delete",
    "trg_attendance_corrections_controlled_update",
    "trg_attendance_corrections_no_delete",
    "trg_leave_ledger_no_update",
    "trg_leave_ledger_no_delete",
    "trg_payroll_snapshot_no_update",
    "trg_payroll_snapshot_no_delete",
    "trg_payroll_components_frozen_insert",
    "trg_payroll_components_frozen_update",
    "trg_payroll_components_no_delete",
    "trg_payroll_payslip_financial_freeze",
    "trg_payroll_payslip_no_delete",
    "trg_payroll_payslip_amounts_insert",
    "trg_payroll_payslip_amounts_update",
    "trg_payroll_payments_validate_insert",
    "trg_payroll_payments_financial_freeze",
    "trg_payroll_payments_no_delete",
    "trg_payroll_allocations_validate_insert",
    "trg_payroll_allocations_financial_freeze",
    "trg_payroll_allocations_no_delete",
    "trg_payroll_approval_events_no_update",
    "trg_payroll_approval_events_no_delete",
    "trg_hr_payroll_command_receipts_no_update",
    "trg_hr_payroll_command_receipts_no_delete",
)

internal fun installHrPayrollGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_employees_code_required_insert
        BEFORE INSERT ON employees
        WHEN NEW.employeeCode IS NULL OR TRIM(NEW.employeeCode)=''
        BEGIN SELECT RAISE(ABORT,'EMPLOYEE_CODE_REQUIRED'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_employees_code_stable_update
        BEFORE UPDATE OF employeeCode ON employees
        WHEN OLD.employeeCode IS NOT NEW.employeeCode
         AND EXISTS(SELECT 1 FROM payroll_payslips p WHERE p.employeeId=OLD.id)
        BEGIN SELECT RAISE(ABORT,'EMPLOYEE_CODE_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_employees_status_validate_insert
        BEFORE INSERT ON employees
        WHEN NEW.status NOT IN ('APPLICANT','ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED','ARCHIVED')
        BEGIN SELECT RAISE(ABORT,'EMPLOYEE_STATUS_INVALID'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_employees_status_validate_update
        BEFORE UPDATE OF status ON employees
        WHEN NEW.status NOT IN ('APPLICANT','ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED','ARCHIVED')
        BEGIN SELECT RAISE(ABORT,'EMPLOYEE_STATUS_INVALID'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_employees_history_no_delete
        BEFORE DELETE ON employees
        WHEN EXISTS(SELECT 1 FROM payroll_payslips p WHERE p.employeeId=OLD.id)
          OR EXISTS(SELECT 1 FROM employment_contract_versions c WHERE c.employeeId=OLD.id)
          OR EXISTS(SELECT 1 FROM attendance_events a WHERE a.employeeId=OLD.id)
        BEGIN SELECT RAISE(ABORT,'EMPLOYEE_HAS_HISTORY'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_contract_versions_overlap_insert
        BEFORE INSERT ON employment_contract_versions
        WHEN NEW.status IN ('PENDING_APPROVAL','APPROVED','ACTIVE')
         AND EXISTS(
            SELECT 1 FROM employment_contract_versions c
            WHERE c.employeeId=NEW.employeeId
              AND c.status IN ('PENDING_APPROVAL','APPROVED','ACTIVE','LEGACY')
              AND c.id!=COALESCE(NEW.replacesContractId,-1)
              AND c.effectiveFromEpochDay<=COALESCE(NEW.effectiveToEpochDay,9223372036854775807)
              AND COALESCE(c.effectiveToEpochDay,9223372036854775807)>=NEW.effectiveFromEpochDay
         )
        BEGIN SELECT RAISE(ABORT,'CONTRACT_OVERLAP'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_contract_versions_overlap_update
        BEFORE UPDATE ON employment_contract_versions
        WHEN NEW.status IN ('PENDING_APPROVAL','APPROVED','ACTIVE')
         AND EXISTS(
            SELECT 1 FROM employment_contract_versions c
            WHERE c.employeeId=NEW.employeeId
              AND c.id!=NEW.id
              AND c.id!=COALESCE(NEW.replacesContractId,-1)
              AND c.status IN ('PENDING_APPROVAL','APPROVED','ACTIVE','LEGACY')
              AND c.effectiveFromEpochDay<=COALESCE(NEW.effectiveToEpochDay,9223372036854775807)
              AND COALESCE(c.effectiveToEpochDay,9223372036854775807)>=NEW.effectiveFromEpochDay
         )
        BEGIN SELECT RAISE(ABORT,'CONTRACT_OVERLAP'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_contract_versions_frozen_update
        BEFORE UPDATE ON employment_contract_versions
        WHEN EXISTS(
            SELECT 1 FROM payroll_payslips p
            WHERE p.contractId=OLD.id
              AND p.status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID','PAID','REVERSED')
        )
         AND (
            NEW.employeeId!=OLD.employeeId OR NEW.contractNumber!=OLD.contractNumber OR
            NEW.contractType!=OLD.contractType OR NEW.effectiveFromEpochDay!=OLD.effectiveFromEpochDay OR
            NEW.effectiveToEpochDay IS NOT OLD.effectiveToEpochDay OR NEW.baseSalaryRial!=OLD.baseSalaryRial OR
            NEW.standardDailyMinutes!=OLD.standardDailyMinutes OR NEW.standardWeeklyMinutes!=OLD.standardWeeklyMinutes OR
            NEW.overtimePolicyId IS NOT OLD.overtimePolicyId OR NEW.payrollPolicyId IS NOT OLD.payrollPolicyId OR
            NEW.jobTitleSnapshot!=OLD.jobTitleSnapshot OR NEW.departmentSnapshot!=OLD.departmentSnapshot OR
            NEW.branchSnapshot!=OLD.branchSnapshot
         )
        BEGIN SELECT RAISE(ABORT,'CONTRACT_VERSION_FROZEN'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_contract_versions_no_delete
        BEFORE DELETE ON employment_contract_versions
        BEGIN SELECT RAISE(ABORT,'CONTRACT_HISTORY_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_attendance_events_no_update
        BEFORE UPDATE ON attendance_events
        BEGIN SELECT RAISE(ABORT,'ATTENDANCE_EVENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_attendance_events_no_delete
        BEFORE DELETE ON attendance_events
        BEGIN SELECT RAISE(ABORT,'ATTENDANCE_EVENT_IMMUTABLE'); END""",
    )
    db.execSQL("DROP TRIGGER IF EXISTS trg_attendance_corrections_controlled_update")
    db.execSQL(
        """CREATE TRIGGER trg_attendance_corrections_controlled_update
        BEFORE UPDATE ON attendance_corrections
        WHEN OLD.status!='SUBMITTED' OR NEW.status NOT IN ('APPROVED','REJECTED')
          OR NEW.approvedByActorId IS NULL OR NEW.approvedAtEpochMillis IS NULL
          OR NEW.employeeId!=OLD.employeeId OR NEW.businessEpochDay!=OLD.businessEpochDay
          OR NEW.beforeSnapshot!=OLD.beforeSnapshot OR NEW.afterSnapshot!=OLD.afterSnapshot
          OR NEW.reason!=OLD.reason OR NEW.requestedByActorId!=OLD.requestedByActorId
          OR NEW.requestedAtEpochMillis!=OLD.requestedAtEpochMillis
          OR NEW.correlationId!=OLD.correlationId
        BEGIN SELECT RAISE(ABORT,'ATTENDANCE_CORRECTION_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_attendance_corrections_no_delete
        BEFORE DELETE ON attendance_corrections
        BEGIN SELECT RAISE(ABORT,'ATTENDANCE_CORRECTION_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_leave_ledger_no_update
        BEFORE UPDATE ON leave_ledger_entries
        BEGIN SELECT RAISE(ABORT,'LEAVE_LEDGER_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_leave_ledger_no_delete
        BEFORE DELETE ON leave_ledger_entries
        BEGIN SELECT RAISE(ABORT,'LEAVE_LEDGER_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_snapshot_no_update
        BEFORE UPDATE ON payroll_snapshots
        BEGIN SELECT RAISE(ABORT,'PAYROLL_SNAPSHOT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_snapshot_no_delete
        BEFORE DELETE ON payroll_snapshots
        BEGIN SELECT RAISE(ABORT,'PAYROLL_SNAPSHOT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_components_frozen_insert
        BEFORE INSERT ON payroll_components
        WHEN EXISTS(SELECT 1 FROM payroll_payslips p WHERE p.id=NEW.payslipId AND p.status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID','PAID','REVERSED'))
        BEGIN SELECT RAISE(ABORT,'PAYROLL_COMPONENTS_FROZEN'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_components_frozen_update
        BEFORE UPDATE ON payroll_components
        WHEN EXISTS(SELECT 1 FROM payroll_payslips p WHERE p.id=OLD.payslipId AND p.status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID','PAID','REVERSED'))
        BEGIN SELECT RAISE(ABORT,'PAYROLL_COMPONENTS_FROZEN'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_components_no_delete
        BEFORE DELETE ON payroll_components
        BEGIN SELECT RAISE(ABORT,'PAYROLL_COMPONENT_HISTORY_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payslip_financial_freeze
        BEFORE UPDATE ON payroll_payslips
        WHEN OLD.status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID','PAID','REVERSED')
         AND (
            NEW.employeeId!=OLD.employeeId OR NEW.periodId!=OLD.periodId OR NEW.revisionNo!=OLD.revisionNo OR
            NEW.contractId IS NOT OLD.contractId OR NEW.grossPayRial!=OLD.grossPayRial OR
            NEW.totalDeductionsRial!=OLD.totalDeductionsRial OR NEW.netPayRial!=OLD.netPayRial OR
            NEW.employeeCodeSnapshot!=OLD.employeeCodeSnapshot OR NEW.employeeNameSnapshot!=OLD.employeeNameSnapshot
         )
        BEGIN SELECT RAISE(ABORT,'PAYSLIP_FINANCIALS_FROZEN'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payslip_no_delete
        BEFORE DELETE ON payroll_payslips
        BEGIN SELECT RAISE(ABORT,'PAYSLIP_HISTORY_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payslip_amounts_insert
        BEFORE INSERT ON payroll_payslips
        WHEN NEW.grossPayRial<0 OR NEW.totalDeductionsRial<0 OR NEW.netPayRial<0 OR NEW.paidAmountRial<0 OR NEW.remainingAmountRial<0
          OR NEW.grossPayRial-NEW.totalDeductionsRial!=NEW.netPayRial
          OR NEW.paidAmountRial+NEW.remainingAmountRial!=NEW.netPayRial
        BEGIN SELECT RAISE(ABORT,'PAYSLIP_AMOUNTS_INVALID'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payslip_amounts_update
        BEFORE UPDATE ON payroll_payslips
        WHEN NEW.grossPayRial<0 OR NEW.totalDeductionsRial<0 OR NEW.netPayRial<0 OR NEW.paidAmountRial<0 OR NEW.remainingAmountRial<0
          OR NEW.grossPayRial-NEW.totalDeductionsRial!=NEW.netPayRial
          OR NEW.paidAmountRial+NEW.remainingAmountRial!=NEW.netPayRial
        BEGIN SELECT RAISE(ABORT,'PAYSLIP_AMOUNTS_INVALID'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payments_validate_insert
        BEFORE INSERT ON payroll_payments
        WHEN NEW.amountRial<=0 OR NEW.paymentEpochDay<=0 OR NEW.status NOT IN ('POSTED','REVERSED','FAILED')
        BEGIN SELECT RAISE(ABORT,'PAYROLL_PAYMENT_INVALID'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payments_financial_freeze
        BEFORE UPDATE ON payroll_payments
        WHEN NEW.payslipId!=OLD.payslipId OR NEW.amountRial!=OLD.amountRial OR
             NEW.treasuryAccountId!=OLD.treasuryAccountId OR NEW.channel!=OLD.channel OR
             NEW.paymentEpochDay!=OLD.paymentEpochDay OR NEW.reversalOfPaymentId IS NOT OLD.reversalOfPaymentId
        BEGIN SELECT RAISE(ABORT,'PAYROLL_PAYMENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_payments_no_delete
        BEFORE DELETE ON payroll_payments
        BEGIN SELECT RAISE(ABORT,'PAYROLL_PAYMENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_allocations_validate_insert
        BEFORE INSERT ON payroll_advance_allocations_v2
        WHEN NEW.amountRial<=0 OR NEW.status NOT IN ('ALLOCATED','REVERSED')
        BEGIN SELECT RAISE(ABORT,'PAYROLL_ADVANCE_ALLOCATION_INVALID'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_allocations_financial_freeze
        BEFORE UPDATE ON payroll_advance_allocations_v2
        WHEN NEW.payslipId!=OLD.payslipId OR NEW.advanceId!=OLD.advanceId OR NEW.amountRial!=OLD.amountRial
        BEGIN SELECT RAISE(ABORT,'PAYROLL_ADVANCE_ALLOCATION_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_allocations_no_delete
        BEFORE DELETE ON payroll_advance_allocations_v2
        BEGIN SELECT RAISE(ABORT,'PAYROLL_ADVANCE_ALLOCATION_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_approval_events_no_update
        BEFORE UPDATE ON payroll_approval_events
        BEGIN SELECT RAISE(ABORT,'PAYROLL_APPROVAL_EVENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_payroll_approval_events_no_delete
        BEFORE DELETE ON payroll_approval_events
        BEGIN SELECT RAISE(ABORT,'PAYROLL_APPROVAL_EVENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_hr_payroll_command_receipts_no_update
        BEFORE UPDATE ON hr_payroll_command_receipts
        BEGIN SELECT RAISE(ABORT,'HR_PAYROLL_COMMAND_RECEIPT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_hr_payroll_command_receipts_no_delete
        BEFORE DELETE ON hr_payroll_command_receipts
        BEGIN SELECT RAISE(ABORT,'HR_PAYROLL_COMMAND_RECEIPT_IMMUTABLE'); END""",
    )
}
