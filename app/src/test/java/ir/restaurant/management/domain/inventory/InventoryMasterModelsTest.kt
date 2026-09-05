package ir.restaurant.management.domain.inventory

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InventoryMasterModelsTest {
    @Test
    fun typedPersistenceMappingsAcceptKnownAndExplicitLegacyLocationValues() {
        assertEquals(InventoryItemType.PACKAGING, InventoryItemType.fromStoredValue("PACKAGING"))
        assertEquals(InventoryLocationType.WAREHOUSE, InventoryLocationType.fromStoredValue("PRIMARY"))
        assertEquals(InventoryLocationType.COLD_STORAGE, InventoryLocationType.fromStoredValue("COLD"))
    }

    @Test
    fun unknownBusinessStateFailsExplicitly() {
        val error = assertFailsWith<BusinessRuleViolation> {
            InventoryItemType.fromStoredValue("mystery")
        }
        assertTrue(error.error is BusinessError.UnknownStoredValue)
    }

    @Test
    fun expiryTrackingRequiresLotTracking() {
        assertFailsWith<IllegalArgumentException> {
            validDraft().copy(trackLot = false, trackExpiry = true).validated()
        }
    }

    @Test
    fun masterDraftNormalizesStableIdentifiers() {
        val value = validDraft().copy(sku = " food-001 ", primaryBarcode = " 6261234567890 ").validated()
        assertEquals("FOOD-001", value.sku)
        assertEquals("6261234567890", value.primaryBarcode)
    }

    private fun validDraft() = InventoryItemMasterDraft(
        sku = "ING-001",
        name = "برنج ایرانی",
        category = "مواد اولیه",
        itemType = InventoryItemType.INGREDIENT,
        baseUnit = "کیلوگرم",
        purchaseUnit = "کیسه",
        purchaseToBaseNumerator = 10,
        purchaseToBaseDenominator = 1,
        recipeUnit = "گرم",
        recipeToBaseNumerator = 1,
        recipeToBaseDenominator = 1_000,
        primaryBarcode = null,
        brand = "",
        storageCondition = InventoryStorageCondition.DRY,
        shelfLifeDays = 365,
        trackLot = true,
        trackExpiry = true,
        minimumStockMicros = 5_000_000,
        maximumStockMicros = 100_000_000,
        safetyStockMicros = 10_000_000,
        reorderPointMicros = 20_000_000,
        preferredSupplierId = null,
        leadTimeDays = 3,
    )
}
