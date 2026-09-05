package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.ManagementRuleThresholdEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.security.Permission

class ControlSettingsService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val audit = LocalAuditEventWriter(database)

    suspend fun updateBasisPointThreshold(branchId: Long?, key: String, valueBasisPoints: Int): Long {
        val actor = authorizer.require(Permission.CONTROL_ASSIGN)
        require(branchId == null || branchId > 0)
        require(key.matches(Regex("[A-Z][A-Z0-9_]{2,63}")))
        require(valueBasisPoints in 0..100_000)
        val now=clock()
        val id=database.businessOperationsDao().upsertThreshold(ManagementRuleThresholdEntity(branchScopeId=branchId ?: 0L,key=key,valueBasisPoints=valueBasisPoints,updatedByUserId=actor.id,updatedAtEpochMillis=now))
        audit.appendAuthorized(authorizer,"UPDATE","MANAGEMENT_THRESHOLD",id,"$key=$valueBasisPoints bp",now,correlationId="threshold:$key:${branchId ?: 0}:$now")
        return id
    }

    suspend fun updateRialThreshold(branchId: Long?, key: String, valueRial: Long): Long {
        val actor = authorizer.require(Permission.CONTROL_ASSIGN)
        require(branchId == null || branchId > 0)
        require(key.matches(Regex("[A-Z][A-Z0-9_]{2,63}")))
        require(valueRial >= 0)
        val now=clock()
        val id=database.businessOperationsDao().upsertThreshold(ManagementRuleThresholdEntity(branchScopeId=branchId ?: 0L,key=key,valueRial=valueRial,updatedByUserId=actor.id,updatedAtEpochMillis=now))
        audit.appendAuthorized(authorizer,"UPDATE","MANAGEMENT_THRESHOLD",id,"$key=$valueRial ریال",now,correlationId="threshold:$key:${branchId ?: 0}:$now")
        return id
    }
}
