#!/usr/bin/env python3
from pathlib import Path


def read(path): return Path(path).read_text(encoding='utf-8')
def write(path, text): Path(path).write_text(text, encoding='utf-8')
def replace_once(path, old, new, label):
    text=read(path)
    if new in text: return
    count=text.count(old)
    if count != 1: raise SystemExit(f'FIX16_REPLACE_FAIL:{label}:count={count}')
    write(path,text.replace(old,new,1))

DAO='app/src/main/java/ir/sabou/inventory/data/db/AlertDao.kt'
REPO='app/src/main/java/ir/sabou/inventory/data/repository/LocalAlertRepository.kt'
RESET='app/src/main/java/ir/sabou/inventory/data/db/migration/DatabaseIntegrityLifecycle.kt'

old='''    @Query("""\n        INSERT INTO app_alerts(sourceType,sourceId,title,message,severity,dueEpochDay,isRead,isDismissed,createdAtEpochMillis,updatedAtEpochMillis,status)\n        VALUES(:sourceType,:sourceId,:title,:message,:severity,:dueEpochDay,0,0,:now,:now,'NEW')\n        ON CONFLICT(sourceType,sourceId) DO UPDATE SET\n            title=excluded.title,\n            message=excluded.message,\n            severity=excluded.severity,\n            dueEpochDay=excluded.dueEpochDay,\n            isDismissed=0,\n            status=CASE WHEN app_alerts.status IN ('RESOLVED','DISMISSED') THEN 'NEW' ELSE app_alerts.status END,\n            updatedAtEpochMillis=excluded.updatedAtEpochMillis\n    """)\n    suspend fun upsertGenerated(sourceType: String, sourceId: Long, title: String, message: String, severity: String, dueEpochDay: Long?, now: Long)\n'''
new='''    @Query("""\n        UPDATE app_alerts SET\n            title=:title,\n            message=:message,\n            severity=:severity,\n            dueEpochDay=:dueEpochDay,\n            isDismissed=0,\n            status=CASE WHEN status IN ('RESOLVED','DISMISSED') THEN 'NEW' ELSE status END,\n            updatedAtEpochMillis=:now\n        WHERE sourceType=:sourceType AND sourceId=:sourceId\n    """)\n    suspend fun updateGenerated(sourceType: String, sourceId: Long, title: String, message: String, severity: String, dueEpochDay: Long?, now: Long): Int\n\n    @Query("""\n        INSERT OR IGNORE INTO app_alerts(sourceType,sourceId,title,message,severity,dueEpochDay,isRead,isDismissed,createdAtEpochMillis,updatedAtEpochMillis,status)\n        VALUES(:sourceType,:sourceId,:title,:message,:severity,:dueEpochDay,0,0,:now,:now,'NEW')\n    """)\n    suspend fun insertGeneratedIfAbsent(sourceType: String, sourceId: Long, title: String, message: String, severity: String, dueEpochDay: Long?, now: Long)\n'''
replace_once(DAO,old,new,'alert_api23_sql')

old='''                db.alertDao().upsertGenerated(\n                    sourceType = sourceType,\n                    sourceId = row.sourceId,\n                    title = row.title,\n                    message = row.message,\n                    severity = row.severity,\n                    dueEpochDay = row.dueEpochDay,\n                    now = now,\n                )'''
new='''                val updated = db.alertDao().updateGenerated(\n                    sourceType = sourceType,\n                    sourceId = row.sourceId,\n                    title = row.title,\n                    message = row.message,\n                    severity = row.severity,\n                    dueEpochDay = row.dueEpochDay,\n                    now = now,\n                )\n                if (updated == 0) {\n                    db.alertDao().insertGeneratedIfAbsent(\n                        sourceType = sourceType,\n                        sourceId = row.sourceId,\n                        title = row.title,\n                        message = row.message,\n                        severity = row.severity,\n                        dueEpochDay = row.dueEpochDay,\n                        now = now,\n                    )\n                }'''
replace_once(REPO,old,new,'alert_repository_two_phase_upsert')

