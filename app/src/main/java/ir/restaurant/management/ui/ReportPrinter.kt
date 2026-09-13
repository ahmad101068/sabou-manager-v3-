package ir.restaurant.management.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import ir.restaurant.management.RestaurantManagementApplication
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ir.restaurant.management.domain.accounting.JournalDetails
import ir.restaurant.management.domain.accounting.ProfitLossSnapshot
import ir.restaurant.management.domain.accounting.TrialBalanceSnapshot
import ir.restaurant.management.domain.assets.AssetRecord
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.personnel.AttendanceSummary
import ir.restaurant.management.domain.personnel.PayrollRecord
import ir.restaurant.management.domain.personnel.PayrollStatus
import ir.restaurant.management.domain.purchase.PurchaseDetails
import ir.restaurant.management.domain.purchase.PurchaseOrderRecord
import ir.restaurant.management.domain.recipe.MenuItem
import ir.restaurant.management.domain.recipe.RecipeIngredientItem
import ir.restaurant.management.domain.sales.SalesInvoiceDetails
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.organizationDisplayName
import ir.restaurant.management.core.SignedLongMath

private const val PRINT_CSS = """
    @page { size: A4 portrait; margin: 12mm 10mm 14mm; }
    html, body { direction: rtl; }
    * { box-sizing: border-box; }
    body {
      margin: 0; font-family: Tahoma, Arial, sans-serif; color: #1d1b20; line-height: 1.7;
      font-size: 11.5pt; -webkit-print-color-adjust: exact; print-color-adjust: exact;
    }
    h1 { color: #8d332c; font-size: 20pt; margin: 0 0 4mm; break-after: avoid-page; page-break-after: avoid; }
    h2 { font-size: 14pt; margin: 7mm 0 2mm; break-after: avoid-page; page-break-after: avoid; }
    .meta { color: #625b71; margin-bottom: 5mm; }
    table { width: 100%; border-collapse: collapse; margin-top: 3mm; page-break-inside: auto; }
    thead { display: table-header-group; }
    tfoot { display: table-footer-group; }
    tr { break-inside: avoid; page-break-inside: avoid; }
    th, td { border: 0.25mm solid #aaa; padding: 2mm; text-align: right; vertical-align: top; }
    th { background: #f5e9e7; font-weight: 700; }
    .total { font-weight: bold; background: #f7f2fa; }
    .footer { margin-top: 8mm; padding-top: 3mm; border-top: 0.25mm solid #ddd; color: #625b71; font-size: 9pt; }
    small { font-size: 8.5pt; color: #625b71; }
"""

private fun htmlDocument(context: Context, title: String, body: String): String =
    """<!doctype html><html dir="rtl" lang="fa"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>$PRINT_CSS</style></head><body><h1>${title.html()}</h1>$body<div class="footer">${context.organizationDisplayName().html()} · ${epochDayToPersian(currentEpochDay()).display()}</div></body></html>"""

private fun String.html(): String = buildString(length) {
    this@html.forEach { char ->
        append(
            when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> char
            },
        )
    }
}

private data class PrintAuditDescriptor(
    val entityType: String = "REPORT",
    val entityId: Long? = null,
    val businessEpochDay: Long? = null,
)

