package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent

private enum class TreasuryFormMode(val title: String) {
    RECEIPT("دریافت"), PAYMENT("پرداخت"), SETTLEMENT("تسویه"), TRANSFER("انتقال داخلی"), RECONCILIATION("مغایرت‌گیری")
}

@Composable
internal fun TreasuryScreen(
    state: TreasuryUiState,
    onReceipt: (String, Long, String, Long, String) -> Unit,
    onPayment: (String, Long, String, Long, String) -> Unit,
    onSettlement: (String, TreasuryDirection, Long, String, Long, String) -> Unit,
    onTransfer: (String, String, Long, String) -> Unit,
    onReconcile: (String, Long, Long, String) -> Unit,
    onReverse: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var mode by remember { mutableStateOf(TreasuryFormMode.RECEIPT) }
    var accountId by remember { mutableStateOf(state.accounts.firstOrNull()?.id?.value.orEmpty()) }
    var targetAccountId by remember { mutableStateOf(state.accounts.drop(1).firstOrNull()?.id?.value.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var expected by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf("") }
    var sourceIntent by remember { mutableStateOf(TreasuryBusinessIntent.OTHER_INCOME) }
    var sourceId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var settlementDirection by remember { mutableStateOf(TreasuryDirection.RECEIPT) }
    var reversalTransactionId by remember { mutableStateOf<String?>(null) }
    var reversalReason by remember { mutableStateOf("") }

    reversalTransactionId?.let { transactionId ->
        AlertDialog(
            onDismissRequest = { if (!state.isBusy) { reversalTransactionId = null; reversalReason = "" } },
            title = { Text("برگشت تراکنش خزانه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("عملیات ثبت‌شده ویرایش نمی‌شود؛ یک تراکنش و سند حسابداری جبرانی ساخته خواهد شد.")
                    OutlinedTextField(
                        value = reversalReason,
                        onValueChange = { reversalReason = it.take(500) },
                        modifier = Modifier.fillMaxWidth().testTag("treasury_reverse_reason"),
                        label = { Text("دلیل برگشت") },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.isBusy && reversalReason.trim().length >= 3,
                    modifier = Modifier.testTag("treasury_reverse_confirm"),
                    onClick = {
                        onReverse(transactionId, reversalReason)
                        reversalTransactionId = null
                        reversalReason = ""
                    },
                ) { Text("ثبت برگشت") }
            },
            dismissButton = {
                TextButton(onClick = { reversalTransactionId = null; reversalReason = "" }, enabled = !state.isBusy) { Text("انصراف") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("treasury_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ErpModuleHeader(
                title = "خزانه‌داری",
                subtitle = "صندوق، بانک، دریافت، پرداخت و انتقال‌های ثبت‌شده",
                trailing = { TextButton(onClick = onBack) { Text("بازگشت") } },
            )
        }
        item {
            val summary = treasuryDashboardSummary(state)
            ErpDashboardHero(
                eyebrow = "موجودی حساب‌های خزانه",
                value = ErpDisplayFormatters.money(summary.totalBalanceRial),
                caption = "مانده از Ledger واقعی حساب‌های فعال محاسبه شده است",
                metrics = listOf(
                    ErpKpiItem("حساب فعال", ErpDisplayFormatters.integer(summary.activeAccountCount)),
                    ErpKpiItem("دریافت اخیر", ErpDisplayFormatters.money(summary.recentReceiptRial)),
                    ErpKpiItem("پرداخت اخیر", ErpDisplayFormatters.money(summary.recentPaymentRial)),
                ),
            )
        }
        item {
            SectionHeading("عملیات سریع", "هر عملیات از سرویس واقعی خزانه ثبت و در صورت لزوم سند حسابداری ایجاد می‌کند")
            ErpQuickActionsGrid(
                listOf(
                    ErpActionItem("دریافت", Icons.Outlined.CallReceived, ErpPalette.GreenSoft, ErpPalette.Green, onClick = { mode = TreasuryFormMode.RECEIPT }),
                    ErpActionItem("پرداخت", Icons.Outlined.CallMade, ErpPalette.RedSoft, ErpPalette.Red, onClick = { mode = TreasuryFormMode.PAYMENT }),
                    ErpActionItem("انتقال", Icons.Outlined.CompareArrows, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = { mode = TreasuryFormMode.TRANSFER }),
                    ErpActionItem("تسویه", Icons.Outlined.Handshake, ErpPalette.TealSoft, ErpPalette.Teal, onClick = { mode = TreasuryFormMode.SETTLEMENT }),
                    ErpActionItem("مغایرت", Icons.Outlined.FactCheck, ErpPalette.AmberSoft, ErpPalette.Amber, onClick = { mode = TreasuryFormMode.RECONCILIATION }),
                ),
            )
        }
        item {
            SectionHeading("ثبت عملیات", "حساب و نوع عملیات را انتخاب کنید")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TreasuryFormMode.entries, key = { it.name }) { item ->
                    FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.title) })
                }
            }
        }
        if (state.accounts.isEmpty()) {
            item { Text("هیچ حساب خزانه فعالی تعریف نشده است.", color = MaterialTheme.colorScheme.error) }
        } else {
            item {
                Text("حساب مبدأ/عملیاتی")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.accounts, key = { it.id.value }) { account ->
                        FilterChip(
                            selected = accountId == account.id.value,
                            onClick = { accountId = account.id.value },
                            label = { Text(account.name, maxLines = 1) },
                        )
                    }
                }
            }
            if (mode == TreasuryFormMode.TRANSFER) {
                item {
                    Text("حساب مقصد")
                    val destinationAccounts = state.accounts.filter { it.id.value != accountId }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(destinationAccounts, key = { it.id.value }) { account ->
                            FilterChip(
                                selected = targetAccountId == account.id.value,
                                onClick = { targetAccountId = account.id.value },
                                label = { Text(account.name, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        }
        if (mode == TreasuryFormMode.RECONCILIATION) {
            item {
                OutlinedTextField(expected, { expected = formatRialMoneyInput(it) }, Modifier.fillMaxWidth().testTag("treasury_expected_amount"), label = { Text("مانده مورد انتظار (ریال)") })
            }
            item {
                OutlinedTextField(actual, { actual = formatRialMoneyInput(it) }, Modifier.fillMaxWidth().testTag("treasury_actual_amount"), label = { Text("مانده واقعی (ریال)") })
            }
        } else {
            item {
                OutlinedTextField(amount, { amount = formatRialMoneyInput(it) }, Modifier.fillMaxWidth().testTag("treasury_amount"), label = { Text("مبلغ (ریال)") })
            }
        }
        if (mode != TreasuryFormMode.TRANSFER && mode != TreasuryFormMode.RECONCILIATION) {
            item {
                val direction = when (mode) {
                    TreasuryFormMode.RECEIPT -> TreasuryDirection.RECEIPT
                    TreasuryFormMode.PAYMENT -> TreasuryDirection.PAYMENT
                    TreasuryFormMode.SETTLEMENT -> settlementDirection
                    else -> null
                }
                val intents = when (mode) {
                    TreasuryFormMode.RECEIPT -> listOf(TreasuryBusinessIntent.OWNER_CAPITAL, TreasuryBusinessIntent.OTHER_INCOME)
                    TreasuryFormMode.PAYMENT -> listOf(TreasuryBusinessIntent.OPERATING_EXPENSE, TreasuryBusinessIntent.TAX_PAYMENT)
                    TreasuryFormMode.SETTLEMENT -> if (direction == TreasuryDirection.RECEIPT) {
                        listOf(TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION)
                    } else {
                        listOf(TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT)
                    }
                    else -> emptyList()
                }
                val effectiveIntent = sourceIntent.takeIf { it in intents } ?: intents.first()
                Text("نوع مرجع")
                LazyRow(Modifier.fillMaxWidth().testTag("treasury_source_type"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(intents, key = { it.storedValue }) { intent ->
                        FilterChip(
                            selected = effectiveIntent == intent,
                            onClick = { sourceIntent = intent },
                            modifier = Modifier.testTag("treasury_source_type_${intent.storedValue}"),
                            label = { Text(treasuryBusinessIntentTitle(intent), maxLines = 1) },
                        )
                    }
                }
            }
            item {
                OutlinedTextField(sourceId, { sourceId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().testTag("treasury_source_id"), label = { Text("شناسه شخص/مرجع") })
            }
        }
        if (mode == TreasuryFormMode.SETTLEMENT) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TreasuryDirection.entries, key = { it.name }) { direction ->
                        FilterChip(
                            selected = settlementDirection == direction,
                            onClick = { settlementDirection = direction },
                            label = { Text(if (direction == TreasuryDirection.RECEIPT) "تسویه دریافتنی" else "تسویه پرداختنی", maxLines = 1) },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth().testTag("treasury_reason"), label = { Text("شرح / دلیل") })
        }
        item {
            Button(
                enabled = !state.isBusy && accountId.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("treasury_submit"),
                onClick = {
                    val value = parseMoneyInputOrNull(amount) ?: 0L
                    val refId = normalizeNumberInput(sourceId).toLongOrNull() ?: 0L
                    val sourceType = when (mode) {
                        TreasuryFormMode.RECEIPT -> sourceIntent.takeIf { it in setOf(TreasuryBusinessIntent.OWNER_CAPITAL, TreasuryBusinessIntent.OTHER_INCOME) } ?: TreasuryBusinessIntent.OTHER_INCOME
                        TreasuryFormMode.PAYMENT -> sourceIntent.takeIf { it in setOf(TreasuryBusinessIntent.OPERATING_EXPENSE, TreasuryBusinessIntent.TAX_PAYMENT) } ?: TreasuryBusinessIntent.OPERATING_EXPENSE
                        TreasuryFormMode.SETTLEMENT -> if (settlementDirection == TreasuryDirection.RECEIPT) TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION else TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT
                        else -> null
                    }?.storedValue.orEmpty()
                    when (mode) {
                        TreasuryFormMode.RECEIPT -> onReceipt(accountId, value, sourceType, refId, reason)
                        TreasuryFormMode.PAYMENT -> onPayment(accountId, value, sourceType, refId, reason)
                        TreasuryFormMode.SETTLEMENT -> onSettlement(accountId, settlementDirection, value, sourceType, refId, reason)
                        TreasuryFormMode.TRANSFER -> onTransfer(accountId, targetAccountId, value, reason)
                        TreasuryFormMode.RECONCILIATION -> onReconcile(accountId, parseMoneyInputOrNull(expected) ?: 0L, parseMoneyInputOrNull(actual) ?: 0L, reason)
                    }
                },
            ) { Text(if (state.isBusy) "در حال ثبت…" else "ثبت عملیات") }
        }
        state.message?.let { message ->
            item { Text(message, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        }
        item { SectionHeading("گردش اخیر", "آخرین تراکنش‌های ثبت‌شده خزانه") }
        if (state.transactions.isEmpty()) {
            item { ErpStatePanel("هنوز تراکنشی ثبت نشده", "اولین دریافت، پرداخت یا انتقال پس از ثبت در این قسمت نمایش داده می‌شود.") }
        } else {
            items(state.transactions, key = { it.id }) { transaction ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${treasuryKindTitle(transaction.kind)} · ${ErpDisplayFormatters.money(transaction.amountRial)}")
                        Text("${treasuryStatusTitle(transaction.status)} · ${ErpDisplayFormatters.activityDateTime(transaction.businessEpochDay, transaction.createdAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                        Text(transaction.reason, style = MaterialTheme.typography.bodySmall)
                        if (transaction.status == "POSTED" && transaction.journalEntryId != null) {
                            TextButton(
                                modifier = Modifier.testTag("treasury_reverse_${transaction.id}"),
                                onClick = { reversalTransactionId = transaction.id },
                            ) { Text("برگشت با سند جبرانی") }
                        }
                    }
                }
            }
        }
    }
}

private fun treasuryBusinessIntentTitle(intent: TreasuryBusinessIntent): String = when (intent) {
    TreasuryBusinessIntent.OWNER_CAPITAL -> "آورده مالک"
    TreasuryBusinessIntent.OTHER_INCOME -> "سایر درآمد"
    TreasuryBusinessIntent.OPERATING_EXPENSE -> "هزینه عملیاتی"
    TreasuryBusinessIntent.TAX_PAYMENT -> "پرداخت مالیات"
    TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION -> "وصول مطالبه مشتری"
    TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT -> "تسویه بدهی خرید"
    else -> intent.storedValue
}

private fun treasuryKindTitle(kind: ir.restaurant.management.domain.treasury.TreasuryTransactionKind): String = when (kind) {
    ir.restaurant.management.domain.treasury.TreasuryTransactionKind.RECEIPT -> "دریافت"
    ir.restaurant.management.domain.treasury.TreasuryTransactionKind.PAYMENT -> "پرداخت"
    ir.restaurant.management.domain.treasury.TreasuryTransactionKind.INTERNAL_TRANSFER -> "انتقال داخلی"
    ir.restaurant.management.domain.treasury.TreasuryTransactionKind.SETTLEMENT -> "تسویه"
    ir.restaurant.management.domain.treasury.TreasuryTransactionKind.RECONCILIATION -> "مغایرت‌گیری"
}

private fun treasuryStatusTitle(status: String): String = when (status) {
    "POSTED" -> "ثبت‌شده"
    "REVERSED" -> "برگشت‌شده"
    else -> status
}