old='''internal fun AppDatabase.clearAllTablesForFactoryReset() {\n    val sqlite = openHelper.writableDatabase\n    dropDataIntegrityGuardsForFactoryReset(sqlite)\n    try {\n        clearAllTables()\n    } finally {\n        AccountSeedCallback.seedMissingAccounts(sqlite)\n        AccountSeedCallback.seedSystemLocations(sqlite)\n        AccountSeedCallback.installClosedPeriodGuards(sqlite)\n        installSalesDayGuards(sqlite)\n        installAccountingPeriodGuards(sqlite)\n        installJournalLineGuards(sqlite)\n        installRecipeVersionGuards(sqlite)\n        installAuditLogGuards(sqlite)\n        installPostedJournalGuards(sqlite)\n        installStockMovementGuards(sqlite)\n        installInventoryBalanceGuards(sqlite)\n        installInventoryLotGuards(sqlite)\n        installInventoryCountSessionGuards(sqlite)\n        installInventoryCountGuards(sqlite)\n        installWasteDocumentGuards(sqlite)\n        installStockTransferGuards(sqlite)\n        installHrPayrollGuards(sqlite)\n        installProfessionalSalesGuards(sqlite)\n    }\n}\n'''
new='''internal fun AppDatabase.clearAllTablesForFactoryReset() {\n    val sqlite = openHelper.writableDatabase\n    // Android 6 ships an older SQLite engine whose immediate FK enforcement can make\n    // Room's generated clearAllTables() fail solely because of delete ordering. Factory\n    // reset is the one owner-authorized destructive operation, so FK enforcement is\n    // suspended only for the clear itself and restored before any seed data is written.\n    sqlite.execSQL("PRAGMA foreign_keys=OFF")\n    dropDataIntegrityGuardsForFactoryReset(sqlite)\n    try {\n        clearAllTables()\n    } finally {\n        sqlite.execSQL("PRAGMA foreign_keys=ON")\n        AccountSeedCallback.seedMissingAccounts(sqlite)\n        AccountSeedCallback.seedSystemLocations(sqlite)\n        AccountSeedCallback.installClosedPeriodGuards(sqlite)\n        installSalesDayGuards(sqlite)\n        installAccountingPeriodGuards(sqlite)\n        installJournalLineGuards(sqlite)\n        installRecipeVersionGuards(sqlite)\n        installAuditLogGuards(sqlite)\n        installPostedJournalGuards(sqlite)\n        installStockMovementGuards(sqlite)\n        installInventoryBalanceGuards(sqlite)\n        installInventoryLotGuards(sqlite)\n        installInventoryCountSessionGuards(sqlite)\n        installInventoryCountGuards(sqlite)\n        installWasteDocumentGuards(sqlite)\n        installStockTransferGuards(sqlite)\n        installHrPayrollGuards(sqlite)\n        installProfessionalSalesGuards(sqlite)\n        sqlite.query("PRAGMA foreign_key_check").use { cursor ->\n            check(!cursor.moveToFirst()) { "FACTORY_RESET_FOREIGN_KEY_VIOLATION" }\n        }\n    }\n}\n'''
replace_once(RESET,old,new,'factory_reset_fk_order')

checks=[
(DAO,'INSERT OR IGNORE INTO app_alerts'),
(DAO,'suspend fun updateGenerated'),
(REPO,'if (updated == 0)'),
(RESET,'PRAGMA foreign_keys=OFF'),
(RESET,'PRAGMA foreign_key_check'),
]
for path,needle in checks:
    if needle not in read(path): raise SystemExit(f'FIX16_VERIFY_FAIL:{path}:{needle}')
if 'ON CONFLICT(sourceType,sourceId) DO UPDATE' in read(DAO):
    raise SystemExit('FIX16_VERIFY_FAIL:modern_alert_upsert_still_present')
print('DASHBOARD_UX2_FIX16_API23_SQLITE_COMPAT=PASS alert_two_phase_upsert=1 factory_reset_fk_guard=1')
