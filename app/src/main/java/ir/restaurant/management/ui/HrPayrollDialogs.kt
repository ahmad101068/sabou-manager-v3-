@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.restaurant.management.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.personnel.ApproveManualAdjustmentCommand
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.AttendanceRecord
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.ClosePayrollPeriodCommand
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeContractRecord
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.personnel.HrDocumentDraft
import ir.restaurant.management.domain.personnel.HrDocumentType
import ir.restaurant.management.domain.personnel.EmploymentContractStatus
import ir.restaurant.management.domain.personnel.EmploymentStatus
import ir.restaurant.management.domain.personnel.ManualAdjustmentStatus
import ir.restaurant.management.domain.personnel.ManualPayrollAdjustmentCommand
import ir.restaurant.management.domain.personnel.PayPayslipCommand
import ir.restaurant.management.domain.personnel.PayrollAdvanceDeductionRequest
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollBatchRecordV2
import ir.restaurant.management.domain.personnel.PayrollBatchStatus
import ir.restaurant.management.domain.personnel.PayrollComponentDirection
import ir.restaurant.management.domain.personnel.PayrollComponentType
import ir.restaurant.management.domain.personnel.PayrollPaymentStatus
import ir.restaurant.management.domain.personnel.PayrollPayslipDetailV2
import ir.restaurant.management.domain.personnel.PayrollPayslipRecordV2
import ir.restaurant.management.domain.personnel.PayrollPayslipStatus
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPeriodRecordV2
import ir.restaurant.management.domain.personnel.PayrollPeriodStatus
import ir.restaurant.management.domain.personnel.ReopenPayrollPeriodCommand
import ir.restaurant.management.domain.personnel.ReversePayrollPaymentCommand
import ir.restaurant.management.domain.personnel.ReversePayslipCommandV2
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryAccount

private enum class EmployeeDetailSection(val title: String) {
    OVERVIEW("نمای کلی"),
    EMPLOYMENT("استخدام"),
    CONTRACTS("قراردادها"),
    ATTENDANCE("حضور"),
    LEAVE("مرخصی"),
    PAYROLL("حقوق"),
    ADVANCES("مساعده"),
    PERFORMANCE("عملکرد"),
    DOCUMENTS("اسناد"),
    AUDIT("ممیزی"),
}

