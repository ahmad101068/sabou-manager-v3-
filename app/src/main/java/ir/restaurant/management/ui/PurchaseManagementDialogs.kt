package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.purchase.PurchaseDetails
import ir.restaurant.management.domain.purchase.PurchaseReversalDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementRecord
import ir.restaurant.management.domain.purchase.PurchaseSettlementReversalDraft
import ir.restaurant.management.domain.purchase.SettlementPaymentMethod
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus

@Composable
internal fun PurchaseDetailsDialog(
    details: PurchaseDetails,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSettle: (PurchaseSettlementDraft, () -> Unit) -> Unit,
    onReverseSettlement: (PurchaseSettlementReversalDraft, () -> Unit) -> Unit,
    onReverse: (PurchaseReversalDraft, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var settlementOpen by remember(details.id, details.paidRial) { mutableStateOf(false) }
    var selectedSettlementForReversal by remember(details.id, details.paidRial) {
        mutableStateOf<PurchaseSettlementRecord?>(null)
    }
    var reversalOpen by remember(details.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فاکتور ${details.invoiceNo}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(details.supplierName, fontWeight = FontWeight.Bold)
                Text("تاریخ خرید: ${epochDayToPersian(details.purchaseEpochDay).display()}")
                Text("سررسید: ${epochDayToPersian(details.dueEpochDay).display()}")
                Text("مبلغ کل: ${formatMoney(details.totalRial)}")
                Text("پرداخت‌شده: ${formatMoney(details.paidRial)}")
                Text("مانده: ${formatMoney(details.outstandingRial)}")
                Text("وضعیت: ${purchaseStatusTitle(details.paymentStatus)}")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("اقلام فاکتور", style = MaterialTheme.typography.titleSmall)
                details.lines.forEach { line ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(line.itemName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatQuantity(line.quantityMicros)} ${line.unit} × " +
                                "${formatMoney(line.unitCostRial)} = ${formatMoney(line.lineTotalRial)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (details.settlements.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("تاریخچه تسویه", style = MaterialTheme.typography.titleSmall)
                    details.settlements.forEach { settlement ->
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "${settlement.entryNo} · " +
                                    epochDayToPersian(settlement.settlementEpochDay).display(),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("${formatMoney(settlement.amountRial)} · ${settlement.paymentMethod.title}")
                            if (settlement.referenceNo.isNotBlank()) {
                                Text("پیگیری: ${settlement.referenceNo}")
                            }
                            if (settlement.notes.isNotBlank()) Text(settlement.notes)
                            if (settlement.isReversed) {
                                Text("برگشت‌خورده", color = MaterialTheme.colorScheme.error)
                            } else {
                                TextButton(
                                    onClick = { selectedSettlementForReversal = settlement },
                                    enabled = !busy,
                                ) { Text("برگشت این تسویه") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (details.canSettle) {
                Button(
                    onClick = { settlementOpen = true },
                    enabled = !busy,
                ) { Text("ثبت تسویه") }
            } else {
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { printPurchaseInvoice(context, details) }) { Text("چاپ / PDF") }
                if (details.canReverse) {
                    TextButton(
                        onClick = { reversalOpen = true },
                        enabled = !busy,
                    ) { Text("برگشت فاکتور") }
                }
                if (details.canSettle) {
                    TextButton(onClick = onDismiss) { Text("بستن") }
                }
            }
        },
    )

    if (settlementOpen) {
        SettlementDialog(
            details = details,
            busy = busy,
            onDismiss = { settlementOpen = false },
            onConfirm = { draft ->
                onSettle(draft) { settlementOpen = false }
            },
        )
    }
    selectedSettlementForReversal?.let { settlement ->
        SettlementReversalDialog(
            details = details,
            settlement = settlement,
            busy = busy,
            onDismiss = { selectedSettlementForReversal = null },
            onConfirm = { draft ->
                onReverseSettlement(draft) { selectedSettlementForReversal = null }
            },
        )
    }
    if (reversalOpen) {
        PurchaseReversalDialog(
            details = details,
            busy = busy,
            onDismiss = { reversalOpen = false },
            onConfirm = { draft ->
                onReverse(draft) { reversalOpen = false }
            },
        )
    }
}

@Composable
private fun SettlementDialog(
    details: PurchaseDetails,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseSettlementDraft) -> Unit,
) {
    val today = remember { currentEpochDay() }
    var epochDay by remember { mutableLongStateOf(today.coerceAtLeast(details.purchaseEpochDay)) }
    var amount by remember(details.paidRial) { mutableStateOf(formatMoneyInputFromRial(details.outstandingRial)) }
    var method by remember { mutableStateOf(SettlementPaymentMethod.TRANSFER) }
    var referenceNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember(details.id) { ir.restaurant.management.core.GlobalId.new().value }
    val methods = SettlementPaymentMethod.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت تسویه فاکتور") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("مانده قابل پرداخت: ${formatMoney(details.outstandingRial)}")
                error?.let { MessageCard(it, isError = true) }
                PersianDateField("تاریخ تسویه", epochDay) { epochDay = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = formatMoneyInput(it) },
                    label = { Text("مبلغ پرداختی (${currencyUnitLabel()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectionField(
                    label = "روش پرداخت",
                    selectedText = method.title,
                    options = methods.mapIndexed { index, value -> index.toLong() to value.title },
                    onSelected = { method = methods[it.toInt()] },
                )
                OutlinedTextField(
                    value = referenceNo,
                    onValueChange = { referenceNo = it },
                    label = { Text("شماره پیگیری (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val draft = PurchaseSettlementDraft(
                            purchaseId = details.id,
                            settlementEpochDay = epochDay,
                            amount = parseMoneyRial(amount),
                            paymentMethod = method,
                            referenceNo = referenceNo,
                            notes = notes,
                            commandId = commandId,
                        ).validated()
                        require(draft.amount.value <= details.outstandingRial) {
                            "مبلغ از مانده فاکتور بیشتر است."
                        }
                        error = null
                        onConfirm(draft)
                    } catch (exception: Exception) {
                        error = exception.message ?: "اطلاعات تسویه معتبر نیست."
                    }
                },
            ) { Text("ثبت قطعی") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PurchaseReversalDialog(
    details: PurchaseDetails,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseReversalDraft) -> Unit,
) {
    val today = remember { currentEpochDay() }
    var epochDay by remember { mutableLongStateOf(today.coerceAtLeast(details.purchaseEpochDay)) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت کنترل‌شده فاکتور") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "این عملیات موجودی خرید را کم می‌کند و سند حسابداری معکوس می‌سازد؛ سابقه حذف نمی‌شود.",
                )
                error?.let { MessageCard(it, isError = true) }
                PersianDateField("تاریخ برگشت", epochDay) { epochDay = it }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل برگشت") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val draft = PurchaseReversalDraft(details.id, epochDay, reason).validated()
                        error = null
                        onConfirm(draft)
                    } catch (exception: Exception) {
                        error = exception.message ?: "اطلاعات برگشت معتبر نیست."
                    }
                },
            ) { Text("تأیید برگشت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun purchaseStatusTitle(status: PurchasePaymentStatus): String = when (status) {
    PurchasePaymentStatus.PAID -> "تسویه‌شده"
    PurchasePaymentStatus.PARTIAL -> "پرداخت ناقص"
    PurchasePaymentStatus.REVERSED -> "برگشت‌خورده"
    PurchasePaymentStatus.UNPAID -> "تسویه‌نشده"
    PurchasePaymentStatus.LEGACY_UNKNOWN -> "وضعیت قدیمی نیازمند بررسی"
}


@Composable
private fun SettlementReversalDialog(
    details: PurchaseDetails,
    settlement: PurchaseSettlementRecord,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseSettlementReversalDraft) -> Unit,
) {
    val today = remember { currentEpochDay() }
    var epochDay by remember(settlement.journalEntryId) {
        mutableLongStateOf(today.coerceAtLeast(settlement.settlementEpochDay))
    }
    var reason by remember(settlement.journalEntryId) { mutableStateOf("") }
    var error by remember(settlement.journalEntryId) { mutableStateOf<String?>(null) }
    val commandId = remember(settlement.journalEntryId) { ir.restaurant.management.core.GlobalId.new().value }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت تسویه ${settlement.entryNo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("مبلغ: ${formatMoney(settlement.amountRial)}")
                Text("این عملیات سند تسویه را حذف نمی‌کند و یک سند معکوس قابل پیگیری می‌سازد.")
                error?.let { MessageCard(it, isError = true) }
                PersianDateField("تاریخ برگشت", epochDay) { epochDay = it }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل برگشت تسویه") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val draft = PurchaseSettlementReversalDraft(
                            purchaseId = details.id,
                            settlementJournalEntryId = settlement.journalEntryId,
                            reversalEpochDay = epochDay,
                            reason = reason,
                            commandId = commandId,
                        ).validated()
                        error = null
                        onConfirm(draft)
                    } catch (exception: Exception) {
                        error = exception.message ?: "اطلاعات برگشت تسویه معتبر نیست."
                    }
                },
            ) { Text("ثبت برگشت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
