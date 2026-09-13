package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HrPayrollDao {
    @Query("SELECT * FROM employee_private_profiles WHERE employeeId=:employeeId LIMIT 1")
    suspend fun privateProfile(employeeId: Long): EmployeePrivateProfileEntity?

    @Query("SELECT * FROM hr_documents WHERE employeeId=:employeeId AND status='ACTIVE' ORDER BY createdAtEpochMillis DESC,id DESC")
    fun observeHrDocuments(employeeId: Long): Flow<List<HrDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHrDocument(entity: HrDocumentEntity): Long

    @Query("UPDATE hr_documents SET status='ARCHIVED' WHERE id=:id AND status='ACTIVE'")
    suspend fun archiveHrDocument(id: Long): Int

    @Query("SELECT * FROM employee_private_profiles ORDER BY employeeId")
    fun observePrivateProfiles(): Flow<List<EmployeePrivateProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivateProfile(entity: EmployeePrivateProfileEntity)

    @Query("SELECT * FROM employment_assignments WHERE employeeId=:employeeId ORDER BY effectiveFromEpochDay DESC,id DESC")
    fun observeAssignments(employeeId: Long): Flow<List<EmploymentAssignmentEntity>>

    @Query("SELECT * FROM employment_assignments WHERE employeeId=:employeeId ORDER BY effectiveFromEpochDay,id")
    suspend fun assignments(employeeId: Long): List<EmploymentAssignmentEntity>

    /**
     * Employee 360 timeline is a bounded projection over authoritative records. It deliberately
     * does not introduce an employee-events table that could drift from contracts or payroll.
     */
    @Query(
        """
        SELECT 'employee:hire:' || id AS stableKey,
               id AS employeeId,
               hireEpochDay AS businessEpochDay,
               createdAtEpochMillis AS occurredAtEpochMillis,
               'HIRED' AS eventType,
               COALESCE(NULLIF(displayName,''),name) AS title,
               'EMPLOYEE' AS referenceType,
               id AS referenceId
          FROM employees
         WHERE id=:employeeId AND hireEpochDay IS NOT NULL
        UNION ALL
        SELECT 'employee:termination:' || id,
               id,
               terminationEpochDay,
               updatedAtEpochMillis,
               'TERMINATED',
               COALESCE(NULLIF(displayName,''),name),
               'EMPLOYEE',
               id
          FROM employees
         WHERE id=:employeeId AND terminationEpochDay IS NOT NULL
        UNION ALL
        SELECT 'assignment:' || id,
               employeeId,
               effectiveFromEpochDay,
               createdAtEpochMillis,
               'JOB_CHANGED',
               jobTitle || ' / ' || department,
               'EMPLOYMENT_ASSIGNMENT',
               id
          FROM employment_assignments
         WHERE employeeId=:employeeId AND reason!='EMPLOYEE_CREATED'
        UNION ALL
        SELECT 'contract:' || c.id,
               c.employeeId,
               c.effectiveFromEpochDay,
               COALESCE(c.approvedAtEpochMillis,c.createdAtEpochMillis),
               CASE
                   WHEN c.versionNo=1 THEN 'CONTRACT_STARTED'
                   WHEN old.id IS NOT NULL AND old.baseSalaryRial!=c.baseSalaryRial THEN 'SALARY_CHANGED'
                   ELSE 'CONTRACT_CHANGED'
               END,
               c.contractNumber || ' · v' || c.versionNo,
               'EMPLOYMENT_CONTRACT',
               c.id
          FROM employment_contract_versions c
          LEFT JOIN employment_contract_versions old ON old.id=c.replacesContractId
         WHERE c.employeeId=:employeeId
           AND c.status IN ('APPROVED','ACTIVE','SUPERSEDED','LEGACY')
        UNION ALL
        SELECT 'leave:' || id,
               employeeId,
               startEpochDay,
               COALESCE(reviewedAtEpochMillis,createdAtEpochMillis),
               'LEAVE',
               leaveType || ' · ' || status,
               'LEAVE',
               id
          FROM leaves
         WHERE employeeId=:employeeId AND status IN ('APPROVED','TAKEN')
        UNION ALL
        SELECT 'employment_status:' || id,
               entityId,
               businessEpochDay,
               createdAtEpochMillis,
               CASE
                   WHEN afterSnapshot LIKE 'status=ON_LEAVE%' THEN 'LEAVE'
                   WHEN beforeSnapshot LIKE 'status=ON_LEAVE%' AND afterSnapshot LIKE 'status=ACTIVE%' THEN 'RETURNED'
                   ELSE 'EMPLOYMENT_STATUS_CHANGED'
               END,
               description,
               'AUDIT_LOG',
               id
          FROM audit_logs
         WHERE entityType='EMPLOYEE' AND entityId=:employeeId AND action='STATUS_CHANGE'
           AND (afterSnapshot LIKE 'status=ON_LEAVE%' OR beforeSnapshot LIKE 'status=ON_LEAVE%')
        UNION ALL
        SELECT 'payslip:approved:' || p.id,
               p.employeeId,
               period.endEpochDay,
               p.approvedAtEpochMillis,
               'PAYROLL_APPROVED',
               batch.documentNumber || ' · revision ' || p.revisionNo,
               'PAYROLL_PAYSLIP',
               p.id
          FROM payroll_payslips p
          JOIN payroll_periods period ON period.id=p.periodId
          JOIN payroll_batches batch ON batch.id=p.batchId
         WHERE p.employeeId=:employeeId AND p.approvedAtEpochMillis IS NOT NULL
        UNION ALL
        SELECT 'payment:' || payment.id,
               p.employeeId,
               payment.paymentEpochDay,
               payment.createdAtEpochMillis,
               CASE WHEN payment.reversalOfPaymentId IS NULL THEN 'PAYROLL_PAID' ELSE 'PAYROLL_PAYMENT_REVERSED' END,
               payment.paymentReference,
               'PAYROLL_PAYMENT',
               payment.id
          FROM payroll_payments payment
          JOIN payroll_payslips p ON p.id=payment.payslipId
         WHERE p.employeeId=:employeeId
        UNION ALL
        SELECT 'payslip:reversed:' || id,
               employeeId,
               reversalEpochDay,
               reversedAtEpochMillis,
               'PAYROLL_REVERSED',
               COALESCE(reversalReason,'PAYROLL_REVERSAL'),
               'PAYROLL_PAYSLIP',
               id
          FROM payroll_payslips
         WHERE employeeId=:employeeId AND reversalEpochDay IS NOT NULL
        UNION ALL
        SELECT 'advance:' || id,
               employeeId,
               advanceEpochDay,
               createdAtEpochMillis,
               'ADVANCE_CREATED',
               status,
               'EMPLOYEE_ADVANCE',
               id
          FROM employee_advances
         WHERE employeeId=:employeeId
        ORDER BY businessEpochDay DESC, occurredAtEpochMillis DESC, stableKey DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeEmployeeTimeline(
        employeeId: Long,
        limit: Int,
        offset: Int,
    ): Flow<List<EmployeeTimelineRow>>

    @Query("SELECT * FROM employment_assignments WHERE employeeId=:employeeId AND effectiveFromEpochDay<=:businessEpochDay AND COALESCE(effectiveToEpochDay,9223372036854775807)>=:businessEpochDay ORDER BY effectiveFromEpochDay,id")
    suspend fun effectiveAssignments(employeeId: Long, businessEpochDay: Long): List<EmploymentAssignmentEntity>

    @Query("SELECT * FROM employment_assignments WHERE employeeId=:employeeId AND effectiveToEpochDay IS NULL ORDER BY effectiveFromEpochDay DESC,id DESC LIMIT 1")
    suspend fun openAssignment(employeeId: Long): EmploymentAssignmentEntity?

    @Insert
    suspend fun insertAssignment(entity: EmploymentAssignmentEntity): Long

    @Query("UPDATE employment_assignments SET effectiveToEpochDay=:effectiveToEpochDay WHERE id=:id AND effectiveToEpochDay IS NULL AND effectiveFromEpochDay<=:effectiveToEpochDay")
    suspend fun closeAssignment(id: Long, effectiveToEpochDay: Long): Int

    @Query("SELECT * FROM employment_contract_versions WHERE employeeId=:employeeId ORDER BY effectiveFromEpochDay DESC,versionNo DESC,id DESC")
    fun observeContractVersions(employeeId: Long): Flow<List<EmploymentContractVersionEntity>>

    @Query(
        """
        SELECT c.employeeId,c.status
          FROM employment_contract_versions c
         WHERE c.id=(
             SELECT latest.id FROM employment_contract_versions latest
              WHERE latest.employeeId=c.employeeId
              ORDER BY latest.effectiveFromEpochDay DESC,latest.versionNo DESC,latest.id DESC LIMIT 1
         )
         ORDER BY c.employeeId
        """,
    )
    fun observeLatestContractStatuses(): Flow<List<EmployeeContractStatusRow>>

    @Query("SELECT * FROM employment_contract_versions WHERE employeeId=:employeeId ORDER BY effectiveFromEpochDay,versionNo,id")
    suspend fun contractVersions(employeeId: Long): List<EmploymentContractVersionEntity>

    @Query("SELECT * FROM employment_contract_versions WHERE id=:id LIMIT 1")
    suspend fun contractVersion(id: Long): EmploymentContractVersionEntity?

    @Query("SELECT * FROM employment_contract_versions WHERE contractNumber=:contractNumber LIMIT 1")
    suspend fun contractByNumber(contractNumber: String): EmploymentContractVersionEntity?

    @Query("SELECT * FROM employment_contract_versions WHERE employeeId=:employeeId AND status IN ('APPROVED','ACTIVE','SUPERSEDED','LEGACY') AND effectiveFromEpochDay<=:businessEpochDay AND COALESCE(effectiveToEpochDay,9223372036854775807)>=:businessEpochDay ORDER BY effectiveFromEpochDay,versionNo,id")
    suspend fun effectiveContractCandidates(employeeId: Long, businessEpochDay: Long): List<EmploymentContractVersionEntity>

    @Query("SELECT * FROM employment_contract_versions WHERE employeeId IN (:employeeIds) AND status IN ('APPROVED','ACTIVE','SUPERSEDED','LEGACY') AND effectiveFromEpochDay<=:toEpochDay AND COALESCE(effectiveToEpochDay,9223372036854775807)>=:fromEpochDay ORDER BY employeeId,effectiveFromEpochDay,versionNo,id")
    suspend fun contractVersionsForEmployeesInRange(
        employeeIds: List<Long>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): List<EmploymentContractVersionEntity>

    @Query("SELECT * FROM employment_contract_versions WHERE employeeId=:employeeId AND id!=:excludeId AND status NOT IN ('CANCELLED','SUPERSEDED','LEGACY_UNKNOWN') AND effectiveFromEpochDay<=:toEpochDay AND COALESCE(effectiveToEpochDay,9223372036854775807)>=:fromEpochDay ORDER BY effectiveFromEpochDay,id")
    suspend fun overlappingContracts(
        employeeId: Long,
        fromEpochDay: Long,
        toEpochDay: Long,
        excludeId: Long = 0,
    ): List<EmploymentContractVersionEntity>

    @Insert
    suspend fun insertContractVersion(entity: EmploymentContractVersionEntity): Long

    @Query("UPDATE employment_contract_versions SET status='SUPERSEDED' WHERE id=:id AND status IN ('DRAFT','PENDING_APPROVAL','APPROVED','ACTIVE','LEGACY')")
    suspend fun markContractSuperseded(id: Long): Int

    @Query("UPDATE employment_contract_versions SET status='APPROVED',approvedAtEpochMillis=:approvedAt,approvedByActorId=:approvedBy WHERE id=:id AND status='PENDING_APPROVAL'")
    suspend fun approveContract(id: Long, approvedAt: Long, approvedBy: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM payroll_payslips WHERE contractId=:contractId AND status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID','PAID','REVERSED'))")
    suspend fun contractUsedByFrozenPayroll(contractId: Long): Boolean

    @Query("SELECT * FROM attendance_events WHERE idempotencyKey=:idempotencyKey LIMIT 1")
    suspend fun attendanceEventByIdempotencyKey(idempotencyKey: String): AttendanceEventEntity?

    @Insert
    suspend fun insertAttendanceEvent(entity: AttendanceEventEntity): Long

    @Query("SELECT * FROM attendance_events WHERE employeeId=:employeeId AND businessEpochDay=:businessEpochDay ORDER BY timestampEpochMillis,id")
    suspend fun attendanceEventsForDay(employeeId: Long, businessEpochDay: Long): List<AttendanceEventEntity>

    @Query("SELECT * FROM attendance_events WHERE employeeId=:employeeId AND businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY businessEpochDay,timestampEpochMillis,id")
    suspend fun attendanceEventsInRange(employeeId: Long, fromEpochDay: Long, toEpochDay: Long): List<AttendanceEventEntity>

    @Query("SELECT * FROM attendance_events WHERE employeeId IN (:employeeIds) AND businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY employeeId,businessEpochDay,timestampEpochMillis,id")
    suspend fun attendanceEventsForEmployeesInRange(
        employeeIds: List<Long>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): List<AttendanceEventEntity>

    @Query("SELECT * FROM attendance_events WHERE employeeId=:employeeId ORDER BY businessEpochDay DESC,timestampEpochMillis DESC,id DESC LIMIT :limit OFFSET :offset")
    suspend fun attendanceEventPage(employeeId: Long, limit: Int, offset: Int): List<AttendanceEventEntity>

    @Query("SELECT * FROM attendance_events WHERE employeeId=:employeeId AND eventType IN ('CLOCK_IN','CLOCK_OUT') ORDER BY timestampEpochMillis DESC,id DESC LIMIT 1")
    suspend fun latestAttendanceClockEvent(employeeId: Long): AttendanceEventEntity?

    @Query("SELECT * FROM attendance_events WHERE employeeId=:employeeId ORDER BY timestampEpochMillis DESC,id DESC LIMIT :limit")
    fun observeAttendanceEvents(employeeId: Long, limit: Int): Flow<List<AttendanceEventEntity>>

    @Query("SELECT * FROM attendance_corrections WHERE idempotencyKey=:idempotencyKey LIMIT 1")
    suspend fun attendanceCorrectionByKey(idempotencyKey: String): AttendanceCorrectionEntity?

    @Insert
    suspend fun insertAttendanceCorrection(entity: AttendanceCorrectionEntity): Long

    @Query("UPDATE attendance_corrections SET status='APPROVED',approvedByActorId=:actorId,approvedAtEpochMillis=:approvedAt WHERE id=:id AND status='SUBMITTED'")
    suspend fun approveAttendanceCorrection(id: Long, actorId: Long, approvedAt: Long): Int

    @Query("UPDATE attendance_corrections SET status='REJECTED',approvedByActorId=:actorId,approvedAtEpochMillis=:reviewedAt WHERE id=:id AND status='SUBMITTED'")
    suspend fun rejectAttendanceCorrection(id: Long, actorId: Long, reviewedAt: Long): Int

    @Query("SELECT * FROM attendance_corrections WHERE status='SUBMITTED' ORDER BY requestedAtEpochMillis DESC,id DESC")
    fun observePendingAttendanceCorrections(): Flow<List<AttendanceCorrectionEntity>>

    @Query("SELECT * FROM attendance_corrections WHERE employeeId=:employeeId ORDER BY businessEpochDay DESC,requestedAtEpochMillis DESC LIMIT :limit OFFSET :offset")
    suspend fun attendanceCorrectionPage(employeeId: Long, limit: Int, offset: Int): List<AttendanceCorrectionEntity>

    @Query("SELECT * FROM attendance_corrections WHERE employeeId=:employeeId AND businessEpochDay=:businessEpochDay AND status='APPROVED' ORDER BY approvedAtEpochMillis DESC,id DESC LIMIT 1")
    suspend fun latestApprovedAttendanceCorrection(employeeId: Long, businessEpochDay: Long): AttendanceCorrectionEntity?

    @Query("SELECT * FROM attendance_corrections WHERE employeeId IN (:employeeIds) AND businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND status='APPROVED' ORDER BY employeeId,businessEpochDay,approvedAtEpochMillis DESC,id DESC")
    suspend fun approvedAttendanceCorrectionsForEmployeesInRange(
        employeeIds: List<Long>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): List<AttendanceCorrectionEntity>

    @Query("SELECT * FROM attendance_corrections WHERE id=:id LIMIT 1")
    suspend fun attendanceCorrection(id: Long): AttendanceCorrectionEntity?


    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOvertimeApproval(entity: OvertimeApprovalEntity): Long

    @Query("SELECT * FROM overtime_approvals WHERE employeeId=:employeeId AND businessEpochDay=:businessEpochDay LIMIT 1")
    suspend fun overtimeApproval(employeeId: Long, businessEpochDay: Long): OvertimeApprovalEntity?

    @Query("SELECT * FROM overtime_approvals WHERE id=:id LIMIT 1")
    suspend fun overtimeApprovalById(id: Long): OvertimeApprovalEntity?

    @Query("SELECT * FROM overtime_approvals WHERE status='PENDING' ORDER BY businessEpochDay DESC,id DESC")
    fun observePendingOvertimeApprovals(): Flow<List<OvertimeApprovalEntity>>

    @Query("SELECT * FROM overtime_approvals WHERE employeeId IN (:employeeIds) AND businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND status='APPROVED'")
    suspend fun approvedOvertimeForEmployeesInRange(
        employeeIds: List<Long>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): List<OvertimeApprovalEntity>

    @Query("UPDATE overtime_approvals SET approvedMinutes=:approvedMinutes,rejectedMinutes=rawMinutes-:approvedMinutes,status=:status,reason=:reason,reviewedByActorId=:actorId,reviewedAtEpochMillis=:now WHERE id=:id AND status='PENDING'")
    suspend fun reviewOvertimeApproval(
        id: Long,
        approvedMinutes: Int,
        status: String,
        reason: String,
        actorId: Long,
        now: Long,
    ): Int

    @Query("UPDATE overtime_approvals SET commandId=:commandId,rawMinutes=:rawMinutes,approvedMinutes=0,rejectedMinutes=0,status='PENDING',reason='در انتظار تأیید اضافه‌کار',requestedByActorId=:actorId,reviewedByActorId=NULL,requestedAtEpochMillis=:now,reviewedAtEpochMillis=NULL,correlationId=:correlationId WHERE id=:id")
    suspend fun reopenOvertimeApproval(id: Long, commandId: String, rawMinutes: Int, actorId: Long, now: Long, correlationId: String): Int

    @Query("SELECT * FROM leave_ledger_entries WHERE idempotencyKey=:idempotencyKey LIMIT 1")
    suspend fun leaveLedgerByIdempotencyKey(idempotencyKey: String): LeaveLedgerEntryEntity?

    @Insert
    suspend fun insertLeaveLedgerEntry(entity: LeaveLedgerEntryEntity): Long

    @Query("SELECT * FROM leave_ledger_entries WHERE employeeId=:employeeId AND leaveType=:leaveType ORDER BY businessEpochDay,id")
    suspend fun leaveLedger(employeeId: Long, leaveType: String): List<LeaveLedgerEntryEntity>

    @Query("SELECT * FROM payroll_periods ORDER BY startEpochDay DESC,id DESC")
    fun observePayrollPeriods(): Flow<List<PayrollPeriodEntity>>

    @Query("SELECT * FROM payroll_periods WHERE id=:id LIMIT 1")
    suspend fun payrollPeriod(id: Long): PayrollPeriodEntity?

    @Query("SELECT * FROM payroll_periods WHERE periodKey=:periodKey LIMIT 1")
    suspend fun payrollPeriodByKey(periodKey: String): PayrollPeriodEntity?

    @Insert
    suspend fun insertPayrollPeriod(entity: PayrollPeriodEntity): Long

    @Query("UPDATE payroll_periods SET status=:toStatus,rowVersion=rowVersion+1,closedAtEpochMillis=:closedAt,reopenedAtEpochMillis=:reopenedAt WHERE id=:id AND status=:fromStatus AND rowVersion=:expectedVersion")
    suspend fun transitionPayrollPeriod(
        id: Long,
        fromStatus: String,
        toStatus: String,
        expectedVersion: Long,
        closedAt: Long?,
        reopenedAt: Long?,
    ): Int

    @Query("SELECT * FROM payroll_batches ORDER BY id DESC")
    fun observePayrollBatches(): Flow<List<PayrollBatchEntity>>

    @Query("""
        SELECT b.*,
          (SELECT COUNT(*) FROM payroll_payslips p WHERE p.batchId=b.id) AS employeesIncluded,
          COALESCE((SELECT SUM(p.grossPayRial) FROM payroll_payslips p WHERE p.batchId=b.id),0) AS grossPayrollRial,
          COALESCE((SELECT SUM(p.totalDeductionsRial) FROM payroll_payslips p WHERE p.batchId=b.id),0) AS deductionsRial,
          COALESCE((SELECT SUM(p.netPayRial) FROM payroll_payslips p WHERE p.batchId=b.id),0) AS netPayrollRial,
          COALESCE((SELECT SUM(p.paidAmountRial) FROM payroll_payslips p WHERE p.batchId=b.id),0) AS paidRial,
          COALESCE((SELECT SUM(p.remainingAmountRial) FROM payroll_payslips p WHERE p.batchId=b.id),0) AS remainingRial,
          (SELECT COUNT(*) FROM payroll_exceptions x WHERE x.batchId=b.id AND x.resolvedAtEpochMillis IS NULL) AS exceptionCount
        FROM payroll_batches b
        ORDER BY b.id DESC
    """)
    fun observePayrollBatchDashboard(): Flow<List<PayrollBatchDashboardRow>>

    @Query("SELECT * FROM payroll_batches WHERE id=:id LIMIT 1")
    suspend fun payrollBatch(id: Long): PayrollBatchEntity?

    @Query("SELECT * FROM payroll_batches WHERE periodId=:periodId ORDER BY id")
    suspend fun payrollBatchesForPeriod(periodId: Long): List<PayrollBatchEntity>

    @Query("SELECT * FROM payroll_batches WHERE idempotencyKey=:key LIMIT 1")
    suspend fun payrollBatchByIdempotencyKey(key: String): PayrollBatchEntity?

    @Insert
    suspend fun insertPayrollBatch(entity: PayrollBatchEntity): Long

    @Query("UPDATE payroll_batches SET status=:toStatus,rowVersion=rowVersion+1,calculatedByActorId=COALESCE(:calculatedBy,calculatedByActorId),calculatedAtEpochMillis=COALESCE(:calculatedAt,calculatedAtEpochMillis),reviewedByActorId=COALESCE(:reviewedBy,reviewedByActorId),reviewedAtEpochMillis=COALESCE(:reviewedAt,reviewedAtEpochMillis),approvedByActorId=COALESCE(:approvedBy,approvedByActorId),approvedAtEpochMillis=COALESCE(:approvedAt,approvedAtEpochMillis),accrualJournalEntryId=COALESCE(:accrualJournalId,accrualJournalEntryId),reversalJournalEntryId=COALESCE(:reversalJournalId,reversalJournalEntryId) WHERE id=:id AND status=:fromStatus AND rowVersion=:expectedVersion")
    suspend fun transitionPayrollBatch(
        id: Long,
        fromStatus: String,
        toStatus: String,
        expectedVersion: Long,
        calculatedBy: Long? = null,
        calculatedAt: Long? = null,
        reviewedBy: Long? = null,
        reviewedAt: Long? = null,
        approvedBy: Long? = null,
        approvedAt: Long? = null,
        accrualJournalId: Long? = null,
        reversalJournalId: Long? = null,
    ): Int

    @Query("SELECT * FROM payroll_payslips WHERE id=:id LIMIT 1")
    suspend fun payrollPayslip(id: Long): PayrollPayslipEntity?

    @Query("SELECT * FROM payroll_payslips WHERE globalId=:globalId LIMIT 1")
    suspend fun payrollPayslipByGlobalId(globalId: String): PayrollPayslipEntity?

    @Query("SELECT * FROM payroll_payslips WHERE batchId=:batchId ORDER BY employeeNameSnapshot,employeeId,revisionNo")
    suspend fun batchPayslips(batchId: Long): List<PayrollPayslipEntity>

    @Query("SELECT * FROM payroll_payslips WHERE employeeId=:employeeId ORDER BY periodId DESC,revisionNo DESC,id DESC LIMIT :limit OFFSET :offset")
    suspend fun employeePayslipPage(employeeId: Long, limit: Int, offset: Int): List<PayrollPayslipEntity>

    @Query("SELECT * FROM payroll_payslips WHERE employeeId=:employeeId ORDER BY periodId DESC,revisionNo DESC,id DESC")
    fun observeEmployeePayslips(employeeId: Long): Flow<List<PayrollPayslipEntity>>

    @Query("SELECT * FROM payroll_payslips WHERE employeeId=:employeeId ORDER BY periodId DESC,revisionNo DESC,id DESC LIMIT :limit OFFSET :offset")
    fun observeEmployeePayslipPage(
        employeeId: Long,
        limit: Int,
        offset: Int,
    ): Flow<List<PayrollPayslipEntity>>

    @Query("SELECT * FROM payroll_payslips WHERE employeeId=:employeeId AND periodId=:periodId ORDER BY revisionNo DESC,id DESC LIMIT 1")
    suspend fun latestPayslipForEmployeePeriod(employeeId: Long, periodId: Long): PayrollPayslipEntity?

    @Query("SELECT * FROM payroll_payslips WHERE employeeId IN (:employeeIds) AND periodId=:periodId ORDER BY employeeId,revisionNo DESC,id DESC")
    suspend fun payslipsForEmployeesPeriod(
        employeeIds: List<Long>,
        periodId: Long,
    ): List<PayrollPayslipEntity>

    @Insert
    suspend fun insertPayrollPayslip(entity: PayrollPayslipEntity): Long

    @Query("UPDATE payroll_payslips SET status=:toStatus,rowVersion=rowVersion+1,approvedAtEpochMillis=COALESCE(:approvedAt,approvedAtEpochMillis),paidAtEpochMillis=COALESCE(:paidAt,paidAtEpochMillis),reversalReason=COALESCE(:reversalReason,reversalReason),reversalEpochDay=COALESCE(:reversalEpochDay,reversalEpochDay),reversedAtEpochMillis=COALESCE(:reversedAt,reversedAtEpochMillis) WHERE id=:id AND status=:fromStatus AND rowVersion=:expectedVersion")
    suspend fun transitionPayrollPayslip(
        id: Long,
        fromStatus: String,
        toStatus: String,
        expectedVersion: Long,
        approvedAt: Long? = null,
        paidAt: Long? = null,
        reversalReason: String? = null,
        reversalEpochDay: Long? = null,
        reversedAt: Long? = null,
    ): Int

    @Query("UPDATE payroll_payslips SET paidAmountRial=:paidAmount,remainingAmountRial=:remainingAmount,status=:status,paidAtEpochMillis=:paidAt,rowVersion=rowVersion+1 WHERE id=:id AND rowVersion=:expectedVersion")
    suspend fun updatePayslipPaymentProjection(
        id: Long,
        paidAmount: Long,
        remainingAmount: Long,
        status: String,
        paidAt: Long?,
        expectedVersion: Long,
    ): Int

    @Query("UPDATE payroll_payslips SET accrualJournalEntryId=:journalEntryId,rowVersion=rowVersion+1 WHERE id=:id AND status='UNDER_REVIEW' AND accrualJournalEntryId IS NULL AND rowVersion=:expectedVersion")
    suspend fun attachPayslipAccrualJournal(
        id: Long,
        journalEntryId: Long,
        expectedVersion: Long,
    ): Int

    @Query("UPDATE payroll_payslips SET reversalJournalEntryId=:journalEntryId,rowVersion=rowVersion+1 WHERE id=:id AND status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID','PAID') AND reversalJournalEntryId IS NULL AND rowVersion=:expectedVersion")
    suspend fun attachPayslipReversalJournal(
        id: Long,
        journalEntryId: Long,
        expectedVersion: Long,
    ): Int

    @Insert
    suspend fun insertPayrollSnapshot(entity: PayrollSnapshotEntity)

    @Query("SELECT * FROM payroll_snapshots WHERE payslipId=:payslipId LIMIT 1")
    suspend fun payrollSnapshot(payslipId: Long): PayrollSnapshotEntity?

    @Query("SELECT s.* FROM payroll_snapshots s INNER JOIN payroll_payslips p ON p.id=s.payslipId WHERE p.batchId=:batchId ORDER BY s.payslipId")
    suspend fun payrollSnapshotsForBatch(batchId: Long): List<PayrollSnapshotEntity>

    @Insert
    suspend fun insertPayrollComponents(entities: List<PayrollComponentEntity>)

    @Query("SELECT * FROM payroll_components WHERE payslipId=:payslipId ORDER BY direction,id")
    suspend fun payrollComponents(payslipId: Long): List<PayrollComponentEntity>

    @Query("SELECT c.* FROM payroll_components c INNER JOIN payroll_payslips p ON p.id=c.payslipId WHERE p.batchId=:batchId ORDER BY c.payslipId,c.direction,c.id")
    suspend fun payrollComponentsForBatch(batchId: Long): List<PayrollComponentEntity>

    @Query("SELECT * FROM payroll_manual_adjustments WHERE idempotencyKey=:key LIMIT 1")
    suspend fun manualAdjustmentByKey(key: String): PayrollManualAdjustmentEntity?

    @Query("SELECT * FROM payroll_manual_adjustments WHERE id=:id LIMIT 1")
    suspend fun manualAdjustment(id: Long): PayrollManualAdjustmentEntity?

    @Insert
    suspend fun insertManualAdjustment(entity: PayrollManualAdjustmentEntity): Long

    @Query("SELECT * FROM payroll_manual_adjustments WHERE employeeId=:employeeId AND periodId=:periodId AND status='APPROVED' ORDER BY id")
    suspend fun approvedManualAdjustments(employeeId: Long, periodId: Long): List<PayrollManualAdjustmentEntity>

    @Query("SELECT * FROM payroll_manual_adjustments WHERE employeeId IN (:employeeIds) AND periodId=:periodId AND status='APPROVED' AND consumedByPayslipId IS NULL ORDER BY employeeId,id")
    suspend fun approvedManualAdjustmentsForEmployees(
        employeeIds: List<Long>,
        periodId: Long,
    ): List<PayrollManualAdjustmentEntity>

    @Query("SELECT * FROM payroll_manual_adjustments WHERE employeeId IN (:employeeIds) AND periodId=:periodId AND status='SUBMITTED' ORDER BY employeeId,id")
    suspend fun submittedManualAdjustmentsForEmployees(
        employeeIds: List<Long>,
        periodId: Long,
    ): List<PayrollManualAdjustmentEntity>

    @Query("SELECT * FROM payroll_manual_adjustments WHERE periodId=:periodId ORDER BY status,employeeId,id")
    suspend fun manualAdjustmentsForPeriod(periodId: Long): List<PayrollManualAdjustmentEntity>

    @Query("UPDATE payroll_manual_adjustments SET status='APPROVED',approvedByActorId=:actorId,approvedAtEpochMillis=:approvedAt WHERE id=:id AND status='SUBMITTED' AND createdByActorId!=:actorId")
    suspend fun approveManualAdjustment(id: Long, actorId: Long, approvedAt: Long): Int

    @Query("UPDATE payroll_manual_adjustments SET status='CONSUMED',consumedByPayslipId=:payslipId WHERE id IN (:ids) AND status='APPROVED' AND consumedByPayslipId IS NULL")
    suspend fun consumeManualAdjustments(ids: List<Long>, payslipId: Long): Int

    @Insert
    suspend fun insertApprovalEvent(entity: PayrollApprovalEventEntity): Long

    @Query("SELECT * FROM payroll_approval_events WHERE payslipId=:payslipId OR (payslipId IS NULL AND batchId=:batchId) ORDER BY createdAtEpochMillis,id")
    suspend fun payrollApprovalHistory(payslipId: Long, batchId: Long): List<PayrollApprovalEventEntity>

    @Query("SELECT * FROM payroll_payments WHERE id=:id LIMIT 1")
    suspend fun payrollPayment(id: Long): PayrollPaymentEntity?

    @Query("SELECT * FROM payroll_payments WHERE idempotencyKey=:key LIMIT 1")
    suspend fun payrollPaymentByKey(key: String): PayrollPaymentEntity?

    @Insert
    suspend fun insertPayrollPayment(entity: PayrollPaymentEntity): Long

    @Query("SELECT * FROM payroll_payments WHERE payslipId=:payslipId ORDER BY paymentEpochDay,id")
    suspend fun payrollPayments(payslipId: Long): List<PayrollPaymentEntity>

    @Query("SELECT COALESCE(SUM(amountRial),0) FROM payroll_payments WHERE payslipId=:payslipId AND status='POSTED' AND reversalOfPaymentId IS NULL")
    suspend fun postedPaymentTotal(payslipId: Long): Long

    @Query("UPDATE payroll_payments SET status='REVERSED',reversedAtEpochMillis=:reversedAt,reversalReason=:reason WHERE id=:id AND status='POSTED' AND reversalOfPaymentId IS NULL")
    suspend fun markPaymentReversed(id: Long, reversedAt: Long, reason: String): Int

    @Query("SELECT * FROM payroll_advance_allocations_v2 WHERE idempotencyKey=:key LIMIT 1")
    suspend fun advanceAllocationByKey(key: String): PayrollAdvanceAllocationV2Entity?

    @Insert
    suspend fun insertAdvanceAllocation(entity: PayrollAdvanceAllocationV2Entity): Long

    @Query("SELECT * FROM payroll_advance_allocations_v2 WHERE payslipId=:payslipId ORDER BY id")
    suspend fun payslipAdvanceAllocations(payslipId: Long): List<PayrollAdvanceAllocationV2Entity>

    @Query("SELECT COALESCE(SUM(amountRial),0) FROM payroll_advance_allocations_v2 WHERE advanceId=:advanceId AND status='ALLOCATED'")
    suspend fun allocatedAdvanceTotal(advanceId: Long): Long

    @Query("UPDATE payroll_advance_allocations_v2 SET status='REVERSED',reversedAtEpochMillis=:reversedAt,reversalReason=:reason WHERE id=:id AND status='ALLOCATED'")
    suspend fun reverseAdvanceAllocation(id: Long, reversedAt: Long, reason: String): Int

    @Insert
    suspend fun insertPayrollExceptions(entities: List<PayrollExceptionEntity>)

    @Query("DELETE FROM payroll_exceptions WHERE batchId=:batchId AND resolvedAtEpochMillis IS NULL")
    suspend fun clearUnresolvedPayrollExceptions(batchId: Long): Int

    @Query("SELECT * FROM payroll_exceptions WHERE batchId=:batchId AND resolvedAtEpochMillis IS NULL ORDER BY blocking DESC,code,employeeId")
    suspend fun unresolvedPayrollExceptions(batchId: Long): List<PayrollExceptionEntity>

    @Insert
    suspend fun insertMigrationAnomaly(entity: HrPayrollMigrationAnomalyEntity): Long

    @Query("SELECT * FROM hr_payroll_migration_anomalies ORDER BY entityType,entityId,id")
    suspend fun migrationAnomalies(): List<HrPayrollMigrationAnomalyEntity>

    @Query("SELECT * FROM hr_payroll_command_receipts WHERE idempotencyKey=:key LIMIT 1")
    suspend fun commandReceipt(key: String): HrPayrollCommandReceiptEntity?

    @Insert
    suspend fun insertCommandReceipt(entity: HrPayrollCommandReceiptEntity): Long
}

data class PayrollBatchDashboardRow(
    @Embedded val batch: PayrollBatchEntity,
    val employeesIncluded: Int,
    val grossPayrollRial: Long,
    val deductionsRial: Long,
    val netPayrollRial: Long,
    val paidRial: Long,
    val remainingRial: Long,
    val exceptionCount: Int,
)

data class EmployeeTimelineRow(
    val stableKey: String,
    val employeeId: Long,
    val businessEpochDay: Long,
    val occurredAtEpochMillis: Long?,
    val eventType: String,
    val title: String,
    val referenceType: String,
    val referenceId: Long,
)

data class EmployeeContractStatusRow(
    val employeeId: Long,
    val status: String,
)
