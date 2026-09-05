package ir.restaurant.management.ui

import ir.restaurant.management.domain.search.normalizePersianSearchText

/** Shared matching rules for data-heavy business screens. */
internal fun businessTextMatches(query: String, vararg fields: String?): Boolean {
    val normalized = normalizePersianSearchText(query)
    return normalized.isBlank() || fields.any { normalizePersianSearchText(it.orEmpty()).contains(normalized) }
}

internal fun businessActivityMatches(filter: String, isActive: Boolean): Boolean = when (filter) {
    "ACTIVE" -> isActive
    "INACTIVE" -> !isActive
    else -> true
}