@Composable
internal fun Employee360Dialog(
    employee: EmployeeRecord,
    personnelState: PersonnelUiState,
    hrState: HrPayrollUiState,
    performanceState: PerformanceUiState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onNewContract: (EmployeeContractRecord?) -> Unit,
    onApproveContract: (Long) -> Unit,
    onAdvance: () -> Unit,
    onOpenPayslip: (Long) -> Unit,
    onSaveDocument: (HrDocumentDraft) -> Unit,
    onArchiveDocument: (Long) -> Unit,
    onDeactivate: () -> Unit,
) {
    val context = LocalContext.current
    var pendingDocumentType by rememberSaveable(employee.id) { mutableStateOf(HrDocumentType.HR_ATTACHMENT.storedValue) }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val type = HrDocumentType.entries.firstOrNull { it.storedValue == pendingDocumentType } ?: HrDocumentType.HR_ATTACHMENT
                onSaveDocument(
                    HrDocumentDraft(
                        employeeId = employee.id,
                        documentType = type,
                        displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "سند ${type.persianLabel}",
                        contentUri = uri.toString(),
                        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    ),
                )
            } catch (error: SecurityException) {
                Toast.makeText(context, "مجوز پایدار خواندن فایل صادر نشد: ${error.message ?: "خطای مجوز"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    var section by rememberSaveable(employee.id) { mutableStateOf(EmployeeDetailSection.OVERVIEW) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(employee.displayName)
                Text("${employee.employeeCode ?: "—"} · ${employee.employmentStatus.storedValue}", style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 620.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EmployeeDetailSection.entries, key = { it.name }) { item ->
                        FilterChip(section == item, { section = item }, { Text(item.title) })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (section) {
                        EmployeeDetailSection.OVERVIEW -> {
                            item { CompactInfoRow("نام / تلفن", "${employee.displayName} / ${employee.phone.ifBlank { "—" }}") }
                            item { CompactInfoRow("شغل / دپارتمان", "${employee.jobTitle} / ${employee.department}") }
                            item { CompactInfoRow("شعبه", employee.branchName.ifBlank { "—" }) }
                            item { CompactInfoRow("تاریخ استخدام", employee.hireEpochDay?.let { epochDayToPersian(it).display() } ?: "ثبت نشده") }
                            item { CompactInfoRow("حساب پرداخت", employee.maskedBankAccount.ifBlank { "تعریف نشده" }) }
                            item {
                                personnelState.payrollReadiness?.takeIf { it.employeeId == employee.id }?.let { readiness ->
                                    Card {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                when (readiness.status) {
                                                    ir.restaurant.management.domain.personnel.PayrollReadinessStatus.READY -> "آماده محاسبه حقوق ✅"
                                                    ir.restaurant.management.domain.personnel.PayrollReadinessStatus.WARNING -> "نیازمند بررسی ⚠️"
                                                    ir.restaurant.management.domain.personnel.PayrollReadinessStatus.BLOCKED -> "نیازمند تکمیل ⚠️"
                                                },
                                                fontWeight = FontWeight.Bold,
                                            )
                                            readiness.issues.forEach { issue ->
                                                Text("• ${issue.message} — ${issue.action}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                } ?: EmptyStatePanel("Payroll Readiness در حال ارزیابی است", "وضعیت قرارداد، برنامه کاری، سیاست حقوق و مقصد پرداخت بررسی می‌شود.")
                            }
                        }
                        EmployeeDetailSection.EMPLOYMENT -> {
                            item { Text("وضعیت استخدام: ${employee.employmentStatus.storedValue}", fontWeight = FontWeight.Bold) }
                            item { CompactInfoRow("شروع همکاری", employee.hireEpochDay?.let { epochDayToPersian(it).display() } ?: "نامشخص") }
                            item { CompactInfoRow("خاتمه همکاری", employee.terminationEpochDay?.let { epochDayToPersian(it).display() } ?: "—") }
                            item { SectionHeading("خط زمانی", "نمای تاریخ‌دار از منابع اصلی نیروی انسانی؛ بدون کپی‌کردن داده مرجع") }
                            if (hrState.employeeTimeline.isEmpty()) {
                                item { EmptyStatePanel("رویداد تاریخی ثبت نشده است", "سمت و قرارداد جدید با تاریخ اثر در خط زمانی ظاهر می‌شوند.") }
                            }
                            items(hrState.employeeTimeline, key = { it.stableKey }) { event ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(event.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            StatusPill(event.eventType)
                                        }
                                        Text(
                                            "${epochDayToPersian(event.businessEpochDay).display()} · ${event.referenceType} #${event.referenceId}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                        EmployeeDetailSection.CONTRACTS -> {
                            item { Button(onClick = { onNewContract(null) }, modifier = Modifier.fillMaxWidth()) { Text("قرارداد جدید") } }
                            items(personnelState.contracts, key = { it.id }) { contract ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text("${contract.contractNumber.ifBlank { "قرارداد #${contract.id}" }} · نسخه ${contract.versionNo}", fontWeight = FontWeight.Bold)
                                        CompactInfoRow("بازه", "${epochDayToPersian(contract.startEpochDay).display()} تا ${contract.endEpochDay?.let { epochDayToPersian(it).display() } ?: "باز"}")
                                        CompactInfoRow("حقوق تصویر ثابت", formatMoney(contract.baseSalaryRial))
                                        StatusPill(contract.typedStatus.storedValue)
                                        if (contract.typedStatus == EmploymentContractStatus.PENDING_APPROVAL) OutlinedButton(onClick = { onApproveContract(contract.id) }, modifier = Modifier.fillMaxWidth()) { Text("تأیید قرارداد") }
                                        OutlinedButton(
                                            onClick = { onNewContract(contract) },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = contract.typedStatus !in setOf(
                                                EmploymentContractStatus.SUPERSEDED,
                                                EmploymentContractStatus.CANCELLED,
                                            ),
                                        ) { Text("نسخه اصلاحی") }
                                    }
                                }
                            }
                        }
                        EmployeeDetailSection.ATTENDANCE -> items(personnelState.attendance.filter { it.employeeId == employee.id }.take(30), key = { it.id }) { row ->
                            CompactInfoRow(epochDayToPersian(row.workEpochDay).display(), "${row.status} · ${row.workedMinutes} دقیقه")
                        }
                        EmployeeDetailSection.LEAVE -> items(personnelState.leaves.filter { it.employeeId == employee.id }, key = { it.id }) { leave ->
                            CompactInfoRow("${leave.typedLeaveType.storedValue} · ${leave.typedStatus.storedValue}", "${epochDayToPersian(leave.startEpochDay).display()} تا ${epochDayToPersian(leave.endEpochDay).display()}")
                        }
                        EmployeeDetailSection.PAYROLL -> {
                            if (hrState.employeePayslips.isEmpty()) item { EmptyStatePanel("تاریخچه حقوق موجود نیست", "هیچ مبلغی از حقوق فعلی برای گذشته بازسازی نمی‌شود.") }
                            items(hrState.employeePayslips, key = { it.id }) { payslip ->
                                PayslipHistoryRow(payslip, hrState.periods.firstOrNull { it.id == payslip.periodId }, onOpenPayslip)
                            }
                        }
                        EmployeeDetailSection.ADVANCES -> {
                            item { Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) { Text("ثبت/تسویه مساعده") } }
                            items(personnelState.advances, key = { it.id }) { advance ->
                                CompactInfoRow("مساعده #${advance.id} · ${advance.status}", "مانده ${formatMoney(advance.remainingAmountRial)}")
                            }
                        }
                        EmployeeDetailSection.PERFORMANCE -> {
                            items(performanceState.goals.filter { it.employeeId == employee.id }, key = { it.id }) { goal ->
                                CompactInfoRow(goal.title, goal.status)
                            }
                            items(performanceState.reviews.filter { it.employeeId == employee.id }, key = { "review-${it.id}" }) { review ->
                                CompactInfoRow("ارزیابی ${review.status}", "امتیاز ${review.finalScoreBasisPoints / 100.0}")
                            }
                        }
                        EmployeeDetailSection.DOCUMENTS -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("افزودن سند", fontWeight = FontWeight.Bold)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(HrDocumentType.entries, key = { it.storedValue }) { type ->
                                            FilterChip(
                                                selected = pendingDocumentType == type.storedValue,
                                                onClick = { pendingDocumentType = type.storedValue },
                                                label = { Text(type.persianLabel) },
                                            )
                                        }
                                    }
                                    Button(onClick = { documentLauncher.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("انتخاب فایل ${HrDocumentType.entries.first { it.storedValue == pendingDocumentType }.persianLabel}")
                                    }
                                }
                            }
                            if (personnelState.documents.isEmpty()) {
                                item { EmptyStatePanel("سندی ثبت نشده است", "فایل از Document Provider دستگاه انتخاب و URI پایدار آن با Audit ذخیره می‌شود.") }
                            }
                            items(personnelState.documents, key = { "doc-${it.id}" }) { document ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(document.displayName, fontWeight = FontWeight.Bold)
                                        Text("${document.documentType.persianLabel} · ${document.mimeType}", style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = {
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(document.contentUri)).apply {
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            type = document.mimeType
                                                        },
                                                    )
                                                } catch (error: ActivityNotFoundException) {
                                                    Toast.makeText(context, "برنامه‌ای برای نمایش این نوع سند نصب نیست.", Toast.LENGTH_LONG).show()
                                                }
                                            }) { Text("نمایش") }
                                            TextButton(onClick = { onArchiveDocument(document.id) }) { Text("بایگانی") }
                                        }
                                    }
                                }
                            }
                        }
                        EmployeeDetailSection.AUDIT -> {
                            if (personnelState.auditTimeline.isEmpty()) {
                                item { EmptyStatePanel("رویداد ممیزی ثبت نشده است", "خط زمانی ممیزی مستقیماً از دفتر ثبت تغییرناپذیر خوانده می‌شود.") }
                            }
                            items(personnelState.auditTimeline, key = { "audit-${it.id}" }) { audit ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(audit.action, fontWeight = FontWeight.Bold)
                                            StatusPill(audit.entityType)
                                        }
                                        Text("${audit.actor} · ${audit.businessEpochDay?.let { epochDayToPersian(it).display() } ?: "بدون روز کاری"}", style = MaterialTheme.typography.bodySmall)
                                        Text(audit.reason.ifBlank { "بدون توضیح" })
                                        audit.beforeSnapshot?.takeIf { it.isNotBlank() }?.let { Text("قبل: $it", style = MaterialTheme.typography.labelSmall) }
                                        audit.afterSnapshot?.takeIf { it.isNotBlank() }?.let { Text("بعد: $it", style = MaterialTheme.typography.labelSmall) }
                                        Text("شناسه هم‌بستگی: ${audit.correlationId}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("ویرایش پروفایل") } },
        dismissButton = {
            Row {
                if (employee.employmentStatus != EmploymentStatus.ARCHIVED) TextButton(onClick = onDeactivate) { Text("بایگانی") }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        },
    )
}

@Composable
internal fun PayslipDetailDialog(
    detail: PayrollPayslipDetailV2,
    onDismiss: () -> Unit,
    onPay: () -> Unit,
    onReverse: () -> Unit,
    onReversePayment: (Long) -> Unit,
) {
    val earnings = detail.components.filter { it.direction == PayrollComponentDirection.EARNING }
    val deductions = detail.components.filter { it.direction == PayrollComponentDirection.DEDUCTION }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیش ${detail.payslip.employeeNameSnapshot} · نسخه ${detail.payslip.revisionNo}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { StatusPill(detail.payslip.status.storedValue) }
                item { SectionHeading("Earnings", "اجزای مثبت با منبع") }
                if (earnings.isEmpty()) item { Text("جزئیات درآمد در منبع موجود نیست.") }
                items(earnings) { component -> CompactInfoRow(component.description, formatMoney(component.amountRial)) }
                item { SectionHeading("Deductions", "کسورات مثبت با Direction مجزا") }
                if (deductions.isEmpty()) item { Text("کسری ثبت نشده است.") }
                items(deductions) { component -> CompactInfoRow(component.description, formatMoney(component.amountRial)) }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            CompactInfoRow("ناخالص", formatMoney(detail.payslip.grossPay.value))
                            CompactInfoRow("کسورات", formatMoney(detail.payslip.totalDeductions.value))
                            CompactInfoRow("خالص", formatMoney(detail.payslip.netPay.value))
                            CompactInfoRow("پرداخت / مانده", "${formatMoney(detail.payslip.paidAmount.value)} / ${formatMoney(detail.payslip.remainingAmount.value)}")
                        }
                    }
                }
                item { SectionHeading("تصویر ثابت حضور و مرخصی", "ورودی‌های تأییدشده همان دوره") }
                detail.snapshot?.let { snapshot ->
                    item { CompactInfoRow("کارکرد / اضافه‌کاری", "${snapshot.actualWorkMinutes} / ${snapshot.overtimeMinutes} دقیقه") }
                    item { CompactInfoRow("غیبت / تأخیر", "${snapshot.absenceMinutes} / ${snapshot.lateMinutes} دقیقه") }
                    item { CompactInfoRow("مرخصی باحقوق / بی‌حقوق", "${snapshot.paidLeaveMinutes} / ${snapshot.unpaidLeaveMinutes} دقیقه") }
                    item { CompactInfoRow("قرارداد / نسخه", "${snapshot.contractId} / ${snapshot.contractVersionNo}") }
                    item { CompactInfoRow("حقوق پایه تصویر ثابت", formatMoney(snapshot.baseSalaryRial)) }
                    item { CompactInfoRow("Policy / نسخه", "${snapshot.payrollPolicyId} / ${snapshot.payrollPolicyVersion}") }
                } ?: item { Text("داده تاریخی: تصویر ثابت کامل در منبع قبلی وجود نداشته است.", color = MaterialTheme.colorScheme.error) }
                item { SectionHeading("Advance allocations", "کسرها از مانده مساعده بیشتر نمی‌شوند") }
                items(detail.advanceAllocations) { allocation -> CompactInfoRow("مساعده #${allocation.advanceId}", formatMoney(allocation.amountRial)) }
                item { SectionHeading("Payment ledger", "پرداخت کامل/جزئی و برگشت جبرانی") }
                items(detail.payments, key = { it.id }) { payment ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            CompactInfoRow("${payment.status.storedValue} · ${payment.paymentReference}", formatMoney(payment.amountRial))
                            CompactInfoRow("تاریخ / خزانه", "${epochDayToPersian(payment.paymentEpochDay).display()} / ${payment.treasuryAccountId}")
                            if (payment.status == PayrollPaymentStatus.POSTED && payment.reversalOfPaymentId == null) TextButton(onClick = { onReversePayment(payment.id) }) { Text("برگشت پرداخت") }
                        }
                    }
                }
                item { SectionHeading("Accounting", "تعهد و تسویه جدا") }
                item { CompactInfoRow("سند تعهد", detail.accrualJournalEntryId?.toString() ?: "—") }
                item { CompactInfoRow("سند برگشت", detail.reversalJournalEntryId?.toString() ?: "—") }
                item { SectionHeading("Approval & Audit", "actor، timestamp، reason و Correlation") }
                items(detail.approvalHistory, key = { it.id }) { approval ->
                    CompactInfoRow("${approval.eventType} · actor ${approval.actorId}", "${approval.fromStatus} → ${approval.toStatus}")
                }
            }
        },
        confirmButton = {
            if (detail.payslip.status in setOf(PayrollPayslipStatus.PAYMENT_PENDING, PayrollPayslipStatus.PARTIALLY_PAID)) {
                Button(onClick = onPay, modifier = Modifier.testTag("payroll_pay_open_${detail.payslip.id}")) { Text("پرداخت ${formatMoney(detail.payslip.remainingAmount.value)}") }
            }
        },
        dismissButton = {
            Row {
                if (detail.payslip.status in setOf(PayrollPayslipStatus.APPROVED, PayrollPayslipStatus.PAYMENT_PENDING, PayrollPayslipStatus.PARTIALLY_PAID, PayrollPayslipStatus.PAID)) {
                    TextButton(onClick = onReverse) { Text("برگشت فیش") }
                }
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        },
    )
}

