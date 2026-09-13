package ir.restaurant.management.domain.treasury
import ir.restaurant.management.core.MoneyRial
import org.junit.Test
class TreasuryModelsTest { @Test fun `valid movement requires source and positive amount`() { TreasuryMovementDraft(TreasuryDirection.PAYMENT,TreasuryChannel.BANK,MoneyRial.of(1),1,"PAYROLL",1).validated() } }
