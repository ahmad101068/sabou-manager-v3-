package ir.restaurant.management.phase2

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.treasury.validated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FinancialTruthContractTest {
    @Test
    fun treasuryAccountsHaveDistinctLiquidityGlRoles() {
        val catalog = DefaultTreasuryAccountCatalog()
        val cash = requireNotNull(catalog.account(TreasuryAccountId.parse("cash_main")))
        val bank = requireNotNull(catalog.account(TreasuryAccountId.parse("bank_main")))
        val card = requireNotNull(catalog.account(TreasuryAccountId.parse("card_terminal")))
        val petty = requireNotNull(catalog.account(TreasuryAccountId.parse("petty_cash")))
        assertEquals(SemanticAccountRole.CASH, cash.settlementRole)
        assertEquals(SemanticAccountRole.BANK, bank.settlementRole)
        assertEquals(SemanticAccountRole.CARD_SETTLEMENT, card.settlementRole)
        assertEquals(SemanticAccountRole.PETTY_CASH, petty.settlementRole)
        assertEquals(4, setOf(cash.settlementRole, bank.settlementRole, card.settlementRole, petty.settlementRole).size)
    }

    @Test(expected = BusinessRuleViolation::class)
    fun unknownGenericReceiptFailsClosed() {
        TreasuryBusinessIntent.fromExternalSource("UNKNOWN_RECEIPT", TreasuryDirection.RECEIPT)
    }

    @Test(expected = BusinessRuleViolation::class)
    fun unknownGenericPaymentFailsClosed() {
        TreasuryBusinessIntent.fromExternalSource("UNKNOWN_PAYMENT", TreasuryDirection.PAYMENT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun branchScopedTreasuryCommandRequiresBranch() {
        val id = GlobalId.new()
        TreasuryCommand.Receipt(
            commandId = id,
            businessEpochDay = 20_000,
            correlationId = CorrelationId.forCommand("branch-scope", id),
            businessIntent = TreasuryBusinessIntent.OWNER_CAPITAL,
            sourceId = 1,
            reason = "آزمون دامنه شعبه",
            accountingScope = AccountingScope.BRANCH,
            branchId = null,
            accountId = TreasuryAccountId.parse("cash_main"),
            channel = TreasuryChannel.CASH,
            amount = MoneyRial.of(1_000),
        ).validated()
    }

    @Test
    fun personalAndCorporateCollectionsUseDifferentArRoles() {
        assertNotEquals(
            TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION.counterpartRole,
            TreasuryBusinessIntent.CORPORATE_RECEIVABLE_COLLECTION.counterpartRole,
        )
    }
}
