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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WarningAmber
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

private enum class AccountingTab { DOCUMENTS, ACCOUNTS, REPORTS }

@Composable
fun AccountingScreen(
    state: AccountingUiState,
    onSearch: (String) -> Unit,
    onSelectJournal: (Long?) -> Unit,
    onSelectLedger: (String?) -> Unit,
    onSaveAccount: (String?, AccountDraft, () -> Unit) -> Unit,
    onDeactivateAccount: (String) -> Unit,
    onReverse: (Long, Long, String) -> Unit,
    onSetProfitLossRange: (Long, Long) -> Unit,
    onAddJournal: () -> Unit,
    onOpenTreasury: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AccountingTab.DOCUMENTS) }
    var accountEditor by remember { mutableStateOf<AccountBalanceRecord?>(null) }
    var newAccountEditorOpen by remember { mutableStateOf(false) }
    var deactivateTarget by remember { mutableStateOf<AccountBalanceRecord?>(null) }
    var reversalTarget by remember { mutableStateOf<JournalDetails?>(null) }

    Scaffold(
        topBar = {
            ProfessionalTopBar(
                title = "حسابداری",
                subtitle = "اسناد، دفتر کل، تراز و گزارش‌های مالی",
                onBack = onBack,
                actionLabel = if (tab == AccountingTab.DOCUMENTS) "سند جدید" else null,
                onAction = if (tab == AccountingTab.DOCUMENTS) onAddJournal else null,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { MessageCard(it) }
            AccountingOverview(state)
            SectionHeading("عملیات سریع", "ورود مستقیم به عملیات واقعی حسابداری")
            ErpQuickActionsGrid(
                listOf(
                    ErpActionItem("ثبت سند", Icons.Outlined.AddCard, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = onAddJournal),
                    ErpActionItem("دفتر کل", Icons.Outlined.AccountBalance, ErpPalette.TealSoft, ErpPalette.Teal, onClick = { tab = AccountingTab.ACCOUNTS }),
                    ErpActionItem("گزارش‌ها", Icons.Outlined.Assessment, ErpPalette.AmberSoft, ErpPalette.Amber, onClick = { tab = AccountingTab.REPORTS }),
                    ErpActionItem("خزانه", Icons.Outlined.ReceiptLong, ErpPalette.GreenSoft, ErpPalette.Green, onClick = onOpenTreasury),
                ),
            )
            if (!state.trialBalance.isBalanced) {
                ErpAttentionRow(
                    title = "تراز حسابداری نیازمند بررسی است",
                    description = "جمع بدهکار و بستانکار برابر نیست؛ قبل از ادامه گزارش‌های مالی را بررسی کنید.",
                    icon = Icons.Outlined.WarningAmber,
                    accent = ErpPalette.Red,
                    soft = ErpPalette.RedSoft,
                    onClick = { tab = AccountingTab.REPORTS },
                )
            }
            AccountingTabs(tab) { tab = it }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    AccountingTab.DOCUMENTS -> JournalList(state.journals, state.journalSearch, onSearch, onSelectJournal)
                    AccountingTab.ACCOUNTS -> AccountList(
                        accounts = state.accounts,
                        onNew = { newAccountEditorOpen = true },
                        onEdit = { accountEditor = it },
                        onDeactivate = { deactivateTarget = it },
                        onLedger = onSelectLedger,
                    )
                    AccountingTab.REPORTS -> AccountingReports(state, onSetProfitLossRange)
                }
            }
        }
    }

    state.selectedJournal?.let { details ->
        JournalDetailsDialog(details, { onSelectJournal(null) }) {
            reversalTarget = details
            onSelectJournal(null)
        }
    }
    state.selectedLedgerCode?.let { code ->
        LedgerDialog(state.accounts.firstOrNull { it.code == code }, state.ledgerRows) { onSelectLedger(null) }
    }
    if (newAccountEditorOpen || accountEditor != null) {
        AccountEditorDialog(
            existing = accountEditor,
            busy = state.busy,
            onDismiss = { newAccountEditorOpen = false; accountEditor = null },
            onSave = { draft ->
                onSaveAccount(accountEditor?.code, draft) {
                    newAccountEditorOpen = false
                    accountEditor = null
                }
            },
        )
    }
    deactivateTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deactivateTarget = null },
            title = { Text("غیرفعال‌کردن حساب") },
            text = { Text("حساب «${account.name}» فقط در صورت صفر بودن مانده غیرفعال می‌شود و گردش‌های قبلی حفظ خواهند شد.") },
            confirmButton = {
                Button(enabled = !state.busy, onClick = { deactivateTarget = null; onDeactivateAccount(account.code) }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { deactivateTarget = null }) { Text("انصراف") } },
        )
    }
    reversalTarget?.let { details ->
        ReversalDialog(
            details = details,
            busy = state.busy,
            onDismiss = { reversalTarget = null },
            onConfirm = { epochDay, reason ->
                reversalTarget = null
                onSelectJournal(null)
                onReverse(details.id, epochDay, reason)
            },
        )
    }
}

