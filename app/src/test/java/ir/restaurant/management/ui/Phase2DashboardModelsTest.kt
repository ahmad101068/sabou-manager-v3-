package ir.restaurant.management.ui

import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.AccountBalanceRecord
import ir.restaurant.management.domain.accounting.AccountType
import ir.restaurant.management.domain.accounting.JournalSummary
import ir.restaurant.management.domain.accounting.calculateTrialBalance
import ir.restaurant.management.domain.assets.AssetRecord
import ir.restaurant.management.domain.personnel.PayrollPeriodStatus
import ir.restaurant.management.domain.sales.CustomerRecord
import ir.restaurant.management.domain.treasury.TreasuryAccount
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryAccountKind
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryLedgerRecord
import ir.restaurant.management.domain.treasury.TreasuryTransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2DashboardModelsTest {
    @Test
    fun accounting_summary_uses_real_account_balances_and_journal_state() {
        val accounts = listOf(
            AccountBalanceRecord("1101", "صندوق", AccountType.ASSET, true, 500L, 0L),
            AccountBalanceRecord("1301", "دریافتنی", AccountType.ASSET, true, 300L, 0L),
            AccountBalanceRecord("2101", "پرداختنی", AccountType.LIABILITY, true, 0L, 200L),
            AccountBalanceRecord("3101", "سرمایه", AccountType.EQUITY, true, 0L, 600L),
        )
        val journals = listOf(
            JournalSummary(1L, "1", 1L, "ثبت", "TEST", 100L, 100L, false),
            JournalSummary(2L, "2", 1L, "برگشت", "TEST", 50L, 50L, true),
        )
        val summary = accountingDashboardSummary(
            AccountingUiState(accounts = accounts, journals = journals, trialBalance = calculateTrialBalance(accounts)),
        )
        assertEquals(500L, summary.cashAndBankRial)
        assertEquals(300L, summary.receivablesRial)
        assertEquals(200L, summary.payablesRial)
        assertEquals(1, summary.activeJournalCount)
        assertEquals(1, summary.reversedJournalCount)
        assertTrue(summary.isBalanced)
    }

    @Test
    fun treasury_summary_uses_observed_balances_and_real_ledger_kinds() {
        val account = TreasuryAccount(
            TreasuryAccountId.parse("cash_main"), "صندوق اصلی", TreasuryAccountKind.CASH, TreasuryChannel.CASH, SemanticAccountRole.CASH, true,
        )
        val rows = listOf(
            TreasuryLedgerRecord("r1", TreasuryTransactionKind.RECEIPT, 1L, "SALE", 1L, 100L, "POSTED", "دریافت", 1L, 1_000L),
            TreasuryLedgerRecord("p1", TreasuryTransactionKind.PAYMENT, 1L, "PURCHASE", 2L, 40L, "POSTED", "پرداخت", 2L, 2_000L),
        )
        val summary = treasuryDashboardSummary(
            TreasuryUiState(accounts = listOf(account), transactions = rows, balances = mapOf(account.id.value to 900L)),
        )
        assertEquals(900L, summary.totalBalanceRial)
        assertEquals(100L, summary.recentReceiptRial)
        assertEquals(40L, summary.recentPaymentRial)
        assertEquals(2, summary.postedTransactionCount)
    }

    @Test
    fun crm_summary_and_search_use_customer_domain_values() {
        val customers = listOf(
            CustomerRecord(1L, "C-1", "علی رضایی", "021", "1", 1_000L, 900L, "", true, mobile = "0912", status = "ACTIVE"),
            CustomerRecord(2L, "C-2", "مینا", "", "2", 0L, 0L, "", true, status = "ON_HOLD"),
        )
        val summary = crmDashboardSummary(CrmUiState(customers = customers))
        assertEquals(1, summary.activeCustomers)
        assertEquals(1, summary.debtorCustomers)
        assertEquals(1, summary.onHoldCustomers)
        assertEquals(900L, summary.totalReceivableRial)
        assertEquals(1, summary.nearCreditLimitCustomers)
        assertTrue(customerMatches(customers.first(), "0912"))
        assertFalse(customerMatches(customers.first(), "ناموجود"))
    }

    @Test
    fun asset_summary_never_invents_values() {
        val assets = listOf(
            AssetRecord(1L, "A1", "یخچال", "تجهیزات", 1, 1L, 1_000L, 0L, 200L, 60, "شعبه", "", true, true),
            AssetRecord(2L, "A2", "میز", "اثاث", 1, 1L, 500L, 0L, 100L, 60, "شعبه", "", false, false),
        )
        val summary = assetDashboardSummary(AssetUiState(assets = assets))
        assertEquals(1_500L, summary.totalPurchaseValueRial)
        assertEquals(1_200L, summary.totalBookValueRial)
        assertEquals(300L, summary.accumulatedDepreciationRial)
        assertEquals(1, summary.activeAssetCount)
        assertEquals(1, summary.disposedAssetCount)
        assertEquals(1, summary.unrecognizedAssetCount)
    }

    @Test
    fun payroll_status_titles_are_user_facing() {
        assertEquals("آماده‌سازی", payrollStatusTitle(PayrollPeriodStatus.OPEN))
        assertEquals("بازبینی", payrollStatusTitle(PayrollPeriodStatus.REVIEW))
        assertEquals("در حال پرداخت", payrollStatusTitle(PayrollPeriodStatus.PAYMENT))
    }
}
