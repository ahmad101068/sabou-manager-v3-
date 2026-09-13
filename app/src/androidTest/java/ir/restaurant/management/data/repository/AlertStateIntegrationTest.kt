package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryBalanceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertStateIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalAlertRepository
    private var now = 6_000_000L
    private val today = 24_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, authorizer = authorizer, clock = { now }).save(
            null,
            UserDraft("alert-owner", "مالک هشدار", "123456", UserRole.OWNER, "87654321"),
        )
        repository = LocalAlertRepository(database, authorizer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun repeatedRefresh_isIdempotent_andStateTransitionsFollowRealConditionLifecycle() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "کالای هشدار تست",
                category = "TEST",
                unit = "عدد",
                stockMicros = 5 * QuantityMicros.SCALE,
                alertThresholdMicros = 10 * QuantityMicros.SCALE,
                alertEnabled = true,
                createdAtEpochMillis = ++now,
                updatedAtEpochMillis = ++now,
            ),
        )

        val locationId = requireNotNull(database.managementControlDao().defaultLocationId())
        database.inventoryBalanceDao().initialize(
            InventoryBalanceEntity(
                itemId = itemId, locationId = locationId,
                onHandMicros = 5 * QuantityMicros.SCALE, inventoryValueRial = 50_000,
                updatedAtEpochMillis = ++now,
            ),
        )

        repository.refresh(today)
        val first = repository.alerts().first().single { it.sourceType == "LOW_STOCK" && it.sourceId == itemId && it.locationId == locationId }
        assertEquals("NEW", first.status)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM app_alerts WHERE sourceType='LOW_STOCK' AND sourceId=$itemId"))

        repository.refresh(today)
        val replay = repository.alerts().first().single { it.sourceType == "LOW_STOCK" && it.sourceId == itemId }
        assertEquals(first.id, replay.id)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM app_alerts WHERE sourceType='LOW_STOCK' AND sourceId=$itemId"))

        repository.markRead(first.id)
        assertEquals("READ", stringScalar("SELECT status FROM app_alerts WHERE id=${first.id}"))
        assertEquals(1L, scalar("SELECT isRead FROM app_alerts WHERE id=${first.id}"))

        repository.markActioned(first.id)
        assertEquals("ACTIONED", stringScalar("SELECT status FROM app_alerts WHERE id=${first.id}"))

        database.openHelper.writableDatabase.execSQL(
            "UPDATE inventory_balances SET onHandMicros=${20 * QuantityMicros.SCALE}, updatedAtEpochMillis=${++now} WHERE itemId=$itemId AND locationId=$locationId",
        )
        repository.refresh(today)
        assertEquals("RESOLVED", stringScalar("SELECT status FROM app_alerts WHERE id=${first.id}"))
        assertTrue(repository.alerts().first().none { it.id == first.id })

        database.openHelper.writableDatabase.execSQL(
            "UPDATE inventory_balances SET onHandMicros=${5 * QuantityMicros.SCALE}, updatedAtEpochMillis=${++now} WHERE itemId=$itemId AND locationId=$locationId",
        )
        repository.refresh(today)
        val reopened = repository.alerts().first().single { it.sourceType == "LOW_STOCK" && it.sourceId == itemId }
        assertEquals(first.id, reopened.id)
        assertEquals("NEW", reopened.status)

        repository.snooze(reopened.id, System.currentTimeMillis() + 120_000L)
        assertTrue(repository.alerts().first().none { it.id == reopened.id })
        database.openHelper.writableDatabase.execSQL("UPDATE app_alerts SET snoozedUntilEpochMillis=NULL WHERE id=${reopened.id}")
        repository.dismiss(reopened.id)
        assertEquals("DISMISSED", stringScalar("SELECT status FROM app_alerts WHERE id=${reopened.id}"))
        assertTrue(repository.alerts().first().none { it.id == reopened.id })
        repository.refresh(today)
        assertEquals("DISMISSED", stringScalar("SELECT status FROM app_alerts WHERE id=${reopened.id}"))
        assertTrue(repository.alerts().first().none { it.id == reopened.id })
        repository.clearDismissed()
        assertEquals(0L, scalar("SELECT COUNT(*) FROM app_alerts WHERE id=${reopened.id}"))
    }

    @Test
    fun alertRepositoryDeniesCashierAtDataBoundaryAndAuditsDenial() = runBlocking {
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, authorizer = authorizer, clock = { now })
        val cashierId = security.save(null, UserDraft("alert-cashier", "صندوقدار هشدار", "654321", UserRole.CASHIER))
        security.switchUser(cashierId, "654321")
        val denied = LocalAlertRepository(database, authorizer)
        try {
            denied.alerts().first()
            throw AssertionError("کاربر فاقد مجوز نباید Alert feed را از Data boundary بخواند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE action='ACCESS_DENIED' AND actorId=$cashierId AND reason LIKE 'PERMISSION_DENIED:%'"),
        )
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
