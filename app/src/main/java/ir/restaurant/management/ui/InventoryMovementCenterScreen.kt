package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReferenceType

@Composable
internal fun InventoryMovementCenterScreen(state: InventoryWorkspaceUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeading("دفتر غیرقابل‌ویرایش", "۱۰۰ گردش اخیر؛ اصلاح فقط با Reversal و سند اصلاحی") }
        if (!state.loading && state.movements.isEmpty()) item { InventoryEmptyState("گردش موجودی ثبت نشده است.") }
        items(state.movements, key = { "movement-${it.id}" }) { movement ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(movement.itemName, fontWeight = FontWeight.Bold)
                    Text(
                        "${movementTypeTitle(movement.movementType)} · ${epochDayToPersian(movement.businessEpochDay).display()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CompactInfoRow("تغییر مقدار", "${formatQuantity(movement.quantityDeltaMicros)} ${movement.baseUnit}", movement.quantityDeltaMicros < 0)
                    CompactInfoRow("تغییر ارزش", formatMoney(movement.valueDeltaRial), movement.valueDeltaRial < 0)
                    CompactInfoRow("محل", movement.locationName ?: "داده تاریخی / نامشخص", movement.locationId == null)
                    movement.lotNumbers?.takeIf { it.isNotBlank() }?.let { CompactInfoRow("لات", it) }
                    CompactInfoRow("سند مبدأ", "${referenceTypeTitle(movement.sourceType)} #${movement.sourceId}")
                    CompactInfoRow("ثبت‌کننده", movement.actorId?.let { "#$it" } ?: "داده تاریخی", movement.actorId == null)
                    CompactInfoRow("شناسه هم‌بستگی", movement.correlationId)
                    movement.reversalOfMovementId?.let { CompactInfoRow("برگشت گردش", "#$it", true) }
                    if (movement.reason.isNotBlank()) Text(movement.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

internal fun movementTypeTitle(type: InventoryMovementType): String = when (type) {
    InventoryMovementType.PURCHASE -> "خرید قدیمی"
    InventoryMovementType.PURCHASE_REVERSAL -> "برگشت خرید"
    InventoryMovementType.GOODS_RECEIPT -> "رسید خرید"
    InventoryMovementType.PURCHASE_RETURN -> "مرجوعی خرید"
    InventoryMovementType.LEGACY_SALE_CONSUMPTION -> "مصرف فروش قدیمی"
    InventoryMovementType.DAILY_SALES_CONSUMPTION -> "مصرف فروش روزانه"
    InventoryMovementType.DAILY_SALES_REVERSAL -> "برگشت فروش روزانه"
    InventoryMovementType.SALES_INVOICE_CONSUMPTION -> "مصرف فاکتور فروش"
    InventoryMovementType.SALES_RETURN -> "برگشت فروش حرفه‌ای"
    InventoryMovementType.SALES_VOID -> "ابطال فروش حرفه‌ای"
    InventoryMovementType.RECIPE_CONSUMPTION -> "مصرف رسپی"
    InventoryMovementType.PRODUCTION_OUTPUT -> "خروجی تولید"
    InventoryMovementType.INVENTORY_COUNT -> "شمارش قدیمی"
    InventoryMovementType.COUNT_VARIANCE -> "مغایرت شمارش"
    InventoryMovementType.INVENTORY_ADJUSTMENT -> "اصلاح موجودی"
    InventoryMovementType.WASTE -> "ضایعات"
    InventoryMovementType.TRANSFER_IN -> "ورود انتقال"
    InventoryMovementType.TRANSFER_OUT -> "خروج انتقال"
    InventoryMovementType.OPENING_BALANCE -> "مانده افتتاحیه"
    InventoryMovementType.REVERSAL -> "برگشت گردش"
    InventoryMovementType.LEGACY_UNKNOWN -> "نوع قدیمی نامعتبر"
}

private fun referenceTypeTitle(type: InventoryReferenceType): String = when (type) {
    InventoryReferenceType.PURCHASE -> "خرید"
    InventoryReferenceType.GOODS_RECEIPT -> "رسید خرید"
    InventoryReferenceType.PURCHASE_RETURN -> "مرجوعی خرید"
    InventoryReferenceType.DAILY_SALES -> "فروش روزانه"
    InventoryReferenceType.SALES_INVOICE -> "فاکتور فروش"
    InventoryReferenceType.SALES_RETURN -> "مرجوعی فروش"
    InventoryReferenceType.SALES_VOID -> "ابطال فروش"
    InventoryReferenceType.INVENTORY_COUNT -> "انبارگردانی"
    InventoryReferenceType.WASTE -> "ضایعات"
    InventoryReferenceType.STOCK_TRANSFER -> "انتقال"
    InventoryReferenceType.INVENTORY_ADJUSTMENT -> "اصلاح موجودی"
    InventoryReferenceType.RECIPE -> "رسپی"
    InventoryReferenceType.PRODUCTION -> "تولید"
    InventoryReferenceType.MIGRATION -> "مهاجرت"
    InventoryReferenceType.LEGACY_UNKNOWN -> "مرجع قدیمی"
}
