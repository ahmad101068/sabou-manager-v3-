package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY integritySequence ASC, id ASC")
    suspend fun allForIntegrityVerification(): List<AuditLogEntity>

    @Query("SELECT integritySequence,eventHash FROM audit_logs ORDER BY integritySequence DESC LIMIT 1")
    suspend fun latestIntegrityHead(): AuditIntegrityHeadRow?

    @Query("SELECT COUNT(*) FROM audit_logs WHERE eventHash=:eventHash")
    suspend fun countByEventHash(eventHash: String): Int

    @Query("SELECT * FROM audit_logs ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<AuditLogEntity>>

    @Query("""
        SELECT * FROM audit_logs
        WHERE (:search = '' OR description LIKE '%' || :search || '%' OR action LIKE '%' || :search || '%' OR entityType LIKE '%' || :search || '%' OR actor LIKE '%' || :search || '%' OR correlationId LIKE '%' || :search || '%')
          AND (:actor = '' OR actor LIKE '%' || :actor || '%')
          AND (:action = '' OR action = :action)
          AND (:entityType = '' OR entityType = :entityType)
          AND (:entityId IS NULL OR entityId = :entityId)
          AND (:sourceReference = '' OR referenceType LIKE '%' || :sourceReference || '%' OR CAST(referenceId AS TEXT) LIKE '%' || :sourceReference || '%' OR correlationId LIKE '%' || :sourceReference || '%')
          AND (
              :severity = ''
              OR (:severity = 'CRITICAL' AND (action LIKE '%DELETE%' OR action LIKE '%REVERSE%' OR action LIKE '%RESTORE%' OR action LIKE '%REOPEN%'))
              OR (:severity = 'WARNING' AND NOT (action LIKE '%DELETE%' OR action LIKE '%REVERSE%' OR action LIKE '%RESTORE%' OR action LIKE '%REOPEN%') AND (action LIKE '%REJECT%' OR action LIKE '%VOID%' OR UPPER(description) LIKE '%FAILED%' OR description LIKE '%ناموفق%' OR UPPER(reason) LIKE '%FAILED%' OR reason LIKE '%ناموفق%'))
              OR (:severity = 'NOTICE' AND NOT (action LIKE '%DELETE%' OR action LIKE '%REVERSE%' OR action LIKE '%RESTORE%' OR action LIKE '%REOPEN%' OR action LIKE '%REJECT%' OR action LIKE '%VOID%') AND (action LIKE '%APPROVE%' OR action LIKE '%POST%' OR action LIKE '%PAY%' OR action LIKE '%LOGIN%'))
              OR (:severity = 'INFO' AND NOT (action LIKE '%DELETE%' OR action LIKE '%REVERSE%' OR action LIKE '%RESTORE%' OR action LIKE '%REOPEN%' OR action LIKE '%REJECT%' OR action LIKE '%VOID%' OR action LIKE '%APPROVE%' OR action LIKE '%POST%' OR action LIKE '%PAY%' OR action LIKE '%LOGIN%'))
          )
          AND (:fromMillis IS NULL OR createdAtEpochMillis >= :fromMillis)
          AND (:toExclusiveMillis IS NULL OR createdAtEpochMillis < :toExclusiveMillis)
        ORDER BY createdAtEpochMillis DESC, id DESC
        LIMIT :limit
    """)
    fun observeFiltered(
        search: String,
        actor: String,
        action: String,
        entityType: String,
        entityId: Long?,
        sourceReference: String,
        severity: String,
        fromMillis: Long?,
        toExclusiveMillis: Long?,
        limit: Int = 300,
    ): Flow<List<AuditLogEntity>>

    @Query("""
        SELECT a.* FROM audit_logs a
        WHERE (a.entityType='EMPLOYEE' AND a.entityId=:employeeId)
           OR (a.referenceType='EMPLOYEE' AND a.referenceId=:employeeId)
           OR (a.entityType='EMPLOYMENT_CONTRACT' AND a.entityId IN (SELECT id FROM employment_contract_versions WHERE employeeId=:employeeId))
           OR (a.entityType='ATTENDANCE' AND a.entityId IN (SELECT id FROM attendance WHERE employeeId=:employeeId))
           OR (a.referenceType='ATTENDANCE' AND a.referenceId IN (SELECT id FROM attendance WHERE employeeId=:employeeId))
           OR (a.entityType='ATTENDANCE_CORRECTION' AND a.entityId IN (SELECT id FROM attendance_corrections WHERE employeeId=:employeeId))
           OR (a.entityType='OVERTIME_APPROVAL' AND a.entityId IN (SELECT id FROM overtime_approvals WHERE employeeId=:employeeId))
           OR (a.entityType='LEAVE' AND a.entityId IN (SELECT id FROM leaves WHERE employeeId=:employeeId))
           OR (a.entityType='EMPLOYEE_ADVANCE' AND a.entityId IN (SELECT id FROM employee_advances WHERE employeeId=:employeeId))
           OR (a.entityType='PAYROLL_PAYSLIP' AND a.entityId IN (SELECT id FROM payroll_payslips WHERE employeeId=:employeeId))
           OR (a.correlationId LIKE ('contract:' || :employeeId || ':%'))
        ORDER BY a.createdAtEpochMillis DESC, a.id DESC
        LIMIT :limit
    """)
    fun observeEmployeeTimeline(employeeId: Long, limit: Int = 300): Flow<List<AuditLogEntity>>
}


data class AuditIntegrityHeadRow(val integritySequence: Long, val eventHash: String)
