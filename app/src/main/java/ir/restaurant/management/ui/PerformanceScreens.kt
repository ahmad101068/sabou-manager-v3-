package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.personnel.PerformanceGoalDraft
import ir.restaurant.management.domain.personnel.PerformanceGoalRecord
import ir.restaurant.management.domain.personnel.PerformanceReviewDraft
import ir.restaurant.management.domain.personnel.PerformanceScoreDraft

@Composable
internal fun PerformanceSection(
    state: PerformanceUiState,
    employees: List<EmployeeRecord>,
    onSaveGoal: (PerformanceGoalDraft) -> Unit,
    onDeactivateGoal: (Long) -> Unit,
    onSubmitReview: (PerformanceReviewDraft) -> Unit,
) {
    var showGoal by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.message?.let { MessageCard(it) }
        SectionHeading("عملکرد و اهداف", "هدف‌گذاری شفاف و ارزیابی وزنی تیم")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showGoal = true }, modifier = Modifier.weight(1f)) { Text("هدف جدید") }
            OutlinedButton(onClick = { if (state.goals.isNotEmpty()) showReview = true }, modifier = Modifier.weight(1f)) { Text("ارزیابی جدید") }
        }
        if (state.goals.isEmpty()) EmptyStatePanel("هدف عملکردی ثبت نشده", "برای هر عضو تیم هدف قابل‌اندازه‌گیری تعریف کنید.")
        state.goals.take(8).forEach { goal ->
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(goal.title, fontWeight = FontWeight.Bold); Text("${goal.weightPercent}%", fontWeight = FontWeight.ExtraBold) }
                    Text(goal.employeeName, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Text("${epochDayToPersian(goal.periodStartEpochDay).display()} تا ${epochDayToPersian(goal.periodEndEpochDay).display()}", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { onDeactivateGoal(goal.id) }) { Text("بستن هدف") }
                }
            }
        }
        if (state.reviews.isNotEmpty()) {
            SectionHeading("ارزیابی‌های ثبت‌شده", "آخرین امتیازهای نهایی")
            state.reviews.take(5).forEach { review ->
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(review.employeeName, fontWeight = FontWeight.Bold)
                        Text("امتیاز نهایی: ${review.finalScoreBasisPoints / 100.0} از ۱۰۰", fontWeight = FontWeight.ExtraBold)
                        Text("ارزیاب: ${review.reviewerName}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (showGoal) GoalDialog(employees, { showGoal = false }) { onSaveGoal(it); showGoal = false }
    if (showReview) ReviewDialog(state.goals, employees, { showReview = false }) { onSubmitReview(it); showReview = false }
}

@Composable
private fun GoalDialog(employees: List<EmployeeRecord>, onDismiss: () -> Unit, onSave: (PerformanceGoalDraft) -> Unit) {
    val active = employees.filter { it.isActive }
    var employeeId by remember { mutableStateOf(active.firstOrNull()?.id ?: 0L) }
    var title by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("100") }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var periodStart by remember { mutableLongStateOf(currentEpochDay()) }
    var periodEnd by remember { mutableLongStateOf(currentEpochDay() + 30) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("هدف عملکردی جدید") }, text = {
        LazyColumn(modifier = Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { error?.let { MessageCard(it, true) } }
            item { OutlinedTextField(active.firstOrNull { it.id == employeeId }?.name ?: "پرسنل انتخاب نشده", {}, label = { Text("پرسنل") }, readOnly = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(title, { title = it }, label = { Text("عنوان هدف") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(weight, { weight = it.filter(Char::isDigit) }, label = { Text("وزن درصدی") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(target, { target = it }, label = { Text("مقدار هدف اختیاری") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(unit, { unit = it }, label = { Text("واحد") }, modifier = Modifier.fillMaxWidth()) }
            item { PersianDateField("شروع دوره هدف", periodStart, { periodStart = it }) }
            item { PersianDateField("پایان دوره هدف", periodEnd, { periodEnd = it }) }
        }
    }, confirmButton = { Button(onClick = {
        runCatching { PerformanceGoalDraft(employeeId, title, weightPercent = weight.toIntOrNull() ?: 0, targetValueMicros = target.takeIf { it.isNotBlank() }?.let { parseQuantity(it).value }, unit = unit, periodStartEpochDay = periodStart, periodEndEpochDay = periodEnd).validated() }
            .onSuccess(onSave).onFailure { error = it.message ?: "هدف معتبر نیست." }
    }) { Text("ذخیره هدف") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun ReviewDialog(goals: List<PerformanceGoalRecord>, employees: List<EmployeeRecord>, onDismiss: () -> Unit, onSave: (PerformanceReviewDraft) -> Unit) {
    val employee = employees.firstOrNull { it.id == goals.firstOrNull()?.employeeId }
    var reviewer by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("100") }
    var periodStart by remember { mutableLongStateOf(currentEpochDay() - 30) }
    var periodEnd by remember { mutableLongStateOf(currentEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ارزیابی عملکرد") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { MessageCard(it, true) }
            Text(employee?.name ?: "پرسنل", fontWeight = FontWeight.Bold)
            Text("هدف‌های فعال: ${goals.size}")
            OutlinedTextField(reviewer, { reviewer = it }, label = { Text("نام ارزیاب") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(score, { score = it.filter(Char::isDigit) }, label = { Text("امتیاز کل (۰ تا ۱۰۰)") }, modifier = Modifier.fillMaxWidth())
            PersianDateField("شروع دوره ارزیابی", periodStart, { periodStart = it })
            PersianDateField("پایان دوره ارزیابی", periodEnd, { periodEnd = it })
        }
    }, confirmButton = { Button(onClick = {
        runCatching {
            val basis = ((score.toIntOrNull() ?: 0).coerceIn(0, 100)) * 100
            PerformanceReviewDraft(employee?.id ?: 0L, periodStart, periodEnd, reviewer, scores = goals.map { PerformanceScoreDraft(it.id, scoreBasisPoints = basis) }).validated()
        }.onSuccess(onSave).onFailure { error = it.message ?: "ارزیابی معتبر نیست." }
    }) { Text("ثبت ارزیابی") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}
