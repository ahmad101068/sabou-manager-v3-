package ir.restaurant.management.domain.search

sealed interface GlobalSearchTarget {
    data class InventoryItem(val id: Long) : GlobalSearchTarget
    data class StockMovement(val id: Long, val itemId: Long) : GlobalSearchTarget
    data class Purchase(val id: Long) : GlobalSearchTarget
    data class Account(val code: String) : GlobalSearchTarget
    data class Journal(val id: Long) : GlobalSearchTarget
    data class Employee(val id: Long) : GlobalSearchTarget
    data class Customer(val id: Long) : GlobalSearchTarget
}

data class GlobalSearchResult(
    val title: String,
    val subtitle: String,
    val target: GlobalSearchTarget,
) {
    val stableKey: String get() = when (val value = target) {
        is GlobalSearchTarget.InventoryItem -> "inventory:${value.id}"
        is GlobalSearchTarget.StockMovement -> "movement:${value.id}:${value.itemId}"
        is GlobalSearchTarget.Purchase -> "purchase:${value.id}"
        is GlobalSearchTarget.Account -> "account:${value.code}"
        is GlobalSearchTarget.Journal -> "journal:${value.id}"
        is GlobalSearchTarget.Employee -> "employee:${value.id}"
        is GlobalSearchTarget.Customer -> "customer:${value.id}"
    }
}

interface GlobalSearchRepository {
    suspend fun search(query: String, limit: Int = 120): List<GlobalSearchResult>
}

/** Canonical Persian search normalization shared by DB retrieval and tests. */
fun normalizePersianSearchText(raw: String): String = buildString(raw.length) {
    raw.trim().lowercase().forEach { ch ->
        append(
            when (ch) {
                'ي', 'ى' -> 'ی'
                'ك' -> 'ک'
                '۰', '٠' -> '0'
                '۱', '١' -> '1'
                '۲', '٢' -> '2'
                '۳', '٣' -> '3'
                '۴', '٤' -> '4'
                '۵', '٥' -> '5'
                '۶', '٦' -> '6'
                '۷', '٧' -> '7'
                '۸', '٨' -> '8'
                '۹', '٩' -> '9'
                '\u200c', '\u200f', '\u200e' -> ' '
                else -> ch
            },
        )
    }
}.replace(Regex("\\s+"), " ").trim()
