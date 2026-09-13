package ir.restaurant.management.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.accounting.AccountBalanceRecord
import ir.restaurant.management.domain.accounting.AccountDraft
import ir.restaurant.management.domain.accounting.AccountType
import ir.restaurant.management.domain.accounting.JournalDetails
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.accounting.JournalSummary
import ir.restaurant.management.domain.accounting.LedgerRow
import ir.restaurant.management.domain.accounting.ManualJournalDraft
import ir.restaurant.management.domain.accounting.PostedJournal

@Composable
internal fun AccountingReports(state: AccountingUiState, onSetProfitLossRange: (Long, Long) -> Unit) {
    val context = LocalContext.current
    var from by remember(state.profitLossFromEpochDay) { mutableLongStateOf(state.profitLossFromEpochDay) }
    var to by remember(state.profitLossToEpochDay) { mutableLongStateOf(state.profitLossToEpochDay) }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بازه سود و زیان", fontWeight = FontWeight.ExtraBold)
                    PersianDateField("از تاریخ", from, { from = it })
                    PersianDateField("تا تاریخ", to, { to = it })
                    Button(onClick = { if (from > 0 && to >= from) onSetProfitLossRange(from, to) }, modifier = Modifier.fillMaxWidth()) {
                        Text("اعمال بازه")
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { printAccountingSummary(context, state.profitLoss, state.trialBalance, state.profitLossFromEpochDay, state.profitLossToEpochDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("چاپ سود و زیان و تراز / PDF") }
        }
        item { SectionHeading("سود و زیان", "از ${epochDayToPersian(state.profitLossFromEpochDay).display()} تا ${epochDayToPersian(state.profitLossToEpochDay).display()}") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("درآمد", formatMoney(state.profitLoss.revenueRial), Modifier.widthIn(min = 155.dp))
                MetricTile("هزینه", formatMoney(state.profitLoss.expenseRial), Modifier.widthIn(min = 155.dp))
                MetricTile(if (state.profitLoss.netProfitRial >= 0) "سود خالص" else "زیان خالص", formatMoney(state.profitLoss.netProfitRial), Modifier.widthIn(min = 165.dp))
            }
        }
        item { SectionHeading("تراز آزمایشی", "کنترل برابری گردش و مانده بدهکار و بستانکار") }
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.trialBalance.isBalanced) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.trialBalance.isBalanced) "تراز حسابداری برقرار است" else "عدم تراز شناسایی شد", fontWeight = FontWeight.ExtraBold)
                    Text("گردش بدهکار: ${formatMoney(state.trialBalance.totalDebitTurnoverRial)}")
                    Text("گردش بستانکار: ${formatMoney(state.trialBalance.totalCreditTurnoverRial)}")
                    Text("مانده بدهکار: ${formatMoney(state.trialBalance.totalDebitBalanceRial)}")
                    Text("مانده بستانکار: ${formatMoney(state.trialBalance.totalCreditBalanceRial)}")
                }
            }
        }
        items(state.trialBalance.accounts, key = { it.code }) { account ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${account.code} · ${account.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("بدهکار: ${formatMoney(account.debitBalanceRial)}")
                    Text("بستانکار: ${formatMoney(account.creditBalanceRial)}")
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
internal fun LedgerDialog(
    account: AccountBalanceRecord?,
    rows: List<LedgerRow>,
    onDismiss: () -> Unit,
) {
    val firstDay = rows.minOfOrNull { it.entryEpochDay } ?: currentEpochDay()
    val lastDay = rows.maxOfOrNull { it.entryEpochDay } ?: currentEpochDay()
    var fromDay by remember(account?.code, firstDay) { mutableLongStateOf(firstDay) }
    var toDay by remember(account?.code, lastDay) { mutableLongStateOf(lastDay) }
    var query by rememberSaveable(account?.code) { mutableStateOf("") }
    var typeFilter by rememberSaveable(account?.code) { mutableStateOf("ALL") }
    val openingBalance = rows.lastOrNull { it.entryEpochDay < fromDay }?.balanceAfterRial ?: 0L
    val visibleRows = rows.filter { row ->
        row.entryEpochDay in fromDay..toDay &&
            businessTextMatches(query, row.entryNo, row.description) &&
            when (typeFilter) { "DEBIT" -> row.debitRial > 0; "CREDIT" -> row.creditRial > 0; else -> true }
    }
    val totalDebit = visibleRows.sumOf { it.debitRial }
    val totalCredit = visibleRows.sumOf { it.creditRial }
    val closingBalance = rows.lastOrNull { it.entryEpochDay <= toDay }?.balanceAfterRial ?: openingBalance
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دفتر ${account?.name ?: "حساب"}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("مانده افتتاحیه", signedBalanceText(openingBalance), Modifier.weight(1f))
                    MetricTile("مانده اختتامیه", signedBalanceText(closingBalance), Modifier.weight(1f))
                }
                PersianDateField("از تاریخ", fromDay, { if (it <= toDay) fromDay = it })
                PersianDateField("تا تاریخ", toDay, { if (it >= fromDay) toDay = it })
                PremiumSearchField(query, { query = it }, "شماره یا شرح سند")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(typeFilter == "ALL", { typeFilter = "ALL" }, { Text("همه") })
                    FilterChip(typeFilter == "DEBIT", { typeFilter = "DEBIT" }, { Text("بدهکار") })
                    FilterChip(typeFilter == "CREDIT", { typeFilter = "CREDIT" }, { Text("بستانکار") })
                }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactInfoRow("جمع بدهکار بازه", formatMoney(totalDebit), true)
                        CompactInfoRow("جمع بستانکار بازه", formatMoney(totalCredit), true)
                        Text("${visibleRows.size} گردش", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (visibleRows.isEmpty()) {
                    Text(if (rows.isEmpty()) "برای این حساب هنوز گردشی ثبت نشده است." else "گردشی با این بازه و فیلتر پیدا نشد.")
                } else {
                    visibleRows.asReversed().forEach { row ->
                        Card {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(row.entryNo, fontWeight = FontWeight.Bold)
                                    Text(epochDayToPersian(row.entryEpochDay).display())
                                }
                                Text(row.description)
                                Text(
                                    "بدهکار: ${
                                        if (row.debitRial == 0L) "—" else formatMoney(row.debitRial)
                                    }",
                                )
                                Text(
                                    "بستانکار: ${
                                        if (row.creditRial == 0L) "—" else formatMoney(row.creditRial)
                                    }",
                                )
                                Text(
                                    signedBalanceText(row.balanceAfterRial),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("بستن") } },
    )
}


