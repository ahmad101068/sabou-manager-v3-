package ir.restaurant.management.phase2
import ir.restaurant.management.domain.sales.*
import kotlin.test.*
class ProfitabilityLiquiditySeparationTest {
 @Test fun creditRevenueDoesNotPretendToBeCash(){
   val p=ProfitabilitySnapshot(125,0,0,125,0,0,125,48,77,null,null,null)
   val l=LiquiditySnapshot(15,70,5,0,35,35)
   assertEquals(125,p.netSalesRial); assertEquals(90,l.cashReceivedRial+l.cardReceivedRial+l.transferReceivedRial); assertEquals(35,l.newReceivablesRial)
 }
}
