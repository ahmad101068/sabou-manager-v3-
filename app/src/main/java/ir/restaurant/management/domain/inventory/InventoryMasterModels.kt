package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import kotlinx.coroutines.flow.Flow

enum class InventoryItemType(val storedValue: String) {
    INGREDIENT("INGREDIENT"),
    PACKAGING("PACKAGING"),
    CONSUMABLE("CONSUMABLE"),
    PREPARED_ITEM("PREPARED_ITEM"),
    FINISHED_GOOD("FINISHED_GOOD");

    companion object {
        fun fromStoredValue(value: String): InventoryItemType = entries.firstOrNull {
            it.storedValue == value.trim().uppercase()
        } ?: throw BusinessError.UnknownStoredValue("INVENTORY", "itemType", value).asViolation()
    }
}

enum class InventoryStorageCondition(val storedValue: String) {
    AMBIENT("AMBIENT"),
    DRY("DRY"),
    CHILLED("CHILLED"),
    FROZEN("FROZEN"),
    OTHER("OTHER");

    companion object {
        fun fromStoredValue(value: String): InventoryStorageCondition = entries.firstOrNull {
            it.storedValue == value.trim().uppercase()
        } ?: throw BusinessError.UnknownStoredValue("INVENTORY", "storageCondition", value).asViolation()
    }
}

enum class InventoryLocationType(val storedValue: String) {
    WAREHOUSE("WAREHOUSE"),
    COLD_STORAGE("COLD_STORAGE"),
    FREEZER("FREEZER"),
    KITCHEN("KITCHEN"),
    PREP("PREP"),
    BAR("BAR"),
    OTHER("OTHER");

    companion object {
        fun fromStoredValue(value: String): InventoryLocationType {
            val normalized = value.trim().uppercase()
            return when (normalized) {
                "PRIMARY", "MAIN", "WAREHOUSE" -> WAREHOUSE
                "COLD", "COLD_STORAGE" -> COLD_STORAGE
                else -> entries.firstOrNull { it.storedValue == normalized }
                    ?: throw BusinessError.UnknownStoredValue("INVENTORY", "locationType", value).asViolation()
            }
        }
    }
}

@JvmInline
value class InventorySku private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[A-Z0-9][A-Z0-9._/-]{2,39}")

        fun parse(value: String): InventorySku {
            val normalized = value.trim().uppercase()
            require(normalized.matches(pattern)) { "SKU باید ۳ تا ۴۰ نویسه و شامل حروف لاتین، عدد یا . _ / - باشد." }
            return InventorySku(normalized)
        }

        fun generated(): InventorySku {
            val token = GlobalId.new().value.filter { it.isLetterOrDigit() }.uppercase().take(12)
            return InventorySku("SKU-$token")
        }
    }
}

@JvmInline
value class ItemBarcode private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[A-Za-z0-9._/-]{4,80}")

        fun parse(value: String): ItemBarcode {
            val normalized = value.trim()
            require(normalized.matches(pattern)) { "بارکد کالا معتبر نیست." }
            return ItemBarcode(normalized)
        }
    }
}

@JvmInline
value class InventoryLocationCode private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[A-Z0-9][A-Z0-9_-]{1,19}")

        fun parse(value: String): InventoryLocationCode {
            val normalized = value.trim().uppercase()
            require(normalized.matches(pattern)) { "کد محل باید ۲ تا ۲۰ نویسه لاتین/عدد باشد." }
            return InventoryLocationCode(normalized)
        }

        fun generated(): InventoryLocationCode {
            val token = GlobalId.new().value.filter { it.isLetterOrDigit() }.uppercase().take(10)
            return InventoryLocationCode("LOC-$token")
        }
    }
}

