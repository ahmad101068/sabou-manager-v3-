package ir.restaurant.management.data.treasury

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.treasury.TreasuryChannel

/**
 * Explicit compatibility mapping for stored treasury values. Unknown values are never converted to
 * cash, bank, payable, or another financially meaningful fallback.
 */
object StoredTreasuryChannelMapper {
    fun fromPurchaseStoredValue(value: String?): TreasuryChannel? = when (value?.trim()) {
        null -> null
        "نقدی" -> TreasuryChannel.CASH
        "کارتخوان" -> TreasuryChannel.CARD
        "حواله" -> TreasuryChannel.TRANSFER
        else -> unknown("purchase", "paymentMethod", value)
    }

    fun fromPersonnelStoredValue(value: String): TreasuryChannel = when (value.trim().uppercase()) {
        TreasuryChannel.CASH.storedValue -> TreasuryChannel.CASH
        TreasuryChannel.BANK.storedValue -> TreasuryChannel.BANK
        else -> unknown("workforce", "paymentMethod", value)
    }

    fun toPersonnelStoredValue(channel: TreasuryChannel): String = when (channel) {
        TreasuryChannel.CASH,
        TreasuryChannel.BANK,
        -> channel.storedValue
        TreasuryChannel.CARD,
        TreasuryChannel.TRANSFER,
        -> TreasuryChannel.BANK.storedValue
    }

    private fun unknown(ownerDomain: String, field: String, value: String?): Nothing =
        throw BusinessError.UnknownStoredValue(ownerDomain, field, value).asViolation()
}
