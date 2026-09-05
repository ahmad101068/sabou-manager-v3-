package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodClosureDetails
import ir.restaurant.management.domain.operations.InventoryPeriodClosureRecord
import ir.restaurant.management.domain.operations.InventoryPeriodStatus

@Composable
internal fun InventoryPeriodCenterScreen(
    state: OperationsUiState,
    onClose: (InventoryPeriodCloseDraft, String, () -> Unit) -> Unit,
    onReopen: (Long, String, String, () -> Unit) -> Unit,
    onSelectClosure: (Long?) -> Unit,
) {
    var showClose by remember { mutableStateOf(false) }
    var reopenTarget by remember { mutableStateOf<InventoryPeriodClosureRecord?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MessageCard("دوره بسته، ثبت گردش Backdated را متوقف می‌کند. حسابداری و دوره انبار دو کنترل مستقل باقی مانده‌اند.") }
        item { Button(onClick = { showClose = true }, enabled = !state.busy) { Text("بستن دوره انبار") } }
        item { SectionHeading("سوابق دوره", "بازگشایی فقط با PIN مالک، دلیل اجباری و Audit انجام می‌شود") }
        if (state.inventoryPeriodClosures.isEmpty()) item { InventoryEmptyState("دوره بسته‌ای وجود ندارد.") }
        items(state.inventoryPeriodClosures, key = { "period-${it.id}" }) { closure ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${epochDayToPersian(closure.fromEpochDay).display()} تا ${epochDayToPersian(closure.toEpochDay).display()}", fontWeight = FontWeight.Bold)
                    CompactInfoRow("پایان شمارش‌شده", formatMoney(closure.countedClosingValueRial), true)
                    CompactInfoRow("مغایرت", formatMoney(closure.varianceValueRial), closure.varianceValueRial != 0L)
                    StatusPill(
                        text = if (closure.status == InventoryPeriodStatus.CLOSED) "بسته" else "بازگشایی‌شده",
                        containerColor = if (closure.status == InventoryPeriodStatus.REOPENED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (closure.status == InventoryPeriodStatus.REOPENED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    OutlinedButton(onClick = { onSelectClosure(closure.id) }, modifier = Modifier.fillMaxWidth()) { Text("جزئیات و گزارش") }
                    if (closure.status == InventoryPeriodStatus.CLOSED) OutlinedButton(
                        onClick = { reopenTarget = closure },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("بازگشایی کنترل‌شده") }
                }
            }
        }
    }
    if (showClose) InventoryPeriodClose2Dialog(
        lastClosureEnd = state.inventoryPeriodClosures.maxOfOrNull { it.toEpochDay },
        busy = state.busy,
        onDismiss = { showClose = false },
        onSave = { draft, pin -> onClose(draft, pin) { showClose = false } },
    )
    reopenTarget?.let { closure ->
        InventoryPeriodReopenDialog(
            closure = closure,
            busy = state.busy,
            onDismiss = { reopenTarget = null },
            onConfirm = { reason, pin -> onReopen(closure.id, reason, pin) { reopenTarget = null } },
        )
    }
    state.selectedInventoryClosureDetails?.let { details ->
        InventoryPeriodDetails2Dialog(details) { onSelectClosure(null) }
    }
}

@Composable
private fun InventoryPeriodClose2Dialog(
    lastClosureEnd: Long?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (InventoryPeriodCloseDraft, String) -> Unit,
) {
    var from by remember(lastClosureEnd) { mutableLongStateOf(lastClosureEnd?.plus(1) ?: (currentEpochDay() - 30)) }
    var to by remember { mutableLongStateOf(currentEpochDay()) }
    var note by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بستن قطعی دوره انبار") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageCard("برای همه کالاهای فعال باید در روز پایان، شمارش معتبر وجود داشته باشد.")
                error?.let { MessageCard(it, true) }
                PersianDateField("شروع دوره", from) { from = it }
                PersianDateField("پایان و تاریخ شمارش", to) { to = it }
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("یادداشت") })
                SensitivePinField(pin, { pin = it })
            }
        },
        confirmButton = {
            Button(enabled = !busy && pin.length in 6..12, onClick = {
                runCatching { InventoryPeriodCloseDraft(from, to, note).validated() }
                    .onSuccess { draft -> val value = pin; pin = ""; onSave(draft, value) }
                    .onFailure { error = it.message }
            }) { Text("بستن و قفل‌کردن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun InventoryPeriodReopenDialog(
    closure: InventoryPeriodClosureRecord,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var reason by remember(closure.id) { mutableStateOf("") }
    var pin by remember(closure.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازگشایی دوره بسته") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("این عملیات Audit می‌شود و پس از اصلاح باید دوره دوباره بسته شود.")
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل اجباری") }, minLines = 3)
                SensitivePinField(pin, { pin = it })
            }
        },
        confirmButton = { Button(enabled = !busy && reason.trim().length >= 5 && pin.length in 6..12, onClick = { val value = pin; pin = ""; onConfirm(reason.trim(), value) }) { Text("بازگشایی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun InventoryPeriodDetails2Dialog(details: InventoryPeriodClosureDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش قطعی دوره") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("${epochDayToPersian(details.closure.fromEpochDay).display()} تا ${epochDayToPersian(details.closure.toEpochDay).display()}", fontWeight = FontWeight.Bold) }
                item { CompactInfoRow("اول دوره", formatMoney(details.closure.openingValueRial)) }
                item { CompactInfoRow("پایان انتظار", formatMoney(details.closure.expectedClosingValueRial)) }
                item { CompactInfoRow("پایان شمارش", formatMoney(details.closure.countedClosingValueRial)) }
                item { CompactInfoRow("مغایرت", formatMoney(details.closure.varianceValueRial), details.closure.varianceValueRial != 0L) }
                items(details.lines, key = { it.itemId }) { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(line.itemName, fontWeight = FontWeight.Bold)
                            Text("انتظار ${formatQuantity(line.expectedClosingQuantityMicros)} · شمارش ${formatQuantity(line.countedClosingQuantityMicros)} ${line.unit}")
                            Text("مغایرت ${formatMoney(line.varianceValueRial)}")
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { printInventoryPeriodClosure(context, details) }) { Text("چاپ / PDF") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
