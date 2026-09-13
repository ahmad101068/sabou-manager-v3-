package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.control.BudgetCategory
import ir.restaurant.management.domain.control.AvailabilityDraft
import ir.restaurant.management.domain.control.BudgetDraft
import ir.restaurant.management.domain.control.BudgetRecord
import ir.restaurant.management.domain.control.LaborPolicy
import ir.restaurant.management.domain.control.ProcurementException
import ir.restaurant.management.domain.control.ShiftSwapDraft
import ir.restaurant.management.domain.control.AccountingPeriodDraft
import ir.restaurant.management.domain.control.AccountingPeriodStatus
import ir.restaurant.management.domain.control.CashReconciliationDraft
import ir.restaurant.management.domain.personnel.EmployeeRecord

@Composable
fun ManagementControlScreen(
    state: ControlCenterUiState,
    employees: List<EmployeeRecord>,
    onSetRange: (Long, Long) -> Unit,
    onFollowUp: (Long, String, () -> Unit) -> Unit,
    onOpenInventory: () -> Unit,
    onOpenWorkforce: () -> Unit,
    onSaveBudget: (Long?, BudgetDraft, () -> Unit) -> Unit,
    onRecordSpend: (Long, Long, Long, String, () -> Unit) -> Unit,
    onCloseAccountingPeriod: (AccountingPeriodDraft, String, () -> Unit) -> Unit,
    onReopenAccountingPeriod: (Long, String, () -> Unit) -> Unit,
    onReconcileSalesCash: (CashReconciliationDraft, () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val snapshot = state.snapshot
    var showBudget by remember { mutableStateOf(false) }
    var followUpTarget by remember { mutableStateOf<ProcurementException?>(null) }
    var spendTarget by remember { mutableStateOf<BudgetRecord?>(null) }
    var showPeriodClose by remember { mutableStateOf(false) }
    var reopenPeriodId by remember { mutableStateOf<Long?>(null) }
    var showCashReconcile by remember { mutableStateOf(false) }

    Scaffold(topBar = { ProfessionalTopBar("مرکز کنترل مدیریت", "استثناهای خرید، بودجه، نیروی انسانی و کنترل مالی", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.message?.let { item { MessageCard(it) } }
            item {
                FormSection("دوره تحلیل بهای تمام‌شده") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { PersianDateField("از تاریخ", state.fromEpochDay) { onSetRange(it, state.toEpochDay) } }
                        Column(Modifier.weight(1f)) { PersianDateField("تا تاریخ", state.toEpochDay) { onSetRange(state.fromEpochDay, it) } }
                    }
                    snapshot?.foodCost?.let { food ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("تئوری", formatMoney(food.theoreticalCostRial), Modifier.weight(1f))
                            MetricTile("Actual", food.actualCostRial?.let(::formatMoney) ?: "داده کافی نیست", Modifier.weight(1f), emphasized = (food.varianceRial ?: 0) > 0)
                        }
                        CompactInfoRow("مغایرت مصرف", food.varianceRial?.let(::formatMoney) ?: "ناموجود", (food.varianceRial ?: 0) > 0)
                        CompactInfoRow("ضایعات دوره", formatMoney(food.wasteCostRial))
                        val actualBp = food.actualBasisPoints
                        CompactInfoRow("کیفیت Actual", food.actualDataQuality.name)
                        CompactInfoRow("درصد واقعی از فروش", actualBp?.let { "${it / 100}.${(it % 100).toString().padStart(2, '0')}٪" } ?: "ناموجود")
                    }
                }
            }
            item { SectionHeading("استثناهای سفارش خرید", "مواردی که به پیگیری عملی نیاز دارند") }
            if (snapshot?.procurementExceptions.isNullOrEmpty()) item { EmptyStatePanel("مورد بحرانی وجود ندارد", "سفارش‌های باز در محدوده زمانی قابل‌قبول هستند.") }
            else items(snapshot!!.procurementExceptions, key = { "${it.purchaseOrderId}-${it.kind}" }) { exception ->
                ControlCard(exception.title, "${exception.orderNo} · ${exception.supplierName}") {
                    CompactInfoRow("روزهای تأخیر", exception.ageDays.toString(), exception.ageDays > 0)
                    OutlinedButton(onClick = { followUpTarget = exception }, modifier = Modifier.fillMaxWidth()) { Text("ثبت پیگیری") }
                }
            }
            item {
                ControlCard("کنترل‌های انبار منتقل شد", "مالکیت Location، Lot، FEFO، Count، Waste و Transfer با Inventory است") {
                    Text("این لینک انتقالی جای UI تکراری را می‌گیرد؛ عملیات انبار فقط از Boundaryهای Inventory 2.0 اجرا می‌شود.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onOpenInventory, modifier = Modifier.fillMaxWidth()) { Text("باز کردن مرکز کنترل انبار") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionHeading("بودجه و کنترل هزینه", "خرید، حقوق و ضایعات به‌صورت خودکار محاسبه می‌شوند")
                    TextButton(onClick = { showBudget = true }) { Text("بودجه جدید") }
                }
            }
            if (snapshot?.budgets.isNullOrEmpty()) item { EmptyStatePanel("بودجه‌ای تعریف نشده", "برای دوره جاری سقف هزینه تعریف کنید.") }
            else items(snapshot!!.budgets, key = { it.id }) { budget ->
                ControlCard(budget.name, "${budget.costCenter} · ${budget.category.name}") {
                    CompactInfoRow("مصرف / سقف", "${formatMoney(budget.actualRial)} / ${formatMoney(budget.limitRial)}", budget.actualRial > budget.limitRial)
                    CompactInfoRow("تعهد باز", formatMoney(budget.committedRial), budget.committedRial > 0)
                    CompactInfoRow("مانده", formatMoney(budget.remainingRial), budget.remainingRial < 0)
                    OutlinedButton(onClick = { spendTarget = budget }, modifier = Modifier.fillMaxWidth()) { Text("ثبت هزینه دستی") }
                }
            }
            item {
                ControlCard("کنترل‌های نیروی انسانی", "شیفت، دسترسی، استراحت و سیاست کاری از بخش نیروی انسانی مدیریت می‌شود") {
                    Text("هشدارهای نیروی انسانی و عملیات زمان‌بندی از Workspace پرسنل مدیریت می‌شوند.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onOpenWorkforce, modifier = Modifier.fillMaxWidth()) { Text("باز کردن نیروی انسانی") }
                }
            }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SectionHeading("کنترل مالی قطعی","قفل دوره، تطبیق صندوق و مسیر سند");Row{TextButton(onClick={showCashReconcile=true}){Text("تطبیق صندوق")};TextButton(onClick={showPeriodClose=true}){Text("بستن دوره")}}} }
            if (!snapshot?.accountingPeriods.isNullOrEmpty()) items(snapshot!!.accountingPeriods,key={"period-${it.id}"}) { period ->
                ControlCard("${epochDayToPersian(period.fromEpochDay).display()} تا ${epochDayToPersian(period.toEpochDay).display()}","${period.status.title} · ${period.closedBy}") { Text(period.reason,style=MaterialTheme.typography.bodySmall); if(period.status==AccountingPeriodStatus.CLOSED) OutlinedButton(onClick={reopenPeriodId=period.id},modifier=Modifier.fillMaxWidth()){Text("بازگشایی فقط توسط مالک")} }
            }
            if (!snapshot?.cashReconciliations.isNullOrEmpty()) items(snapshot!!.cashReconciliations.take(10),key={"cash-${it.id}"}) { cash ->
                ControlCard("تطبیق ${epochDayToPersian(cash.businessEpochDay).display()} · نسخه ${cash.revisionNo}",cash.status.title) { CompactInfoRow("انتظار / واقعی","${formatMoney(cash.expectedTotalRial)} / ${formatMoney(cash.actualTotalRial)}",cash.varianceRial!=0L); CompactInfoRow("مغایرت",formatMoney(cash.varianceRial),cash.varianceRial!=0L) }
            }
            item { SectionHeading("ردیابی KPI تا سند","آخرین اسناد قطعی بازه انتخاب‌شده") }
            if (!snapshot?.kpiTrace.isNullOrEmpty()) items(snapshot!!.kpiTrace.take(15),key={"trace-${it.id}"}) { trace -> ControlCard(trace.entryNo,trace.description){Text("${trace.sourceType} #${trace.sourceId} · ${epochDayToPersian(trace.entryEpochDay).display()}",style=MaterialTheme.typography.bodySmall);CompactInfoRow("گردش بدهکار / بستانکار","${formatMoney(trace.debitRial)} / ${formatMoney(trace.creditRial)}") } }
        }
    }

    followUpTarget?.let { target -> FollowUpDialog(target, { followUpTarget = null }) { note -> onFollowUp(target.purchaseOrderId, note) { followUpTarget = null } } }
    if (showBudget) BudgetDialog({ showBudget = false }) { onSaveBudget(null, it) { showBudget = false } }
    spendTarget?.let { budget -> SpendDialog(budget, { spendTarget = null }) { amount, day, reference -> onRecordSpend(budget.id, amount, day, reference) { spendTarget = null } } }
    if (showPeriodClose) AccountingPeriodDialog(state.busy, state.message, { showPeriodClose = false }) { draft, pin ->
        onCloseAccountingPeriod(draft, pin) { showPeriodClose = false }
    }
    reopenPeriodId?.let { periodId ->
        SensitiveActionConfirmationDialog(
            title = "بازگشایی دوره مالی",
            description = "قفل دوره مالی برداشته می‌شود و عملیات در گزارش حسابرسی ثبت خواهد شد.",
            confirmLabel = "بازگشایی دوره",
            busy = state.busy,
            message = state.message,
            onDismiss = { reopenPeriodId = null },
            onConfirm = { pin ->
                onReopenAccountingPeriod(periodId, pin) { reopenPeriodId = null }
            },
        )
    }
    if(showCashReconcile) CashReconciliationDialog({showCashReconcile=false}){onReconcileSalesCash(it){showCashReconcile=false}}
}

