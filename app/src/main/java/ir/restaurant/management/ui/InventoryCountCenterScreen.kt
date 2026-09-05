package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.InventoryCountLineView
import ir.restaurant.management.domain.inventory.InventoryCountSession
import ir.restaurant.management.domain.inventory.InventoryCountStatus
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun InventoryCountCenterScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
) {
    var showCreate by remember { mutableStateOf(false) }
    val role = state.currentUser?.role
    val canCreate = role?.allows(Permission.INVENTORY_COUNT_CREATE) == true
    val canPerform = role?.allows(Permission.INVENTORY_COUNT_PERFORM) == true
    val canApprove = role?.allows(Permission.INVENTORY_COUNT_APPROVE) == true
    val canPost = role?.allows(Permission.INVENTORY_COUNT_POST) == true

    LaunchedEffect(state.pendingAction) {
        if (state.pendingAction == InventoryWorkspaceAction.CREATE_COUNT) {
            showCreate = true
            viewModel.consumeAction(InventoryWorkspaceAction.CREATE_COUNT)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("inventory_count_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(onClick = { showCreate = true }, enabled = canCreate && state.locations.any { it.active } && !state.busy) {
                Text("جلسه انبارگردانی جدید")
            }
        }
        item { SectionHeading("چرخه شمارش", "پیش‌نویس ← باز ← شمارش/بازشماری ← تأیید ← ثبت نهایی") }
        if (!state.loading && state.countSessions.isEmpty()) item { InventoryEmptyState("جلسه انبارگردانی ثبت نشده است.") }
        items(state.countSessions, key = { "count-session-${it.id}" }) { session ->
            val location = state.locations.firstOrNull { it.id == session.locationId }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(session.documentNumber, fontWeight = FontWeight.Bold)
                    Text("${location?.name ?: "محل #${session.locationId}"} · ${epochDayToPersian(session.businessEpochDay).display()}", style = MaterialTheme.typography.bodySmall)
                    CompactInfoRow("شعبه", location?.branchName?.ifBlank { "سطح سازمان" } ?: "—")
                    CompactInfoRow("وضعیت", countStatusTitle(session.status), session.status in setOf(InventoryCountStatus.RECOUNT_REQUIRED, InventoryCountStatus.PENDING_APPROVAL))
                    CompactInfoRow("نوع", if (session.blindCount) "شمارش کور" else "شمارش با نمایش مانده")
                    session.assignedToActorId?.let { CompactInfoRow("شمارشگر", "کاربر #$it") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (session.status == InventoryCountStatus.DRAFT && canCreate) {
                            Button(onClick = { viewModel.openCount(session.id) }, modifier = Modifier.weight(1f).testTag("inventory_count_open_${session.id}")) { Text("باز کردن") }
                        }
                        if (session.status in setOf(InventoryCountStatus.OPEN, InventoryCountStatus.COUNTING, InventoryCountStatus.RECOUNT_REQUIRED) && canPerform) {
                            Button(onClick = { viewModel.selectCount(session, canApprove) }, modifier = Modifier.weight(1f).testTag("inventory_count_select_${session.id}")) { Text("شمارش") }
                        } else {
                            OutlinedButton(onClick = { viewModel.selectCount(session, canApprove) }, modifier = Modifier.weight(1f).testTag("inventory_count_select_${session.id}")) { Text("جزئیات") }
                        }
                        if (session.status == InventoryCountStatus.PENDING_APPROVAL && canApprove) {
                            Button(onClick = { viewModel.approveCount(session.id) }, modifier = Modifier.weight(1f).testTag("inventory_count_approve_${session.id}")) { Text("تأیید") }
                        }
                        if (session.status == InventoryCountStatus.APPROVED && canPost) {
                            Button(onClick = { viewModel.postCount(session.id) }, modifier = Modifier.weight(1f).testTag("inventory_count_post_${session.id}")) { Text("ثبت نهایی") }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateInventoryCountDialog(
            state = state,
            onDismiss = { showCreate = false },
            onSave = { locationId, itemId, blind, notes ->
                viewModel.createCount(locationId, itemId?.let(::setOf).orEmpty(), blind, notes) { showCreate = false }
            },
        )
    }
    state.selectedCountSession?.let { session ->
        InventoryCountSessionDialog(
            session = session,
            lines = state.selectedCountLines,
            state = state,
            canPerform = canPerform,
            onDismiss = { viewModel.selectCount(null, canApprove) },
            onRecord = viewModel::recordCount,
            onSubmit = { viewModel.submitCount(session.id) },
        )
    }
}

@Composable
private fun CreateInventoryCountDialog(
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, Boolean, String) -> Unit,
) {
    val locations = state.locations.filter { it.active }
    var locationId by remember { mutableLongStateOf(locations.firstOrNull()?.id ?: 0) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    var blind by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جلسه انبارگردانی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField(
                    "محل شمارش",
                    locations.firstOrNull { it.id == locationId }?.let(::countLocationLabel),
                    locations.map { it.id to countLocationLabel(it) },
                ) { locationId = it }
                SelectionField(
                    "دامنه",
                    selectedItemId?.let { id -> state.items.firstOrNull { it.id == id }?.name } ?: "تمام کالاهای محل",
                    listOf(0L to "تمام کالاهای محل") + state.items.map { it.id to it.name },
                ) { selectedItemId = it.takeIf { id -> id != 0L } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(blind, { blind = it })
                    Text("شمارش کور؛ مانده سیستم به شمارشگر نمایش داده نشود")
                }
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("یادداشت و دستور شمارش") })
            }
        },
        confirmButton = { Button(enabled = !state.busy && locationId > 0, onClick = { onSave(locationId, selectedItemId, blind, notes) }) { Text("ایجاد پیش‌نویس") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun countLocationLabel(location: ir.restaurant.management.domain.inventory.InventoryLocationRecord): String =
    "${location.name} · ${location.branchName.ifBlank { "سطح سازمان" }}"

@Composable
private fun InventoryCountSessionDialog(
    session: InventoryCountSession,
    lines: List<InventoryCountLineView>,
    state: InventoryWorkspaceUiState,
    canPerform: Boolean,
    onDismiss: () -> Unit,
    onRecord: (Long, Long, Long?, String) -> Unit,
    onSubmit: () -> Unit,
) {
    var lineTarget by remember { mutableStateOf<InventoryCountLineView?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.documentNumber) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lines.isEmpty()) Text("در حال دریافت ردیف‌ها…")
                lines.forEach { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.items.firstOrNull { it.id == line.itemId }?.name ?: "کالا #${line.itemId}", fontWeight = FontWeight.Bold)
                            line.systemQuantityMicros?.let { CompactInfoRow("مانده تصویر ثابت", formatQuantity(it)) }
                            line.finalCountQuantityMicros?.let { CompactInfoRow("شمارش نهایی", formatQuantity(it), line.varianceQuantityMicros != 0L) }
                            line.varianceQuantityMicros?.let { CompactInfoRow("مغایرت", formatQuantity(it), it != 0L) }
                            CompactInfoRow(
                                "اثر بهای مغایرت",
                                line.varianceValueRial?.let(::formatMoney) ?: "— · داده کافی موجود نیست",
                                line.varianceValueRial?.let { it != 0L } == true,
                            )
                            CompactInfoRow("وضعیت ردیف", line.status.name)
                            if (canPerform && session.status in setOf(InventoryCountStatus.OPEN, InventoryCountStatus.COUNTING, InventoryCountStatus.RECOUNT_REQUIRED)) {
                                TextButton(onClick = { lineTarget = line }, modifier = Modifier.testTag("inventory_count_record_${line.lineId}")) { Text(if (line.firstCountQuantityMicros == null) "ثبت شمارش" else "ثبت بازشماری") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canPerform && session.status in setOf(InventoryCountStatus.COUNTING, InventoryCountStatus.RECOUNT_REQUIRED) && lines.isNotEmpty()) {
                Button(enabled = !state.busy, onClick = onSubmit, modifier = Modifier.testTag("inventory_count_submit")) { Text("ارسال برای بررسی") }
            } else TextButton(onClick = onDismiss, modifier = Modifier.testTag("inventory_count_close")) { Text("بستن") }
        },
        dismissButton = { if (session.status in setOf(InventoryCountStatus.COUNTING, InventoryCountStatus.RECOUNT_REQUIRED)) TextButton(onClick = onDismiss) { Text("بعداً") } },
    )
    lineTarget?.let { line ->
        RecordCountLineDialog(
            line = line,
            busy = state.busy,
            onDismiss = { lineTarget = null },
            onSave = { quantity, unitCost, reason -> onRecord(line.lineId, quantity, unitCost, reason); lineTarget = null },
        )
    }
}

@Composable
private fun RecordCountLineDialog(
    line: InventoryCountLineView,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, String) -> Unit,
) {
    var quantity by rememberSaveable(line.lineId) { mutableStateOf("") }
    var unitCost by rememberSaveable(line.lineId) { mutableStateOf("") }
    var reason by rememberSaveable(line.lineId) { mutableStateOf("") }
    var error by remember(line.lineId) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (line.firstCountQuantityMicros == null) "ثبت شمارش" else "ثبت بازشماری") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("مقدار شمارش‌شده") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("inventory_count_quantity"))
                OutlinedTextField(unitCost, { unitCost = formatMoneyInput(it) }, label = { Text("بهای واحد فقط در صورت مانده صفر (اختیاری)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل مغایرت (در صورت وجود)") }, modifier = Modifier.testTag("inventory_count_reason"))
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                runCatching {
                    Triple(parseQuantity(quantity).value, unitCost.takeIf { it.isNotBlank() }?.let { parseMoneyRial(it).value }, reason)
                }.onSuccess { onSave(it.first, it.second, it.third) }.onFailure { error = it.message }
            }, modifier = Modifier.testTag("inventory_count_record_submit")) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

internal fun countStatusTitle(status: InventoryCountStatus): String = when (status) {
    InventoryCountStatus.DRAFT -> "پیش‌نویس"
    InventoryCountStatus.OPEN -> "باز"
    InventoryCountStatus.COUNTING -> "در حال شمارش"
    InventoryCountStatus.RECOUNT_REQUIRED -> "نیازمند بازشماری"
    InventoryCountStatus.PENDING_APPROVAL -> "در انتظار تأیید"
    InventoryCountStatus.APPROVED -> "تأییدشده"
    InventoryCountStatus.POSTED -> "ثبت نهایی"
    InventoryCountStatus.CANCELLED -> "لغوشده"
    InventoryCountStatus.LEGACY_UNKNOWN -> "وضعیت قدیمی نامعتبر"
}
