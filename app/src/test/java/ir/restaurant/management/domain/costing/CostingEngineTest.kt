package ir.restaurant.management.domain.costing
import org.junit.Assert.assertEquals
import org.junit.Test
class CostingEngineTest { @Test fun `actual minus standard is explicit variance`() { val v=CostingEngine.variance(StandardCostBreakdown(100,10,20,30),ActualCostBreakdown(115,18,35)); assertEquals(15,v.foodRial); assertEquals(-2,v.laborRial); assertEquals(5,v.overheadRial) } }
