package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.db.ReceivableEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.AlertTarget
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertReceivableIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var alerts: LocalAlertRepository
    private var now = 100_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, authorizer = authorizer, clock = { now }).save(
            null,
            UserDraft("alert-recv-owner", "مالک مطالبات", "123456", UserRole.OWNER, "87654321"),
        )
        alerts = LocalAlertRepository(database, authorizer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun canonicalReceivableMaster_excludesSettled_andAlertsOnlyPartialOutstanding() = runBlocking {
        val today = 40_000L
        val settled = insertCustomer("CUS-ALERT-1", "مشتری تسویه‌شده")
        val partial = insertCustomer("CUS-ALERT-2", "مشتری بدهکار")
        insertReceivable(
            settled,
            sourceId = 101,
            issueDay = today - 20,
            dueDay = today - 10,
            original = 100_000L,
            paid = 100_000L,
            outstanding = 0L,
            status = "PAID",
        )
        val partialReceivableId = insertReceivable(
            partial,
            sourceId = 102,
            issueDay = today - 20,
            dueDay = today - 10,
            original = 100_000L,
            paid = 40_000L,
            outstanding = 60_000L,
            status = "PARTIALLY_PAID",
        )

        alerts.refresh(today)
        val receivableAlerts = alerts.alerts().first().filter { it.sourceType == "CUSTOMER_RECEIVABLE" }

        assertEquals(1, receivableAlerts.size)
        assertEquals(partialReceivableId, receivableAlerts.single().sourceId)
        assertEquals(60_000L, messageAmount(receivableAlerts.single().message))
        assertEquals(AlertTarget.Receivable(partialReceivableId), receivableAlerts.single().target)
    }

    private suspend fun insertCustomer(code: String, name: String): Long = database.salesDao().insertCustomer(
        CustomerEntity(
            customerCode = code,
            name = name,
            phone = "",
            nationalId = "",
            creditLimitRial = 500_000L,
            notes = "",
            createdAtEpochMillis = now++,
            updatedAtEpochMillis = now++,
        ),
    )

    private suspend fun insertReceivable(
        customerId: Long,
        sourceId: Long,
        issueDay: Long,
        dueDay: Long,
        original: Long,
        paid: Long,
        outstanding: Long,
        status: String,
    ): Long = database.businessOperationsDao().insertReceivable(
        ReceivableEntity(
            globalId = "alert-receivable-$sourceId",
            branchId = 1,
            partyId = customerId,
            type = "TRADE",
            sourceType = "DAILY_SALES",
            sourceId = sourceId,
            originalAmountRial = original,
            paidAmountRial = paid,
            outstandingAmountRial = outstanding,
            issueEpochDay = issueDay,
            dueEpochDay = dueDay,
            status = status,
            createdAtEpochMillis = now++,
            updatedAtEpochMillis = now++,
        ),
    )

    private fun messageAmount(message: String): Long =
        Regex("مانده سررسیدشده ([0-9]+) ریال").find(message)?.groupValues?.get(1)?.toLong()
            ?: error("مبلغ هشدار از پیام قابل استخراج نیست: $message")
}
