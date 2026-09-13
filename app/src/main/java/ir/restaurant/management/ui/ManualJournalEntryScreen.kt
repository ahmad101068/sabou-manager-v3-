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

private data class JournalLineForm(
    val rowId: Int,
    val accountCode: String? = null,
    val debitRial: String = "",
    val creditRial: String = "",
    val memo: String = "",
)

@Composable
fun ManualJournalEntryScreen(
    state: AccountingUiState,
    onPost: (ManualJournalDraft, (PostedJournal) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val today = remember { currentEpochDay() }
    var description by remember { mutableStateOf("") }
    var epochDay by remember { mutableLongStateOf(today) }
    var nextRowId by remember { mutableIntStateOf(3) }
    var lines by remember {
        mutableStateOf(
            listOf(
                JournalLineForm(rowId = 1),
                JournalLineForm(rowId = 2),
            ),
        )
    }
    var localError by remember { mutableStateOf<String?>(null) }
    val commandId = rememberSaveable { ir.restaurant.management.core.GlobalId.new().value }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "ثبت سند حسابداری",
                actionLabel = null,
                onAction = {},
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { MessageCard(it) }
            localError?.let { MessageCard(it, isError = true) }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("شرح سند") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PersianDateField(
                label = "تاریخ سند",
                epochDay = epochDay,
                onSelected = { epochDay = it },
            )
            Text(
                "آرتیکل‌های سند",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            lines.forEachIndexed { index, line ->
                JournalLineEditor(
                    index = index,
                    line = line,
                    accounts = state.accounts,
                    removable = lines.size > 2,
                    onChanged = { changed ->
                        lines = lines.map { current ->
                            if (current.rowId == line.rowId) changed else current
                        }
                    },
                    onRemove = {
                        lines = lines.filterNot { it.rowId == line.rowId }
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    lines = lines + JournalLineForm(rowId = nextRowId)
                    nextRowId++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("افزودن آرتیکل")
            }
            val debitPreview = enteredTotal(lines.map { it.debitRial })
            val creditPreview = enteredTotal(lines.map { it.creditRial })
            if (debitPreview != null && creditPreview != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (debitPreview == creditPreview) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("جمع بدهکار: ${formatMoney(debitPreview)}")
                        Text("جمع بستانکار: ${formatMoney(creditPreview)}")
                        Text(
                            if (debitPreview == creditPreview) "سند تراز است" else "سند تراز نیست",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Button(
                enabled = !state.busy && state.accounts.isNotEmpty(),
                onClick = {
                    try {
                        localError = null
                        val draft = ManualJournalDraft(
                            description = description,
                            entryEpochDay = epochDay,
                            lines = lines.map { line ->
                                JournalLineDraft(
                                    accountCode = line.accountCode
                                        ?: error("حساب همه آرتیکل‌ها را انتخاب کنید."),
                                    debit = parseOptionalMoney(line.debitRial),
                                    credit = parseOptionalMoney(line.creditRial),
                                    memo = line.memo,
                                )
                            },
                            commandId = commandId,
                        )
                        draft.validated()
                        onPost(draft) { onBack() }
                    } catch (failure: Exception) {
                        localError = failure.message ?: "اطلاعات سند معتبر نیست."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "در حال ثبت…" else "ثبت نهایی سند")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun JournalLineEditor(
    index: Int,
    line: JournalLineForm,
    accounts: List<AccountBalanceRecord>,
    removable: Boolean,
    onChanged: (JournalLineForm) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("آرتیکل ${index + 1}", fontWeight = FontWeight.Bold)
                if (removable) {
                    TextButton(onClick = onRemove) { Text("حذف ردیف") }
                }
            }
            SelectionField(
                label = "حساب",
                selectedText = accounts.firstOrNull { it.code == line.accountCode }?.let {
                    "${it.code} · ${it.name}"
                },
                options = accounts.map { account ->
                    account.code.toLong() to "${account.code} · ${account.name}"
                },
                onSelected = { selected ->
                    onChanged(line.copy(accountCode = selected.toString()))
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formatMoneyInput(line.debitRial),
                    onValueChange = { onChanged(line.copy(debitRial = formatMoneyInput(it))) },
                    label = { Text("بدهکار (${currencyUnitLabel()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = formatMoneyInput(line.creditRial),
                    onValueChange = { onChanged(line.copy(creditRial = formatMoneyInput(it))) },
                    label = { Text("بستانکار (${currencyUnitLabel()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = line.memo,
                onValueChange = { onChanged(line.copy(memo = it)) },
                label = { Text("شرح آرتیکل (اختیاری)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun parseOptionalMoney(value: String): MoneyRial =
    if (value.isBlank()) MoneyRial.ZERO else parseMoneyRial(value)

private fun enteredTotal(values: List<String>): Long? = try {
    values.fold(0L) { total, value ->
        SignedLongMath.add(total, parseOptionalMoney(value).value)
    }
} catch (_: Exception) {
    null
}