/** Every print invocation is append-only audited before Android receives the print job. Reprints therefore create a second audit event. */
private fun printHtml(
    context: Context,
    title: String,
    html: String,
    audit: PrintAuditDescriptor = PrintAuditDescriptor(),
) {
    val application = context.applicationContext as? RestaurantManagementApplication
    if (application == null) {
        Toast.makeText(context, "ثبت حسابرسی چاپ ممکن نیست؛ چاپ انجام نشد.", Toast.LENGTH_LONG).show()
        return
    }
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
        try {
            val actor = application.container.authorizationService.actorIdentity()
            val now = System.currentTimeMillis()
            application.container.auditService.record(
                AuditEventDraft(
                    action = AuditAction.of("REPORT_PRINT"),
                    entityType = AuditEntityType.of(audit.entityType),
                    entityId = audit.entityId,
                    actorId = actor.id,
                    actorDisplayName = actor.displayName,
                    occurredAtEpochMillis = now,
                    businessEpochDay = audit.businessEpochDay,
                    deviceId = "local-android",
                    referenceType = audit.entityType.takeIf { audit.entityId != null },
                    referenceId = audit.entityId,
                    reason = "PRINT_OR_REPRINT_REQUEST",
                    beforeSnapshot = null,
                    afterSnapshot = "title=${title.take(160)}",
                    correlationId = CorrelationId.new("report_print").value,
                    description = "چاپ یا چاپ مجدد سند: $title",
                    actorRoleSnapshot = actor.role.name,
                ),
            )
        } catch (error: Exception) {
            Toast.makeText(context, "ثبت حسابرسی چاپ ناموفق بود؛ چاپ انجام نشد.", Toast.LENGTH_LONG).show()
            return@launch
        }
        renderPrintJob(context, title, html)
    }
}

private fun renderPrintJob(context: Context, title: String, html: String) {
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        blockNetworkLoads = true
    }
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            view.webViewClient = WebViewClient()
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print(
                title,
                view.createPrintDocumentAdapter(title),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build(),
            )
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private fun treasuryChannelTitle(channel: TreasuryChannel): String = when (channel) {
    TreasuryChannel.CASH -> "نقدی"
    TreasuryChannel.BANK -> "بانکی"
    TreasuryChannel.CARD -> "کارتخوان"
    TreasuryChannel.TRANSFER -> "انتقال بانکی"
}

fun printPurchaseInvoice(context: Context, details: PurchaseDetails) {
    val rows = details.lines.joinToString("") {
        "<tr><td>${it.itemName.html()}</td><td>${formatQuantity(it.quantityMicros)} ${it.unit.html()}</td><td>${formatMoney(it.unitCostRial).html()}</td><td>${formatMoney(it.lineTotalRial).html()}</td></tr>"
    }
    val body = """
        <div class="meta">تأمین‌کننده: ${details.supplierName.html()} · تاریخ: ${epochDayToPersian(details.purchaseEpochDay).display()}</div>
        <table><tr><th>شرح</th><th>تعداد</th><th>فی</th><th>جمع</th></tr>$rows
        <tr class="total"><td colspan="3">جمع کل</td><td>${formatMoney(details.totalRial).html()}</td></tr></table>
    """.trimIndent()
    printHtml(context, "فاکتور خرید ${details.invoiceNo}", htmlDocument(context, "فاکتور خرید ${details.invoiceNo}", body), PrintAuditDescriptor("PURCHASE", details.id, details.purchaseEpochDay))
}

