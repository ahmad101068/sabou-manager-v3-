package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.InventoryItemMasterRecord

private enum class InventoryItemDetailSection { OVERVIEW, LOCATIONS, LOTS, MOVEMENTS, REPLENISHMENT, CONTROL }

@Composable
internal fun InventoryItemDetail2Dialog(
    item: InventoryItemMasterRecord,
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
) {
    var section by remember(item.id) { mutableStateOf(InventoryItemDetailSection.OVERVIEW) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.name} · ${item.sku.value}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(InventoryItemDetailSection.entries, key = { it.name }) { target ->
                        FilterChip(section == target, { section = target }, { Text(itemDetailSectionTitle(target)) })
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (section) {
                        InventoryItemDetailSection.OVERVIEW -> item {
                            DetailCard {
                                CompactInfoRow("نوع", itemTypeTitle(item.itemType))
                                CompactInfoRow("دسته", item.category)
                                CompactInfoRow("واحد پایه", item.baseUnit)
                                CompactInfoRow("واحد خرید", "${item.purchaseUnit} · ${item.purchaseToBaseNumerator}/${item.purchaseToBaseDenominator}")
                                CompactInfoRow("واحد رسپی", "${item.recipeUnit} · ${item.recipeToBaseNumerator}/${item.recipeToBaseDenominator}")
                                item.primaryBarcode?.let { CompactInfoRow("بارکد", it.value) }
                                CompactInfoRow("رهگیری بچ / انقضا", "${if (item.trackLot) "فعال" else "غیرفعال"} / ${if (item.trackExpiry) "فعال" else "غیرفعال"}")
                                CompactInfoRow("حداقل / نقطه سفارش", "${formatQuantity(item.minimumStockMicros)} / ${formatQuantity(item.reorderPointMicros)}")
                                CompactInfoRow("ذخیره ایمن / زمان تأمین", "${formatQuantity(item.safetyStockMicros)} / ${item.leadTimeDays} روز")
                            }
                        }
                        InventoryItemDetailSection.LOCATIONS -> {
                            val rows = state.balances.filter { it.itemId == item.id }
                            if (rows.isEmpty()) item { InventoryEmptyState("مانده مکانی در صفحه جاری نیست.") }
                            items(rows, key = { "detail-balance-${it.locationId}" }) { balance ->
                                DetailCard {
                                    Text(balance.locationName, fontWeight = FontWeight.Bold)
                                    CompactInfoRow("فیزیکی / قابل مصرف", "${formatQuantity(balance.onHandMicros)} / ${formatQuantity(balance.availableMicros)} ${balance.baseUnit}")
                                    CompactInfoRow("در راه", formatQuantity(balance.inTransitMicros))
                                    CompactInfoRow("ارزش", formatMoney(balance.inventoryValueRial))
                                }
                            }
                        }
                        InventoryItemDetailSection.LOTS -> {
                            val rows = state.lots.filter { it.itemId == item.id }
                            if (rows.isEmpty()) item { InventoryEmptyState("لاتی در صفحه جاری نیست.") }
                            items(rows, key = { "detail-lot-${it.id}" }) { lot ->
                                DetailCard {
                                    Text(lot.lotNumber, fontWeight = FontWeight.Bold)
                                    CompactInfoRow("محل", state.locations.firstOrNull { it.id == lot.locationId }?.name ?: "#${lot.locationId}")
                                    CompactInfoRow("وضعیت", lotStatusTitle(lot.status))
                                    CompactInfoRow("باقیمانده", formatQuantity(lot.remainingQuantityMicros))
                                    CompactInfoRow("انقضا", lot.expiryEpochDay?.let { epochDayToPersian(it).display() } ?: "—")
                                }
                            }
                        }
                        InventoryItemDetailSection.MOVEMENTS -> {
                            val rows = state.movements.filter { it.itemId == item.id }
                            if (rows.isEmpty()) item { InventoryEmptyState("گردشی در صفحه جاری نیست.") }
                            items(rows, key = { "detail-movement-${it.id}" }) { movement ->
                                DetailCard {
                                    Text(movementTypeTitle(movement.movementType), fontWeight = FontWeight.Bold)
                                    CompactInfoRow("تاریخ", epochDayToPersian(movement.businessEpochDay).display())
                                    CompactInfoRow("مقدار", formatQuantity(movement.quantityDeltaMicros), movement.quantityDeltaMicros < 0)
                                    CompactInfoRow("سند", "${movement.sourceType.storedValue} #${movement.sourceId}")
                                }
                            }
                        }
                        InventoryItemDetailSection.REPLENISHMENT -> {
                            val rows = state.replenishment.filter { it.itemId == item.id }
                            if (rows.isEmpty()) item { InventoryEmptyState("پیشنهاد تأمینی در صفحه جاری نیست.") }
                            items(rows, key = { "detail-replenishment-${it.locationId ?: 0}" }) { row ->
                                DetailCard {
                                    Text(row.locationName ?: "تمام محل‌ها", fontWeight = FontWeight.Bold)
                                    CompactInfoRow("قابل مصرف", formatQuantity(row.availableMicros))
                                    CompactInfoRow("پیشنهاد", formatQuantity(row.suggestedQuantityMicros), row.isActionable)
                                    CompactInfoRow("ارزش سفارش", formatMoney(row.estimatedOrderValueRial))
                                }
                            }
                        }
                        InventoryItemDetailSection.CONTROL -> item {
                            DetailCard {
                                val waste = state.wasteDocuments.filter { it.itemId == item.id }
                                val counts = state.selectedCountLines.filter { it.itemId == item.id }
                                CompactInfoRow("اسناد ضایعات صفحه", waste.size.toString(), waste.isNotEmpty())
                                CompactInfoRow(
                                    "ردیف‌های شمارش انتخاب‌شده",
                                    counts.size.toString(),
                                    counts.any { it.varianceQuantityMicros?.let { value -> value != 0L } == true },
                                )
                                Text("برای مشاهده کل سوابق از مراکز ضایعات و انبارگردانی استفاده کنید.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

private fun itemDetailSectionTitle(section: InventoryItemDetailSection): String = when (section) {
    InventoryItemDetailSection.OVERVIEW -> "نمای کلی"
    InventoryItemDetailSection.LOCATIONS -> "محل‌ها"
    InventoryItemDetailSection.LOTS -> "لات‌ها"
    InventoryItemDetailSection.MOVEMENTS -> "گردش"
    InventoryItemDetailSection.REPLENISHMENT -> "تأمین"
    InventoryItemDetailSection.CONTROL -> "کنترل"
}
