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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.InventoryReplenishmentReason
import ir.restaurant.management.domain.inventory.InventoryReplenishmentRisk
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun InventoryReplenishmentCenterScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val canCreateRequisition = state.currentUser?.role?.allows(Permission.PURCHASES) == true
    val recommendations = state.replenishment.sortedWith(
        compareBy({ replenishmentRiskOrder(it.risk) }, { it.itemName }),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("تصمیم تأمین", fontWeight = FontWeight.Bold)
                    Text("موجودی قابل مصرف + در راه + سفارش باز در برابر زمان تأمین و موجودی اطمینان سنجیده می‌شود.")
                    Text("روش FEFO برای انتخاب لات است و روش ارزش‌گذاری نیست.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (selectedIds.isNotEmpty()) item {
            Button(
                onClick = { viewModel.submitReplenishment(selectedIds.toList()); selectedIds = emptySet() },
                enabled = canCreateRequisition && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ارسال ${selectedIds.size} پیشنهاد به درخواست خرید") }
        }
        item { SectionHeading("پیشنهادهای مکان‌محور", "حداکثر ۱۰۰ نتیجه؛ سفارش خرید مستقیماً ایجاد نمی‌شود") }
        if (!state.loading && recommendations.isEmpty()) item { InventoryEmptyState("داده کافی برای پیشنهاد تأمین وجود ندارد.") }
        items(recommendations, key = { "replenishment-${it.itemId}-${it.locationId ?: 0}" }) { row ->
            val selectable = row.isActionable && canCreateRequisition
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectable) Checkbox(
                            checked = row.itemId in selectedIds,
                            onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + row.itemId else selectedIds - row.itemId },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(row.itemName, fontWeight = FontWeight.Bold)
                            Text(row.locationName ?: "تمام محل‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val risky = row.risk !in setOf(InventoryReplenishmentRisk.HEALTHY, InventoryReplenishmentRisk.NO_USAGE_HISTORY)
                        StatusPill(
                            text = replenishmentRiskTitle(row.risk),
                            containerColor = if (risky) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (risky) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    CompactInfoRow("قابل مصرف", "${formatQuantity(row.availableMicros)} ${row.unit}")
                    CompactInfoRow("مصرف روزانه", "${formatQuantity(row.averageDailyUsageMicros)} ${row.unit}")
                    CompactInfoRow("روز پوشش", formatDaysCover(row.daysOfCoverBasisPoints), row.risk == InventoryReplenishmentRisk.LEAD_TIME_RISK)
                    CompactInfoRow("نقطه سفارش", formatQuantity(row.reorderPointMicros))
                    CompactInfoRow("در راه / سفارش باز", "${formatQuantity(row.inTransitMicros)} / ${formatQuantity(row.onOrderMicros)}")
                    CompactInfoRow("مقدار پیشنهادی", formatQuantity(row.suggestedQuantityMicros), row.isActionable)
                    CompactInfoRow("ارزش برآوردی", formatMoney(row.estimatedOrderValueRial))
                    CompactInfoRow("علت", replenishmentReasonTitle(row.reason))
                    row.preferredSupplierName?.let { CompactInfoRow("تأمین‌کننده ترجیحی", it) }
                    if (row.hasPendingRequisition) Text("برای این کالا درخواست خرید باز وجود دارد.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun replenishmentRiskOrder(risk: InventoryReplenishmentRisk): Int = when (risk) {
    InventoryReplenishmentRisk.OUT_OF_STOCK -> 0
    InventoryReplenishmentRisk.BELOW_SAFETY_STOCK -> 1
    InventoryReplenishmentRisk.BELOW_REORDER_POINT -> 2
    InventoryReplenishmentRisk.LEAD_TIME_RISK -> 3
    InventoryReplenishmentRisk.NO_USAGE_HISTORY -> 4
    InventoryReplenishmentRisk.HEALTHY -> 5
    InventoryReplenishmentRisk.DISABLED -> 6
}

private fun replenishmentRiskTitle(risk: InventoryReplenishmentRisk): String = when (risk) {
    InventoryReplenishmentRisk.OUT_OF_STOCK -> "اتمام"
    InventoryReplenishmentRisk.BELOW_SAFETY_STOCK -> "زیر ذخیره ایمن"
    InventoryReplenishmentRisk.BELOW_REORDER_POINT -> "زیر نقطه سفارش"
    InventoryReplenishmentRisk.LEAD_TIME_RISK -> "ریسک زمان تأمین"
    InventoryReplenishmentRisk.NO_USAGE_HISTORY -> "بدون سابقه مصرف"
    InventoryReplenishmentRisk.HEALTHY -> "سالم"
    InventoryReplenishmentRisk.DISABLED -> "سیاست غیرفعال"
}

private fun replenishmentReasonTitle(reason: InventoryReplenishmentReason): String = when (reason) {
    InventoryReplenishmentReason.OUT_OF_STOCK -> "موجودی قابل مصرف صفر است"
    InventoryReplenishmentReason.SAFETY_STOCK_BREACH -> "ذخیره ایمن نقض شده است"
    InventoryReplenishmentReason.MINIMUM_STOCK -> "پایین‌تر از حداقل موجودی"
    InventoryReplenishmentReason.LEAD_TIME_COVERAGE -> "پوشش تا زمان تحویل کافی نیست"
    InventoryReplenishmentReason.REORDER_POINT_REACHED -> "نقطه سفارش رسیده است"
    InventoryReplenishmentReason.NO_USAGE_HISTORY -> "مصرف تاریخی برای محاسبه کافی نیست"
    InventoryReplenishmentReason.NO_ACTION_REQUIRED -> "اقدامی لازم نیست"
    InventoryReplenishmentReason.POLICY_DISABLED -> "سیاست تأمین غیرفعال است"
}

private fun formatDaysCover(basisPoints: Long?): String {
    if (basisPoints == null) return "نامحدود / بدون مصرف"
    val whole = basisPoints / 10_000
    val decimal = (basisPoints % 10_000) / 100
    return "$whole.${decimal.toString().padStart(2, '0')} روز"
}