fun printSalesInvoice(context: Context, details: SalesInvoiceDetails) {
    val lineRows = details.lines.joinToString("") { line ->
        "<tr><td>${line.name.html()}</td><td>${formatQuantity(line.quantityMicros)}</td><td>${formatMoney(line.unitPriceRial).html()}</td><td>${formatMoney(line.discountRial).html()}</td><td>${formatMoney(line.netRial).html()}</td></tr>"
    }
    val paymentRows = details.payments.joinToString("") { payment ->
        "<tr><td>${payment.method.title.html()}</td><td>${formatMoney(payment.amountRial).html()}</td><td>${payment.referenceNo.ifBlank { "—" }.html()}</td></tr>"
    }
    val body = """
        <div class="meta">تاریخ: ${epochDayToPersian(details.invoice.businessEpochDay).display()} · مشتری: ${(details.invoice.customerName ?: "مشتری نقدی").html()} · وضعیت: ${details.invoice.status.storedValue.html()}</div>
        <table><tr><th>شرح</th><th>تعداد</th><th>فی</th><th>تخفیف</th><th>خالص</th></tr>$lineRows</table>
        <table>
          <tr><th>ناخالص</th><td>${formatMoney(details.invoice.grossRial).html()}</td><th>تخفیف</th><td>${formatMoney(details.invoice.discountRial).html()}</td></tr>
          <tr><th>سرویس</th><td>${formatMoney(details.invoice.serviceRial).html()}</td><th>مالیات</th><td>${formatMoney(details.invoice.taxRial).html()}</td></tr>
          <tr class="total"><th>قابل پرداخت</th><td colspan="3">${formatMoney(details.invoice.netRial).html()}</td></tr>
        </table>
        <h2>روش‌های پرداخت</h2><table><tr><th>روش</th><th>مبلغ</th><th>پیگیری</th></tr>$paymentRows</table>
        ${if (details.invoice.notes.isBlank()) "" else "<p>توضیحات: ${details.invoice.notes.html()}</p>"}
    """.trimIndent()
    printHtml(context, "فاکتور فروش ${details.invoice.invoiceNo}", htmlDocument(context, "فاکتور فروش ${details.invoice.invoiceNo}", body), PrintAuditDescriptor("SALES_INVOICE", details.invoice.id, details.invoice.businessEpochDay))
}

fun printPurchaseOrder(context: Context, order: PurchaseOrderRecord) {
    val rows = order.lines.joinToString("") { line ->
        "<tr><td>${line.itemName.html()}${line.supplierSku?.let { "<br><small>SKU: ${it.html()}</small>" } ?: ""}</td><td>${formatQuantity(line.orderedQtyMicros)}</td><td>${formatMoney(line.unitCostRial).html()}</td><td>${formatMoney(ir.restaurant.management.core.MoneyRial.of(line.unitCostRial).times(ir.restaurant.management.core.QuantityMicros.of(line.orderedQtyMicros)).value).html()}</td></tr>"
    }
    val expected = order.confirmedExpectedEpochDay ?: order.expectedEpochDay
    val body = """
        <div class="meta">تأمین‌کننده: ${order.supplierName.html()} · تاریخ سفارش: ${epochDayToPersian(order.orderEpochDay).display()} · موعد تحویل: ${epochDayToPersian(expected).display()}</div>
        <table><tr><th>شرح / کد تأمین‌کننده</th><th>مقدار</th><th>قیمت واحد</th><th>جمع</th></tr>$rows
        <tr class="total"><td colspan="3">جمع سفارش</td><td>${formatMoney(order.orderedValueRial).html()}</td></tr></table>
        ${order.supplierConfirmationNo?.let { "<p>شماره تأیید تأمین‌کننده: ${it.html()}</p>" } ?: ""}
        <p>این سند، سفارش خرید رسمی مجموعه است.</p>
    """.trimIndent()
    printHtml(context, "سفارش خرید ${order.orderNo}", htmlDocument(context, "سفارش خرید ${order.orderNo}", body), PrintAuditDescriptor("PURCHASE_ORDER", order.id, order.orderEpochDay))
}

