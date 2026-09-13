@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.restaurant.management.ui

import ir.restaurant.management.domain.operations.AlertTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AlertCenterScreen(
    state: AlertUiState,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRefresh: () -> Unit,
    onRead: (Long) -> Unit,
    onActioned: (Long) -> Unit,
    onResolve: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onOpenSource: (AlertTarget) -> Unit,
    onClearDismissed: () -> Unit,
    onBack: () -> Unit,
) {
    val unreadCount = state.alerts.count { !it.isRead }
    val highCount = state.alerts.count { it.severity == "HIGH" }

    Scaffold(
        topBar = {
            ProfessionalTopBar(
                title = "مرکز هشدارها",
                subtitle = "پیگیری موجودی، سررسیدها و رویدادهای مهم کسب‌وکار",
                onBack = onBack,
                actionLabel = if (state.refreshing) null else "بررسی اکنون",
                onAction = onRefresh,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!notificationPermissionGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("اعلان‌های دستگاه خاموش‌اند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "برای دریافت یادآوری کمبود موجودی و سررسیدها، اجازه اعلان را فعال کنید. هشدارها حتی بدون این مجوز داخل برنامه قابل مشاهده‌اند.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                                Text("فعال‌کردن اعلان‌ها")
                            }
                        }
                    }
                }
            }
            if (state.refreshing) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("هشدار فعال", state.alerts.size.toString(), Modifier.weight(1f))
                        MetricTile("خوانده‌نشده", unreadCount.toString(), Modifier.weight(1f))
                    }
                    MetricTile("اولویت بالا", highCount.toString(), Modifier.fillMaxWidth())
                }
            }
            state.message?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onClearDismissed, modifier = Modifier.fillMaxWidth()) {
                    Text("پاک‌سازی هشدارهای کنارگذاشته‌شده")
                }
            }
            item { SectionHeading("هشدارهای جاری", "موارد مهم‌تر در بالای فهرست نمایش داده می‌شوند") }
            if (state.alerts.isEmpty()) {
                item { EmptyStatePanel("همه‌چیز تحت کنترل است", "در حال حاضر هشدار فعالی برای نمایش وجود ندارد.") }
            } else {
                items(state.alerts, key = { it.id }) { alert ->
                    val isHigh = alert.severity == "HIGH"
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHigh) MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    alert.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (alert.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                StatusPill(when {
                                    alert.status == "ACTIONED" -> "در حال اقدام"
                                    alert.status == "RESOLVED" -> "حل‌شده"
                                    isHigh -> "فوری"
                                    alert.isRead -> "خوانده‌شده"
                                    else -> "جدید"
                                })
                            }
                            Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            alert.dueEpochDay?.let { due ->
                                Text("سررسید: ${epochDayToPersian(due).display()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(onClick = { onRead(alert.id); onOpenSource(alert.target) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(when (alert.target) {
                                        is AlertTarget.InventoryItem -> "بازکردن کالا و گردش"
                                        is AlertTarget.InventoryLot -> "بازکردن لات موجودی"
                                        is AlertTarget.InventoryCount -> "بازکردن انبارگردانی"
                                        is AlertTarget.PurchaseOrder -> "بازکردن سفارش خرید"
                                        is AlertTarget.Purchase -> "بازکردن خرید و تسویه"
                                        is AlertTarget.Receivable -> "بازکردن مطالبه"
                                        is AlertTarget.EmploymentContract -> "بازکردن قرارداد پرسنل"
                                        is AlertTarget.Payroll -> "بازکردن فیش حقوق"
                                        is AlertTarget.AttendanceCorrection -> "بازکردن اصلاح حضور"
                                        is AlertTarget.Asset -> "بازکردن دارایی"
                                        is AlertTarget.SecurityEvent -> "بازکردن رویداد امنیتی"
                                        AlertTarget.None -> "مشاهده جزئیات"
                                    })
                                }
                                if (!alert.isRead) {
                                    Button(onClick = { onRead(alert.id) }, modifier = Modifier.fillMaxWidth()) { Text("علامت‌گذاری به‌عنوان خوانده‌شده") }
                                }
                                if (alert.status != "ACTIONED") {
                                    OutlinedButton(onClick = { onActioned(alert.id) }, modifier = Modifier.fillMaxWidth()) { Text("ثبت در حال اقدام") }
                                }
                                if (alert.status != "RESOLVED") {
                                    OutlinedButton(onClick = { onResolve(alert.id) }, modifier = Modifier.fillMaxWidth()) { Text("علامت‌گذاری حل‌شده") }
                                }
                                OutlinedButton(onClick = { onSnooze(alert.id) }, modifier = Modifier.fillMaxWidth()) { Text("تعویق ۲۴ ساعته") }
                                TextButton(onClick = { onDismiss(alert.id) }, modifier = Modifier.fillMaxWidth()) { Text("کنار گذاشتن هشدار") }
                            }
                        }
                    }
                }
            }
        }
    }
}
