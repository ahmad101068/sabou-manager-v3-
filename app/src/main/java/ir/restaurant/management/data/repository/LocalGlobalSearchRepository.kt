package ir.restaurant.management.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.domain.search.GlobalSearchRepository
import ir.restaurant.management.domain.search.GlobalSearchResult
import ir.restaurant.management.domain.search.GlobalSearchTarget
import ir.restaurant.management.domain.search.normalizePersianSearchText
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bounded, authorization-aware search against the real Room database.
 * UI state caches are intentionally not used as a search source.
 */
class LocalGlobalSearchRepository(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
) : GlobalSearchRepository {
    private val dataScope = LocalDataScopeService(database, authorizer)

    override suspend fun search(query: String, limit: Int): List<GlobalSearchResult> {
        val normalized = normalizePersianSearchText(query)
        if (normalized.length < 2) return emptyList()
        val boundedLimit = limit.coerceIn(1, MAX_TOTAL_RESULTS)
        val actor = authorizer.actorIdentity()
        val branchIds = if (actor.role == UserRole.OWNER) null else dataScope.activeBranches().map { it.id }.distinct()
        val locationIds = if (actor.role == UserRole.OWNER) null else dataScope.activeLocations().map { it.id }.distinct()
        val canInventory = authorizer.can(Permission.INVENTORY)
        val canPurchases = authorizer.can(Permission.PURCHASES)
        val canAccounting = authorizer.can(Permission.ACCOUNTING)
        val canPersonnel = authorizer.can(Permission.PERSONNEL_VIEW)
        val canCustomers = authorizer.can(Permission.CUSTOMERS)
        return withContext(Dispatchers.IO) {
            buildList {
                if (canInventory) {
                    addAll(searchInventory(normalized, PER_TYPE_LIMIT))
                    addAll(searchMovements(normalized, locationIds, PER_TYPE_LIMIT))
                }
                if (canPurchases) addAll(searchPurchases(normalized, branchIds, PER_TYPE_LIMIT))
                if (canAccounting) {
                    addAll(searchAccounts(normalized, PER_TYPE_LIMIT))
                    addAll(searchJournals(normalized, branchIds, PER_TYPE_LIMIT))
                }
                if (canPersonnel) addAll(searchEmployees(normalized, branchIds, PER_TYPE_LIMIT))
                if (canCustomers) addAll(searchCustomers(normalized, PER_TYPE_LIMIT))
            }.distinctBy(GlobalSearchResult::stableKey).take(boundedLimit)
        }
    }

    private fun searchInventory(query: String, limit: Int): List<GlobalSearchResult> = queryRows(
        sql = """
            SELECT id,name,category,sku FROM inventory_items
            WHERE isActive=1 AND ${matches(query, listOf("name", "category", "sku", "brand", "primaryBarcode")).first}
            ORDER BY name COLLATE NOCASE,id LIMIT ?
        """.trimIndent(),
        args = matches(query, listOf("name", "category", "sku", "brand", "primaryBarcode")).second + limit,
    ) { c ->
        val id = c.getLong(0)
        val name = c.getString(1)
        val category = c.getString(2)
        val sku = c.getString(3).orEmpty()
        GlobalSearchResult(name, listOf("کالا", category, sku.takeIf { it.isNotBlank() }?.let { "SKU $it" }).filterNotNull().joinToString(" · "), GlobalSearchTarget.InventoryItem(id))
    }

    private fun searchMovements(query: String, locationIds: List<Long>?, limit: Int): List<GlobalSearchResult> {
        val match = matches(query, listOf("i.name", "i.sku", "m.referenceType", "m.notes", "m.movementType"))
        val scope = scopeClause("m.locationId", locationIds)
        return queryRows(
            sql = """
                SELECT m.id,m.itemId,i.name,m.movementType,m.referenceType
                FROM stock_movements m JOIN inventory_items i ON i.id=m.itemId
                WHERE ${match.first}${scope.first}
                ORDER BY m.movementEpochDay DESC,m.id DESC LIMIT ?
            """.trimIndent(),
            args = match.second + scope.second + limit,
        ) { c ->
            val id = c.getLong(0)
            val itemId = c.getLong(1)
            val itemName = c.getString(2)
            val movementType = c.getString(3)
            val referenceType = c.getString(4)
            GlobalSearchResult(itemName, "گردش موجودی · $movementType · $referenceType", GlobalSearchTarget.StockMovement(id, itemId))
        }
    }

    private fun searchPurchases(query: String, branchIds: List<Long>?, limit: Int): List<GlobalSearchResult> {
        val match = matches(query, listOf("p.invoiceNo", "p.normalizedInvoiceNo", "s.name", "s.code"))
        val scope = scopeClause("p.branchId", branchIds)
        return queryRows(
            sql = """
                SELECT p.id,p.invoiceNo,s.name FROM purchases p JOIN suppliers s ON s.id=p.supplierId
                WHERE ${match.first}${scope.first}
                ORDER BY p.purchaseEpochDay DESC,p.id DESC LIMIT ?
            """.trimIndent(),
            args = match.second + scope.second + limit,
        ) { c -> GlobalSearchResult("فاکتور ${c.getString(1)}", "خرید · ${c.getString(2)}", GlobalSearchTarget.Purchase(c.getLong(0))) }
    }

    private fun searchAccounts(query: String, limit: Int): List<GlobalSearchResult> {
        val match = matches(query, listOf("code", "name", "type"))
        return queryRows(
            sql = "SELECT code,name FROM accounts WHERE isActive=1 AND ${match.first} ORDER BY code LIMIT ?",
            args = match.second + limit,
        ) { c -> GlobalSearchResult("${c.getString(0)} — ${c.getString(1)}", "حساب حسابداری", GlobalSearchTarget.Account(c.getString(0))) }
    }

    private fun searchJournals(query: String, branchIds: List<Long>?, limit: Int): List<GlobalSearchResult> {
        val match = matches(query, listOf("entryNo", "description", "sourceType"))
        val scope = scopeClause("branchId", branchIds)
        return queryRows(
            sql = "SELECT id,entryNo,description FROM journal_entries WHERE ${match.first}${scope.first} ORDER BY entryEpochDay DESC,id DESC LIMIT ?",
            args = match.second + scope.second + limit,
        ) { c -> GlobalSearchResult(c.getString(1), "سند حسابداری · ${c.getString(2)}", GlobalSearchTarget.Journal(c.getLong(0))) }
    }

    private fun searchEmployees(query: String, branchIds: List<Long>?, limit: Int): List<GlobalSearchResult> {
        val match = matches(query, listOf("name", "displayName", "employeeCode", "jobTitle", "department", "phone"))
        val scope = scopeClause("branchId", branchIds)
        return queryRows(
            sql = "SELECT id,COALESCE(NULLIF(displayName,''),name),jobTitle,employeeCode FROM employees WHERE status!='TERMINATED' AND ${match.first}${scope.first} ORDER BY name,id LIMIT ?",
            args = match.second + scope.second + limit,
        ) { c ->
            val code = if (c.isNull(3)) "" else c.getString(3)
            GlobalSearchResult(c.getString(1), listOf(c.getString(2), code.takeIf { it.isNotBlank() }?.let { "کد $it" }).filterNotNull().joinToString(" · "), GlobalSearchTarget.Employee(c.getLong(0)))
        }
    }

    private fun searchCustomers(query: String, limit: Int): List<GlobalSearchResult> {
        val match = matches(query, listOf("name", "customerCode", "phone", "mobile", "nationalId"))
        return queryRows(
            sql = "SELECT id,name,customerCode,COALESCE(NULLIF(mobile,''),phone) FROM customers WHERE isActive=1 AND ${match.first} ORDER BY name,id LIMIT ?",
            args = match.second + limit,
        ) { c -> GlobalSearchResult(c.getString(1), "مشتری · کد ${c.getString(2)} · ${c.getString(3)}", GlobalSearchTarget.Customer(c.getLong(0))) }
    }

    private fun matches(query: String, columns: List<String>): Pair<String, List<Any>> {
        val pattern = "%$query%"
        return columns.joinToString(" OR ", prefix = "(", postfix = ")") { "${normalizedSql(it)} LIKE ?" } to List(columns.size) { pattern }
    }

    private fun scopeClause(column: String, ids: List<Long>?): Pair<String, List<Any>> = when {
        ids == null -> "" to emptyList()
        ids.isEmpty() -> " AND 1=0" to emptyList()
        else -> " AND $column IN (${ids.joinToString(",") { "?" }})" to ids.map { it as Any }
    }

    private fun normalizedSql(column: String): String {
        var expression = "LOWER(COALESCE($column,''))"
        val replacements = listOf(
            "ي" to "ی", "ى" to "ی", "ك" to "ک",
            "۰" to "0", "۱" to "1", "۲" to "2", "۳" to "3", "۴" to "4", "۵" to "5", "۶" to "6", "۷" to "7", "۸" to "8", "۹" to "9",
            "٠" to "0", "١" to "1", "٢" to "2", "٣" to "3", "٤" to "4", "٥" to "5", "٦" to "6", "٧" to "7", "٨" to "8", "٩" to "9",
        )
        replacements.forEach { (from, to) -> expression = "REPLACE($expression,'$from','$to')" }
        return expression
    }

    private fun <T> queryRows(sql: String, args: List<Any>, mapper: (android.database.Cursor) -> T): List<T> {
        return database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, args.toTypedArray())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(mapper(cursor))
            }
        }
    }

    private companion object {
        const val PER_TYPE_LIMIT = 30
        const val MAX_TOTAL_RESULTS = 160
    }
}
