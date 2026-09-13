#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])

# Alert state: pin LocalAlertRepository itself to the deterministic test clock.
alert_state = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/AlertStateIntegrationTest.kt"
state = alert_state.read_text(encoding="utf-8")
old = "repository = LocalAlertRepository(database, authorizer)"
new = "repository = LocalAlertRepository(database, authorizer, clock = { now })"
if new not in state:
    count = state.count(old)
    if count != 1:
        raise SystemExit(f"expected one primary LocalAlertRepository fixture, found {count}")
    state = state.replace(old, new, 1)
    alert_state.write_text(state, encoding="utf-8")
if new not in alert_state.read_text(encoding="utf-8"):
    raise SystemExit("deterministic LocalAlertRepository clock not established")
print("PHASE6_HOTFIX_03_ALERT_STATE_CLOCK=APPLIED_OR_ALREADY_CORRECT")

# Receivable alert: Phase 6 canonical source of truth is receivables, not legacy invoice/ledger projection.
receivable = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/AlertReceivableIntegrationTest.kt"
receivable.write_text("""package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.db.ReceivableEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.AlertDrillDownType
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
        alerts = LocalAlertRepository(database, authorizer, clock = { now })
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
        assertEquals(AlertDrillDownType.RECEIVABLE, receivableAlerts.single().drillDownType)
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
""", encoding="utf-8")
check = receivable.read_text(encoding="utf-8")
required = [
    "ReceivableEntity",
    "canonicalReceivableMaster_excludesSettled_andAlertsOnlyPartialOutstanding",
    "database.businessOperationsDao().insertReceivable",
    "AlertDrillDownType.RECEIVABLE",
    "clock = { now }",
]
missing = [token for token in required if token not in check]
if missing:
    raise SystemExit(f"canonical receivable fixture missing tokens: {missing}")
legacy = ["SalesInvoiceEntity", "CustomerReceivableLedgerEntity", "insertCreditInvoice", "insertLedger"]
remaining = [token for token in legacy if token in check]
if remaining:
    raise SystemExit(f"legacy receivable fixture remains: {remaining}")
print("PHASE6_HOTFIX_03_RECEIVABLE_CANONICAL=APPLIED")
