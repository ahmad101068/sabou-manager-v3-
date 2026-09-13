package ir.restaurant.management.phase2
import ir.restaurant.management.domain.receivables.*
import kotlin.test.*
class ReceivableCollectionPolicyTest {
 @Test fun partialCollectionAllowed() { val d=ReceivableCollectionDraft(receivableId=1, amountRial=10, method=ReceivableCollectionMethod.CASH, businessEpochDay=2).validated(30); assertEquals(10,d.amountRial) }
 @Test fun overCollectionBlocked() { assertFailsWith<IllegalArgumentException>{ ReceivableCollectionDraft(receivableId=1, amountRial=25, method=ReceivableCollectionMethod.CASH, businessEpochDay=2).validated(20) } }
}
