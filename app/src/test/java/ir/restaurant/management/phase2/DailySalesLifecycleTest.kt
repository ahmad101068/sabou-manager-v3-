package ir.restaurant.management.phase2
import ir.restaurant.management.domain.sales.*
import kotlin.test.*
class DailySalesLifecycleTest {
 @Test fun draftConfirmPostIsValid(){ DailySalesLifecycle.requireTransition(DailySalesStatus.DRAFT,DailySalesStatus.CONFIRMED); DailySalesLifecycle.requireTransition(DailySalesStatus.CONFIRMED,DailySalesStatus.POSTED) }
 @Test fun postedCannotReturnToDraft(){ assertFailsWith<IllegalArgumentException>{ DailySalesLifecycle.requireTransition(DailySalesStatus.POSTED,DailySalesStatus.DRAFT) } }
}