@Composable
private fun AccountingOverview(state: AccountingUiState) {
    val summary = accountingDashboardSummary(state)
    ErpDashboardHero(
        eyebrow = "مانده نقد و بانک",
        value = ErpDisplayFormatters.money(summary.cashAndBankRial),
        caption = "سود خالص بازه ${epochDayToPersian(state.profitLossFromEpochDay).display()} تا ${epochDayToPersian(state.profitLossToEpochDay).display()}",
        metrics = listOf(
            ErpKpiItem("دریافتنی", ErpDisplayFormatters.money(summary.receivablesRial)),
            ErpKpiItem("پرداختنی", ErpDisplayFormatters.money(summary.payablesRial)),
            ErpKpiItem("سود/زیان", ErpDisplayFormatters.money(summary.netProfitRial)),
        ),
    )
}

@Composable
private fun AccountingTabs(selected: AccountingTab, onSelected: (AccountingTab) -> Unit) {
    val tabs = listOf(
        AccountingTab.DOCUMENTS to "اسناد",
        AccountingTab.ACCOUNTS to "حساب‌ها",
        AccountingTab.REPORTS to "گزارش‌ها",
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { (tab, title) ->
                if (selected == tab) {
                    Button(
                        onClick = { onSelected(tab) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                    ) { Text(title, maxLines = 1) }
                } else {
                    TextButton(
                        onClick = { onSelected(tab) },
                        modifier = Modifier.weight(1f),
                    ) { Text(title, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun JournalList(journals: List<JournalSummary>, search: String, onSearch: (String) -> Unit, onSelect: (Long) -> Unit) {
    var statusFilter by rememberSaveable { mutableStateOf("ACTIVE") }
    val visibleJournals = journals.filter { journal -> when (statusFilter) { "REVERSED" -> journal.isReversed; "MANUAL" -> !journal.isReversed && journal.sourceType == "MANUAL"; "ALL" -> true; else -> !journal.isReversed } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("جست‌وجوی شماره، شرح یا حساب") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(statusFilter == "ACTIVE", { statusFilter = "ACTIVE" }, { Text("فعال") })
            FilterChip(statusFilter == "MANUAL", { statusFilter = "MANUAL" }, { Text("دستی") })
            FilterChip(statusFilter == "REVERSED", { statusFilter = "REVERSED" }, { Text("برگشتی") })
            FilterChip(statusFilter == "ALL", { statusFilter = "ALL" }, { Text("همه") })
        }
        Text("${visibleJournals.size} سند", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (visibleJournals.isEmpty()) {
            EmptyStatePanel(
                if (journals.isEmpty() && search.isBlank()) "هنوز سندی ثبت نشده" else "نتیجه‌ای با این جست‌وجو و فیلتر پیدا نشد",
                if (journals.isEmpty() && search.isBlank()) "برای شروع، یک سند جدید ثبت کنید." else "فیلتر را تغییر دهید یا عبارت جست‌وجو را پاک کنید.",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleJournals, key = { it.id }) { journal ->
                    ElevatedCard(
                        onClick = { onSelect(journal.id) },
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(journal.entryNo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text(epochDayToPersian(journal.entryEpochDay).display(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(if (journal.isReversed) "برگشت‌خورده" else sourceTitle(journal.sourceType))
                            }
                            Text(journal.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("جمع سند")
                                    Text(formatMoney(journal.totalDebitRial), fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountList(
    accounts: List<AccountBalanceRecord>,
    onNew: () -> Unit,
    onEdit: (AccountBalanceRecord) -> Unit,
    onDeactivate: (AccountBalanceRecord) -> Unit,
    onLedger: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("افزودن حساب تفصیلی") }
        if (accounts.isEmpty()) {
            EmptyStatePanel("حسابی وجود ندارد", "یک حساب تفصیلی جدید بسازید.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accounts, key = { it.code }) { account ->
                    ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${account.code} · ${account.type.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (account.isSystem) StatusPill("سیستمی")
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricTile("بدهکار", formatMoney(account.debitTurnoverRial), Modifier.widthIn(min = 135.dp))
                                MetricTile("بستانکار", formatMoney(account.creditTurnoverRial), Modifier.widthIn(min = 135.dp))
                                MetricTile("مانده", accountBalanceText(account), Modifier.widthIn(min = 155.dp))
                            }
                            Column(Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { onLedger(account.code) }, modifier = Modifier.fillMaxWidth()) { Text("مشاهده دفتر حساب") }
                                if (!account.isSystem) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { onEdit(account) }, modifier = Modifier.weight(1f)) { Text("ویرایش") }
                                        TextButton(onClick = { onDeactivate(account) }, modifier = Modifier.weight(1f)) { Text("غیرفعال‌کردن") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalDetailsDialog(
    details: JournalDetails,
    onDismiss: () -> Unit,
    onReverse: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سند ${details.entryNo}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(details.description, fontWeight = FontWeight.Bold)
                Text("تاریخ: ${epochDayToPersian(details.entryEpochDay).display()}")
                Text("نوع: ${sourceTitle(details.sourceType)}")
                if (details.isReversed) {
                    Text(
                        "این سند با یک سند برگشت خنثی شده است.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                details.lines.forEach { line ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "${line.accountCode} · ${line.accountName}",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "بدهکار: ${
                                    if (line.debitRial == 0L) "—" else formatMoney(line.debitRial)
                                }",
                            )
                            Text(
                                "بستانکار: ${
                                    if (line.creditRial == 0L) "—" else formatMoney(line.creditRial)
                                }",
                            )
                            if (line.memo.isNotBlank()) Text("شرح: ${line.memo}")
                        }
                    }
                }
                Text("جمع بدهکار: ${formatMoney(details.totalDebitRial)}")
                Text("جمع بستانکار: ${formatMoney(details.totalCreditRial)}")
                if (details.sourceType != "MANUAL" && details.sourceType != "REVERSAL") {
                    Text(
                        "برای اصلاح این سند باید فاکتور یا عملیات مبدأ اصلاح شود.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = { printJournal(context, details) }) { Text("چاپ / PDF") }
                if (details.canReverse) {
                    TextButton(onClick = onReverse) { Text("ثبت برگشت") }
                }
                Button(onClick = onDismiss) { Text("بستن") }
            }
        },
    )
}

@Composable
private fun AccountEditorDialog(
    existing: AccountBalanceRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (AccountDraft) -> Unit,
) {
    var code by remember(existing?.code) { mutableStateOf(existing?.code.orEmpty()) }
    var name by remember(existing?.code) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(existing?.code) { mutableStateOf(existing?.type ?: AccountType.EXPENSE) }
    var error by remember(existing?.code) { mutableStateOf<String?>(null) }
    val types = AccountType.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "حساب جدید" else "ویرایش حساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    enabled = existing == null,
                    label = { Text("کد چهاررقمی حساب") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام حساب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectionField(
                    label = "نوع حساب",
                    selectedText = type.title,
                    options = types.mapIndexed { index, value ->
                        index.toLong() to value.title
                    },
                    onSelected = { type = types[it.toInt()] },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val draft = AccountDraft(
                            code = normalizeNumberInput(code),
                            name = name,
                            type = type,
                        ).validated()
                        onSave(draft)
                    } catch (failure: Exception) {
                        error = failure.message ?: "اطلاعات حساب معتبر نیست."
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ReversalDialog(
    details: JournalDetails,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    val today = remember { currentEpochDay() }
    var epochDay by remember(details.id) {
        mutableLongStateOf(maxOf(today, details.entryEpochDay))
    }
    var reason by remember(details.id) { mutableStateOf("") }
    var error by remember(details.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت سند ${details.entryNo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سند اصلی حذف نمی‌شود و یک سند معکوس برای حفظ سابقه ثبت خواهد شد.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PersianDateField(
                    label = "تاریخ برگشت",
                    epochDay = epochDay,
                    onSelected = { epochDay = it },
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل برگشت") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    if (reason.trim().length < 3) {
                        error = "دلیل برگشت را کامل وارد کنید."
                    } else {
                        onConfirm(epochDay, reason)
                    }
                },
            ) { Text("ثبت سند برگشت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun sourceTitle(sourceType: String): String = when (sourceType) {
    "PURCHASE" -> "خرید"
    "MANUAL" -> "سند دستی"
    "REVERSAL" -> "سند برگشت"
    "SALE" -> "فروش"
    "PAYROLL" -> "حقوق"
    else -> sourceType
}

internal fun accountBalanceText(account: AccountBalanceRecord): String = when {
    account.debitBalanceRial > 0 -> "مانده بدهکار: ${formatMoney(account.debitBalanceRial)}"
    account.creditBalanceRial > 0 -> "مانده بستانکار: ${formatMoney(account.creditBalanceRial)}"
    else -> "مانده: صفر"
}

internal fun signedBalanceText(balanceRial: Long): String = when {
    balanceRial > 0 -> "مانده پس از سند: بدهکار ${formatMoney(balanceRial)}"
    balanceRial < 0 ->
        "مانده پس از سند: بستانکار ${formatMoney(balanceRial).removePrefix("−")}"
    else -> "مانده پس از سند: صفر"
}