@Composable
internal fun PayrollPeriodDialog(onDismiss: () -> Unit, onSave: (PayrollPeriodDraftV2) -> Unit) {
    val today = currentEpochDay()
    val persian = epochDayToPersian(today)
    var key by remember { mutableStateOf("PAY-${persian.year}-${persian.month.toString().padStart(2, '0')}") }
    var start by remember { mutableLongStateOf(PersianDate(persian.year, persian.month, 1).toEpochDay()) }
    var end by remember { mutableLongStateOf(PersianDate(persian.year, persian.month, daysInPersianMonth(persian.year, persian.month)).toEpochDay()) }
    var due by remember { mutableStateOf<Long?>(null) }
    val commandId = remember { GlobalId.new().value }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دوره حقوق") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            OutlinedTextField(key, { key = it.uppercase().take(40) }, label = { Text("شناسه یکتا دوره") }, modifier = Modifier.fillMaxWidth())
            PersianDateField("شروع", start) { start = it }
            PersianDateField("پایان", end) { end = it }
            OptionalPersianDateField("سررسید پرداخت", due, { due = it }, end)
        } },
        confirmButton = { Button(onClick = {
            runCatching { PayrollPeriodDraftV2(key, start, end, due, commandId).validated() }.onSuccess(onSave).onFailure { error = it.message }
        }) { Text("باز کردن دوره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun PayrollBatchDialog(periods: List<PayrollPeriodRecordV2>, onDismiss: () -> Unit, onSave: (PayrollBatchDraftV2) -> Unit) {
    val available = periods.filter { it.status in setOf(PayrollPeriodStatus.OPEN, PayrollPeriodStatus.REOPENED, PayrollPeriodStatus.CALCULATING) }
    var periodId by remember { mutableLongStateOf(available.firstOrNull()?.id ?: 0L) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دسته پرداخت حقوق") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            Text("هر Batch یک سند مستقل با Calculator، Reviewer، Approver و Correlation ID است.")
            available.forEach { period -> FilterChip(periodId == period.id, { periodId = period.id }, { Text(period.periodKey) }) }
            OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching { PayrollBatchDraftV2(periodId, notes = notes, commandId = commandId).validated() }
                .onSuccess(onSave)
                .onFailure { error = it.message }
        }, enabled = periodId > 0) { Text("ایجاد Batch") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun CalculateBatchDialog(
    batch: PayrollBatchRecordV2,
    state: PersonnelUiState,
    onDismiss: () -> Unit,
    onCalculate: (CalculatePayrollBatchCommand) -> Unit,
) {
    val employees = state.employees.filter { it.employmentStatus !in setOf(EmploymentStatus.APPLICANT, EmploymentStatus.ARCHIVED) }
    var advanceEmployeeId by remember { mutableLongStateOf(0L) }
    var advanceAmount by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محاسبه Atomic ${batch.documentNumber}") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            Text("${employees.size} کارمند بررسی می‌شوند. در صورت Contract/Attendance/Policy Exception هیچ فیشی ساخته نمی‌شود.")
            Text("کسر اختیاری مساعده", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(advanceEmployeeId == 0L, { advanceEmployeeId = 0 }, { Text("بدون کسر") }) }
                items(employees) { employee -> FilterChip(advanceEmployeeId == employee.id, { advanceEmployeeId = employee.id }, { Text(employee.displayName) }) }
            }
            OutlinedTextField(advanceAmount, { advanceAmount = formatMoneyInput(it) }, label = { Text("مبلغ کسر مساعده") }, enabled = advanceEmployeeId > 0, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            val amount = parseMoneyInputOrZero(advanceAmount)
            runCatching {
                CalculatePayrollBatchCommand(
                    batchId = batch.id,
                    employeeIds = employees.map { it.id },
                    advanceDeductions = if (advanceEmployeeId > 0 && amount > 0) listOf(PayrollAdvanceDeductionRequest(advanceEmployeeId, amount)) else emptyList(),
                    commandId = commandId,
                ).validated()
            }.onSuccess(onCalculate).onFailure { error = it.message }
        }, enabled = employees.isNotEmpty(), modifier = Modifier.testTag("payroll_calculate_submit")) { Text("محاسبه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun ManualAdjustmentDialog(
    state: PersonnelUiState,
    periods: List<PayrollPeriodRecordV2>,
    onDismiss: () -> Unit,
    onSave: (ManualPayrollAdjustmentCommand) -> Unit,
) {
    var employeeId by remember { mutableLongStateOf(state.employees.firstOrNull { it.isActive }?.id ?: 0L) }
    var periodId by remember { mutableLongStateOf(periods.firstOrNull { it.status != PayrollPeriodStatus.CLOSED }?.id ?: 0L) }
    var direction by remember { mutableStateOf(PayrollComponentDirection.EARNING) }
    var type by remember { mutableStateOf(PayrollComponentType.BONUS) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعدیل دستی حقوق") },
        text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            Text("ثبت دلیل و تأیید کاربر مستقل الزامی است.")
            state.employees.filter { it.isActive }.forEach { employee -> FilterChip(employeeId == employee.id, { employeeId = employee.id }, { Text(employee.displayName) }) }
            periods.filter { it.status != PayrollPeriodStatus.CLOSED }.forEach { period -> FilterChip(periodId == period.id, { periodId = period.id }, { Text(period.periodKey) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(direction == PayrollComponentDirection.EARNING, { direction = PayrollComponentDirection.EARNING; type = PayrollComponentType.BONUS }, { Text("درآمد") })
                FilterChip(direction == PayrollComponentDirection.DEDUCTION, { direction = PayrollComponentDirection.DEDUCTION; type = PayrollComponentType.OTHER_DEDUCTION }, { Text("کسری") })
            }
            val types = if (direction == PayrollComponentDirection.EARNING) listOf(PayrollComponentType.BONUS, PayrollComponentType.ALLOWANCE, PayrollComponentType.COMMISSION, PayrollComponentType.OTHER_EARNING) else listOf(PayrollComponentType.OTHER_DEDUCTION, PayrollComponentType.LOAN_DEDUCTION)
            types.forEach { value -> FilterChip(type == value, { type = value }, { Text(value.storedValue) }) }
            OutlinedTextField(amount, { amount = formatMoneyInput(it) }, label = { Text("مبلغ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching { ManualPayrollAdjustmentCommand(employeeId, periodId, type, direction, parseMoneyInputOrZero(amount), reason, commandId = commandId).validated() }
                .onSuccess(onSave)
                .onFailure { error = it.message }
        }) { Text("ارسال برای تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun ContractVersionDialog(
    state: PersonnelUiState,
    base: EmployeeContractRecord?,
    onDismiss: () -> Unit,
    onSave: (EmployeeContractDraft) -> Unit,
) {
    val employeeId = state.selectedEmployeeId ?: 0L
    val employee = state.employees.firstOrNull { it.id == employeeId }
    var type by remember { mutableStateOf(base?.contractType ?: "FIXED_TERM") }
    var start by remember { mutableLongStateOf(base?.startEpochDay ?: currentEpochDay()) }
    var end by remember { mutableStateOf(base?.endEpochDay) }
    var salary by remember { mutableStateOf(formatMoneyInputFromRial(base?.baseSalaryRial ?: employee?.monthlySalaryRial ?: 0L)) }
    var dailyMinutes by remember { mutableStateOf((base?.dailyWorkMinutes ?: 480).toString()) }
    var weeklyDays by remember { mutableStateOf((base?.weeklyWorkDays ?: 6).toString()) }
    var policyId by remember { mutableStateOf(base?.payrollPolicyId ?: state.payrollPolicies.firstOrNull()?.id) }
    var workScheduleId by remember { mutableStateOf(base?.workScheduleId ?: state.workSchedules.firstOrNull { it.active }?.id) }
    var defaultShiftTemplateId by remember { mutableStateOf(base?.defaultShiftTemplateId ?: state.shiftTemplates.firstOrNull { it.active }?.id) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (base == null) "قرارداد جدید" else "نسخه اصلاحی قرارداد") },
        text = { Column(Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            OutlinedTextField(type, { type = it.uppercase().take(40) }, label = { Text("نوع قرارداد") }, modifier = Modifier.fillMaxWidth())
            PersianDateField("شروع اثر", start) { start = it }
            OptionalPersianDateField("پایان", end, { end = it }, start)
            OutlinedTextField(salary, { salary = formatMoneyInput(it) }, label = { Text("حقوق پایه ریال") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dailyMinutes, { dailyMinutes = it.filter(Char::isDigit) }, label = { Text("دقیقه کار روزانه") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weeklyDays, { weeklyDays = it.filter(Char::isDigit) }, label = { Text("روز کار هفتگی") }, modifier = Modifier.fillMaxWidth())
            Text("برنامه کاری", fontWeight = FontWeight.Bold)
            if (state.workSchedules.isEmpty()) MessageCard("ابتدا از بخش شیفت و برنامه کاری، برنامه کاری معتبر بسازید.", true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.workSchedules.filter { it.active }, key = { it.id }) { schedule ->
                    FilterChip(workScheduleId == schedule.id, { workScheduleId = schedule.id }, { Text(schedule.name) })
                }
            }
            Text("شیفت پیش‌فرض / fallback", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.shiftTemplates.filter { it.active }, key = { it.id }) { shift ->
                    FilterChip(defaultShiftTemplateId == shift.id, { defaultShiftTemplateId = shift.id }, { Text("${shift.name} · ${shift.category.faLabel}") })
                }
            }
            Text("سیاست حقوق", fontWeight = FontWeight.Bold)
            state.payrollPolicies.forEach { policy -> FilterChip(policyId == policy.id, { policyId = policy.id }, { Text("${policy.title} · v${policy.versionNo}") }) }
            if (base != null) OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل اصلاح الزامی") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                EmployeeContractDraft(
                    employeeId = employeeId,
                    contractType = type,
                    startEpochDay = start,
                    endEpochDay = end,
                    baseSalaryRial = parseMoneyInputOrZero(salary),
                    dailyWorkMinutes = dailyMinutes.toInt(),
                    weeklyWorkDays = weeklyDays.toInt(),
                    payrollPolicyId = policyId,
                    workScheduleId = workScheduleId,
                    defaultShiftTemplateId = defaultShiftTemplateId,
                    jobTitleSnapshot = employee?.jobTitle.orEmpty(),
                    departmentSnapshot = employee?.department.orEmpty(),
                    branchSnapshot = employee?.branchName.orEmpty(),
                    correctionReason = reason,
                ).validated()
            }.onSuccess(onSave).onFailure { error = it.message }
        }) { Text("ذخیره نسخه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun AttendanceCorrectionDialog(
    row: AttendanceRecord,
    onDismiss: () -> Unit,
    onSave: (AttendanceDraft) -> Unit,
) {
    var status by remember { mutableStateOf(row.status) }
    var checkIn by remember { mutableStateOf(row.checkInMinute?.let(::formatMinuteOfDay).orEmpty()) }
    var checkOut by remember { mutableStateOf(row.checkOutMinute?.let(::formatMinuteOfDay).orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اصلاح حضور با Audit") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            Text("وضعیت اصلاح‌شده", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("PRESENT", "ABSENT", "LEAVE", "MISSION", "HOLIDAY", "OFF_DAY")) { value ->
                    val label = when (value) {
                        "PRESENT" -> "حاضر"
                        "ABSENT" -> "غایب"
                        "LEAVE" -> "مرخصی"
                        "MISSION" -> "ماموریت"
                        "HOLIDAY" -> "تعطیل"
                        else -> "روز استراحت"
                    }
                    FilterChip(status == value, { status = value }, { Text(label) })
                }
            }
            OutlinedTextField(checkIn, { checkIn = it.take(5) }, label = { Text("ورود HH:mm") }, enabled = status == "PRESENT")
            OutlinedTextField(checkOut, { checkOut = it.take(5) }, label = { Text("خروج HH:mm") }, enabled = status == "PRESENT")
            OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل اصلاح") })
            Text("قبل/بعد، actor، timestamp و Correlation ID ثبت می‌شوند.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                AttendanceDraft(
                    employeeId = row.employeeId,
                    workEpochDay = row.workEpochDay,
                    status = status,
                    checkInMinute = if (status == "PRESENT") parseClock(checkIn) else null,
                    checkOutMinute = if (status == "PRESENT") parseClock(checkOut) else null,
                    notes = "اصلاح دستی",
                    commandId = commandId,
                    correctionReason = reason,
                    requiresApproval = true,
                ).validated()
            }.onSuccess(onSave).onFailure { error = it.message }
        }) { Text("ثبت اصلاح") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun PayslipPaymentDialog(
    payslip: PayrollPayslipRecordV2,
    treasuryAccounts: List<TreasuryAccount>,
    onDismiss: () -> Unit,
    onPay: (PayPayslipCommand) -> Unit,
) {
    var amount by remember { mutableStateOf(formatMoneyInputFromRial(payslip.remainingAmount.value)) }
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var channel by remember { mutableStateOf(TreasuryChannel.BANK) }
    var accountId by remember { mutableStateOf(treasuryAccounts.firstOrNull { it.isActive && it.channel == TreasuryChannel.BANK }?.id?.value.orEmpty()) }
    var reference by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پرداخت کامل یا جزئی") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            Text("مانده قابل پرداخت: ${formatMoney(payslip.remainingAmount.value)}")
            OutlinedTextField(amount, { amount = formatMoneyInput(it) }, label = { Text("مبلغ پرداخت") }, modifier = Modifier.fillMaxWidth().testTag("payroll_payment_amount"))
            PersianDateField("تاریخ پرداخت", day) { day = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(channel == TreasuryChannel.BANK, {
                    channel = TreasuryChannel.BANK
                    accountId = treasuryAccounts.firstOrNull { it.isActive && it.channel == TreasuryChannel.BANK }?.id?.value.orEmpty()
                }, { Text("بانک") })
                FilterChip(channel == TreasuryChannel.CASH, {
                    channel = TreasuryChannel.CASH
                    accountId = treasuryAccounts.firstOrNull { it.isActive && it.channel == TreasuryChannel.CASH }?.id?.value.orEmpty()
                }, { Text("صندوق") })
            }
            Text("حساب خزانه", fontWeight = FontWeight.Bold)
            val compatibleAccounts = treasuryAccounts.filter { it.isActive && it.channel == channel }
            if (compatibleAccounts.isEmpty()) MessageCard("برای روش پرداخت انتخاب‌شده حساب فعال خزانه تعریف نشده است.", true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(compatibleAccounts, key = { it.id.value }) { account ->
                    FilterChip(accountId == account.id.value, { accountId = account.id.value }, { Text(account.name) })
                }
            }
            OutlinedTextField(reference, { reference = it.take(120) }, label = { Text("مرجع پرداخت") }, modifier = Modifier.fillMaxWidth().testTag("payroll_payment_reference"))
        } },
        confirmButton = { Button(onClick = {
            runCatching {
                PayPayslipCommand(
                    payslip.id,
                    parseMoneyInputOrZero(amount),
                    accountId,
                    channel,
                    day,
                    reference,
                    commandId,
                ).validated()
            }.onSuccess(onPay).onFailure { error = it.message }
        }, modifier = Modifier.testTag("payroll_payment_submit")) { Text("ثبت پرداخت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun PayslipReversalDialog(
    payslip: PayrollPayslipRecordV2,
    onDismiss: () -> Unit,
    onReverse: (ReversePayslipCommandV2) -> Unit,
) {
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت فیش نسخه ${payslip.revisionNo}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            MessageCard("اگر پرداخت POSTED وجود دارد ابتدا همان پرداخت را برگشت دهید. اصلاح حقوق با Revision جدید ثبت می‌شود.", true)
            PersianDateField("تاریخ کسب‌وکار برگشت", day) { day = it }
            OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل برگشت") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching { ReversePayslipCommandV2(payslip.id, day, reason, commandId).validated() }
                .onSuccess(onReverse)
                .onFailure { error = it.message }
        }) { Text("برگشت کنترل‌شده") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun PaymentReversalDialog(
    paymentId: Long,
    onDismiss: () -> Unit,
    onReverse: (ReversePayrollPaymentCommand) -> Unit,
) {
    var day by remember { mutableLongStateOf(currentEpochDay()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val commandId = remember { GlobalId.new().value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت پرداخت #$paymentId") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            PersianDateField("تاریخ برگشت", day) { day = it }
            OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("دلیل") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(onClick = {
            runCatching { ReversePayrollPaymentCommand(paymentId, day, reason, commandId).validated() }
                .onSuccess(onReverse)
                .onFailure { error = it.message }
        }) { Text("ثبت تراکنش جبرانی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun parseClock(value: String): Int {
    val parts = value.trim().split(':')
    require(parts.size == 2) { "قالب ساعت باید HH:mm باشد." }
    val hour = parts[0].toInt()
    val minute = parts[1].toInt()
    require(hour in 0..23 && minute in 0..59) { "ساعت معتبر نیست." }
    return hour * 60 + minute
}
