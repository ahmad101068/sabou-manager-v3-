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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.CreateInventoryTransferCommand
import ir.restaurant.management.domain.inventory.CreateInventoryTransferLine
import ir.restaurant.management.domain.inventory.InventoryTransferStatus
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun InventoryTransferCenterScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
) {
    var showCreate by remember { mutableStateOf(false) }
    val role = state.currentUser?.role
    val canCreate = role?.allows(Permission.INVENTORY_TRANSFER_CREATE) == true
    val canIssue = role?.allows(Permission.INVENTORY_TRANSFER_ISSUE) == true
    val canReceive = role?.allows(Permission.INVENTORY_TRANSFER_RECEIVE) == true

    LaunchedEffect(state.pendingAction) {
        if (state.pendingAction == InventoryWorkspaceAction.CREATE_TRANSFER) {
            showCreate = true
            viewModel.consumeAction(InventoryWorkspaceAction.CREATE_TRANSFER)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("inventory_transfer_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Button(onClick = { showCreate = true }, enabled = canCreate && state.locations.count { it.active } > 1 && !state.busy) { Text("درخواست انتقال جدید") } }
        item { SectionHeading("انتقال‌های سندمحور", "صدور و دریافت دو Boundary مستقل‌اند؛ مانده در راه قابل ردیابی است") }
        if (!state.loading && state.transfers.isEmpty()) item { InventoryEmptyState("انتقالی ثبت نشده است.") }
        items(state.transfers, key = { "transfer-${it.id}" }) { document ->
            val source = state.locations.firstOrNull { it.id == document.sourceLocationId }
            val destination = state.locations.firstOrNull { it.id == document.destinationLocationId }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(document.documentNumber, fontWeight = FontWeight.Bold)
                    Text("${source?.name ?: "#${document.sourceLocationId}"} ← ${destination?.name ?: "#${document.destinationLocationId}"}", style = MaterialTheme.typography.bodySmall)
                    CompactInfoRow("وضعیت", transferStatusTitle(document.status), document.status == InventoryTransferStatus.IN_TRANSIT)
                    CompactInfoRow("تاریخ", epochDayToPersian(document.businessEpochDay).display())
                    CompactInfoRow("تعداد ردیف", document.lines.size.toString())
                    document.lines.forEach { line ->
                        val item = state.items.firstOrNull { it.id == line.itemId }
                        CompactInfoRow(
                            item?.name ?: "کالا #${line.itemId}",
                            "درخواست ${formatQuantity(line.requestedQuantityMicros)} · صدور ${line.issuedQuantityMicros?.let(::formatQuantity) ?: "—"} · دریافت ${line.receivedQuantityMicros?.let(::formatQuantity) ?: "—"}",
                            line.varianceQuantityMicros?.let { it != 0L } == true,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (document.status == InventoryTransferStatus.REQUESTED && canIssue) {
                            Button(onClick = { viewModel.approveTransfer(document.id, "تأیید انتقال داخلی") }, modifier = Modifier.weight(1f).testTag("inventory_transfer_approve_${document.id}")) { Text("تأیید") }
                        }
                        if (document.status == InventoryTransferStatus.APPROVED && canIssue) {
                            Button(onClick = { viewModel.issueTransfer(document.id, "صدور کالا از محل مبدأ") }, modifier = Modifier.weight(1f).testTag("inventory_transfer_issue_${document.id}")) { Text("صدور") }
                        }
                        if (document.status == InventoryTransferStatus.IN_TRANSIT && canReceive) {
                            Button(onClick = { viewModel.receiveTransfer(document, "دریافت کامل در محل مقصد") }, modifier = Modifier.weight(1f).testTag("inventory_transfer_receive_${document.id}")) { Text("دریافت کامل") }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateInventoryTransferDialog(
            state = state,
            onDismiss = { showCreate = false },
            onSave = { viewModel.createTransfer(it) { showCreate = false } },
        )
    }
}

@Composable
private fun CreateInventoryTransferDialog(
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
    onSave: (CreateInventoryTransferCommand) -> Unit,
) {
    val locations = state.locations.filter { it.active }
    val items = state.items.filter { it.active }
    var sourceId by remember { mutableLongStateOf(locations.firstOrNull()?.id ?: 0) }
    var destinationId by remember(sourceId) { mutableLongStateOf(locations.firstOrNull { it.id != sourceId }?.id ?: 0) }
    var itemId by remember { mutableLongStateOf(items.firstOrNull()?.id ?: 0) }
    val selectedItem = items.firstOrNull { it.id == itemId }
    val candidateLots = state.lots.filter { it.itemId == itemId && it.locationId == sourceId && it.remainingQuantityMicros > 0 }
    var lotId by remember(itemId, sourceId) { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var businessDay by remember { mutableLongStateOf(currentEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    val actorId = state.currentUser?.id ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("درخواست انتقال") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                SelectionField("مبدأ", locations.firstOrNull { it.id == sourceId }?.name, locations.map { it.id to it.name }) { sourceId = it }
                SelectionField("مقصد", locations.firstOrNull { it.id == destinationId }?.name, locations.filter { it.id != sourceId }.map { it.id to it.name }) { destinationId = it }
                SelectionField("کالا", selectedItem?.name, items.map { it.id to it.name }) { itemId = it }
                if (selectedItem?.trackLot == true) SelectionField(
                    "لات مبدأ",
                    candidateLots.firstOrNull { it.id == lotId }?.lotNumber,
                    candidateLots.map { it.id to "${it.lotNumber} · ${formatQuantity(it.remainingQuantityMicros)}" },
                ) { lotId = it }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("مقدار درخواستی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                PersianDateField("تاریخ عملیاتی", businessDay) { businessDay = it }
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("دلیل / یادداشت") })
            }
        },
        confirmButton = {
            Button(enabled = !state.busy && actorId > 0, onClick = {
                runCatching {
                    CreateInventoryTransferCommand(
                        sourceLocationId = sourceId,
                        destinationLocationId = destinationId,
                        businessEpochDay = businessDay,
                        lines = listOf(CreateInventoryTransferLine(itemId, lotId, parseQuantity(quantity).value)),
                        notes = notes.ifBlank { "انتقال داخلی کالا" },
                        actorId = actorId,
                    ).validated()
                }.onSuccess(onSave).onFailure { error = it.message }
            }) { Text("ایجاد درخواست") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

internal fun transferStatusTitle(status: InventoryTransferStatus): String = when (status) {
    InventoryTransferStatus.DRAFT -> "پیش‌نویس"
    InventoryTransferStatus.REQUESTED -> "درخواست‌شده"
    InventoryTransferStatus.APPROVED -> "تأییدشده"
    InventoryTransferStatus.ISSUED -> "صادرشده"
    InventoryTransferStatus.IN_TRANSIT -> "در راه"
    InventoryTransferStatus.RECEIVED -> "دریافت‌شده"
    InventoryTransferStatus.COMPLETED -> "تکمیل‌شده"
    InventoryTransferStatus.CANCELLED -> "لغوشده"
    InventoryTransferStatus.LEGACY_UNKNOWN -> "وضعیت قدیمی نامعتبر"
}
