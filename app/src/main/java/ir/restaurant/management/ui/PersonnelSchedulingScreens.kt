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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.personnel.OvertimeApprovalRecord
import ir.restaurant.management.domain.personnel.OvertimeReviewCommand
import ir.restaurant.management.domain.personnel.PlannedShiftDraft
import ir.restaurant.management.domain.personnel.PlannedShiftStatus
import ir.restaurant.management.domain.personnel.ShiftCategory
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.WorkScheduleDayRule
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkSchedulePatternType

@Composable
internal fun PersonnelSchedulingCenter(
    state: PersonnelUiState,
    onNewShift: () -> Unit,
    onNewSchedule: () -> Unit,
    onPlanShift: () -> Unit,
    onReviewOvertime: (OvertimeApprovalRecord) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
    ) {
        item {
            SectionHeading("Shift & Schedule", "منبع واحد قرارداد → برنامه کاری → شیفت برنامه‌ریزی‌شده → حضور → حقوق")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNewShift, modifier = Modifier.weight(1f)) { Text("شیفت جدید") }
                OutlinedButton(onClick = onNewSchedule, modifier = Modifier.weight(1f)) { Text("برنامه کاری") }
                OutlinedButton(onClick = onPlanShift, modifier = Modifier.weight(1f)) { Text("برنامه‌ریزی نفر") }
            }
        }
        item { Text("الگوهای شیفت", fontWeight = FontWeight.Black) }
        if (state.shiftTemplates.isEmpty()) {
            item { EmptyStatePanel("شیفتی تعریف نشده", "برای حذف فرض ساعت ثابت، حداقل یک Shift Template واقعی بسازید.") }
        } else {
            items(state.shiftTemplates, key = { "shift:${it.id}" }) { shift ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(shift.name, fontWeight = FontWeight.Bold)
                            StatusPill(if (shift.active) "ACTIVE" else "INACTIVE")
                        }
                        Text("${shift.code} · ${shift.category.faLabel} · ${formatMinuteOfDay(shift.startMinute)} تا ${formatMinuteOfDay(shift.endMinute)}")
                        Text(
                            "کار ${shift.plannedWorkMinutes} دقیقه · استراحت ${shift.breakMinutes} · ارفاق ورود ${shift.graceInMinutes} · ارفاق خروج ${shift.graceOutMinutes}" +
                                if (shift.crossesMidnight) " · عبور از نیمه‌شب" else "",
                        )
                        Text(if (shift.overtimeRequiresApproval) "اضافه‌کار نیازمند تأیید مدیر" else "اضافه‌کار طبق شیفت خودکار مجاز است")
                    }
                }
            }
        }
        item { Text("برنامه‌های کاری", fontWeight = FontWeight.Black) }
        if (state.workSchedules.isEmpty()) {
            item { EmptyStatePanel("برنامه کاری وجود ندارد", "برنامه کاری را بسازید و سپس در قرارداد پرسنل انتخاب کنید.") }
        } else {
            items(state.workSchedules, key = { "schedule:${it.id}" }) { schedule ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(schedule.name, fontWeight = FontWeight.Bold)
                            StatusPill(if (schedule.active) "ACTIVE" else "INACTIVE")
                        }
                        Text("${schedule.code} · ${schedule.patternType.storedValue} · چرخه ${schedule.cycleLengthDays} روز")
                        Text("${schedule.days.count { !it.isOffDay }} روز کاری · ${schedule.days.count { it.isOffDay }} روز استراحت")
                    }
                }
            }
        }
        item { Text("شیفت‌های برنامه‌ریزی‌شده پرسنل انتخابی", fontWeight = FontWeight.Black) }
        if (state.selectedEmployeeId == null) {
            item { MessageCard("برای مشاهده شیفت برنامه‌ریزی‌شده، از بخش افراد یک پرسنل را انتخاب کنید.") }
        } else if (state.plannedShifts.isEmpty()) {
            item { EmptyStatePanel("شیفت برنامه‌ریزی‌شده‌ای نیست", "شیفت‌ها هنگام نیاز از برنامه کاری قرارداد ساخته می‌شوند یا می‌توان آن‌ها را دستی برنامه‌ریزی کرد.") }
        } else {
            items(state.plannedShifts.take(30), key = { "planned:${it.id}" }) { planned ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${epochDayToPersian(planned.businessEpochDay).display()} · ${formatMinuteOfDay(planned.startMinute)} تا ${formatMinuteOfDay(planned.endMinute)}", fontWeight = FontWeight.Bold)
                        Text("${planned.source} · ${planned.status.storedValue}${if (planned.crossesMidnight) " · شیفت شب" else ""}")
                    }
                }
            }
        }
        item { Text("صف تأیید اضافه‌کار", fontWeight = FontWeight.Black) }
        if (state.pendingOvertimeApprovals.isEmpty()) {
            item { EmptyStatePanel("اضافه‌کار در انتظار تأیید نیست", "اضافه‌کار خام فقط در صورت نیاز Policy وارد این صف می‌شود.") }
        } else {
            items(state.pendingOvertimeApprovals, key = { "ot:${it.id}" }) { approval ->
                val employee = state.employees.firstOrNull { it.id == approval.employeeId }
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(employee?.displayName ?: "پرسنل #${approval.employeeId}", fontWeight = FontWeight.Bold)
                            Text("${epochDayToPersian(approval.businessEpochDay).display()} · خام ${approval.rawMinutes} دقیقه")
                        }
                        Button(onClick = { onReviewOvertime(approval) }) { Text("بررسی") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShiftTemplateDialog(onDismiss: () -> Unit, onSave: (ShiftTemplateDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ShiftCategory.MORNING) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var breakMinutes by remember { mutableStateOf("0") }
    var graceIn by remember { mutableStateOf("0") }
    var graceOut by remember { mutableStateOf("0") }
    var approvalRequired by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعریف شیفت") },
        text = {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                Text("کد شیفت هنگام ذخیره به‌صورت خودکار و یکتا تولید می‌شود.")
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("نام شیفت") }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ShiftCategory.entries, key = { it.name }) { item ->
                        FilterChip(category == item, { category = item }, { Text(item.faLabel) })
                    }
                }
                OutlinedTextField(start, { start = normalizeClockInput(it) }, label = { Text("شروع HH:mm") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(end, { end = normalizeClockInput(it) }, label = { Text("پایان HH:mm") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(breakMinutes, { breakMinutes = it.filter(Char::isDigit).take(3) }, label = { Text("استراحت (دقیقه)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(graceIn, { graceIn = it.filter(Char::isDigit).take(3) }, label = { Text("ارفاق ورود (دقیقه)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(graceOut, { graceOut = it.filter(Char::isDigit).take(3) }, label = { Text("ارفاق خروج (دقیقه)") }, modifier = Modifier.fillMaxWidth())
                FilterChip(approvalRequired, { approvalRequired = !approvalRequired }, { Text(if (approvalRequired) "تأیید اضافه‌کار الزامی" else "اضافه‌کار بدون تأیید") })
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    ShiftTemplateDraft(
                        code = "",
                        name = name,
                        category = category,
                        startMinute = parseClockMinute(start),
                        endMinute = parseClockMinute(end),
                        breakMinutes = breakMinutes.toIntOrNull() ?: 0,
                        graceInMinutes = graceIn.toIntOrNull() ?: 0,
                        graceOutMinutes = graceOut.toIntOrNull() ?: 0,
                        overtimeRequiresApproval = approvalRequired,
                        notes = notes,
                    ).validated()
                }.onSuccess(onSave).onFailure { error = it.message ?: "اطلاعات شیفت معتبر نیست." }
            }) { Text("ذخیره شیفت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun WorkScheduleDialog(state: PersonnelUiState, onDismiss: () -> Unit, onSave: (WorkScheduleDraft) -> Unit) {
    val activeShifts = state.shiftTemplates.filter { it.active }
    var name by remember { mutableStateOf("") }
    var from by remember { mutableLongStateOf(currentEpochDay()) }
    var assignments by remember { mutableStateOf(List<Long?>(7) { activeShifts.firstOrNull()?.id }) }
    var offDays by remember { mutableStateOf(setOf<Int>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val labels = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برنامه کاری هفتگی") },
        text = {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                if (activeShifts.isEmpty()) MessageCard("ابتدا حداقل یک شیفت فعال بسازید.", true)
                Text("کد برنامه کاری هنگام ذخیره به‌صورت خودکار و یکتا تولید می‌شود.")
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("نام برنامه کاری") }, modifier = Modifier.fillMaxWidth())
                PersianDateField("شروع اثر", from, { from = it })
                (0 until 7).forEach { index ->
                    Text(labels[index], fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(index in offDays, {
                            offDays = if (index in offDays) offDays - index else offDays + index
                        }, { Text("استراحت") })
                    }
                    if (index !in offDays) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(activeShifts, key = { "day:$index:${it.id}" }) { shift ->
                                FilterChip(assignments[index] == shift.id, {
                                    assignments = assignments.toMutableList().also { it[index] = shift.id }
                                }, { Text(shift.name) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = activeShifts.isNotEmpty(), onClick = {
                runCatching {
                    WorkScheduleDraft(
                        code = "",
                        name = name,
                        patternType = WorkSchedulePatternType.WEEKLY_FIXED,
                        cycleLengthDays = 7,
                        effectiveFromEpochDay = from,
                        days = (0 until 7).map { index ->
                            WorkScheduleDayRule(
                                sequenceDay = index,
                                dayOfWeek = index + 1,
                                shiftTemplateId = assignments[index].takeIf { index !in offDays },
                                isOffDay = index in offDays,
                            )
                        },
                    ).validated()
                }.onSuccess(onSave).onFailure { error = it.message ?: "برنامه کاری معتبر نیست." }
            }) { Text("ذخیره برنامه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun PlannedShiftDialog(state: PersonnelUiState, onDismiss: () -> Unit, onSave: (PlannedShiftDraft) -> Unit) {
    val employees = state.employees.filter { it.isActive }
    val shifts = state.shiftTemplates.filter { it.active }
    var employeeId by remember { mutableLongStateOf(state.selectedEmployeeId ?: employees.firstOrNull()?.id ?: 0L) }
    var shiftId by remember { mutableLongStateOf(shifts.firstOrNull()?.id ?: 0L) }
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("شیفت برنامه‌ریزی‌شده") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                OutlinedButton(onClick = { employeeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(employees.firstOrNull { it.id == employeeId }?.displayName ?: "انتخاب پرسنل")
                }
                androidx.compose.material3.DropdownMenu(employeeExpanded, { employeeExpanded = false }) {
                    employees.forEach { employee ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(employee.displayName) }, onClick = { employeeId = employee.id; employeeExpanded = false })
                    }
                }
                PersianDateField("روز کاری", day, { day = it })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shifts, key = { it.id }) { shift ->
                        FilterChip(shiftId == shift.id, { shiftId = shift.id }, { Text("${shift.name} · ${shift.category.faLabel}") })
                    }
                }
                OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل override / توضیح") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching { PlannedShiftDraft(employeeId, day, shiftId, overrideReason = reason, status = PlannedShiftStatus.PUBLISHED).validated() }
                    .onSuccess(onSave).onFailure { error = it.message ?: "شیفت برنامه‌ریزی‌شده معتبر نیست." }
            }) { Text("ثبت و انتشار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun OvertimeReviewDialog(
    approval: OvertimeApprovalRecord,
    employeeName: String,
    onDismiss: () -> Unit,
    onReview: (OvertimeReviewCommand) -> Unit,
) {
    var approved by remember { mutableStateOf(approval.rawMinutes.toString()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأیید اضافه‌کار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                Text("$employeeName · ${epochDayToPersian(approval.businessEpochDay).display()}")
                Text("اضافه‌کار خام: ${approval.rawMinutes} دقیقه", fontWeight = FontWeight.Bold)
                OutlinedTextField(approved, { approved = it.filter(Char::isDigit).take(4) }, label = { Text("دقیقه مورد تأیید") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل تصمیم") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    runCatching { OvertimeReviewCommand(approval.id, 0, reject = true, reason = reason).validated(approval.rawMinutes) }
                        .onSuccess(onReview).onFailure { error = it.message }
                }) { Text("رد") }
                Button(onClick = {
                    runCatching { OvertimeReviewCommand(approval.id, approved.toIntOrNull() ?: -1, reason = reason).validated(approval.rawMinutes) }
                        .onSuccess(onReview).onFailure { error = it.message }
                }) { Text("تأیید") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