fun printPayrollSlip(context: Context, payroll: PayrollRecord) {
    val deductions = SignedLongMath.subtract(payroll.grossPayRial, payroll.netPayRial).coerceAtLeast(0)
    val body = """
        <div class="meta">پرسنل: ${payroll.employeeName.html()} · دوره: ${payroll.periodYear}/${payroll.periodMonth} · نسخه ${payroll.revisionNo}</div>
        <table>
          <tr><th>حقوق و مزایای ناخالص</th><td>${formatMoney(payroll.grossPayRial).html()}</td></tr>
          <tr><th>جمع کسورات</th><td>${formatMoney(deductions).html()}</td></tr>
          <tr class="total"><th>خالص پرداختی</th><td>${formatMoney(payroll.netPayRial).html()}</td></tr>
          <tr><th>تاریخ پرداخت</th><td>${epochDayToPersian(payroll.paymentEpochDay).display()}</td></tr>
          <tr><th>روش پرداخت</th><td>${treasuryChannelTitle(payroll.paymentMethod).html()}</td></tr>
          <tr><th>وضعیت</th><td>${if (payroll.status == PayrollStatus.REVERSED) "باطل‌شده" else if (payroll.status == PayrollStatus.PENDING_APPROVAL) "در انتظار تأیید" else "پرداخت‌شده"}</td></tr>
          ${payroll.reversalEpochDay?.let { "<tr><th>ابطال</th><td>${epochDayToPersian(it).display()} · ${payroll.reversalReason.html()}</td></tr>" }.orEmpty()}
        </table>
    """.trimIndent()
    printHtml(context, "فیش حقوقی ${payroll.employeeName}", htmlDocument(context, "فیش حقوقی", body), PrintAuditDescriptor("PAYROLL", payroll.id, payroll.paymentEpochDay))
}

fun printAttendanceSummary(context: Context, employeeName: String, summary: AttendanceSummary) {
    val body = """
        <div class="meta">پرسنل: ${employeeName.html()} · از ${epochDayToPersian(summary.startEpochDay).display()} تا ${epochDayToPersian(summary.endEpochDay).display()}</div>
        <table>
          <tr><th>روز حاضر</th><td>${ErpDisplayFormatters.integer(summary.presentDays)}</td><th>روز غیبت</th><td>${ErpDisplayFormatters.integer(summary.absentDays)}</td></tr>
          <tr><th>مرخصی</th><td>${ErpDisplayFormatters.integer(summary.leaveDays)}</td><th>ماموریت</th><td>${ErpDisplayFormatters.integer(summary.missionDays)}</td></tr>
          <tr><th>کارکرد</th><td>${ErpDisplayFormatters.integer(summary.workedMinutes)} دقیقه</td><th>تأخیر</th><td>${ErpDisplayFormatters.integer(summary.lateMinutes)} دقیقه</td></tr>
          <tr class="total"><th>اضافه‌کاری</th><td colspan="3">${ErpDisplayFormatters.integer(summary.overtimeMinutes)} دقیقه</td></tr>
        </table>
    """.trimIndent()
    printHtml(context, "گزارش حضور $employeeName", htmlDocument(context, "گزارش حضور و غیاب", body))
}

fun printJournal(context: Context, details: JournalDetails) {
    val rows = details.lines.joinToString("") {
        "<tr><td>${it.accountCode.html()}</td><td>${it.accountName.html()}</td><td>${formatMoney(it.debitRial).html()}</td><td>${formatMoney(it.creditRial).html()}</td><td>${it.memo.html()}</td></tr>"
    }
    val body = """
        <div class="meta">تاریخ: ${epochDayToPersian(details.entryEpochDay).display()} · شرح: ${details.description.html()}</div>
        <table><tr><th>کد</th><th>حساب</th><th>بدهکار</th><th>بستانکار</th><th>شرح</th></tr>$rows
        <tr class="total"><td colspan="2">جمع</td><td>${formatMoney(details.totalDebitRial).html()}</td><td>${formatMoney(details.totalCreditRial).html()}</td><td></td></tr></table>
    """.trimIndent()
    printHtml(context, "سند ${details.entryNo}", htmlDocument(context, "سند حسابداری ${details.entryNo}", body), PrintAuditDescriptor("JOURNAL", details.id, details.entryEpochDay))
}

