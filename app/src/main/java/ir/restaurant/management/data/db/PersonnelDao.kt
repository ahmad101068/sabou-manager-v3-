package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface PersonnelDao {
    @Query("SELECT * FROM payroll_policies ORDER BY effectiveFromEpochDay DESC, id DESC")
    fun observePayrollPolicies(): Flow<List<PayrollPolicyEntity>>

    @Insert
    suspend fun insertPayrollPolicy(entity: PayrollPolicyEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM payroll_policies WHERE effectiveFromEpochDay <= :toEpochDay AND COALESCE(effectiveToEpochDay, 9223372036854775807) >= :fromEpochDay)")
    suspend fun payrollPolicyOverlaps(fromEpochDay: Long, toEpochDay: Long): Boolean

    @Query("SELECT * FROM payroll_policies WHERE effectiveToEpochDay IS NULL AND effectiveFromEpochDay < :newFromEpochDay ORDER BY effectiveFromEpochDay DESC, id DESC LIMIT 1")
    suspend fun openPayrollPolicyBefore(newFromEpochDay: Long): PayrollPolicyEntity?

    @Query("UPDATE payroll_policies SET effectiveToEpochDay = :toEpochDay WHERE id = :id AND effectiveToEpochDay IS NULL AND effectiveFromEpochDay <= :toEpochDay")
    suspend fun closeOpenPayrollPolicy(id: Long, toEpochDay: Long): Int

    @Query("SELECT * FROM payroll_policies WHERE effectiveFromEpochDay <= :fromEpochDay AND COALESCE(effectiveToEpochDay, 9223372036854775807) >= :toEpochDay ORDER BY effectiveFromEpochDay DESC, id DESC LIMIT 1")
    suspend fun payrollPolicyForRange(fromEpochDay: Long, toEpochDay: Long): PayrollPolicyEntity?

    @Query("SELECT * FROM payroll_policies WHERE id=:id LIMIT 1")
    suspend fun payrollPolicyById(id: Long): PayrollPolicyEntity?

    @Query("SELECT * FROM payroll_policies ORDER BY effectiveFromEpochDay,id")
    suspend fun payrollPolicySnapshot(): List<PayrollPolicyEntity>

    @Insert
    suspend fun insertEmployee(entity: EmployeeEntity): Long

    @Update
    suspend fun updateEmployee(entity: EmployeeEntity): Int

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun employeeById(id: Long): EmployeeEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM employees WHERE employeeCode = :employeeCode)")
    suspend fun employeeCodeExists(employeeCode: String): Boolean

    @Query("SELECT * FROM employees WHERE id IN (:ids) ORDER BY id")
    suspend fun employeesByIds(ids: List<Long>): List<EmployeeEntity>

    @Query("SELECT * FROM employees ORDER BY status, name")
    fun observeEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM attendance ORDER BY workEpochDay DESC, id DESC LIMIT 500")
    fun observeAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE id = :id LIMIT 1")
    suspend fun attendanceById(id: Long): AttendanceEntity?

    @Insert
    suspend fun insertAttendance(entity: AttendanceEntity): Long

    @Update
    suspend fun updateAttendance(entity: AttendanceEntity): Int

    @Query("SELECT * FROM attendance WHERE employeeId = :employeeId AND workEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY workEpochDay")
    suspend fun attendanceInRange(employeeId: Long, startEpochDay: Long, endEpochDay: Long): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE employeeId IN (:employeeIds) AND workEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY employeeId,workEpochDay")
    suspend fun attendanceForEmployeesInRange(
        employeeIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): List<AttendanceEntity>

    @Insert
    suspend fun insertLeave(entity: LeaveEntity): Long

    @Update
    suspend fun updateLeave(entity: LeaveEntity): Int

    @Query("SELECT * FROM leaves WHERE id = :id LIMIT 1")
    suspend fun leaveById(id: Long): LeaveEntity?

    @Query("SELECT * FROM leaves WHERE employeeId IN (:employeeIds) AND status IN ('APPROVED','TAKEN') AND startEpochDay<=:endEpochDay AND endEpochDay>=:startEpochDay ORDER BY employeeId,startEpochDay,id")
    suspend fun approvedLeavesForEmployeesInRange(
        employeeIds: List<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
    ): List<LeaveEntity>

    @Query("SELECT * FROM leaves ORDER BY createdAtEpochMillis DESC, id DESC LIMIT 500")
    fun observeLeaves(): Flow<List<LeaveEntity>>

    @Query("SELECT * FROM leaves WHERE status IN ('PENDING','SUBMITTED') ORDER BY createdAtEpochMillis, id LIMIT 500")
    fun observePendingLeaves(): Flow<List<LeaveEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM leaves WHERE employeeId = :employeeId AND status IN ('PENDING','SUBMITTED','APPROVED') AND id != :excludeId AND startEpochDay <= :endEpochDay AND endEpochDay >= :startEpochDay)")
    suspend fun hasLeaveOverlap(employeeId: Long, startEpochDay: Long, endEpochDay: Long, excludeId: Long = 0): Boolean

    @Query("SELECT * FROM attendance WHERE employeeId = :employeeId AND workEpochDay = :workEpochDay LIMIT 1")
    suspend fun attendanceByEmployeeDay(employeeId: Long, workEpochDay: Long): AttendanceEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM payroll_runs WHERE employeeId = :employeeId AND status = 'PAID' AND periodStartEpochDay > 0 AND :workEpochDay BETWEEN periodStartEpochDay AND periodEndEpochDay)")
    suspend fun attendanceDayLocked(employeeId: Long, workEpochDay: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM payroll_runs WHERE employeeId = :employeeId AND status = 'PAID' AND periodStartEpochDay > 0 AND periodStartEpochDay <= :endEpochDay AND periodEndEpochDay >= :startEpochDay)")
    suspend fun attendanceRangeLocked(employeeId: Long, startEpochDay: Long, endEpochDay: Long): Boolean

    @Query("SELECT * FROM employee_contracts WHERE employeeId = :employeeId ORDER BY startEpochDay DESC, id DESC")
    fun observeContracts(employeeId: Long): Flow<List<EmployeeContractEntity>>

    @Insert
    suspend fun insertContract(entity: EmployeeContractEntity): Long

    @Update
    suspend fun updateContract(entity: EmployeeContractEntity): Int

    @Query("SELECT * FROM employee_contracts WHERE id = :id LIMIT 1")
    suspend fun contractById(id: Long): EmployeeContractEntity?

    @Query("""SELECT * FROM employee_contracts WHERE employeeId = :employeeId AND status = 'ACTIVE' AND startEpochDay <= :periodEndEpochDay AND (endEpochDay IS NULL OR endEpochDay >= :periodStartEpochDay) ORDER BY startEpochDay DESC, id DESC LIMIT 1""")
    suspend fun effectiveContract(employeeId: Long, periodStartEpochDay: Long, periodEndEpochDay: Long): EmployeeContractEntity?

    @Query("SELECT * FROM employee_advances WHERE employeeId = :employeeId ORDER BY advanceEpochDay DESC, id DESC")
    fun observeAdvances(employeeId: Long): Flow<List<EmployeeAdvanceEntity>>

    @Query("SELECT * FROM employee_advances WHERE status = 'OPEN' ORDER BY advanceEpochDay DESC, id DESC")
    fun observeOpenAdvances(): Flow<List<EmployeeAdvanceEntity>>

    @Query("SELECT * FROM employee_advances WHERE employeeId = :employeeId AND status = 'OPEN' AND settledAmountRial < amountRial ORDER BY advanceEpochDay, id")
    suspend fun openAdvancesByEmployee(employeeId: Long): List<EmployeeAdvanceEntity>

    @Query("SELECT * FROM employee_advances WHERE employeeId IN (:employeeIds) AND status = 'OPEN' AND settledAmountRial < amountRial ORDER BY employeeId,advanceEpochDay,id")
    suspend fun openAdvancesForEmployees(employeeIds: List<Long>): List<EmployeeAdvanceEntity>

    @Query("UPDATE employee_advances SET settledAmountRial=settledAmountRial+:amountRial,status=CASE WHEN settledAmountRial+:amountRial=amountRial THEN 'SETTLED' ELSE 'OPEN' END,updatedAtEpochMillis=:now WHERE id=:id AND status='OPEN' AND :amountRial>0 AND settledAmountRial+:amountRial<=amountRial")
    suspend fun allocateAdvance(id: Long, amountRial: Long, now: Long): Int

    @Query("UPDATE employee_advances SET settledAmountRial=settledAmountRial-:amountRial,status='OPEN',updatedAtEpochMillis=:now WHERE id=:id AND :amountRial>0 AND settledAmountRial>=:amountRial")
    suspend fun restoreAdvanceAllocation(id: Long, amountRial: Long, now: Long): Int

    @Insert
    suspend fun insertAdvance(entity: EmployeeAdvanceEntity): Long

    @Update
    suspend fun updateAdvance(entity: EmployeeAdvanceEntity): Int

    @Query("SELECT * FROM employee_advances WHERE id = :id LIMIT 1")
    suspend fun advanceById(id: Long): EmployeeAdvanceEntity?

    @Query("UPDATE employees SET status = 'ARCHIVED', updatedAtEpochMillis = :now WHERE id = :id AND status IN ('APPLICANT','ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')")
    suspend fun deactivateEmployee(id: Long, now: Long): Int

    @Query("UPDATE employees SET status=:status,terminationEpochDay=:terminationEpochDay,updatedAtEpochMillis=:now,updatedByActorId=:actorId WHERE id=:id AND status=:expectedStatus")
    suspend fun transitionEmployeeStatus(
        id: Long,
        expectedStatus: String,
        status: String,
        terminationEpochDay: Long?,
        now: Long,
        actorId: Long,
    ): Int

    @Query("SELECT EXISTS(SELECT 1 FROM payroll_runs WHERE employeeId = :employeeId AND periodYear = :year AND periodMonth = :month AND status IN ('PENDING_APPROVAL','PAID'))")
    suspend fun payrollExists(employeeId: Long, year: Int, month: Int): Boolean

    @Query("SELECT * FROM payroll_runs WHERE id = :payrollId LIMIT 1")
    suspend fun payrollById(payrollId: Long): PayrollRunEntity?

    @Query("SELECT * FROM payroll_runs WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun payrollByJournalEntryId(journalEntryId: Long): PayrollRunEntity?

    @Query("SELECT * FROM payroll_runs WHERE globalId = :globalId LIMIT 1")
    suspend fun payrollByGlobalId(globalId: String): PayrollRunEntity?

    @Insert
    suspend fun insertPayroll(entity: PayrollRunEntity): Long

    @Query("UPDATE payroll_runs SET status='PAID',approvedBy=:approvedBy,approvedByActorId=:approvedByActorId,approvedAtEpochMillis=:approvedAt WHERE id=:payrollId AND status='PENDING_APPROVAL'")
    suspend fun approvePayroll(
        payrollId: Long,
        approvedBy: String,
        approvedByActorId: Long,
        approvedAt: Long,
    ): Int

    @Insert
    suspend fun insertPayrollAdvanceAllocations(entities: List<PayrollAdvanceAllocationEntity>)

    @Query("SELECT * FROM payroll_advance_allocations WHERE payrollId = :payrollId ORDER BY advanceId")
    suspend fun payrollAdvanceAllocations(payrollId: Long): List<PayrollAdvanceAllocationEntity>

    @Query("UPDATE payroll_runs SET status = 'REVERSED', reversalEpochDay = :reversalEpochDay, reversalReason = :reason, reversalJournalEntryId = :reversalJournalEntryId, reversedBy = :reversedBy WHERE id = :payrollId AND status = 'PAID'")
    suspend fun markPayrollReversed(payrollId: Long, reversalEpochDay: Long, reason: String, reversalJournalEntryId: Long, reversedBy: String): Int

    @Query("""
        SELECT p.id, p.employeeId, e.name AS employeeName, p.periodYear, p.periodMonth, p.revisionNo,
               (p.baseSalaryRial + p.overtimeRial + p.bonusRial) AS grossPayRial,
               p.netPayRial, p.paymentEpochDay, p.paymentMethod, p.status, p.reversalEpochDay, p.reversalReason
        FROM payroll_runs p
        INNER JOIN employees e ON e.id = p.employeeId
        ORDER BY p.periodYear DESC, p.periodMonth DESC, p.id DESC
        LIMIT 500
    """)
    fun observePayrolls(): Flow<List<PayrollListRow>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM attendance
            WHERE employeeId = :employeeId
              AND workEpochDay BETWEEN :startEpochDay AND :endEpochDay
              AND status != 'LEAVE'
        )
        """,
    )
    suspend fun hasAttendanceConflict(employeeId: Long, startEpochDay: Long, endEpochDay: Long): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM leaves
            WHERE employeeId = :employeeId
              AND status = 'APPROVED'
              AND startEpochDay <= :workEpochDay
              AND endEpochDay >= :workEpochDay
        )
        """,
    )
    suspend fun hasApprovedLeave(employeeId: Long, workEpochDay: Long): Boolean

    @Query("SELECT * FROM leaves WHERE employeeId=:employeeId AND leaveType=:leaveType AND status IN ('PENDING','SUBMITTED') ORDER BY startEpochDay,id")
    suspend fun pendingLeavesForBalance(employeeId: Long, leaveType: String): List<LeaveEntity>

    @Query("SELECT * FROM leaves WHERE idempotencyKey=:idempotencyKey LIMIT 1")
    suspend fun leaveByIdempotencyKey(idempotencyKey: String): LeaveEntity?
}

data class PayrollListRow(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val periodYear: Int,
    val periodMonth: Int,
    val revisionNo: Int,
    val grossPayRial: Long,
    val netPayRial: Long,
    val paymentEpochDay: Long,
    val paymentMethod: String,
    val status: String,
    val reversalEpochDay: Long?,
    val reversalReason: String,
)
