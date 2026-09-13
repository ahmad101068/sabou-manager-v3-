package ir.restaurant.management.domain.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingModelsTest {
    @Test
    fun trialBalanceIncludesTurnoverAndClosingBalances() {
        val accounts = listOf(
            account(
                code = "1101",
                type = AccountType.ASSET,
                debit = 1_000,
                credit = 200,
            ),
            account(
                code = "4101",
                type = AccountType.REVENUE,
                debit = 200,
                credit = 1_000,
            ),
        )

        val result = calculateTrialBalance(accounts)

        assertTrue(result.isBalanced)
        assertEquals(1_200L, result.totalDebitTurnoverRial)
        assertEquals(1_200L, result.totalCreditTurnoverRial)
        assertEquals(800L, result.totalDebitBalanceRial)
        assertEquals(800L, result.totalCreditBalanceRial)
    }

    @Test
    fun profitAndLossUsesRevenueAndExpenseNormalSides() {
        val accounts = listOf(
            account("4101", AccountType.REVENUE, debit = 50, credit = 1_050),
            account("6105", AccountType.EXPENSE, debit = 300, credit = 25),
        )

        val result = calculateProfitLoss(accounts)

        assertEquals(1_000L, result.revenueRial)
        assertEquals(275L, result.expenseRial)
        assertEquals(725L, result.netProfitRial)
    }

    @Test
    fun accountCodeMustBeFourDigitsWithoutLeadingZero() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountDraft("۰۱۰۱", "حساب نامعتبر", AccountType.ASSET).validated()
        }
    }

    private fun account(
        code: String,
        type: AccountType,
        debit: Long,
        credit: Long,
    ) = AccountBalanceRecord(
        code = code,
        name = code,
        type = type,
        isSystem = true,
        debitTurnoverRial = debit,
        creditTurnoverRial = credit,
    )
}
