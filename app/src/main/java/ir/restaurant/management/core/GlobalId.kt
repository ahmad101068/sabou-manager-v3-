package ir.restaurant.management.core

import java.util.Locale
import java.util.UUID

/** Stable cross-device identity; local Room ids remain implementation details. */
@JvmInline
value class GlobalId private constructor(val value: String) {
    companion object {
        private val canonicalUuid = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
        )
        private val legacyId = Regex("legacy:[a-z0-9_]{1,48}:[1-9][0-9]{0,18}")

        fun new(): GlobalId = GlobalId(UUID.randomUUID().toString())

        fun parse(raw: String): GlobalId {
            val normalized = raw.trim().lowercase(Locale.US)
            require(canonicalUuid.matches(normalized) || legacyId.matches(normalized)) {
                "شناسه سراسری معتبر نیست."
            }
            return GlobalId(normalized)
        }

        /** Deterministic identity used only while migrating pre-global-id rows. */
        fun legacy(entityType: String, localId: Long): GlobalId {
            require(localId > 0) { "شناسه محلی legacy معتبر نیست." }
            val normalizedType = entityType.trim().lowercase(Locale.US)
            require(normalizedType.matches(Regex("[a-z0-9_]{1,48}"))) {
                "نوع Entity برای شناسه legacy معتبر نیست."
            }
            return GlobalId("legacy:$normalizedType:$localId")
        }
    }
}
