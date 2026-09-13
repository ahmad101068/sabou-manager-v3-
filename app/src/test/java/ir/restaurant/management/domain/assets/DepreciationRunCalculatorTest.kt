package ir.restaurant.management.domain.assets
import org.junit.Assert.assertEquals
import org.junit.Test
class DepreciationRunCalculatorTest { @Test fun `last period never exceeds depreciable base`() { val p=DepreciationRunCalculator.project(DepreciationCandidate(1,1000,100,10,850)); assertEquals(50,p.amountRial); assertEquals(900,p.closingAccumulatedRial); assertEquals(100,p.closingBookValueRial) } }