@Composable
private fun AccountingPeriodDialog(
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSave: (AccountingPeriodDraft, String) -> Unit,
) {
    var from by remember { mutableLongStateOf(currentEpochDay() - 29) }
    var to by remember { mutableLongStateOf(currentEpochDay()) }
    var reason by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بستن قطعی دوره مالی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersianDateField("از", from) { from = it }
                PersianDateField("تا", to) { to = it }
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل بستن") })
                if (submitted) message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                SensitivePinField(pin, { pin = it })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && reason.trim().length >= 5 && pin.length in 6..12,
                onClick = {
                    val submittedPin = pin
                    pin = ""
                    submitted = true
                    onSave(AccountingPeriodDraft(from, to, reason), submittedPin)
                },
            ) { Text("بستن و قفل اسناد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun CashReconciliationDialog(onDismiss: () -> Unit, onSave: (CashReconciliationDraft) -> Unit) {
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var cash by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    var transfer by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    @Composable fun AmountField(value: String, change: (String) -> Unit, label: String) = OutlinedTextField(
        value = value,
        onValueChange = { change(formatMoneyInput(it)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تطبیق صندوق و کارت‌خوان") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersianDateField("روز فروش بسته‌شده", day) { day = it }
                AmountField(cash, { cash = it }, "نقد واقعی")
                AmountField(card, { card = it }, "کارت واقعی")
                AmountField(transfer, { transfer = it }, "حواله واقعی")
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("توضیح مغایرت") })
            }
        },
        confirmButton = { Button(onClick = { onSave(CashReconciliationDraft(day, parseMoneyInputOrZero(cash), parseMoneyInputOrZero(card), parseMoneyInputOrZero(transfer), note)) }) { Text("ثبت تطبیق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable private fun ControlCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable private fun FollowUpDialog(target: ProcurementException, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("پیگیری ${target.orderNo}") }, text = { OutlinedTextField(note, { note = it.take(300) }, label = { Text("نتیجه تماس یا پیگیری") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { onSave(note) }) { Text("ثبت") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable private fun BudgetDialog(onDismiss: () -> Unit, onSave: (BudgetDraft) -> Unit) {
    var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf(BudgetCategory.PURCHASE) }; var center by remember { mutableStateOf("کل مجموعه") }; var from by remember { mutableLongStateOf(currentEpochDay()) }; var to by remember { mutableLongStateOf(currentEpochDay() + 30) }; var limit by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعریف بودجه") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { error?.let { MessageCard(it, true) }; OutlinedTextField(name, { name = it.take(80) }, label = { Text("نام بودجه") }); SelectionField("نوع", budgetCategoryTitle(category), BudgetCategory.entries.mapIndexed { index, value -> index.toLong() to budgetCategoryTitle(value) }) { category = BudgetCategory.entries[it.toInt()] }; OutlinedTextField(center, { center = it.take(80) }, label = { Text("مرکز هزینه") }); PersianDateField("شروع", from) { from = it }; PersianDateField("پایان", to) { to = it }; OutlinedTextField(limit, { limit = formatMoneyInput(it) }, label = { Text("سقف بودجه") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }, confirmButton = { Button(onClick = { runCatching { BudgetDraft(name, category, center, from, to, parseMoneyInputOrZero(limit)).validated() }.onSuccess(onSave).onFailure { error = it.message } }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable private fun SpendDialog(budget: BudgetRecord, onDismiss: () -> Unit, onSave: (Long, Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }; var day by remember { mutableLongStateOf(currentEpochDay().coerceIn(budget.fromEpochDay, budget.toEpochDay)) }; var reference by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("هزینه دستی ${budget.name}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { error?.let { MessageCard(it, true) }; OutlinedTextField(amount, { amount = formatMoneyInput(it) }, label = { Text("مبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); PersianDateField("تاریخ", day) { day = it }; OutlinedTextField(reference, { reference = it.take(120) }, label = { Text("مرجع / توضیح") }) } }, confirmButton = { Button(onClick = { runCatching { parseMoneyInputOrZero(amount) }.onSuccess { onSave(it, day, reference) }.onFailure { error = it.message ?: "مبلغ هزینه معتبر نیست." } }) { Text("ثبت") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable internal fun LaborPolicyDialog(onDismiss: () -> Unit, onSave: (LaborPolicy) -> Unit) {
    var weekly by remember { mutableStateOf("44") }; var shift by remember { mutableStateOf("12") }; var rest by remember { mutableStateOf("11") }; var breakAfter by remember { mutableStateOf("6") }; var breakMinutes by remember { mutableStateOf("30") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("سیاست کنترل ساعات کار") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { error?.let { MessageCard(it, true) }; OutlinedTextField(weekly, { weekly = it.filter(Char::isDigit) }, label = { Text("سقف هفتگی (ساعت)") }); OutlinedTextField(shift, { shift = it.filter(Char::isDigit) }, label = { Text("سقف هر شیفت (ساعت)") }); OutlinedTextField(rest, { rest = it.filter(Char::isDigit) }, label = { Text("حداقل استراحت بین شیفت‌ها (ساعت)") }); OutlinedTextField(breakAfter, { breakAfter = it.filter(Char::isDigit) }, label = { Text("الزام استراحت پس از (ساعت)") }); OutlinedTextField(breakMinutes, { breakMinutes = it.filter(Char::isDigit) }, label = { Text("حداقل استراحت (دقیقه)") }) } }, confirmButton = { Button(onClick = { runCatching { LaborPolicy(weekly.toInt() * 60, shift.toInt() * 60, rest.toInt() * 60, breakAfter.toInt() * 60, breakMinutes.toInt()).validated() }.onSuccess(onSave).onFailure { error = it.message } }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable internal fun AvailabilityDialog(employees: List<EmployeeRecord>, onDismiss: () -> Unit, onSave: (AvailabilityDraft) -> Unit) {
    var employeeId by remember { mutableLongStateOf(employees.firstOrNull()?.id ?: 0) }; var day by remember { mutableStateOf("1") }; var from by remember { mutableStateOf("480") }; var to by remember { mutableStateOf("1020") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("دسترس‌پذیری هفتگی") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { error?.let { MessageCard(it, true) }; SelectionField("پرسنل", employees.firstOrNull { it.id == employeeId }?.name, employees.map { it.id to it.name }) { employeeId = it }; SelectionField("روز هفته", day, (1L..7L).map { it to "روز $it" }) { day = it.toString() }; OutlinedTextField(from, { from = it.filter(Char::isDigit) }, label = { Text("شروع به دقیقه از نیمه‌شب") }); OutlinedTextField(to, { to = it.filter(Char::isDigit) }, label = { Text("پایان به دقیقه از نیمه‌شب") }) } }, confirmButton = { Button(onClick = { runCatching { AvailabilityDraft(employeeId, day.toInt(), from.toInt(), to.toInt()).validated() }.onSuccess(onSave).onFailure { error = it.message } }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable internal fun ShiftSwapDialog(shifts: List<ir.restaurant.management.domain.control.LaborShiftInput>, employees: List<EmployeeRecord>, onDismiss: () -> Unit, onSave: (ShiftSwapDraft) -> Unit) {
    var shiftId by remember { mutableLongStateOf(shifts.firstOrNull()?.shiftId ?: 0) }; val shift = shifts.firstOrNull { it.shiftId == shiftId }; var target by remember { mutableLongStateOf(employees.firstOrNull { it.id != shift?.employeeId }?.id ?: 0) }; var note by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("درخواست تعویض شیفت") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { error?.let { MessageCard(it, true) }; SelectionField("شیفت", shift?.let { "${it.employeeName} · ${epochDayToPersian(it.epochDay).display()}" }, shifts.map { it.shiftId to "${it.employeeName} · ${epochDayToPersian(it.epochDay).display()}" }) { shiftId = it }; SelectionField("جایگزین", employees.firstOrNull { it.id == target }?.name, employees.filter { it.id != shift?.employeeId }.map { it.id to it.name }) { target = it }; OutlinedTextField(note, { note = it.take(300) }, label = { Text("دلیل درخواست") }) } }, confirmButton = { Button(onClick = { runCatching { ShiftSwapDraft(shiftId, requireNotNull(shift).employeeId, target.takeIf { it > 0 }, note).validated() }.onSuccess(onSave).onFailure { error = it.message } }) { Text("ثبت درخواست") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable internal fun WorkBreakDialog(shift: ir.restaurant.management.domain.control.LaborShiftInput, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var start by remember { mutableStateOf((shift.startMinute + 180).toString()) }; var end by remember { mutableStateOf((shift.startMinute + 210).coerceAtMost(shift.endMinute).toString()) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ثبت استراحت ${shift.employeeName}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { error?.let { MessageCard(it, true) }; Text("شیفت: ${shift.startMinute} تا ${shift.endMinute}"); OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { Text("شروع به دقیقه از نیمه‌شب") }); OutlinedTextField(end, { end = it.filter(Char::isDigit) }, label = { Text("پایان به دقیقه از نیمه‌شب") }) } }, confirmButton = { Button(onClick = { runCatching { start.toInt() to end.toInt() }.onSuccess { (from, to) -> onSave(from, to) }.onFailure { error = it.message ?: "زمان استراحت معتبر نیست." } }) { Text("ثبت") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}


private fun budgetCategoryTitle(value: BudgetCategory): String = when (value) {
    BudgetCategory.PURCHASE -> "خرید"
    BudgetCategory.LABOR -> "نیروی انسانی"
    BudgetCategory.WASTE -> "ضایعات"
    BudgetCategory.OTHER -> "سایر"
}
