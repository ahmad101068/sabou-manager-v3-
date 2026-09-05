package ir.restaurant.management.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class GridRowState { VIEW, EDIT, WARNING, ERROR }

data class ManagementGridColumn<T>(
    val id: String,
    val title: String,
    val weight: Float = 1f,
    val value: (T) -> String,
    val align: TextAlign = TextAlign.Start,
)

data class ManagementGridEditAdapter<T>(
    val editableColumnIds: Set<String>,
    val onValueChange: (row: T, columnId: String, value: String) -> Unit,
    val onCommitRow: (T) -> Unit,
    val onCancelRow: (T) -> Unit,
    val onCommitAll: () -> Unit,
    val normalizeInput: (columnId: String, rawValue: String) -> String = { _, value -> value },
)

/** Canonical dense management grid for tablet/desktop. Mobile uses [MobileSmartRow]. */
@Composable
internal fun <T> ManagementDataGrid(
    rows: List<T>,
    columns: List<ManagementGridColumn<T>>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    rowState: (T) -> GridRowState = { GridRowState.VIEW },
    emptyMessage: String = "داده‌ای برای نمایش وجود ندارد.",
    commandBar: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    editAdapter: ManagementGridEditAdapter<T>? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(max = 720.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        commandBar?.invoke()
        GridHeader(columns)
        if (rows.isEmpty()) {
            Text(
                emptyMessage,
                Modifier.fillMaxWidth().padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn {
                items(rows, key = key) { row ->
                    GridRow(
                        row = row,
                        columns = columns,
                        state = rowState(row),
                        onClick = onRowClick?.let { click -> { click(row) } },
                        editAdapter = editAdapter,
                    )
                }
            }
        }
        summary?.invoke()
    }
}

@Composable
internal fun <T> GridHeader(columns: List<ManagementGridColumn<T>>) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            columns.forEach { column ->
                Text(
                    column.title,
                    Modifier.weight(column.weight),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = column.align,
                )
            }
        }
    }
}

@Composable
internal fun <T> GridRow(
    row: T,
    columns: List<ManagementGridColumn<T>>,
    state: GridRowState,
    onClick: (() -> Unit)? = null,
    editAdapter: ManagementGridEditAdapter<T>? = null,
) {
    val container = when (state) {
        GridRowState.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .28f)
        GridRowState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f)
        GridRowState.EDIT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f)
        GridRowState.VIEW -> MaterialTheme.colorScheme.surface
    }
    Surface(color = container) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { column ->
                if (state == GridRowState.EDIT && editAdapter?.editableColumnIds?.contains(column.id) == true) {
                    EditableGridCell(
                        value = column.value(row),
                        onValueChange = { raw ->
                            editAdapter.onValueChange(row, column.id, editAdapter.normalizeInput(column.id, raw))
                        },
                        onCommitRow = { editAdapter.onCommitRow(row) },
                        onCancelRow = { editAdapter.onCancelRow(row) },
                        onCommitAll = editAdapter.onCommitAll,
                        modifier = Modifier.weight(column.weight),
                        align = column.align,
                    )
                } else {
                    GridCell(column.value(row), Modifier.weight(column.weight), column.align)
                }
            }
        }
    }
}

@Composable
private fun EditableGridCell(
    value: String,
    onValueChange: (String) -> Unit,
    onCommitRow: () -> Unit,
    onCancelRow: () -> Unit,
    onCommitAll: () -> Unit,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .padding(horizontal = 3.dp)
            .onPreviewKeyEvent { event ->
                when (
                    resolveGridKeyboardCommand(
                        key = when (event.key) {
                            Key.Tab -> GridKeyboardKey.TAB
                            Key.Enter -> GridKeyboardKey.ENTER
                            Key.Escape -> GridKeyboardKey.ESCAPE
                            else -> GridKeyboardKey.OTHER
                        },
                        eventType = if (event.type == KeyEventType.KeyDown) GridKeyboardEventType.DOWN else GridKeyboardEventType.OTHER,
                        shiftPressed = event.isShiftPressed,
                        ctrlPressed = event.isCtrlPressed,
                    )
                ) {
                    GridKeyboardCommand.NEXT_CELL -> focusManager.moveFocus(FocusDirection.Next)
                    GridKeyboardCommand.PREVIOUS_CELL -> focusManager.moveFocus(FocusDirection.Previous)
                    GridKeyboardCommand.COMMIT_ROW -> {
                        onCommitRow()
                        focusManager.clearFocus()
                        true
                    }
                    GridKeyboardCommand.CANCEL_ROW -> {
                        onCancelRow()
                        focusManager.clearFocus()
                        true
                    }
                    GridKeyboardCommand.COMMIT_ALL -> {
                        onCommitAll()
                        focusManager.clearFocus()
                        true
                    }
                    GridKeyboardCommand.NONE -> false
                }
            },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = align),
    )
}

