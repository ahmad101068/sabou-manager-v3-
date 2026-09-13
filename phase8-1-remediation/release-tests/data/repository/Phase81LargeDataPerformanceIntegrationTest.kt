package ir.restaurant.management.data.repository

import android.content.Context
import android.os.SystemClock
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AuditLogEntity
import ir.restaurant.management.data.security.AuditIntegrityCanonicalizer
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase81LargeDataPerformanceIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private var now = 1_800_300_000_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, authorizer = authorizer, clock = { now }).save(
            null,
            UserDraft("phase81-perf-owner", "مالک تست کارایی", "123456", UserRole.OWNER, "87654321"),
        )
        seedLargeRepresentativeDataset(database.openHelper.writableDatabase)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun largeOperationalDatasetStaysWithinReleaseBudgets() = runBlocking {
        val repository = LocalGlobalSearchRepository(database, authorizer)
        val searchMs = measureMillis {
            val results = repository.search("needle", 160)
            check(results.any { it.title.contains("needle", ignoreCase = true) })
        }
        val inventoryMs = measureQueryMillis("SELECT id,name FROM inventory_items WHERE isActive=1 ORDER BY name,id LIMIT 100")
        val journalMs = measureQueryMillis("SELECT id,entryNo,description FROM journal_entries ORDER BY entryEpochDay DESC,id DESC LIMIT 100")
        val receivableMs = measureQueryMillis("SELECT id,customerId,debitRial,creditRial FROM customer_receivable_ledger ORDER BY businessEpochDay DESC,id DESC LIMIT 100")
        val auditMs = measureQueryMillis("SELECT id,action,entityType,entityId FROM audit_logs ORDER BY id DESC LIMIT 200")

        val metric = "PHASE81_PERF inventory=10000 customers=50000 operational=100000 journals=50000 receivables=50000 audit=50000 searchMs=$searchMs inventoryMs=$inventoryMs journalMs=$journalMs receivableMs=$receivableMs auditMs=$auditMs"
        println(metric)
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "phase81-performance-results.txt").writeText(metric + "\n")

        assertTrue("global search budget exceeded: ${searchMs}ms", searchMs <= 5_000)
        assertTrue("inventory list budget exceeded: ${inventoryMs}ms", inventoryMs <= 1_500)
        assertTrue("journal list budget exceeded: ${journalMs}ms", journalMs <= 1_500)
        assertTrue("receivable list budget exceeded: ${receivableMs}ms", receivableMs <= 1_500)
        assertTrue("audit list budget exceeded: ${auditMs}ms", auditMs <= 1_500)
    }

    private fun measureMillis(block: suspend () -> Unit): Long {
        val started = SystemClock.elapsedRealtimeNanos()
        runBlocking { block() }
        return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L
    }

    private fun measureQueryMillis(sql: String): Long {
        val db = database.openHelper.readableDatabase
        val started = SystemClock.elapsedRealtimeNanos()
        db.query(sql).use { cursor -> while (cursor.moveToNext()) Unit }
        return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L
    }

    private fun seedLargeRepresentativeDataset(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            for (start in 1..10_000 step 1_000) {
                val end = minOf(start + 999, 10_000)
                db.execSQL(
                    """
                    WITH RECURSIVE seq(x) AS (SELECT $start UNION ALL SELECT x+1 FROM seq WHERE x<$end)
                    INSERT INTO inventory_items(name,category,unit,sku,itemType,purchaseUnit,purchaseToStockNumerator,purchaseToStockDenominator,recipeUnit,recipeToStockNumerator,recipeToStockDenominator,stockMicros,inventoryValueRial,alertEnabled,alertThresholdMicros,brand,storageCondition,trackLot,trackExpiry,minimumStockMicros,maximumStockMicros,safetyStockMicros,reorderPointMicros,leadTimeDays,isActive,createdAtEpochMillis,updatedAtEpochMillis)
                    SELECT CASE WHEN x=10000 THEN 'کالای needle ویژه' ELSE 'کالای '||x END,'مواد','عدد','PERF-I-'||x,'INGREDIENT','عدد',1,1,'عدد',1,1,1000000,100000,1,0,'','AMBIENT',0,0,0,0,0,0,0,1,$now,$now FROM seq
                    """.trimIndent(),
                )
            }
            for (start in 1..50_000 step 1_000) {
                val end = minOf(start + 999, 50_000)
                db.execSQL(
                    """
                    WITH RECURSIVE seq(x) AS (SELECT $start UNION ALL SELECT x+1 FROM seq WHERE x<$end)
                    INSERT INTO customers(id,customerCode,name,phone,nationalId,creditLimitRial,notes,isActive,createdAtEpochMillis,updatedAtEpochMillis,mobile,address,branch,paymentTermsDays,status,partyType)
                    SELECT 1000000+x,'PERF-C-'||x,CASE WHEN x=50000 THEN 'مشتری needle ویژه' ELSE 'مشتری '||x END,'071'||x,'N'||x,0,'',1,$now,$now,'','','',0,'ACTIVE','PERSON' FROM seq
                    """.trimIndent(),
                )
            }
            // Representative operational/event-outbox volume. Do not forge the immutable stock
            // ledger merely to create volume; inventory ledger invariants have dedicated E2E tests.
            for (start in 1..100_000 step 1_000) {
                val end = minOf(start + 999, 100_000)
                db.execSQL(
                    """
                    WITH RECURSIVE seq(x) AS (SELECT $start UNION ALL SELECT x+1 FROM seq WHERE x<$end)
                    INSERT INTO sync_changes(changeId,entityType,entityId,changeType,deviceId,occurredAtEpochMillis,revision,payloadVersion,payload,payloadHash,idempotencyKey,state,lastError,attemptCount,lastAttemptAtEpochMillis,nextAttemptAtEpochMillis)
                    SELECT 'perf-change-'||x,'PERF_ENTITY',x,'UPSERT','perf-device',${now}+x,x,1,'perf-payload-'||x,'perf-hash-'||x,'perf-sync-idem-'||x,'PENDING','',0,0,0 FROM seq
                    """.trimIndent(),
                )
            }
            for (start in 1..50_000 step 1_000) {
                val end = minOf(start + 999, 50_000)
                db.execSQL(
                    """
                    WITH RECURSIVE seq(x) AS (SELECT $start UNION ALL SELECT x+1 FROM seq WHERE x<$end)
                    INSERT INTO journal_entries(entryNo,entryEpochDay,description,sourceType,sourceId,status,createdAtEpochMillis,globalId,idempotencyKey,correlationId,accountingScope)
                    SELECT 'PERF-J-'||x,21000,'سند عملکرد '||x,'PERF',x,'DRAFT',${now}+x,'perf-jg-'||x,'perf-ji-'||x,'perf-jc-'||x,'GENERAL' FROM seq
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    WITH RECURSIVE seq(x) AS (SELECT $start UNION ALL SELECT x+1 FROM seq WHERE x<$end)
                    INSERT INTO customer_receivable_ledger(customerId,businessEpochDay,entryType,debitRial,creditRial,sourceType,sourceId,reference,actorId,createdAtEpochMillis)
                    SELECT 1000000+((x-1)%50000)+1,21000,'INVOICE',1000,0,'PERF',x,'PERF-R-'||x,(SELECT id FROM app_users WHERE username='phase81-perf-owner' LIMIT 1),${now}+x FROM seq
                    """.trimIndent(),
                )
            }
            seedCanonicalAuditEvents(db, 50_000)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedCanonicalAuditEvents(db: SupportSQLiteDatabase, count: Int) {
        val head = db.query("SELECT integritySequence,eventHash FROM audit_logs ORDER BY integritySequence DESC LIMIT 1").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getString(1) else 0L to ""
        }
        val actorId = db.query("SELECT id FROM app_users WHERE username='phase81-perf-owner' LIMIT 1").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
        val statement = db.compileStatement(
            "INSERT INTO audit_logs(action,entityType,entityId,description,actor,createdAtEpochMillis,globalId,actorId,businessEpochDay,deviceId,referenceType,referenceId,reason,beforeSnapshot,afterSnapshot,correlationId,actorRoleSnapshot,actorBranchIdSnapshot,integritySequence,previousEventHash,eventHash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        )
        var previousHash = head.second
        repeat(count) { index ->
            val number = index + 1L
            val sequence = head.first + number
            val event = AuditLogEntity(
                action = "PERF",
                entityType = "PERF_ENTITY",
                entityId = number,
                description = "رویداد عملکرد $number",
                actor = "phase81-perf-owner",
                createdAtEpochMillis = now + number,
                globalId = "perf-a-$number",
                actorId = actorId,
                businessEpochDay = 21_000L,
                deviceId = "instrumentation",
                reason = "PERFORMANCE_FIXTURE",
                correlationId = "perf-ac-$number",
                actorRoleSnapshot = "OWNER",
                integritySequence = sequence,
                previousEventHash = previousHash,
            )
            val eventHash = AuditIntegrityCanonicalizer.hashEvent(event)
            statement.clearBindings()
            statement.bindString(1, event.action)
            statement.bindString(2, event.entityType)
            statement.bindLong(3, number)
            statement.bindString(4, event.description)
            statement.bindString(5, event.actor)
            statement.bindLong(6, event.createdAtEpochMillis)
            statement.bindString(7, event.globalId)
            statement.bindLong(8, actorId)
            statement.bindLong(9, 21_000L)
            statement.bindString(10, event.deviceId)
            statement.bindNull(11)
            statement.bindNull(12)
            statement.bindString(13, event.reason)
            statement.bindNull(14)
            statement.bindNull(15)
            statement.bindString(16, event.correlationId)
            statement.bindString(17, event.actorRoleSnapshot)
            statement.bindNull(18)
            statement.bindLong(19, sequence)
            statement.bindString(20, previousHash)
            statement.bindString(21, eventHash)
            check(statement.executeInsert() > 0L)
            previousHash = eventHash
        }
    }
}
