package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.InventoryBalanceEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.db.UserBranchScopeEntity
import ir.restaurant.management.data.db.UserScopeProfileEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.AlertTarget
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase6AlertIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var security: LocalSecurityRepository
    private lateinit var authorizer: SessionAuthorizer
    private var now = 1_800_100_000_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, authorizer = authorizer, clock = { now })
        security.save(null, UserDraft("phase6-alert-owner", "مالک هشدار فاز شش", "123456", UserRole.OWNER, "87654321"))
        Unit
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun alertsAreBranchLocationScopedTypedAndSnoozeIsDurable() = runBlocking {
        val branchA = insertBranch(911, "P6A")
        val branchB = insertBranch(912, "P6B")
        val locationA = insertLocation(branchA, "P6-A-LOC")
        val locationB = insertLocation(branchB, "P6-B-LOC")
        val itemA = insertLowStockItem("P6-A-ITEM", locationA)
        val itemB = insertLowStockItem("P6-B-ITEM", locationB)

        val ownerAlerts = LocalAlertRepository(database, authorizer)
        ownerAlerts.refresh(50_000)
        val all = ownerAlerts.alerts().first().filter { it.sourceType == "LOW_STOCK" }
        assertEquals(setOf(itemA, itemB), all.map { it.sourceId }.toSet())
        val branchBAlertId = all.single { it.sourceId == itemB }.id

        val managerId = security.save(null, UserDraft("phase6-alert-manager", "مدیر هشدار فاز شش", "654321", UserRole.MANAGER))
        database.phase3Dao().upsertScopeProfile(UserScopeProfileEntity(managerId, branchA, now))
        database.phase3Dao().grantBranch(UserBranchScopeEntity(managerId, branchA, now))
        security.switchUser(managerId, "654321")

        val scoped = LocalAlertRepository(database, authorizer)
        val visible = scoped.alerts().first().filter { it.sourceType == "LOW_STOCK" }
        assertEquals(1, visible.size)
        val alertA = visible.single()
        assertEquals(itemA, alertA.sourceId)
        assertEquals(branchA, alertA.branchId)
        assertEquals(locationA, alertA.locationId)
        assertEquals(AlertTarget.InventoryItem(itemA), alertA.target)

        val until = System.currentTimeMillis() + 120_000L
        scoped.snooze(alertA.id, until)
        assertTrue(scoped.alerts().first().none { it.id == alertA.id })
        assertEquals(until, scalar("SELECT snoozedUntilEpochMillis FROM app_alerts WHERE id=${alertA.id}"))

        try {
            scoped.dismiss(branchBAlertId)
            fail("manager must not mutate another branch alert")
        } catch (_: IllegalArgumentException) {
            // fail-closed scope boundary
        }
        assertEquals("NEW", stringScalar("SELECT status FROM app_alerts WHERE id=$branchBAlertId"))
    }

    private suspend fun insertBranch(id: Long, code: String): Long {
        database.branchDao().insert(
            BranchEntity(
                id = id,
                globalId = "test:phase6:alert:branch:$id",
                code = code,
                name = "شعبه $code",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        return id
    }

    private suspend fun insertLocation(branchId: Long, code: String): Long = database.inventoryLocationDao().insert(
        StorageLocationEntity(
            code = code,
            name = "انبار $code",
            branchId = branchId,
            kind = "WAREHOUSE",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        ),
    )

    private suspend fun insertLowStockItem(name: String, locationId: Long): Long {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = name,
                category = "TEST",
                unit = "عدد",
                stockMicros = 2 * QuantityMicros.SCALE,
                alertThresholdMicros = 5 * QuantityMicros.SCALE,
                alertEnabled = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        database.inventoryBalanceDao().initialize(
            InventoryBalanceEntity(
                itemId = itemId,
                locationId = locationId,
                onHandMicros = 2 * QuantityMicros.SCALE,
                inventoryValueRial = 2_000,
                updatedAtEpochMillis = now,
            ),
        )
        return itemId
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun stringScalar(sql: String): String = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }
}
