package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AssetListKeysTest {
    @Test
    fun assetAndDepreciationWithSameDatabaseIdHaveDifferentComposeKeys() {
        assertNotEquals(assetRecordListKey(1L), assetDepreciationListKey(1L))
    }

    @Test
    fun keysRemainStableForRecomposition() {
        assertEquals("asset-42", assetRecordListKey(42L))
        assertEquals("asset-depreciation-42", assetDepreciationListKey(42L))
    }
}
