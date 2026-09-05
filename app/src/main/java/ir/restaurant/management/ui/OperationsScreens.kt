package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodClosureRecord
import ir.restaurant.management.domain.operations.InventoryPeriodStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.operations.StockMovementRecord
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.operations.SupplierRecord
import ir.restaurant.management.domain.operations.WasteDraft
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseLineDraft
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod

@Composable
fun AuditLogScreen(
    state: OperationsUiState,
    users: List<ir.restaurant.management.domain.operations.AppUserRecord>,
    canSensitiveView: Boolean,
    onSearch: (String) -> Unit,
    onActor: (String) -> Unit,
    onAction: (String) -> Unit,
    onEntity: (String) -> Unit,
    onEntityId: (String) -> Unit,
    onSourceReference: (String) -> Unit,
    onSeverity: (String) -> Unit,
    onDateRange: (Long?, Long?) -> Unit,
    onClearFilters: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { ScreenHeader("رویدادهای سیستم", "امنیت و حسابرسی", {}, onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).testTag("audit_log_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("فیلتر رویدادها", fontWeight = FontWeight.Black)
                        OutlinedTextField(
                            value = state.auditQuery.search,
                            onValueChange = onSearch,
                            label = { Text("جست‌وجوی واقعی") },
                            modifier = Modifier.fillMaxWidth().testTag("audit_filter_search"),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.auditQuery.actor,
                            onValueChange = onActor,
                            label = { Text("کاربر") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.auditQuery.action,
                                onValueChange = onAction,
                                label = { Text("عملیات") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.auditQuery.entityType,
                                onValueChange = onEntity,
                                label = { Text("بخش") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.auditQuery.entityId?.toString().orEmpty(),
                                onValueChange = onEntityId,
                                label = { Text("شناسه موجودیت") },
                                modifier = Modifier.weight(1f).testTag("audit_filter_entity_id"),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.auditQuery.sourceReference,
                                onValueChange = onSourceReference,
                                label = { Text("مرجع/منبع") },
                                modifier = Modifier.weight(1f).testTag("audit_filter_source"),
                                singleLine = true,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("" to "همه", "INFO" to "اطلاعات", "NOTICE" to "توجه", "WARNING" to "هشدار", "CRITICAL" to "بحرانی").forEach { (code, label) ->
                                FilterChip(
                                    selected = state.auditQuery.severity == code,
                                    onClick = { onSeverity(code) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("audit_filter_severity_${code.ifBlank { "ALL" }}"),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { val today = currentEpochDay(); onDateRange(today, today) },
                                modifier = Modifier.weight(1f),
                            ) { Text("امروز") }
                            OutlinedButton(
                                onClick = { val today = currentEpochDay(); onDateRange((today - 6L).coerceAtLeast(1L), today) },
                                modifier = Modifier.weight(1f),
                            ) { Text("۷ روز") }
                            TextButton(onClick = onClearFilters, modifier = Modifier.weight(1f)) { Text("پاک‌کردن") }
                        }
                        if (state.auditQuery.fromEpochDay != null || state.auditQuery.toEpochDay != null) {
                            Text(
                                "بازه: ${state.auditQuery.fromEpochDay?.let(::epochDayToPersian)?.display() ?: "ابتدا"} تا ${state.auditQuery.toEpochDay?.let(::epochDayToPersian)?.display() ?: "اکنون"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (state.auditLogs.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text("رویدادی مطابق فیلترهای فعلی پیدا نشد.", Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.auditLogs, key = { it.id }) { log ->
                val presentation = AuditPresentationMapper.map(log, users)
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("audit_row_${log.id}"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(presentation.actionLabel, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Text(
                                "${presentation.entityLabel} · ${when (presentation.severity) { AuditSeverity.INFO -> "اطلاعات"; AuditSeverity.NOTICE -> "توجه"; AuditSeverity.WARNING -> "هشدار"; AuditSeverity.CRITICAL -> "بحرانی" }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(log.description.ifBlank { "شرحی ثبت نشده است." })
                        Text(
                            "${presentation.actorLabel} · ${presentation.timeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (log.reason.isNotBlank()) Text("دلیل: ${log.reason}", style = MaterialTheme.typography.bodySmall)
                        if (log.correlationId.isNotBlank()) Text("شناسه پیگیری: ${log.correlationId}", style = MaterialTheme.typography.labelSmall)
                        if (canSensitiveView) {
                            AuditPresentationMapper.redactSensitiveSnapshot(log.beforeSnapshot)?.let { Text("قبل: $it", style = MaterialTheme.typography.labelSmall) }
                            AuditPresentationMapper.redactSensitiveSnapshot(log.afterSnapshot)?.let { Text("بعد: $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SelectionField(
    label: String,
    selectedText: String?,
    options: List<Pair<Long, String>>,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedText ?: "انتخاب کنید")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            expanded = false
                            onSelected(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PersianDateField(
    label: String,
    epochDay: Long,
    onSelected: (Long) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val value = epochDayToPersian(epochDay)
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { dialogOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value.display())
        }
    }
    if (dialogOpen) {
        PersianDatePickerDialog(
            initial = value,
            onDismiss = { dialogOpen = false },
            onConfirm = {
                dialogOpen = false
                onSelected(it.toEpochDay())
            },
        )
    }
}

@Composable
private fun PersianDatePickerDialog(
    initial: PersianDate,
    onDismiss: () -> Unit,
    onConfirm: (PersianDate) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val maxDay = daysInPersianMonth(year, month)
    val effectiveDay = day.coerceAtMost(maxDay)
    val firstWeekDay = runCatching {
        val javaDay = PersianDate(year, month, 1).toEpochDay()
        ((javaDay + 3L) % 7L).toInt() // 0 = شنبه
    }.getOrDefault(0)
    val cells = List(firstWeekDay) { 0 } + (1..maxDay).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("انتخاب تاریخ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "${effectiveDay} ${monthNames[month - 1]} ${year}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { monthExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(monthNames[month - 1], maxLines = 1)
                        }
                        DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                            monthNames.forEachIndexed { index, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = {
                                    month = index + 1
                                    day = day.coerceAtMost(daysInPersianMonth(year, month))
                                    monthExpanded = false
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { yearExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(year.toString())
                        }
                        DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                            (1300..1500).forEach { option ->
                                DropdownMenuItem(text = { Text(option.toString()) }, onClick = {
                                    year = option
                                    day = day.coerceAtMost(daysInPersianMonth(year, month))
                                    yearExpanded = false
                                })
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    weekDays.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(250.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false,
                ) {
                    items(cells) { option ->
                        Box(modifier = Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (option > 0) {
                                val selected = option == effectiveDay
                                Surface(
                                    onClick = { day = option },
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = option.toString(),
                                            maxLines = 1,
                                            softWrap = false,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(PersianDate(year, month, effectiveDay)) }) { Text("انتخاب تاریخ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun OptionalPersianDateField(
    label: String,
    epochDay: Long?,
    onSelected: (Long?) -> Unit,
    defaultEpochDay: Long = currentEpochDay(),
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (epochDay == null) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            OutlinedButton(
                onClick = { onSelected(defaultEpochDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("افزودن تاریخ") }
        } else {
            PersianDateField(
                label = label,
                epochDay = epochDay,
                onSelected = { onSelected(it) },
            )
            TextButton(
                onClick = { onSelected(null) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("بدون تاریخ") }
        }
    }
}

@Composable
internal fun OperationsScaffold(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    message: String?,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenHeader(
                title = title,
                actionLabel = actionLabel,
                onAction = onAction,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            message?.let { MessageCard(it) }
            content(androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding()))
        }
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    ProfessionalTopBar(
        title = title,
        subtitle = "کنترل حرفه‌ای اطلاعات و عملیات",
        onBack = onBack,
        actionLabel = actionLabel,
        onAction = onAction.takeIf { actionLabel != null },
    )
}

@Composable
internal fun MessageCard(message: String, isError: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = .25f) else MaterialTheme.colorScheme.primary.copy(alpha = .22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Text(message, modifier = Modifier.padding(12.dp))
    }
}

@Composable
internal fun EmptyState(
    text: String,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}

@Composable
internal fun ConfirmDeactivateDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
