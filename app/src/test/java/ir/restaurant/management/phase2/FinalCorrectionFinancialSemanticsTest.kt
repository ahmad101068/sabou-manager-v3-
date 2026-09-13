package ir.restaurant.management.phase2

import ir.restaurant.management.domain.sales.DailySalesPostingDraft
import ir.restaurant.management.domain.sales.DailySalesSettlementDraft
import ir.restaurant.management.domain.sales.SalesSettlementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun taxServiceScenario(): DailySalesPostingDraft = DailySalesPostingDraft(
    branchId = 2,
    businessEpochDay = 20_100,
    grossSalesRial = 100_000_000,
    discountRial = 5_000_000,
    returnRial = 0,
    settlements = listOf(
        DailySalesSettlementDraft(SalesSettlementType.CASH, 20_000_000),
        DailySalesSettlementDraft(SalesSettlementType.CARD, 60_000_000),
        DailySalesSettlementDraft(
            SalesSettlementType.CORPORATE_CREDIT,
            34_000_000,
            partyId = 77,
            dueEpochDay = 20_130,
        ),
    ),
    serviceRevenueRial = 10_000_000,
    taxPayableRial = 9_000_000,
).validated()

class TaxExcludedFromRevenueContractTest {
    @Test
    fun taxIsPayableAndNeverPartOfRevenue() {
        val draft = taxServiceScenario()
        assertEquals(95_000_000, draft.netSalesRial)
        assertEquals(105_000_000, draft.revenueRial)
        assertEquals(9_000_000, draft.taxPayableRial)
        assertEquals(114_000_000, draft.amountToSettleRial)
    }
}

class ServiceIncludedInRevenueTest {
    @Test
    fun serviceIsIncludedInRevenueButTaxIsNot() {
        val draft = taxServiceScenario()
        assertEquals(draft.netSalesRial + draft.serviceRevenueRial, draft.revenueRial)
        assertEquals(105_000_000, draft.revenueRial)
    }
}

class SettlementIncludesTaxTest {
    @Test
    fun settlementsBalanceAmountDueNotNetSales() {
        val draft = taxServiceScenario()
        assertEquals(114_000_000, draft.settlementTotalRial)
        assertEquals(draft.amountToSettleRial, draft.settlementTotalRial)

        assertFailsWith<IllegalArgumentException> {
            DailySalesPostingDraft(
                branchId = 2,
                businessEpochDay = 20_100,
                grossSalesRial = 100_000_000,
                discountRial = 5_000_000,
                returnRial = 0,
                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 105_000_000)),
                serviceRevenueRial = 10_000_000,
                taxPayableRial = 9_000_000,
            ).validated()
        }
    }
}
