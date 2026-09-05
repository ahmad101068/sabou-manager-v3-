package ir.restaurant.management.phase2

import ir.restaurant.management.domain.sales.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DailySalesMixedSettlementTest {
    @Test fun creditIsRevenueAndSettlementMustBalance() {
        val draft=DailySalesPostingDraft(1,20000,130_000_000,5_000_000,0,listOf(
            DailySalesSettlementDraft(SalesSettlementType.CASH,15_000_000),
            DailySalesSettlementDraft(SalesSettlementType.CARD,70_000_000),
            DailySalesSettlementDraft(SalesSettlementType.BANK_TRANSFER,5_000_000),
            DailySalesSettlementDraft(SalesSettlementType.PERSONAL_CREDIT,10_000_000,partyId=10,dueEpochDay=20010),
            DailySalesSettlementDraft(SalesSettlementType.CORPORATE_CREDIT,25_000_000,partyId=20,dueEpochDay=20030),
        )).validated()
        assertEquals(125_000_000,draft.netSalesRial)
        assertEquals(125_000_000,draft.settlementTotalRial)
    }
    @Test fun mismatchBlocked() {
        assertFailsWith<IllegalArgumentException> {
            DailySalesPostingDraft(1,20000,125,0,0,listOf(DailySalesSettlementDraft(SalesSettlementType.CASH,120))).validated()
        }
    }
    @Test fun negativeSettlementBlocked() {
        assertFailsWith<IllegalArgumentException> {
            DailySalesSettlementDraft(SalesSettlementType.CASH,-1).validated()
        }
    }
}
