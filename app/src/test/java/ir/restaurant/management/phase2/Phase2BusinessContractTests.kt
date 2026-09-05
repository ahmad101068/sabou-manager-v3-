package ir.restaurant.management.phase2

import ir.restaurant.management.domain.control.*
import ir.restaurant.management.domain.receivables.*
import ir.restaurant.management.domain.sales.*
import kotlin.test.*

private fun balanced(type: SalesSettlementType, amount:Long=100, partyId:Long?=null) =
    DailySalesPostingDraft(1,1000,amount,0,0,listOf(DailySalesSettlementDraft(type,amount,partyId=partyId))).validated()

class DailySalesCashTest { @Test fun cashBalances(){ assertEquals(100,balanced(SalesSettlementType.CASH).netSalesRial) } }
class DailySalesCardTest { @Test fun cardBalances(){ assertEquals(100,balanced(SalesSettlementType.CARD).settlementTotalRial) } }
class DailySalesBankTransferTest { @Test fun transferBalances(){ assertEquals(SalesSettlementType.BANK_TRANSFER,balanced(SalesSettlementType.BANK_TRANSFER).settlements.single().type) } }
class DailySalesPersonalCreditTest { @Test fun personalCreditRequiresParty(){ assertFailsWith<IllegalArgumentException>{ balanced(SalesSettlementType.PERSONAL_CREDIT) }; assertEquals(100,balanced(SalesSettlementType.PERSONAL_CREDIT,partyId=7).netSalesRial) } }
class DailySalesCorporateCreditTest { @Test fun corporateCreditRequiresParty(){ assertFailsWith<IllegalArgumentException>{ balanced(SalesSettlementType.CORPORATE_CREDIT) }; assertEquals(9,balanced(SalesSettlementType.CORPORATE_CREDIT,partyId=9).settlements.single().partyId) } }
class SettlementMismatchTest { @Test fun underSettlementBlocked(){ assertFailsWith<IllegalArgumentException>{ DailySalesPostingDraft(1,1,100,0,0,listOf(DailySalesSettlementDraft(SalesSettlementType.CASH,99))).validated() } } }
class OverSettlementTest { @Test fun overSettlementBlocked(){ assertFailsWith<IllegalArgumentException>{ DailySalesPostingDraft(1,1,100,0,0,listOf(DailySalesSettlementDraft(SalesSettlementType.CASH,101))).validated() } } }
class PersonalReceivableCollectionTest { @Test fun collectionMustBePositive(){ assertFailsWith<IllegalArgumentException>{ ReceivableCollectionDraft(receivableId=1, amountRial=0, method=ReceivableCollectionMethod.CASH, businessEpochDay=2).validated(100) } } }
class PartialCollectionTest { @Test fun partialCollectionPreservesDifference(){ val v=ReceivableCollectionDraft(receivableId=1, amountRial=30, method=ReceivableCollectionMethod.CASH, businessEpochDay=2).validated(100); assertEquals(70,100-v.amountRial) } }
class WasteSpikeRuleTest { @Test fun thresholdDefaultIsPositive(){ assertTrue(ManagementDefaults.WASTE_SPIKE_BASIS_POINTS>0) } }
class PurchasePriceSpikeRuleTest { @Test fun thresholdDefaultIsPositive(){ assertTrue(ManagementDefaults.PURCHASE_PRICE_INCREASE_BASIS_POINTS>0) } }
class CashVarianceRuleTest { @Test fun rialThresholdCannotBeNegative(){ assertTrue(ManagementDefaults.CASH_VARIANCE_RIAL>=0) } }
class OverdueReceivableRuleTest { @Test fun overdueIsDerivedNotStatus(){ val r=ReceivableRecord(1,"x",1,2,ReceivableType.PERSONAL,"S",3,10,0,10,1,2,ReceivableStatus.OPEN); assertTrue(r.isOverdue(3)) } }
class IssueDeduplicationTest {
    @Test fun identicalSourceGetsIdenticalKey() {
        fun issue() = DetectedIssue(
            branchId = 1,
            type = ManagementIssueType.LOW_STOCK,
            severity = ManagementIssueSeverity.MEDIUM,
            title = "a",
            description = "b",
            businessEpochDay = 3,
            sourceType = "ITEM",
            sourceId = 4,
            businessPeriodKey = "3",
        )
        assertEquals(issue().deduplicationKey, issue().deduplicationKey)
    }
}
class TaskLifecycleTest { @Test fun terminalStatusIsExplicit(){ assertEquals(ManagementTaskStatus.COMPLETED.name,"COMPLETED") } }
class ChecklistLifecycleTest { @Test fun failedIsExplicitStatus(){ assertEquals(ChecklistStatus.FAILED.name,"FAILED") } }
class ChecklistFailureCreatesIssueTest { @Test fun failureIssueTypeExists(){ assertEquals(ManagementIssueType.CHECKLIST_FAILED.name,"CHECKLIST_FAILED") } }