fun printAccountingSummary(
    context: Context,
    profitLoss: ProfitLossSnapshot,
    trial: TrialBalanceSnapshot,
    fromEpochDay: Long,
    toEpochDay: Long,
) {
    val rows = trial.accounts.joinToString("") {
        "<tr><td>${it.code.html()}</td><td>${it.name.html()}</td><td>${formatMoney(it.debitBalanceRial).html()}</td><td>${formatMoney(it.creditBalanceRial).html()}</td></tr>"
    }
    val body = """
        <div class="meta">بازه سود و زیان: ${epochDayToPersian(fromEpochDay).display()} تا ${epochDayToPersian(toEpochDay).display()}</div>
        <table>
          <tr><th>درآمد</th><td>${formatMoney(profitLoss.revenueRial).html()}</td><th>هزینه</th><td>${formatMoney(profitLoss.expenseRial).html()}</td></tr>
          <tr class="total"><th>سود/زیان خالص</th><td colspan="3">${formatMoney(profitLoss.netProfitRial).html()}</td></tr>
        </table>
        <h2>تراز آزمایشی</h2>
        <table><tr><th>کد</th><th>حساب</th><th>مانده بدهکار</th><th>مانده بستانکار</th></tr>$rows
        <tr class="total"><td colspan="2">جمع</td><td>${formatMoney(trial.totalDebitBalanceRial).html()}</td><td>${formatMoney(trial.totalCreditBalanceRial).html()}</td></tr></table>
    """.trimIndent()
    printHtml(context, "گزارش حسابداری", htmlDocument(context, "سود و زیان و تراز آزمایشی", body))
}

fun printAssetRegister(context: Context, assets: List<AssetRecord>) {
    val rows = assets.joinToString("") {
        "<tr><td>${it.assetCode.html()}</td><td>${it.name.html()}</td><td>${it.category.html()}</td><td>${ErpDisplayFormatters.integer(it.quantity)}</td><td>${formatMoney(it.purchaseCostRial).html()}</td><td>${formatMoney(it.bookValueRial).html()}</td><td>${if (it.isActive) "فعال" else "خارج‌شده"}</td></tr>"
    }
    val body = "<table><tr><th>کد</th><th>دارایی</th><th>دسته</th><th>تعداد</th><th>بهای خرید</th><th>ارزش دفتری</th><th>وضعیت</th></tr>$rows</table>"
    printHtml(context, "دفتر دارایی‌ها", htmlDocument(context, "دفتر دارایی‌های ثابت", body))
}

fun printInventoryRegister(context: Context, items: List<InventoryItemRecord>) {
    val rows = items.joinToString("") {
        "<tr><td>${it.name.html()}</td><td>${it.category.html()}</td><td>${formatQuantity(it.stockMicros)} ${it.unit.html()}</td><td>${formatMoney(it.inventoryValueRial).html()}</td><td>${if (it.alertEnabled) formatQuantity(it.alertThresholdMicros) else "—"}</td></tr>"
    }
    val body = "<table><tr><th>کالا</th><th>دسته</th><th>موجودی</th><th>ارزش</th><th>حد هشدار</th></tr>$rows</table>"
    printHtml(context, "گزارش موجودی", htmlDocument(context, "دفتر موجودی انبار", body))
}

