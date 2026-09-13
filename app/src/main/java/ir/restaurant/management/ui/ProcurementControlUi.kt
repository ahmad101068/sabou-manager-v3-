package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft
import ir.restaurant.management.domain.purchase.GoodsReceiptLineDraft
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseLineDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderRecord
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import ir.restaurant.management.domain.purchase.PurchaseReturnDraft
import ir.restaurant.management.domain.purchase.PurchaseReturnLineDraft
import ir.restaurant.management.domain.purchase.PurchaseRequisitionDraft
import ir.restaurant.management.domain.purchase.ReplenishmentPolicyDraft
import ir.restaurant.management.domain.purchase.ReplenishmentRisk
import ir.restaurant.management.domain.purchase.SupplierOfferDraft
import ir.restaurant.management.domain.purchase.SplitPurchaseOrdersDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderAcknowledgementDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderDispatchChannel
import ir.restaurant.management.domain.purchase.RequisitionLineDraft
import ir.restaurant.management.domain.purchase.RequisitionRecord
import ir.restaurant.management.domain.purchase.RequisitionStatus
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole

@Composable
internal fun ProcurementControlPanel(
    state: OperationsUiState,
    branches: List<BranchRecord>,
    currentUser: AppUserRecord?,
    onSubmit: (PurchaseRequisitionDraft, () -> Unit) -> Unit,
    onReview: (Long, Boolean, String, () -> Unit) -> Unit,
    onCreateOrder: (PurchaseOrderDraft, () -> Unit) -> Unit,
    onCreateSplitOrders: (SplitPurchaseOrdersDraft, () -> Unit) -> Unit,
    onMarkOrderSent: (Long, PurchaseOrderDispatchChannel) -> Unit,
    onAcknowledgeOrder: (PurchaseOrderAcknowledgementDraft, () -> Unit) -> Unit,
    onReceive: (GoodsReceiptDraft, () -> Unit) -> Unit,
    onReturn: (PurchaseReturnDraft, () -> Unit) -> Unit,
    onSaveReplenishmentPolicy: (ReplenishmentPolicyDraft, () -> Unit) -> Unit,
    onSaveSupplierOffer: (SupplierOfferDraft, () -> Unit) -> Unit,
    onSubmitSuggestedRequisition: (List<Long>, () -> Unit) -> Unit,
    onMatchInvoice: (Long, PurchaseDraft, Boolean, () -> Unit) -> Unit,
    onConsumeLaunchAction: (ProcurementLaunchAction) -> Unit,
) {
    var requestDialog by remember { mutableStateOf(false) }
    var orderTarget by remember { mutableStateOf<RequisitionRecord?>(null) }
    var splitOrderTarget by remember { mutableStateOf<RequisitionRecord?>(null) }
    var acknowledgementTarget by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var receiptTarget by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var invoiceTarget by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var returnTarget by remember { mutableStateOf<PurchaseOrderRecord?>(null) }
    var replenishmentPolicyDialog by remember { mutableStateOf(false) }
    var supplierOfferDialog by remember { mutableStateOf(false) }
    var receiptPicker by remember { mutableStateOf(false) }
    var returnPicker by remember { mutableStateOf(false) }
    var orderPicker by remember { mutableStateOf(false) }
    var rejectionTarget by remember { mutableStateOf<RequisitionRecord?>(null) }
    var rejectionReason by remember { mutableStateOf("") }
    var rejectionError by remember { mutableStateOf<String?>(null) }
    val procurement = state.procurement
    val compact = currentErpWindowClass() == ErpWindowClass.COMPACT

    LaunchedEffect(state.procurementLaunchAction) {
        when (val action = state.procurementLaunchAction) {
            ProcurementLaunchAction.REQUISITION -> { requestDialog = true; onConsumeLaunchAction(action) }
            ProcurementLaunchAction.PURCHASE_ORDER -> { orderPicker = true; onConsumeLaunchAction(action) }
            ProcurementLaunchAction.GOODS_RECEIPT -> { receiptPicker = true; onConsumeLaunchAction(action) }
            ProcurementLaunchAction.PURCHASE_RETURN -> { returnPicker = true; onConsumeLaunchAction(action) }
            null -> Unit
        }
    }
    val context = LocalContext.current

    Card(modifier = Modifier.testTag("procurement_control_panel"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (compact) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("کنترل تدارکات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    ProcurementWorkflowStepper(compact = true)
                    Button(onClick = { requestDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("درخواست خرید جدید") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("کنترل تدارکات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        ProcurementWorkflowStepper(compact = false)
                    }
                    Button(onClick = { requestDialog = true }, enabled = !state.busy) { Text("درخواست خرید جدید") }
                }
            }
            if (compact) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("منتظر تأیید", procurement.awaitingApproval.toString(), Modifier.fillMaxWidth())
                    MetricTile("سفارش باز", procurement.openOrders.toString(), Modifier.fillMaxWidth())
                    MetricTile("منتظر فاکتور", procurement.pendingInvoiceMatches.toString(), Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("منتظر تأیید", procurement.awaitingApproval.toString(), Modifier.weight(1f))
                    MetricTile("سفارش باز", procurement.openOrders.toString(), Modifier.weight(1f))
                    MetricTile("منتظر فاکتور", procurement.pendingInvoiceMatches.toString(), Modifier.weight(1f))
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تأمین هوشمند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("پیشنهاد خرید بر پایه مصرف ۳۰ روزه، زمان تأمین و موجودی در راه", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { replenishmentPolicyDialog = true }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("تنظیم سیاست تأمین") }
                    OutlinedButton(onClick = { supplierOfferDialog = true }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("پیشنهاد قیمت") }
                }
            }
            if (procurement.replenishmentSuggestions.isEmpty()) {
                Text(
                    if (procurement.replenishmentPolicies.isEmpty()) "برای شروع، سیاست تأمین کالاها را تعریف کنید."
                    else "در حال حاضر پیشنهاد خرید فعالی وجود ندارد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                procurement.replenishmentSuggestions.take(8).forEach { suggestion ->
                    val supplier = state.suppliers.firstOrNull { it.id == suggestion.preferredSupplierId }?.name ?: "بدون تأمین‌کننده ترجیحی"
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(suggestion.itemName, fontWeight = FontWeight.Bold)
                                Text(replenishmentRiskTitle(suggestion.risk), color = if (suggestion.risk == ReplenishmentRisk.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            }
                            Text("پیشنهاد ${formatQuantity(suggestion.suggestedOrderMicros)} · برآورد ${formatMoney(suggestion.estimatedOrderValueRial)}")
                            Text("موجودی ${formatQuantity(suggestion.currentStockMicros)} · در راه ${formatQuantity(suggestion.openPurchaseOrderMicros)} · مصرف روزانه ${formatQuantity(suggestion.averageDailyUsageMicros)}")
                            Text("پوشش فعلی ${formatDaysBasisPoints(suggestion.daysOfCoverBasisPoints)} روز · موجودی زمان تحویل ${formatQuantity(suggestion.projectedAtDeliveryMicros)}")
                            Text("$supplier${suggestion.preferredSupplierScore?.let { " · امتیاز $it/1000" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            suggestion.recommendedSupplierName?.let { recommended ->
                                Text("بهترین گزینه: $recommended · ${suggestion.comparedOfferCount} پیشنهاد مقایسه شد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("تحویل ${suggestion.recommendedLeadTimeDays} روزه${suggestion.estimatedSavingsRial.takeIf { it > 0 }?.let { " · صرفه‌جویی ${formatMoney(it)}" } ?: ""}")
                            }
                            if (suggestion.blockedByPendingRequest) Text("درخواست خرید فعال برای این کالا وجود دارد.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                val actionableIds = procurement.replenishmentSuggestions.filterNot { it.blockedByPendingRequest }.map { it.itemId }
                Button(
                    onClick = { onSubmitSuggestedRequisition(actionableIds) {} },
                    enabled = actionableIds.isNotEmpty() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ساخت درخواست خرید از ${actionableIds.size} پیشنهاد") }
            }
            if (procurement.supplierOffers.isNotEmpty()) {
                Text("کاتالوگ قیمت تأمین‌کنندگان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                procurement.supplierOffers.take(6).forEach { offer ->
                    Text("${offer.itemName} · ${offer.supplierName} · ${formatMoney(offer.unitCostRial)} · تحویل ${offer.leadTimeDays} روز${if (!offer.isActive) " · غیرفعال" else ""}")
                }
            }
            if (procurement.supplierScorecards.isNotEmpty()) {
                Text("امتیازنامه تأمین‌کنندگان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                procurement.supplierScorecards.take(5).forEach { score ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(score.supplierName, fontWeight = FontWeight.Bold)
                                Text("${score.grade} · ${score.score}/1000", fontWeight = FontWeight.Black)
                            }
                            Text("تحویل به‌موقع ${formatBasisPoints(score.onTimeBasisPoints)} · پذیرش ${formatBasisPoints(score.acceptanceBasisPoints)}")
                            Text("مرجوعی ${formatBasisPoints(score.returnBasisPoints)} · مغایرت قیمت ${formatBasisPoints(score.priceVarianceBasisPoints)}")
                            if (score.openCreditRial > 0) Text("اعتبار باز ${formatMoney(score.openCreditRial)}", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            procurement.requisitions.take(5).forEach { request ->
                val hasApprovePermission = currentUser?.role?.allows(Permission.PURCHASE_APPROVE) == true
                val secondStage = request.status == RequisitionStatus.PENDING_SECOND_APPROVAL
                val canApprove = hasApprovePermission && (!secondStage || currentUser?.role == UserRole.OWNER)
                Card(modifier = Modifier.fillMaxWidth().testTag("procurement_request_${request.id}")) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("${request.requestNo} · ${request.department}", fontWeight = FontWeight.Black)
                        Text("درخواست‌کننده: ${request.requestedBy} · موعد: ${epochDayToPersian(request.requiredEpochDay).display()}", style = MaterialTheme.typography.bodySmall)
                        Text("${request.lineCount} قلم · برآورد ${formatMoney(request.estimatedTotalRial)} · ${requisitionTitle(request.status)}")
                        Text("مرحله تأیید ${request.completedApprovalLevel} از ${request.requiredApprovalLevel}${request.committedBudgetRial.takeIf { it > 0 }?.let { value -> " · تعهد بودجه ${formatMoney(value)}" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        request.note.takeIf { it.isNotBlank() }?.let { Text("یادداشت: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        if (request.supplierGroupCount > 0) Text("${request.supplierGroupCount} تأمین‌کننده تخصیص‌یافته${if (request.unassignedLineCount > 0) " · ${request.unassignedLineCount} قلم بدون تخصیص" else ""}", color = MaterialTheme.colorScheme.primary)
                        if (request.status in setOf(RequisitionStatus.SUBMITTED, RequisitionStatus.PENDING_SECOND_APPROVAL)) {
                            Button(
                                onClick = { onReview(request.id, true, "") {} },
                                enabled = !state.busy && canApprove,
                                modifier = Modifier.fillMaxWidth().testTag("procurement_approve_${request.id}"),
                            ) { Text(if (request.status == RequisitionStatus.SUBMITTED) "تأیید مرحله اول" else "تأیید نهایی مالک") }
                            OutlinedButton(
                                onClick = { rejectionTarget = request; rejectionReason = ""; rejectionError = null },
                                enabled = !state.busy && hasApprovePermission,
                                modifier = Modifier.fillMaxWidth().testTag("procurement_reject_${request.id}"),
                            ) { Text("رد درخواست") }
                            if (!hasApprovePermission) Text("برای بررسی این درخواست دسترسی تأیید خرید لازم است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            else if (secondStage && currentUser?.role != UserRole.OWNER) Text("مرحله نهایی فقط توسط مالک قابل تأیید است.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (request.status == RequisitionStatus.APPROVED) {
                            if (request.supplierGroupCount > 0) {
                                Button(onClick = { splitOrderTarget = request }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("ساخت سفارش‌های تفکیک‌شده") }
                            } else {
                                OutlinedButton(onClick = { orderTarget = request }, modifier = Modifier.fillMaxWidth()) { Text("ساخت سفارش") }
                            }
                        }
                    }
                }
            }
            procurement.orders.take(5).forEach { order ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${order.orderNo} · ${order.supplierName}", fontWeight = FontWeight.Bold)
                        Text("${orderStatusTitle(order.status)} · پذیرفته ${formatMoney(order.acceptedValueRial)} از ${formatMoney(order.orderedValueRial)}")
                        Text(
                            when {
                                order.acknowledgedAtEpochMillis != null -> "تأیید تأمین‌کننده: ${order.supplierConfirmationNo} · موعد قطعی ${epochDayToPersian(requireNotNull(order.confirmedExpectedEpochDay)).display()}"
                                order.sentAtEpochMillis != null -> "ارسال ثبت‌شده · منتظر تأیید تأمین‌کننده"
                                else -> "هنوز برای تأمین‌کننده ارسال نشده است"
                            },
                            color = if (order.sentAtEpochMillis == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        if (order.lines.any { it.rejectedQtyMicros > 0 || it.returnedQtyMicros > 0 }) {
                            Text("این سفارش دارای مغایرت/مرجوعی ثبت‌شده است.", color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (order.status in setOf(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PARTIALLY_RECEIVED)) {
                                OutlinedButton(onClick = { receiptTarget = order }, modifier = Modifier.testTag("procurement_receive_${order.id}")) { Text("ثبت تحویل") }
                            }
                            if (order.status == PurchaseOrderStatus.RECEIVED && order.invoiceNo == null) {
                                Button(onClick = { invoiceTarget = order }) { Text("تطبیق فاکتور") }
                            }
                            if (order.lines.any { it.returnableQtyMicros > 0 } && order.status in setOf(PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CLOSED)) {
                                OutlinedButton(onClick = { returnTarget = order }) { Text("مرجوعی") }
                            }
                            order.invoiceNo?.let { Text("فاکتور $it", fontWeight = FontWeight.Bold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { printPurchaseOrder(context, order) }) { Text(if (order.sentAtEpochMillis == null) "چاپ سفارش" else "چاپ مجدد") }
                            if (order.sentAtEpochMillis == null && order.status == PurchaseOrderStatus.OPEN) {
                                Button(onClick = { onMarkOrderSent(order.id, PurchaseOrderDispatchChannel.PRINT) }, enabled = !state.busy) { Text("ثبت ارسال") }
                            } else if (order.acknowledgedAtEpochMillis == null && order.sentAtEpochMillis != null && order.status in setOf(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PARTIALLY_RECEIVED)) {
                                Button(onClick = { acknowledgementTarget = order }, enabled = !state.busy) { Text("ثبت تأیید") }
                            }
                        }
                    }
                }
            }
            procurement.supplierCredits.filter { it.remainingRial > 0 }.take(5).forEach { credit ->
                Text("${credit.creditNo} · ${credit.supplierName} · اعتبار باز ${formatMoney(credit.remainingRial)}", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    rejectionTarget?.let { target ->
        AlertDialog(
            modifier = Modifier.testTag("procurement_rejection_dialog"),
            onDismissRequest = { if (!state.busy) rejectionTarget = null },
            title = { Text("رد درخواست خرید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${target.requestNo} · علت رد را وارد کنید.")
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it.take(300); rejectionError = null },
                        label = { Text("علت رد") },
                        modifier = Modifier.fillMaxWidth().testTag("procurement_rejection_reason"),
                    )
                    rejectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = rejectionReason.trim()
                        if (reason.length < 3) {
                            rejectionError = "علت رد باید حداقل ۳ نویسه باشد."
                        } else {
                            onReview(target.id, false, reason) {
                                rejectionTarget = null
                                rejectionReason = ""
                                rejectionError = null
                            }
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("procurement_rejection_confirm"),
                ) { Text(if (state.busy) "در حال ثبت…" else "تأیید رد") }
            },
            dismissButton = { TextButton(onClick = { rejectionTarget = null }, enabled = !state.busy) { Text("انصراف") } },
        )
    }

    if (receiptPicker) {
        val eligible = procurement.orders.filter { it.status in setOf(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PARTIALLY_RECEIVED) }
        ProcurementOrderPickerDialog(
            title = "انتخاب سفارش برای دریافت کالا",
            emptyMessage = "سفارش خرید بازی برای دریافت کالا وجود ندارد.",
            orders = eligible,
            onDismiss = { receiptPicker = false },
            onSelect = { order -> receiptPicker = false; receiptTarget = order },
        )
    }
    if (returnPicker) {
        val eligible = procurement.orders.filter { order ->
            order.lines.any { it.returnableQtyMicros > 0 } &&
                order.status in setOf(PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CLOSED)
        }
        ProcurementOrderPickerDialog(
            title = "انتخاب سفارش برای برگشت خرید",
            emptyMessage = "سفارش دارای کالای قابل برگشت وجود ندارد.",
            orders = eligible,
            onDismiss = { returnPicker = false },
            onSelect = { order -> returnPicker = false; returnTarget = order },
        )
    }
    if (orderPicker) {
        val eligible = procurement.requisitions.filter { it.status == RequisitionStatus.APPROVED }
        AlertDialog(
            onDismissRequest = { orderPicker = false },
            title = { Text("انتخاب درخواست خرید تأییدشده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (eligible.isEmpty()) Text("درخواست خرید تأییدشده‌ای برای تبدیل به سفارش وجود ندارد.")
                    eligible.take(10).forEach { request ->
                        OutlinedButton(
                            onClick = {
                                orderPicker = false
                                if (request.supplierGroupCount > 0) splitOrderTarget = request else orderTarget = request
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${request.requestNo} · ${request.department}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { orderPicker = false }) { Text("بستن") } },
        )
    }

    if (requestDialog) RequisitionDialog(state, branches, { requestDialog = false }) { draft ->
        onSubmit(draft) { requestDialog = false }
    }
    if (replenishmentPolicyDialog) ReplenishmentPolicyDialog(state, { replenishmentPolicyDialog = false }) { draft ->
        onSaveReplenishmentPolicy(draft) { replenishmentPolicyDialog = false }
    }
    if (supplierOfferDialog) SupplierOfferDialog(state, { supplierOfferDialog = false }) { draft ->
        onSaveSupplierOffer(draft) { supplierOfferDialog = false }
    }
    orderTarget?.let { target -> OrderDialog(state, target, { orderTarget = null }) { draft ->
        onCreateOrder(draft) { orderTarget = null }
    } }
    splitOrderTarget?.let { target -> SplitOrdersDialog(state, target, { splitOrderTarget = null }) { draft ->
        onCreateSplitOrders(draft) { splitOrderTarget = null }
    } }
    acknowledgementTarget?.let { target -> PurchaseOrderAcknowledgementDialog(target, { acknowledgementTarget = null }) { draft ->
        onAcknowledgeOrder(draft) { acknowledgementTarget = null }
    } }
    receiptTarget?.let { target -> ReceiptDialog(target, { receiptTarget = null }) { draft ->
        onReceive(draft) { receiptTarget = null }
    } }
    invoiceTarget?.let { target -> MatchedInvoiceDialog(state, branches, target, { invoiceTarget = null }) { draft, override ->
        onMatchInvoice(target.id, draft, override) { invoiceTarget = null }
    } }
    returnTarget?.let { target -> PurchaseReturnDialog(target, { returnTarget = null }) { draft ->
        onReturn(draft) { returnTarget = null }
    } }
}

@Composable
private fun ProcurementWorkflowStepper(compact: Boolean) {
    val steps = listOf("۱. درخواست", "۲. سفارش", "۳. دریافت", "۴. تطبیق سه‌طرفه")
    if (compact) {
        Column(Modifier.fillMaxWidth().testTag("procurement_workflow_stepper"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    } else {
        Row(Modifier.fillMaxWidth().testTag("procurement_workflow_stepper"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEach { Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) { Text(it, Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall) } }
        }
    }
}

@Composable
private fun ProcurementOrderPickerDialog(
    title: String,
    emptyMessage: String,
    orders: List<PurchaseOrderRecord>,
    onDismiss: () -> Unit,
    onSelect: (PurchaseOrderRecord) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (orders.isEmpty()) Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                orders.take(10).forEach { order ->
                    OutlinedButton(onClick = { onSelect(order) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${order.orderNo} · ${order.supplierName}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun SplitOrdersDialog(
    state: OperationsUiState,
    request: RequisitionRecord,
    onDismiss: () -> Unit,
    onConfirm: (SplitPurchaseOrdersDraft) -> Unit,
) {
    var orderDay by remember { mutableLongStateOf(currentEpochDay()) }
    var fallbackSupplierId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساخت سفارش‌های تفکیک‌شده") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("${request.lineCount} قلم در حداکثر ${request.supplierGroupCount + if (request.unassignedLineCount > 0) 1 else 0} سفارش تفکیک می‌شود. موعد هر سفارش از بیشترین زمان تحویل اقلام همان تأمین‌کننده محاسبه خواهد شد.")
            PersianDateField("تاریخ سفارش", orderDay) { orderDay = it }
            if (request.unassignedLineCount > 0) {
                SelectionField("تأمین‌کننده اقلام بدون تخصیص", state.suppliers.firstOrNull { it.id == fallbackSupplierId }?.name, state.suppliers.map { it.id to it.name }) { fallbackSupplierId = it }
            }
            OutlinedTextField(note, { note = it }, label = { Text("توضیحات سفارش‌ها") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                SplitPurchaseOrdersDraft(
                    requisitionId = request.id,
                    orderEpochDay = orderDay,
                    fallbackSupplierId = fallbackSupplierId,
                    note = note,
                ).validated()
            }.onSuccess(onConfirm).onFailure { error = it.message }
        }, enabled = !state.busy) { Text("ایجاد سفارش‌ها") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PurchaseOrderAcknowledgementDialog(
    order: PurchaseOrderRecord,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseOrderAcknowledgementDraft) -> Unit,
) {
    var confirmationNo by remember { mutableStateOf("") }
    var confirmedDay by remember { mutableLongStateOf(order.expectedEpochDay) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأیید ${order.orderNo}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("تأمین‌کننده: ${order.supplierName}")
            OutlinedTextField(confirmationNo, { confirmationNo = it }, label = { Text("شماره تأیید تأمین‌کننده") }, modifier = Modifier.fillMaxWidth())
            PersianDateField("موعد قطعی تحویل", confirmedDay) { confirmedDay = it }
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                PurchaseOrderAcknowledgementDraft(order.id, confirmationNo, confirmedDay).validated().also {
                    require(it.confirmedExpectedEpochDay >= order.orderEpochDay) { "موعد قطعی نمی‌تواند قبل از تاریخ سفارش باشد." }
                }
            }.onSuccess(onConfirm).onFailure { error = it.message }
        }) { Text("ثبت تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun SupplierOfferDialog(
    state: OperationsUiState,
    onDismiss: () -> Unit,
    onConfirm: (SupplierOfferDraft) -> Unit,
) {
    var itemId by remember { mutableStateOf<Long?>(null) }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var sku by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var minimumOrder by remember { mutableStateOf("0") }
    var orderMultiple by remember { mutableStateOf("1") }
    var leadTime by remember { mutableStateOf("3") }
    var validUntil by remember { mutableStateOf<Long?>(null) }
    var active by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پیشنهاد قیمت تأمین‌کننده") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SelectionField("کالا", state.inventoryItems.firstOrNull { it.id == itemId }?.name, state.inventoryItems.map { it.id to it.name }) { itemId = it }
            SelectionField("تأمین‌کننده", state.suppliers.firstOrNull { it.id == supplierId }?.name, state.suppliers.map { it.id to it.name }) { supplierId = it }
            OutlinedTextField(sku, { sku = it }, label = { Text("کد کالا نزد تأمین‌کننده") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(unitCost, { unitCost = formatMoneyInput(it) }, label = { Text("قیمت واحد (${currencyUnitLabel()})") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minimumOrder, { minimumOrder = it }, label = { Text("حداقل سفارش") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(orderMultiple, { orderMultiple = it }, label = { Text("مضرب سفارش") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(leadTime, { leadTime = it }, label = { Text("زمان تحویل (روز)") }, modifier = Modifier.fillMaxWidth())
            OptionalPersianDateField("اعتبار قیمت تا", validUntil, { validUntil = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(active, { active = it }); Text("پیشنهاد فعال است") }
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                SupplierOfferDraft(
                    supplierId = requireNotNull(supplierId) { "تأمین‌کننده را انتخاب کنید." },
                    itemId = requireNotNull(itemId) { "کالا را انتخاب کنید." },
                    supplierSku = sku,
                    unitCostRial = parseMoneyRial(unitCost).value,
                    minimumOrderMicros = parseQuantity(minimumOrder).value,
                    orderMultipleMicros = parseQuantity(orderMultiple).value,
                    leadTimeDays = requireNotNull(leadTime.toIntOrNull()) { "زمان تحویل معتبر نیست." },
                    validUntilEpochDay = validUntil,
                    isActive = active,
                ).validated()
            }.onSuccess(onConfirm).onFailure { error = it.message }
        }, enabled = !state.busy) { Text("ذخیره پیشنهاد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ReplenishmentPolicyDialog(
    state: OperationsUiState,
    onDismiss: () -> Unit,
    onConfirm: (ReplenishmentPolicyDraft) -> Unit,
) {
    var itemId by remember { mutableStateOf<Long?>(null) }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var targetCoverDays by remember { mutableStateOf("14") }
    var leadTimeDays by remember { mutableStateOf("3") }
    var safetyStock by remember { mutableStateOf("0") }
    var orderMultiple by remember { mutableStateOf("1") }
    var enabled by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadPolicy(selectedItemId: Long) {
        itemId = selectedItemId
        state.procurement.replenishmentPolicies.firstOrNull { it.itemId == selectedItemId }?.let { policy ->
            supplierId = policy.preferredSupplierId
            targetCoverDays = policy.targetCoverDays.toString()
            leadTimeDays = policy.leadTimeDays.toString()
            safetyStock = formatQuantity(policy.safetyStockMicros)
            orderMultiple = formatQuantity(policy.orderMultipleMicros)
            enabled = policy.isEnabled
        } ?: run {
            supplierId = null
            targetCoverDays = "14"
            leadTimeDays = "3"
            safetyStock = "0"
            orderMultiple = "1"
            enabled = true
        }
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سیاست تأمین هوشمند") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("موتور پیشنهاد، مصرف ۳۰ روز اخیر را با موجودی فعلی و سفارش‌های باز مقایسه می‌کند.")
                SelectionField("کالا", state.inventoryItems.firstOrNull { it.id == itemId }?.name, state.inventoryItems.map { it.id to it.name }, ::loadPolicy)
                val supplierOptions = listOf(0L to "بدون ترجیح") + state.suppliers.map { it.id to it.name }
                SelectionField("تأمین‌کننده ترجیحی", state.suppliers.firstOrNull { it.id == supplierId }?.name ?: "بدون ترجیح", supplierOptions) { supplierId = it.takeIf { selected -> selected > 0 } }
                OutlinedTextField(targetCoverDays, { targetCoverDays = it }, label = { Text("پوشش هدف (روز)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(leadTimeDays, { leadTimeDays = it }, label = { Text("زمان تأمین (روز)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(safetyStock, { safetyStock = it }, label = { Text("ذخیره اطمینان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(orderMultiple, { orderMultiple = it }, label = { Text("مضرب سفارش") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(enabled, { enabled = it })
                    Text("پیشنهاد خودکار برای این کالا فعال باشد")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    ReplenishmentPolicyDraft(
                        itemId = requireNotNull(itemId) { "کالا را انتخاب کنید." },
                        preferredSupplierId = supplierId,
                        targetCoverDays = requireNotNull(targetCoverDays.toIntOrNull()) { "پوشش هدف معتبر نیست." },
                        leadTimeDays = requireNotNull(leadTimeDays.toIntOrNull()) { "زمان تأمین معتبر نیست." },
                        safetyStockMicros = parseQuantity(safetyStock).value,
                        orderMultipleMicros = parseQuantity(orderMultiple).value,
                        isEnabled = enabled,
                    ).validated()
                }.onSuccess(onConfirm).onFailure { error = it.message }
            }, enabled = !state.busy) { Text("ذخیره سیاست") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun RequisitionDialog(
    state: OperationsUiState,
    branches: List<BranchRecord>,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseRequisitionDraft) -> Unit,
) {
    val initialBranchId = remember(branches) { branches.singleOrNull { it.isActive }?.id }
    var branchId by rememberSaveable { mutableStateOf(initialBranchId) }
    var locationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var department by remember { mutableStateOf("") }
    var requiredDay by remember { mutableLongStateOf(currentEpochDay()) }
    var itemId by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<RequisitionLineDraft>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("درخواست خرید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                CanonicalBranchSelector(
                    branches = branches,
                    selectedBranchId = branchId,
                    onBranchSelected = { selected -> branchId = selected; locationId = null },
                    allowAllBranches = false,
                    tag = "procurement_requisition_branch",
                )
                val eligibleLocations = state.inventoryLocations.filter { it.active && it.branchId == branchId }
                SelectionField(
                    "انبار مقصد",
                    eligibleLocations.firstOrNull { it.id == locationId }?.name,
                    eligibleLocations.map { it.id to "${it.code.value} · ${it.name}" },
                ) { locationId = it }
                if (branchId != null && eligibleLocations.isEmpty()) {
                    Text("برای شعبه انتخاب‌شده انبار مجاز فعالی وجود ندارد.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(department, { department = it }, label = { Text("واحد درخواست‌کننده") }, modifier = Modifier.fillMaxWidth())
                PersianDateField("تاریخ نیاز", requiredDay) { requiredDay = it }
                SelectionField("کالا", state.inventoryItems.firstOrNull { it.id == itemId }?.name, state.inventoryItems.map { it.id to it.name }) { itemId = it }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("مقدار") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cost, { cost = formatMoneyInput(it) }, label = { Text("برآورد قیمت واحد (${currencyUnitLabel()})") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    runCatching {
                        val selected = requireNotNull(itemId) { "کالا را انتخاب کنید." }
                        require(lines.none { it.itemId == selected }) { "این کالا قبلاً اضافه شده است." }
                        lines = lines + RequisitionLineDraft(selected, parseQuantity(quantity).value, parseMoneyRial(cost).value)
                        quantity = ""; cost = ""; itemId = null; error = null
                    }.onFailure { error = it.message }
                }, modifier = Modifier.fillMaxWidth()) { Text("افزودن قلم") }
                lines.forEach { line ->
                    Text("• ${state.inventoryItems.firstOrNull { it.id == line.itemId }?.name} — ${formatQuantity(line.quantityMicros)}")
                }
            }
        },
        confirmButton = { Button(onClick = {
            runCatching {
                PurchaseRequisitionDraft(
                    department = department,
                    requiredEpochDay = requiredDay,
                    lines = lines,
                    branchId = requireNotNull(branchId) { "شعبه را انتخاب کنید." },
                    destinationLocationId = requireNotNull(locationId) { "انبار مقصد را انتخاب کنید." },
                ).validated()
            }.onSuccess(onConfirm).onFailure { error = it.message }
        }, enabled = !state.busy) { Text("ارسال برای تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun OrderDialog(
    state: OperationsUiState,
    request: RequisitionRecord,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseOrderDraft) -> Unit,
) {
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var orderDay by remember { mutableLongStateOf(currentEpochDay()) }
    var expectedDay by remember { mutableLongStateOf(request.requiredEpochDay.coerceAtLeast(orderDay)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساخت سفارش از ${request.requestNo}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SelectionField("تأمین‌کننده", state.suppliers.firstOrNull { it.id == supplierId }?.name, state.suppliers.map { it.id to it.name }) { supplierId = it }
            PersianDateField("تاریخ سفارش", orderDay) { orderDay = it; if (expectedDay < it) expectedDay = it }
            PersianDateField("تاریخ تحویل مورد انتظار", expectedDay) { expectedDay = it }
        } },
        confirmButton = { Button(onClick = {
            runCatching { PurchaseOrderDraft(request.id, requireNotNull(supplierId) { "تأمین‌کننده را انتخاب کنید." }, orderDay, expectedDay).validated() }
                .onSuccess(onConfirm).onFailure { error = it.message }
        }, enabled = !state.busy) { Text("ایجاد سفارش") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private data class ReceiptInput(
    val delivered: String,
    val accepted: String,
    val reason: String,
    val hasLot: Boolean = false,
    val lotNumber: String = "",
    val supplierLotNumber: String = "",
    val hasExpiry: Boolean = false,
    val expiryEpochDay: Long = 0,
    val lotBarcode: String = "",
)

@Composable
private fun ReceiptDialog(order: PurchaseOrderRecord, onDismiss: () -> Unit, onConfirm: (GoodsReceiptDraft) -> Unit) {
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var deliveryNo by remember { mutableStateOf("") }
    var finalizeOrder by remember { mutableStateOf(false) }
    var values by remember(order.id) {
        mutableStateOf(
            order.lines.associate {
                it.id to ReceiptInput(
                    delivered = formatQuantity(it.remainingQtyMicros),
                    accepted = formatQuantity(it.remainingQtyMicros),
                    reason = "",
                    expiryEpochDay = day,
                )
            },
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحویل ${order.orderNo}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(deliveryNo, { deliveryNo = it }, label = { Text("شماره حواله تأمین‌کننده") }, modifier = Modifier.fillMaxWidth().testTag("procurement_delivery_no"))
            PersianDateField("تاریخ تحویل", day) { day = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(finalizeOrder, { finalizeOrder = it })
                Text("این آخرین تحویل است؛ مانده ثبت‌نشده به‌عنوان کسری بسته شود")
            }
            order.lines.filter { it.remainingQtyMicros > 0 }.forEach { line ->
                val value = values.getValue(line.id)
                Card { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${line.itemName} · مانده ${formatQuantity(line.remainingQtyMicros)}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value.delivered, { values = values + (line.id to value.copy(delivered = it)) }, label = { Text("مقدار تحویلی") })
                    OutlinedTextField(value.accepted, { values = values + (line.id to value.copy(accepted = it)) }, label = { Text("مقدار پذیرفته‌شده") })
                    OutlinedTextField(value.reason, { values = values + (line.id to value.copy(reason = it)) }, label = { Text("دلیل کسری/رد (در صورت مغایرت)") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(value.hasLot, { enabled -> values = values + (line.id to value.copy(hasLot = enabled)) })
                        Text("ثبت لات و قابلیت ردیابی")
                    }
                    if (value.hasLot) {
                        OutlinedTextField(
                            value.lotNumber,
                            { values = values + (line.id to value.copy(lotNumber = it)) },
                            label = { Text("شماره لات") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value.supplierLotNumber,
                            { values = values + (line.id to value.copy(supplierLotNumber = it)) },
                            label = { Text("شماره لات تأمین‌کننده (اختیاری)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(value.hasExpiry, { enabled -> values = values + (line.id to value.copy(hasExpiry = enabled)) })
                            Text("این لات تاریخ انقضا دارد")
                        }
                        if (value.hasExpiry) {
                            PersianDateField("تاریخ انقضا", value.expiryEpochDay) { expiry ->
                                values = values + (line.id to value.copy(expiryEpochDay = expiry))
                            }
                        }
                        OutlinedTextField(
                            value.lotBarcode,
                            { values = values + (line.id to value.copy(lotBarcode = it)) },
                            label = { Text("بارکد لات (اختیاری)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } }
            }
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                GoodsReceiptDraft(order.id, day, deliveryNo, finalizeOrder = finalizeOrder, lines = order.lines.filter { it.remainingQtyMicros > 0 }.map { line ->
                    val value = values.getValue(line.id)
                    GoodsReceiptLineDraft(
                        purchaseOrderLineId = line.id,
                        deliveredQtyMicros = parseQuantity(value.delivered).value,
                        acceptedQtyMicros = parseQuantity(value.accepted).value,
                        rejectionReason = value.reason,
                        lotNumber = value.lotNumber.takeIf { value.hasLot },
                        supplierLotNumber = value.supplierLotNumber.takeIf { value.hasLot && it.isNotBlank() },
                        expiryEpochDay = value.expiryEpochDay.takeIf { value.hasLot && value.hasExpiry },
                        lotBarcode = value.lotBarcode.takeIf { value.hasLot && it.isNotBlank() },
                    )
                }, destinationLocationId = order.destinationLocationId).validated()
            }.onSuccess(onConfirm).onFailure { error = it.message }
        }, modifier = Modifier.testTag("procurement_receive_submit")) { Text("ثبت رسید و موجودی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun MatchedInvoiceDialog(
    state: OperationsUiState,
    branches: List<BranchRecord>,
    order: PurchaseOrderRecord,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseDraft, Boolean) -> Unit,
) {
    var invoiceNo by remember { mutableStateOf("") }
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var dueDay by remember { mutableLongStateOf(day) }
    val fixedBranch = remember(branches, order.branchId) { branches.firstOrNull { it.id == order.branchId } }
    var costs by remember(order.id) { mutableStateOf(order.lines.filter { it.invoiceableQtyMicros > 0 }.associate { it.id to it.unitCostRial.toString() }) }
    var quantities by remember(order.id) { mutableStateOf(order.lines.filter { it.invoiceableQtyMicros > 0 }.associate { it.id to formatQuantity(it.invoiceableQtyMicros) }) }
    var approveVariance by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تطبیق سه‌طرفه ${order.orderNo}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("هر ردیف فقط تا مقدار دریافت‌شده و هنوز فاکتورنشده قابل ثبت است؛ فاکتور جزئی و چند فاکتور برای یک سفارش پشتیبانی می‌شود.")
            Text("شعبه: ${fixedBranch?.name ?: "نامشخص"} · انبار مقصد سفارش #${order.destinationLocationId}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(invoiceNo, { invoiceNo = it }, label = { Text("شماره فاکتور تأمین‌کننده") }, modifier = Modifier.fillMaxWidth())
            PersianDateField("تاریخ فاکتور", day) { day = it; if (dueDay < it) dueDay = it }
            PersianDateField("سررسید", dueDay) { dueDay = it }
            order.lines.filter { it.invoiceableQtyMicros > 0 }.forEach { line ->
                Card { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${line.itemName} · قابل فاکتور ${formatQuantity(line.invoiceableQtyMicros)}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(quantities.getValue(line.id), { quantities = quantities + (line.id to it) }, label = { Text("مقدار این فاکتور") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(costs.getValue(line.id), { costs = costs + (line.id to it) }, label = { Text("قیمت واحد") }, modifier = Modifier.fillMaxWidth())
                } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(approveVariance, { approveVariance = it })
                Text("تأیید مدیریتی مغایرت قیمت بیش از ۵٪")
            }
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                PurchaseDraft(
                    invoiceNo = invoiceNo,
                    supplierId = order.supplierId,
                    purchaseEpochDay = day,
                    branchName = requireNotNull(fixedBranch) { "شعبه سفارش خرید پیدا نشد." }.name,
                    dueEpochDay = dueDay,
                    paymentMethod = PurchasePaymentMethod.PAYABLE,
                    reminderEnabled = true,
                    reminderEpochDay = dueDay,
                    branchId = order.branchId,
                    locationId = order.destinationLocationId,
                    lines = order.lines.filter { it.invoiceableQtyMicros > 0 }.mapNotNull { line ->
                        val quantity = parseQuantity(quantities.getValue(line.id))
                        if (quantity.value == 0L) null else {
                            require(quantity.value <= line.invoiceableQtyMicros) { "مقدار فاکتور ${line.itemName} از مقدار قابل فاکتور بیشتر است." }
                            PurchaseLineDraft(line.itemId, quantity, parseMoneyRial(costs.getValue(line.id)))
                        }
                    }.also { require(it.isNotEmpty()) { "حداقل یک ردیف فاکتور با مقدار مثبت لازم است." } },
                )
            }.onSuccess { onConfirm(it, approveVariance) }.onFailure { error = it.message }
        }, enabled = !state.busy) { Text("تطبیق و ثبت فاکتور") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private data class PurchaseReturnInput(val quantity: String = "0", val reason: String = "")

@Composable
private fun PurchaseReturnDialog(
    order: PurchaseOrderRecord,
    onDismiss: () -> Unit,
    onConfirm: (PurchaseReturnDraft) -> Unit,
) {
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var reason by remember { mutableStateOf("") }
    var values by remember(order.id) {
        mutableStateOf(order.lines.filter { it.returnableQtyMicros > 0 }.associate { it.id to PurchaseReturnInput() })
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مرجوعی خرید ${order.orderNo}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("فقط مقدار پذیرفته‌شده و مرجوع‌نشده قابل بازگشت است.")
            PersianDateField("تاریخ مرجوعی", day) { day = it }
            OutlinedTextField(reason, { reason = it }, label = { Text("دلیل کلی مرجوعی") }, modifier = Modifier.fillMaxWidth())
            order.lines.filter { it.returnableQtyMicros > 0 }.forEach { line ->
                val value = values.getValue(line.id)
                Card { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${line.itemName} · قابل مرجوع ${formatQuantity(line.returnableQtyMicros)}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value.quantity, { values = values + (line.id to value.copy(quantity = it)) }, label = { Text("مقدار مرجوعی") })
                    OutlinedTextField(value.reason, { values = values + (line.id to value.copy(reason = it)) }, label = { Text("دلیل این قلم") })
                } }
            }
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                val lines = order.lines.filter { it.returnableQtyMicros > 0 }.mapNotNull { line ->
                    val value = values.getValue(line.id)
                    val quantity = parseQuantity(value.quantity).value
                    if (quantity == 0L) null else PurchaseReturnLineDraft(line.id, quantity, value.reason)
                }
                PurchaseReturnDraft(order.id, day, reason, lines).validated()
            }.onSuccess(onConfirm).onFailure { error = it.message }
        }) { Text("ثبت مرجوعی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun formatBasisPoints(value: Long): String {
    val whole = value / 100
    val fraction = (value % 100).toString().padStart(2, '0')
    return "$whole.$fraction٪"
}

private fun replenishmentRiskTitle(risk: ReplenishmentRisk): String = when (risk) {
    ReplenishmentRisk.CRITICAL -> "بحرانی"
    ReplenishmentRisk.HIGH -> "پرریسک"
    ReplenishmentRisk.MEDIUM -> "نیازمند اقدام"
}

private fun formatDaysBasisPoints(value: Long): String {
    val whole = value / 10_000
    val tenth = (value % 10_000) / 1_000
    return "$whole.$tenth"
}

private fun requisitionTitle(status: RequisitionStatus): String = when (status) {
    RequisitionStatus.SUBMITTED -> "منتظر تأیید"
    RequisitionStatus.PENDING_SECOND_APPROVAL -> "منتظر تأیید نهایی مالک"
    RequisitionStatus.APPROVED -> "تأییدشده"
    RequisitionStatus.REJECTED -> "ردشده"
    RequisitionStatus.CONVERTED -> "تبدیل به سفارش"
}

private fun orderStatusTitle(status: PurchaseOrderStatus): String = when (status) {
    PurchaseOrderStatus.OPEN -> "باز"
    PurchaseOrderStatus.PARTIALLY_RECEIVED -> "تحویل جزئی"
    PurchaseOrderStatus.RECEIVED -> "دریافت کامل"
    PurchaseOrderStatus.CLOSED -> "بسته‌شده"
    PurchaseOrderStatus.CANCELLED -> "لغوشده"
}
