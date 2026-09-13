package ir.restaurant.management.phase2
import ir.restaurant.management.domain.sales.*
import kotlin.test.*
class PostedSaleImmutabilityTest {
 @Test fun postedCannotBeDirectlyEdited(){ assertFailsWith<IllegalArgumentException>{ DailySalesLifecycle.requireDirectEdit(DailySalesStatus.POSTED) } }
 @Test fun draftCanBeEdited(){ DailySalesLifecycle.requireDirectEdit(DailySalesStatus.DRAFT) }
}