fun printInventoryPeriodClosure(
    context: Context,
    details: ir.restaurant.management.domain.operations.InventoryPeriodClosureDetails,
) {
    val closure = details.closure
    val rows = details.lines.joinToString("") { line ->
        "<tr><td>${line.itemName.html()}</td><td>${formatQuantity(line.openingQuantityMicros)} ${line.unit.html()}</td><td>${formatQuantity(line.netPurchaseQuantityMicros)}</td><td>${formatQuantity(line.recordedOutflowQuantityMicros)}</td><td>${formatQuantity(line.expectedClosingQuantityMicros)}</td><td>${formatQuantity(line.countedClosingQuantityMicros)}</td><td>${formatQuantity(line.varianceQuantityMicros)}</td><td>${formatMoney(line.varianceValueRial).html()}</td></tr>"
    }
    val body = """
        <div class="meta">دوره: ${epochDayToPersian(closure.fromEpochDay).display()} تا ${epochDayToPersian(closure.toEpochDay).display()} · بسته‌شده توسط ${closure.closedBy.html()}</div>
        <table><tr><th>اول دوره</th><th>خرید خالص</th><th>خروج ثبت‌شده</th><th>پایان موردانتظار</th><th>پایان شمارش‌شده</th><th>مغایرت</th></tr>
        <tr class="total"><td>${formatMoney(closure.openingValueRial).html()}</td><td>${formatMoney(closure.netPurchaseValueRial).html()}</td><td>${formatMoney(closure.recordedOutflowValueRial).html()}</td><td>${formatMoney(closure.expectedClosingValueRial).html()}</td><td>${formatMoney(closure.countedClosingValueRial).html()}</td><td>${formatMoney(closure.varianceValueRial).html()}</td></tr></table>
        <h2>جزئیات کالاها</h2>
        <table><tr><th>کالا</th><th>اول</th><th>خرید</th><th>خروج</th><th>انتظار</th><th>شمارش</th><th>مغایرت مقدار</th><th>مغایرت ارزش</th></tr>$rows</table>
    """.trimIndent()
    printHtml(context, "بستن دوره انبار", htmlDocument(context, "گزارش قطعی دوره انبار", body), PrintAuditDescriptor("INVENTORY_PERIOD", closure.id, closure.toEpochDay))
}

fun printRecipeSheet(context: Context, item: MenuItem, ingredients: List<RecipeIngredientItem>) {
    val rows = ingredients.joinToString("") {
        "<tr><td>${it.inventoryName.html()}</td><td>${formatQuantity(it.quantityMicrosPerUnit)}</td><td>${it.unit.html()}</td></tr>"
    }
    val body = """
        <div class="meta">دسته: ${item.category.ifBlank { "بدون دسته‌بندی" }.html()} · قیمت فروش: ${formatMoney(item.salePriceRial).html()} · نسخه رسپی: ${item.recipeRevisionNo}${if (item.recipeEffectiveFromEpochDay > 0) " · تاریخ اثر: ${epochDayToPersian(item.recipeEffectiveFromEpochDay).display()}" else ""}</div>
        <table><tr><th>ماده اولیه</th><th>مقدار برای یک واحد</th><th>واحد</th></tr>$rows</table>
    """.trimIndent()
    printHtml(context, "رسپی ${item.name}", htmlDocument(context, "برگه رسپی ${item.name}", body), PrintAuditDescriptor("RECIPE", item.id, item.recipeEffectiveFromEpochDay.takeIf { it > 0 }))
}

fun printManagementSummary(
    context: Context,
    netProfitRial: Long,
    liquidityRial: Long,
    salesRial: Long,
    grossProfitRial: Long,
    receivablesRial: Long,
    inventoryRial: Long,
    lowStockCount: Int,
    overdueCount: Int,
    fromEpochDay: Long,
    toEpochDay: Long,
) {
    val body = """
        <div class="meta">بازه گزارش: ${epochDayToPersian(fromEpochDay).display()} تا ${epochDayToPersian(toEpochDay).display()}</div>
        <table>
          <tr><th>سود خالص</th><td>${formatMoney(netProfitRial).html()}</td><th>نقدینگی</th><td>${formatMoney(liquidityRial).html()}</td></tr>
          <tr><th>فروش</th><td>${formatMoney(salesRial).html()}</td><th>سود ناخالص</th><td>${formatMoney(grossProfitRial).html()}</td></tr>
          <tr><th>مطالبات</th><td>${formatMoney(receivablesRial).html()}</td><th>ارزش انبار</th><td>${formatMoney(inventoryRial).html()}</td></tr>
          <tr><th>کالای کم‌موجود</th><td>${ErpDisplayFormatters.integer(lowStockCount)}</td><th>تسویه سررسیدشده</th><td>${ErpDisplayFormatters.integer(overdueCount)}</td></tr>
        </table>
    """.trimIndent()
    printHtml(context, "گزارش مدیریتی", htmlDocument(context, "خلاصه گزارش مدیریتی", body))
}