data class InventoryItemMasterDraft(
    val sku: String,
    val name: String,
    val category: String,
    val itemType: InventoryItemType,
    val baseUnit: String,
    val purchaseUnit: String,
    val purchaseToBaseNumerator: Long,
    val purchaseToBaseDenominator: Long,
    val recipeUnit: String,
    val recipeToBaseNumerator: Long,
    val recipeToBaseDenominator: Long,
    val primaryBarcode: String?,
    val brand: String,
    val storageCondition: InventoryStorageCondition,
    val shelfLifeDays: Int?,
    val trackLot: Boolean,
    val trackExpiry: Boolean,
    val minimumStockMicros: Long,
    val maximumStockMicros: Long,
    val safetyStockMicros: Long,
    val reorderPointMicros: Long,
    val preferredSupplierId: Long?,
    val leadTimeDays: Int,
    val active: Boolean = true,
) {
    fun validated(): InventoryItemMasterDraft {
        val normalizedSku = InventorySku.parse(sku).value
        val normalizedName = name.trim()
        val normalizedCategory = category.trim()
        val normalizedBaseUnit = baseUnit.trim()
        val normalizedPurchaseUnit = purchaseUnit.trim()
        val normalizedRecipeUnit = recipeUnit.trim()
        val normalizedBarcode = primaryBarcode?.takeIf { it.isNotBlank() }?.let { ItemBarcode.parse(it).value }
        require(normalizedName.length in 2..120) { "نام کالا باید بین ۲ تا ۱۲۰ نویسه باشد." }
        require(normalizedCategory.length in 1..80) { "دسته‌بندی کالا معتبر نیست." }
        require(normalizedBaseUnit.length in 1..40) { "واحد پایه معتبر نیست." }
        require(normalizedPurchaseUnit.length in 1..40 && normalizedRecipeUnit.length in 1..40) {
            "واحد خرید و رسپی معتبر نیست."
        }
        require(purchaseToBaseNumerator > 0 && recipeToBaseNumerator > 0) { "صورت نسبت تبدیل باید بیشتر از صفر باشد." }
        FixedPointRatio.multiplyDivide(1, purchaseToBaseNumerator, purchaseToBaseDenominator)
        FixedPointRatio.multiplyDivide(1, recipeToBaseNumerator, recipeToBaseDenominator)
        listOf(minimumStockMicros, maximumStockMicros, safetyStockMicros, reorderPointMicros)
            .forEach(QuantityMicros::of)
        require(maximumStockMicros == 0L || minimumStockMicros <= maximumStockMicros) {
            "حداکثر موجودی باید صفر (بدون سقف) یا حداقل برابر حداقل موجودی باشد."
        }
        require(maximumStockMicros == 0L || safetyStockMicros <= maximumStockMicros) {
            "ذخیره ایمن از حداکثر موجودی بیشتر است."
        }
        require(maximumStockMicros == 0L || reorderPointMicros <= maximumStockMicros) {
            "نقطه سفارش از حداکثر موجودی بیشتر است."
        }
        require(!trackExpiry || trackLot) { "ردیابی تاریخ انقضا به ردیابی لات نیاز دارد." }
        require(shelfLifeDays == null || shelfLifeDays in 1..3_650) { "عمر ماندگاری معتبر نیست." }
        require(preferredSupplierId == null || preferredSupplierId > 0) { "تأمین‌کننده ترجیحی معتبر نیست." }
        require(leadTimeDays in 0..365) { "زمان تأمین معتبر نیست." }
        return copy(
            sku = normalizedSku,
            name = normalizedName,
            category = normalizedCategory,
            baseUnit = normalizedBaseUnit,
            purchaseUnit = normalizedPurchaseUnit,
            recipeUnit = normalizedRecipeUnit,
            primaryBarcode = normalizedBarcode,
            brand = brand.trim().take(80),
        )
    }
}

data class InventoryItemMasterRecord(
    val id: Long,
    val sku: InventorySku,
    val name: String,
    val category: String,
    val itemType: InventoryItemType,
    val baseUnit: String,
    val purchaseUnit: String,
    val purchaseToBaseNumerator: Long,
    val purchaseToBaseDenominator: Long,
    val recipeUnit: String,
    val recipeToBaseNumerator: Long,
    val recipeToBaseDenominator: Long,
    val primaryBarcode: ItemBarcode?,
    val brand: String,
    val storageCondition: InventoryStorageCondition,
    val shelfLifeDays: Int?,
    val trackLot: Boolean,
    val trackExpiry: Boolean,
    val minimumStockMicros: Long,
    val maximumStockMicros: Long,
    val safetyStockMicros: Long,
    val reorderPointMicros: Long,
    val preferredSupplierId: Long?,
    val leadTimeDays: Int,
    val active: Boolean,
    val onHandMicros: Long,
    val inventoryValueRial: Long,
)

data class InventoryLocationDraft(
    val code: String,
    val name: String,
    val type: InventoryLocationType,
    val branchName: String = "",
    val branchId: Long? = null,
) {
    fun validated(): InventoryLocationDraft {
        val normalizedName = name.trim()
        require(normalizedName.length in 2..80) { "نام محل نگهداری معتبر نیست." }
        require(branchId == null || branchId > 0) { "شناسه شعبه محل معتبر نیست." }
        return copy(code = InventoryLocationCode.parse(code).value, name = normalizedName, branchName = branchName.trim().take(80))
    }
}

data class InventoryLocationRecord(
    val id: Long,
    val code: InventoryLocationCode,
    val name: String,
    val type: InventoryLocationType,
    val active: Boolean,
    val branchName: String = "",
    val branchId: Long? = null,
)

data class InventoryItemSearch(
    val query: String = "",
    val category: String? = null,
    val itemType: InventoryItemType? = null,
    val preferredSupplierId: Long? = null,
    val includeInactive: Boolean = false,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryItemSearch {
        require(limit in 1..200) { "اندازه صفحه کالا باید بین ۱ تا ۲۰۰ باشد." }
        require(offset >= 0) { "مبدأ صفحه کالا معتبر نیست." }
        require(preferredSupplierId == null || preferredSupplierId > 0) { "فیلتر تأمین‌کننده معتبر نیست." }
        return copy(query = query.trim().take(80), category = category?.trim()?.takeIf { it.isNotEmpty() })
    }
}

data class InventoryLocationSearch(
    val query: String = "",
    val type: InventoryLocationType? = null,
    val includeInactive: Boolean = false,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryLocationSearch {
        require(limit in 1..200) { "اندازه صفحه محل باید بین ۱ تا ۲۰۰ باشد." }
        require(offset >= 0) { "مبدأ صفحه محل معتبر نیست." }
        return copy(query = query.trim().take(80))
    }
}

interface InventoryRepository {
    val locations: Flow<List<InventoryLocationRecord>>

    suspend fun item(id: Long): InventoryItemMasterRecord
    suspend fun searchItems(search: InventoryItemSearch): List<InventoryItemMasterRecord>
    suspend fun itemByBarcode(barcode: String): InventoryItemMasterRecord?
    suspend fun saveItem(id: Long?, draft: InventoryItemMasterDraft): Long
    suspend fun deactivateItem(id: Long)
    suspend fun searchLocations(search: InventoryLocationSearch): List<InventoryLocationRecord>
    suspend fun saveLocation(id: Long?, draft: InventoryLocationDraft): Long
    suspend fun defaultLocationId(): Long
}
