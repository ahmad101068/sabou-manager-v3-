package ir.restaurant.management.data.treasury

import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.treasury.TreasuryAccount
import ir.restaurant.management.domain.treasury.TreasuryAccountCatalog
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryAccountKind
import ir.restaurant.management.domain.treasury.TreasuryChannel

/** Stable one-to-one mapping from each operational treasury account to its GL settlement role. */
class DefaultTreasuryAccountCatalog : TreasuryAccountCatalog {
    private val accounts = listOf(
        TreasuryAccount(
            TreasuryAccountId.parse("cash_main"),
            "صندوق اصلی",
            TreasuryAccountKind.CASH,
            TreasuryChannel.CASH,
            SemanticAccountRole.CASH,
            isActive = true,
        ),
        TreasuryAccount(
            TreasuryAccountId.parse("bank_main"),
            "حساب بانکی اصلی",
            TreasuryAccountKind.BANK,
            TreasuryChannel.BANK,
            SemanticAccountRole.BANK,
            isActive = true,
        ),
        TreasuryAccount(
            TreasuryAccountId.parse("card_terminal"),
            "کارتخوان",
            TreasuryAccountKind.CARD_TERMINAL,
            TreasuryChannel.CARD,
            SemanticAccountRole.CARD_SETTLEMENT,
            isActive = true,
        ),
        TreasuryAccount(
            TreasuryAccountId.parse("petty_cash"),
            "تنخواه",
            TreasuryAccountKind.PETTY_CASH,
            TreasuryChannel.CASH,
            SemanticAccountRole.PETTY_CASH,
            isActive = true,
        ),
    )

    init {
        require(accounts.map { it.id }.distinct().size == accounts.size) { "treasury_account_id_mapping_duplicate" }
        require(accounts.map { it.settlementRole }.distinct().size == accounts.size) { "treasury_account_gl_mapping_not_one_to_one" }
    }

    override fun activeAccounts(): List<TreasuryAccount> = accounts

    override fun account(id: TreasuryAccountId): TreasuryAccount? =
        accounts.firstOrNull { it.id == id }
}
