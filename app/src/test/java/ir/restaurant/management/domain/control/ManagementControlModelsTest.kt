package ir.restaurant.management.domain.control

import ir.restaurant.management.domain.purchase.PurchaseOrderRecord
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagementControlModelsTest {
    @Test fun `late unacknowledged order creates actionable exceptions`() {
        val order = PurchaseOrderRecord(
            id = 7, orderNo = "PO-7", supplierId = 2, supplierName = "تأمین‌کننده",
            requisitionId = 1, orderEpochDay = 100, expectedEpochDay = 103,
            sentAtEpochMillis = 1_000, sentBy = "manager", dispatchChannel = null,
            acknowledgedAtEpochMillis = null, supplierConfirmationNo = null, confirmedExpectedEpochDay = null,
            status = PurchaseOrderStatus.OPEN, orderedValueRial = 1_000, acceptedValueRial = 0,
            receiptCount = 0, invoiceNo = null, lines = emptyList(),
        )
        val exceptions = ProcurementExceptionCalculator.scan(listOf(order), todayEpochDay = 106)
        assertTrue(exceptions.any { it.kind == ProcurementExceptionKind.NOT_ACKNOWLEDGED })
        assertTrue(exceptions.any { it.kind == ProcurementExceptionKind.DELIVERY_OVERDUE && it.ageDays == 3L })
    }

    @Test fun `food cost exposes actual theoretical variance`() {
        val summary = FoodCostSummary(1, 30, salesRial = 10_000, theoreticalCostRial = 3_000, actualCostRial = 3_500, wasteCostRial = 200)
        assertEquals(500L, summary.varianceRial)
        assertEquals(3_500L, summary.actualBasisPoints)
    }

    @Test fun `labor control detects long shift and missing break`() {
        val alerts = LaborComplianceCalculator.evaluate(
            listOf(LaborShiftInput(1, 8, "کارمند", 100, 480, 1_260, breakMinutes = 0)),
            LaborPolicy(maxShiftMinutes = 720, breakRequiredAfterMinutes = 360, minimumBreakMinutes = 30),
        )
        assertEquals(2, alerts.size)
        assertTrue(alerts.all { it.employeeId == 8L })
    }

    @Test fun `lot validates expiry chronology`() {
        val error = runCatching { LotRegistrationDraft(1, 1, "LOT-1", 20, 19, 1_000_000, 10).validated() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
