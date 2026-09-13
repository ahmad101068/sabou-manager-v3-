@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.personnel.EmployeeDraft
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.PayrollDraft
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeAdvanceDraft
import ir.restaurant.management.domain.personnel.LeaveDraft
import ir.restaurant.management.domain.personnel.LeaveReviewDraft
import ir.restaurant.management.domain.personnel.PayrollPolicyDraft
import ir.restaurant.management.domain.personnel.PayrollRecord
import ir.restaurant.management.domain.personnel.PayrollStatus
import ir.restaurant.management.domain.personnel.PayrollReversalDraft
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.treasury.TreasuryChannel

@Composable
internal fun EmployeeDialog(existing: EmployeeRecord?, branches: List<BranchRecord>, onDismiss: () -> Unit, onSave: (EmployeeDraft) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var fatherName by remember(existing?.id) { mutableStateOf(existing?.fatherName.orEmpty()) }
    var job by remember(existing?.id) { mutableStateOf(existing?.jobTitle.orEmpty()) }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var salary by remember(existing?.id) { mutableStateOf(existing?.monthlySalaryRial?.let(::formatMoneyInputFromRial).orEmpty()) }
    var nationalId by remember(existing?.id) { mutableStateOf(existing?.nationalId.orEmpty()) }
    var employeeCode by remember(existing?.id) { mutableStateOf(existing?.employeeCode.orEmpty()) }
    val initialBranchId = remember(existing?.id, branches) {
        existing?.branchId ?: existing?.branchName?.takeIf { it.isNotBlank() }?.let { legacyName ->
            branches.filter { it.name == legacyName }.singleOrNull()?.id
        }
    }
    var selectedBranchId by remember(existing?.id, initialBranchId) { mutableStateOf(initialBranchId) }
    var insuranceNumber by remember(existing?.id) { mutableStateOf(existing?.insuranceNumber.orEmpty()) }
    var bankCard by remember(existing?.id) { mutableStateOf(existing?.bankCard.orEmpty()) }
    var address by remember(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var emergencyContact by remember(existing?.id) { mutableStateOf(existing?.emergencyContact.orEmpty()) }
    var birthDate by remember(existing?.id) { mutableStateOf(existing?.birthEpochDay) }
    var hireDate by remember(existing?.id) { mutableLongStateOf(existing?.hireEpochDay ?: currentEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "ثبت پرسنل جدید" else "ویرایش اطلاعات پرسنل") },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { MessageCard(it, isError = true) }
                OutlinedTextField(name, { name = it }, label = { Text("نام و نام خانوادگی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fatherName, { fatherName = it }, label = { Text("نام پدر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(job, { job = it }, label = { Text("عنوان شغلی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    employeeCode,
                    { employeeCode = it.take(30) },
                    label = { Text("کد پرسنلی خودکار") },
                    placeholder = { Text("هنگام ثبت صادر می‌شود") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CanonicalBranchSelector(
                    branches = branches,
                    selectedBranchId = selectedBranchId,
                    onBranchSelected = { selectedBranchId = it },
                    label = "شعبه / سطح سازمان",
                    allowAllBranches = true,
                    tag = "personnel_branch_selector",
                )
                OutlinedTextField(nationalId, { nationalId = it.filter(Char::isDigit).take(10) }, label = { Text("کد ملی (۱۰ رقم)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OptionalPersianDateField("تاریخ تولد", birthDate, { birthDate = it })
                PersianDateField("تاریخ استخدام", hireDate, { hireDate = it })
                OutlinedTextField(
                    phone,
                    { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text("شماره تماس") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(insuranceNumber, { insuranceNumber = it.take(30) }, label = { Text("شماره بیمه") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(bankCard, { bankCard = it.filter(Char::isDigit).take(16) }, label = { Text("شماره کارت بانکی (۱۶ رقم)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emergencyContact, { emergencyContact = it.take(80) }, label = { Text("تماس اضطراری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it.take(300) }, label = { Text("آدرس") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    salary,
                    { salary = formatMoneyInput(it) },
                    label = { Text("حقوق پایه (${currencyUnitLabel()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    EmployeeDraft(
                        name = name,
                        fatherName = fatherName,
                        jobTitle = job,
                        phone = phone,
                        monthlySalaryRial = parseMoneyInputOrZero(salary),
                        nationalId = nationalId,
                        birthEpochDay = birthDate,
                        hireEpochDay = hireDate,
                        employeeCode = employeeCode,
                        branchName = branches.firstOrNull { it.id == selectedBranchId }?.name.orEmpty(),
                        insuranceNumber = insuranceNumber,
                        bankCard = bankCard,
                        address = address,
                        emergencyContact = emergencyContact,
                        branchId = selectedBranchId,
                    ).validated()
                }
                    .onSuccess(onSave)
                    .onFailure { error = it.message ?: "اطلاعات پرسنل معتبر نیست." }
            }) { Text(if (existing == null) "ذخیره پرسنل" else "ذخیره تغییرات") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun AttendanceDialog(state: PersonnelUiState, onDismiss: () -> Unit, onSave: (AttendanceDraft) -> Unit) {
    val active = state.employees.filter { it.isActive }
    var selected by remember { mutableStateOf(active.firstOrNull()?.id ?: 0L) }
    var expanded by remember { mutableStateOf(false) }
    val today = remember { currentEpochDay() }
    var workDay by remember { mutableLongStateOf(today) }
    var checkIn by remember { mutableStateOf("") }
    var checkOut by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("PRESENT") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("ثبت حضور روزانه") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { MessageCard(it, isError = true) }
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(active.firstOrNull { it.id == selected }?.name ?: "انتخاب پرسنل") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { active.forEach { employee -> DropdownMenuItem(text = { Text(employee.name) }, onClick = { selected = employee.id; expanded = false }) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { status = "PRESENT" }, modifier = Modifier.weight(1f)) { Text(if (status == "PRESENT") "✓ حاضر" else "حاضر") }
                OutlinedButton(onClick = { status = "ABSENT" }, modifier = Modifier.weight(1f)) { Text(if (status == "ABSENT") "✓ غایب" else "غایب") }
            }
            if (status == "PRESENT") {
                ClockField(checkIn, { checkIn = it }, "ساعت ورود واقعی")
                ClockField(checkOut, { checkOut = it }, "ساعت خروج واقعی")
                Text("شیفت برنامه‌ریزی‌شده، ارفاق، تأخیر، خروج زودهنگام و اضافه‌کار از برنامه کاری پرسنل خوانده می‌شود.", style = MaterialTheme.typography.bodySmall)
            }
            PersianDateField("تاریخ حضور", workDay, { workDay = it })
            OutlinedTextField(notes, { notes = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                AttendanceDraft(
                    selected,
                    workDay,
                    status,
                    if (status == "PRESENT") parseClockMinute(checkIn) else null,
                    if (status == "PRESENT") parseClockMinute(checkOut) else null,
                    notes = notes,
                ).validated()
            }.onSuccess(onSave).onFailure { error = it.message ?: "اطلاعات حضور معتبر نیست." }
        }) { Text("ذخیره حضور") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun PayrollPolicyDialog(onDismiss: () -> Unit, onSave: (PayrollPolicyDraft) -> Unit) {
    var title by remember { mutableStateOf("سیاست استاندارد حقوق") }
    var from by remember { mutableLongStateOf(currentEpochDay()) }
    var to by remember { mutableStateOf<Long?>(null) }
    var overtimeRate by remember { mutableStateOf("") }
    var absenceRate by remember { mutableStateOf("") }
    var lateRate by remember { mutableStateOf("0") }
    var overtimeMultiplierPercent by remember { mutableStateOf("100") }
    var insurancePercent by remember { mutableStateOf("0") }
    var taxPercent by remember { mutableStateOf("0") }
    var holidayMultiplierPercent by remember { mutableStateOf("100") }
    var nightMultiplierPercent by remember { mutableStateOf("100") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سیاست نسخه‌دار حقوق") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            MessageCard("سیاست پس از ثبت و استفاده در فیش تغییر نمی‌کند. برای نرخ‌های جدید، نسخه‌ای با بازه بدون هم‌پوشانی بسازید.")
            error?.let { MessageCard(it, true) }
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("عنوان سیاست") }, modifier = Modifier.fillMaxWidth())
            PersianDateField("شروع اعتبار", from, { from = it })
            OptionalPersianDateField("پایان اعتبار", to, { to = it }, defaultEpochDay = from)
            MoneyField(overtimeRate, { overtimeRate = it }, "نرخ هر ساعت اضافه‌کار (${currencyUnitLabel()})")
            MoneyField(absenceRate, { absenceRate = it }, "کسر هر روز غیبت (${currencyUnitLabel()})")
            MoneyField(lateRate, { lateRate = it }, "کسر هر دقیقه تأخیر (${currencyUnitLabel()})")
            OutlinedTextField(overtimeMultiplierPercent, { overtimeMultiplierPercent = it.filter(Char::isDigit).take(4) }, label = { Text("ضریب اضافه‌کاری (درصد)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(holidayMultiplierPercent, { holidayMultiplierPercent = it.filter(Char::isDigit).take(4) }, label = { Text("ضریب کار در تعطیل (درصد)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nightMultiplierPercent, { nightMultiplierPercent = it.filter(Char::isDigit).take(4) }, label = { Text("ضریب شب‌کاری (درصد)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(insurancePercent, { insurancePercent = it.filter(Char::isDigit).take(3) }, label = { Text("بیمه سهم کارمند (درصد)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(taxPercent, { taxPercent = it.filter(Char::isDigit).take(3) }, label = { Text("مالیات حقوق (درصد)") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                fun percentBasisPoints(raw: String, minimum: Int = 0): Int {
                    val percent = raw.toIntOrNull() ?: error("درصد واردشده معتبر نیست.")
                    require(percent >= minimum) { "درصد واردشده کمتر از حد مجاز است." }
                    return Math.multiplyExact(percent, 100)
                }
                PayrollPolicyDraft(
                    title = title,
                    effectiveFromEpochDay = from,
                    effectiveToEpochDay = to,
                    overtimeHourlyRateRial = parseMoneyInputOrZero(overtimeRate),
                    absenceDailyDeductionRial = parseMoneyInputOrZero(absenceRate),
                    lateMinuteDeductionRial = parseMoneyInputOrZero(lateRate),
                    overtimeMultiplierBasisPoints = percentBasisPoints(overtimeMultiplierPercent),
                    insuranceBasisPoints = percentBasisPoints(insurancePercent),
                    taxBasisPoints = percentBasisPoints(taxPercent),
                    holidayMultiplierBasisPoints = percentBasisPoints(holidayMultiplierPercent, 100),
                    nightMultiplierBasisPoints = percentBasisPoints(nightMultiplierPercent, 100),
                ).validated()
            }.onSuccess(onSave).onFailure { error = it.message }
        }) { Text("ثبت نسخه سیاست") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun LeaveDialog(state: PersonnelUiState, onDismiss: () -> Unit, onSave: (LeaveDraft) -> Unit) {
    val active = state.employees.filter { it.isActive }; var employeeId by remember { mutableStateOf(active.firstOrNull()?.id ?: 0L) }; var expanded by remember { mutableStateOf(false) }; var start by remember { mutableLongStateOf(currentEpochDay()) }; var end by remember { mutableLongStateOf(currentEpochDay()) }; var type by remember { mutableStateOf("PAID") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("درخواست مرخصی")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(active.firstOrNull{it.id==employeeId}?.name?:"انتخاب پرسنل")}; DropdownMenu(expanded,{expanded=false}){active.forEach{e->DropdownMenuItem(text={Text(e.name)},onClick={employeeId=e.id;expanded=false})}}; PersianDateField("از تاریخ",start,{start=it}); PersianDateField("تا تاریخ",end,{end=it}); Row{listOf("PAID" to "استحقاقی","SICK" to "استعلاجی","UNPAID" to "بدون حقوق").forEach{(v,t)->TextButton(onClick={type=v}){Text(if(type==v)"✓ $t" else t)}}};OutlinedTextField(notes,{notes=it},label={Text("توضیحات")})}},confirmButton={Button(onClick={onSave(LeaveDraft(employeeId,start,end,type,notes))}){Text("ثبت درخواست")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable
internal fun AttendanceSummaryDialog(state: PersonnelUiState, onDismiss: () -> Unit, onLoad: (Long, Long, Long) -> Unit) {
    val active=state.employees.filter{it.isActive}; var employeeId by remember{mutableStateOf(active.firstOrNull()?.id?:0L)}; var expanded by remember{mutableStateOf(false)}; var from by remember{mutableLongStateOf(currentEpochDay()-30)}; var to by remember{mutableLongStateOf(currentEpochDay())}; val context=LocalContext.current
    AlertDialog(onDismissRequest=onDismiss,title={Text("گزارش حضور")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(active.firstOrNull{it.id==employeeId}?.name?:"انتخاب پرسنل")};DropdownMenu(expanded,{expanded=false}){active.forEach{e->DropdownMenuItem(text={Text(e.name)},onClick={employeeId=e.id;expanded=false})}};PersianDateField("از تاریخ",from,{from=it});PersianDateField("تا تاریخ",to,{to=it});state.attendanceSummary?.let{Text("حاضر: ${it.presentDays} • غیبت: ${it.absentDays} • مرخصی: ${it.leaveDays}\nکارکرد: ${it.workedMinutes} دقیقه • تأخیر: ${it.lateMinutes} • اضافه‌کاری: ${it.overtimeMinutes}",fontWeight=FontWeight.Bold);OutlinedButton(onClick={printAttendanceSummary(context,active.firstOrNull{employee->employee.id==employeeId}?.name?:"پرسنل",it)},modifier=Modifier.fillMaxWidth()){Text("چاپ گزارش حضور / PDF")}}}},confirmButton={Button(onClick={onLoad(employeeId,from,to)}){Text("محاسبه")}},dismissButton={TextButton(onClick=onDismiss){Text("بستن")}})
}

@Composable
internal fun ContractDialog(state: PersonnelUiState, onDismiss: () -> Unit, onSave: (EmployeeContractDraft) -> Unit) {
    val id=state.selectedEmployeeId?:0L; var type by remember{mutableStateOf("تمام‌وقت")}; var start by remember{mutableLongStateOf(currentEpochDay())}; var end by remember{mutableStateOf<Long?>(null)}; var salary by remember{mutableStateOf(state.employees.firstOrNull{it.id==id}?.monthlySalaryRial?.let(::formatMoneyInputFromRial).orEmpty())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ثبت قرارداد")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){state.contracts.firstOrNull()?.let{Text("قرارداد فعلی: ${it.contractType} • ${formatMoney(it.baseSalaryRial)}")};OutlinedTextField(type,{type=it},label={Text("نوع قرارداد")});PersianDateField("تاریخ شروع",start,{start=it});OptionalPersianDateField("تاریخ پایان",end,{end=it},defaultEpochDay=start);MoneyField(salary,{salary=it},"حقوق قرارداد")}},confirmButton={Button(onClick={onSave(EmployeeContractDraft(id,type,start,end,parseMoneyInputOrZero(salary)))}){Text("ذخیره قرارداد")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}

@Composable
internal fun AdvanceDialog(
    state: PersonnelUiState,
    onDismiss: () -> Unit,
    onSettle: (Long, Long, TreasuryChannel, Long) -> Unit,
    onSave: (EmployeeAdvanceDraft) -> Unit,
) {
    val active = state.employees.filter { it.isActive }
    var employeeId by remember { mutableLongStateOf(state.selectedEmployeeId ?: active.firstOrNull()?.id ?: 0L) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var method by remember { mutableStateOf(TreasuryChannel.BANK) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val employee = active.firstOrNull { it.id == employeeId }
    val employeeAdvances = state.openAdvances.filter { it.employeeId == employeeId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مساعده پرسنل") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { MessageCard(it, true) }
                OutlinedButton(onClick = { employeeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(employee?.displayName ?: "انتخاب پرسنل")
                }
                DropdownMenu(employeeExpanded, { employeeExpanded = false }) {
                    active.forEach { row ->
                        DropdownMenuItem(
                            text = { Text("${row.displayName} · ${row.employeeCode ?: "—"}") },
                            onClick = { employeeId = row.id; employeeExpanded = false },
                        )
                    }
                }
                MoneyField(amount, { amount = it }, "مبلغ مساعده (${currencyUnitLabel()})")
                PersianDateField("تاریخ مساعده", day, { day = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(method == TreasuryChannel.BANK, { method = TreasuryChannel.BANK }, { Text("بانکی") })
                    FilterChip(method == TreasuryChannel.CASH, { method = TreasuryChannel.CASH }, { Text("نقدی") })
                }
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("دلیل / یادداشت") }, modifier = Modifier.fillMaxWidth())
                if (employeeAdvances.isNotEmpty()) {
                    Text("مساعده‌های باز این پرسنل", fontWeight = FontWeight.Bold)
                    employeeAdvances.forEach { advance ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("مانده: ${formatMoney(advance.remainingAmountRial)}")
                            TextButton(onClick = { onSettle(advance.id, advance.remainingAmountRial, method, currentEpochDay()) }) { Text("تسویه کامل") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching { EmployeeAdvanceDraft(employeeId, parseMoneyInputOrZero(amount), day, method, notes).validated() }
                    .onSuccess(onSave)
                    .onFailure { error = it.message ?: "اطلاعات مساعده معتبر نیست." }
            }) { Text("ثبت مساعده") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(formatMoneyInput(it)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun ClockField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(normalizeClockInput(it)) },
        label = { Text("$label (HH:mm)") },
        supportingText = { Text("نمونه: 08:30") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
