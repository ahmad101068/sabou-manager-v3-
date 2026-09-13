package ir.restaurant.management.domain.treasury

import ir.restaurant.management.data.treasury.StoredTreasuryChannelMapper
import ir.restaurant.management.domain.control.AccountingPeriodStatus
import ir.restaurant.management.domain.control.CashReconciliationStatus
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TypedStateMappingTest {
    @Test
    fun knownLegacyChannelsMapExplicitly() {
        assertEquals(TreasuryChannel.CASH, StoredTreasuryChannelMapper.fromPurchaseStoredValue("نقدی"))
        assertEquals(TreasuryChannel.CARD, StoredTreasuryChannelMapper.fromPurchaseStoredValue("کارتخوان"))
        assertEquals(TreasuryChannel.BANK, StoredTreasuryChannelMapper.fromPersonnelStoredValue("BANK"))
    }

    @Test
    fun unknownStoredChannelNeverFallsBackToCashOrBank() {
        try {
            StoredTreasuryChannelMapper.fromPersonnelStoredValue("WIRE")
            fail("unknown legacy channel must be explicit")
        } catch (error: BusinessRuleViolation) {
            val failure = error.error as BusinessError.UnknownStoredValue
            assertEquals("WIRE", failure.storedValue)
        }
    }

    @Test
    fun legacyControlStatesRemainExplicitRatherThanSilentlyChangingMeaning() {
        assertEquals(AccountingPeriodStatus.CLOSED, AccountingPeriodStatus.fromStoredValue("CLOSED"))
        assertEquals(AccountingPeriodStatus.LEGACY_UNKNOWN, AccountingPeriodStatus.fromStoredValue("ARCHIVED"))
        assertEquals(CashReconciliationStatus.MATCHED, CashReconciliationStatus.fromStoredValue("MATCHED"))
        assertEquals(CashReconciliationStatus.LEGACY_UNKNOWN, CashReconciliationStatus.fromStoredValue("PENDING"))
    }

    @Test
    fun stockLedgerReadModelUsesExplicitLegacyStates() {
        assertEquals(
            InventoryMovementType.LEGACY_SALE_CONSUMPTION,
            InventoryMovementType.fromStoredValue("SALE_CONSUMPTION"),
        )
        assertEquals(
            InventoryMovementType.LEGACY_UNKNOWN,
            InventoryMovementType.fromStoredValue("MANUAL_FIX"),
        )
        assertEquals(
            InventoryReferenceType.LEGACY_UNKNOWN,
            InventoryReferenceType.fromStoredValue("OLD_DOCUMENT"),
        )
    }

    @Test
    fun unknownPurchasePaymentMethodIsAStoredValueFailure() {
        try {
            PurchasePaymentMethod.fromStored("CHEQUE")
            fail("unknown purchase payment method must not become payable")
        } catch (error: BusinessRuleViolation) {
            assertEquals("CHEQUE", (error.error as BusinessError.UnknownStoredValue).storedValue)
        }
    }
}