@Composable
internal fun GridCell(value: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        value,
        modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun MoneyCell(value: String, modifier: Modifier = Modifier) = GridCell(value, modifier, TextAlign.End)

@Composable
internal fun QuantityCell(value: String, modifier: Modifier = Modifier) = GridCell(value, modifier, TextAlign.End)

@Composable
internal fun StatusChip(label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            label,
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun TrendIndicator(label: String, positive: Boolean?, modifier: Modifier = Modifier) {
    val color = when (positive) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, modifier, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
}

@Composable
internal fun GridCommandBar(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun GridSummaryFooter(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
        }
    }
}

@Composable
internal fun MobileSmartRow(
    title: String,
    primaryValue: String,
    supporting: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    status: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(primaryValue, fontWeight = FontWeight.ExtraBold)
            }
            supporting.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            status?.let { StatusChip(it) }
        }
    }
}

@Composable
internal fun EditableMoneyCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
    )
}

@Composable
internal fun EditableQuantityCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
    )
}

@Composable
internal fun UiPagingBar(window: UiPageWindow, onPageChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(enabled = window.hasPrevious, onClick = { onPageChange(window.pageIndex - 1) }) { Text("قبلی") }
        Text(
            "صفحه ${toPersianDigits((window.pageIndex + 1).toString())} از ${toPersianDigits(window.pageCount.toString())} · ${ErpDisplayFormatters.integer(window.totalCount)} رکورد",
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(enabled = window.hasNext, onClick = { onPageChange(window.pageIndex + 1) }) { Text("بعدی") }
    }
}

@Composable
internal fun InlineWarning(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .45f),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}


/** Responsive canonical list: smart cards on mobile, dense grid on tablet/desktop. */
@Composable
internal fun <T> AdaptiveManagementList(
    rows: List<T>,
    columns: List<ManagementGridColumn<T>>,
    key: (T) -> Any,
    mobileTitle: (T) -> String,
    mobilePrimaryValue: (T) -> String,
    mobileSupporting: (T) -> List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    mobileStatus: (T) -> String? = { null },
    rowState: (T) -> GridRowState = { GridRowState.VIEW },
    emptyMessage: String = "داده‌ای برای نمایش وجود ندارد.",
    commandBar: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    listTestTag: String? = null,
    rowTestTag: ((T) -> String)? = null,
    editAdapter: ManagementGridEditAdapter<T>? = null,
    pageSize: Int = 50,
) {
    val firstKey = rows.firstOrNull()?.let(key)
    val lastKey = rows.lastOrNull()?.let(key)
    var requestedPage by remember(rows.size, firstKey, lastKey) { mutableStateOf(0) }
    val pageWindow = uiPageWindow(rows.size, requestedPage, pageSize)
    val visibleRows = rows.page(pageWindow)
    val pager: @Composable () -> Unit = {
        if (pageWindow.pageCount > 1) UiPagingBar(pageWindow) { requestedPage = it }
    }
    if (currentErpWindowClass() == ErpWindowClass.COMPACT) {
        LazyColumn(
            modifier = modifier.fillMaxWidth().heightIn(max = 720.dp).then(listTestTag?.let { Modifier.testTag(it) } ?: Modifier),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            commandBar?.let { bar -> item(key = "management_mobile_command_bar") { bar() } }
            if (rows.isEmpty()) {
                item(key = "management_mobile_empty") {
                    Text(
                        emptyMessage,
                        Modifier.fillMaxWidth().padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(visibleRows, key = key) { row ->
                    MobileSmartRow(
                        title = mobileTitle(row),
                        primaryValue = mobilePrimaryValue(row),
                        supporting = mobileSupporting(row),
                        status = mobileStatus(row),
                        onClick = onRowClick?.let { click -> { click(row) } },
                        modifier = rowTestTag?.let { Modifier.testTag(it(row)) } ?: Modifier,
                    )
                }
            }
            if (pageWindow.pageCount > 1) item(key = "management_mobile_pager") { pager() }
            summary?.let { footer -> item(key = "management_mobile_summary") { footer() } }
        }
    } else {
        ManagementDataGrid(
            rows = visibleRows,
            columns = columns,
            key = key,
            modifier = modifier,
            rowState = rowState,
            emptyMessage = emptyMessage,
            commandBar = commandBar,
            summary = {
                pager()
                summary?.invoke()
            },
            onRowClick = onRowClick,
            editAdapter = editAdapter,
        )
    }
}
