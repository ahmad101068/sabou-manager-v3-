package ir.restaurant.management.ui

/** Pure presentation paging contract shared by large ERP lists and search results. */
internal data class UiPageWindow(
    val pageIndex: Int,
    val pageCount: Int,
    val totalCount: Int,
    val fromIndex: Int,
    val toIndexExclusive: Int,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex + 1 < pageCount
    val visibleCount: Int get() = (toIndexExclusive - fromIndex).coerceAtLeast(0)
}

internal fun uiPageWindow(totalCount: Int, requestedPage: Int, pageSize: Int): UiPageWindow {
    require(pageSize > 0) { "pageSize must be positive" }
    val safeTotal = totalCount.coerceAtLeast(0)
    val pageCount = if (safeTotal == 0) 1 else ((safeTotal - 1) / pageSize) + 1
    val pageIndex = requestedPage.coerceIn(0, pageCount - 1)
    val from = (pageIndex * pageSize).coerceAtMost(safeTotal)
    val to = (from + pageSize).coerceAtMost(safeTotal)
    return UiPageWindow(pageIndex, pageCount, safeTotal, from, to)
}

internal fun <T> List<T>.page(window: UiPageWindow): List<T> =
    if (isEmpty() || window.fromIndex >= size) emptyList() else subList(window.fromIndex, window.toIndexExclusive.coerceAtMost(size))
