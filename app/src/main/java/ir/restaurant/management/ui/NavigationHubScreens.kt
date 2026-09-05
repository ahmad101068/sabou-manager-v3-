package ir.restaurant.management.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.operations.AppUserRecord

private data class HubModule(val title: String, val subtitle: String, val screen: AppScreen)
private data class HubGroup(val title: String, val modules: List<HubModule>)

private val controlModules = listOf(
    HubModule("مسائل مدیریتی", "موارد بحرانی و نیازمند اقدام", AppScreen.MANAGEMENT_ISSUES),
    HubModule("وظایف", "پیگیری اقدام‌های مدیریتی", AppScreen.MANAGEMENT_TASKS),
    HubModule("چک‌لیست‌ها", "کنترل اجرای استانداردها", AppScreen.CHECKLISTS),
    HubModule("گزارش روزانه مدیریت", "نمای مدیریتی عملکرد روز", AppScreen.DAILY_BRIEF),
    HubModule("ناهنجاری‌ها", "هشدارها و انحراف‌های مهم", AppScreen.ALERTS),
)

private val operationsModules = listOf(
    HubModule("موجودی", "موجودی و گردش کالا", AppScreen.INVENTORY),
    HubModule("شمارش موجودی", "انبارگردانی و مغایرت", AppScreen.INVENTORY_COUNT),
    HubModule("انتقال", "انتقال کنترل‌شده بین محل‌ها", AppScreen.INVENTORY_TRANSFER),
    HubModule("ضایعات", "ثبت مقدار، بها، دلیل، شعبه و تاریخ", AppScreen.INVENTORY_WASTE),
    HubModule("خرید", "خرید و دریافت کالا", AppScreen.PURCHASES),
    HubModule("تأمین", "درخواست، سفارش، دریافت و مرجوعی", AppScreen.PURCHASES),
    HubModule("تأمین‌کنندگان", "تأمین، قیمت و مانده", AppScreen.SUPPLIERS),
    HubModule("رسپی و Costing", "رسپی و بهای مواد", AppScreen.RECIPES),
    HubModule("دارایی‌ها", "اموال و استهلاک", AppScreen.ASSETS),
    HubModule("پرسنل", "کارکنان و قراردادها", AppScreen.PERSONNEL),
    HubModule("حضور و غیاب", "ثبت و کنترل حضور", AppScreen.PERSONNEL),
)

private val financeModules = listOf(
    HubModule("فروش روزانه", "فروش، تسویه و ثبت نهایی", AppScreen.SALES),
    HubModule("مطالبات", "طرف‌حساب‌ها و دریافتنی‌ها", AppScreen.CRM),
    HubModule("وصول مطالبات", "ثبت وصول بدون ایجاد درآمد جدید", AppScreen.CRM),
    HubModule("حسابداری", "اسناد و دفاتر", AppScreen.ACCOUNTING),
    HubModule("خزانه", "صندوق، بانک و پرداخت", AppScreen.TREASURY),
    HubModule("مغایرت صندوق", "تطبیق و ثبت مغایرت نقد", AppScreen.TREASURY),
    HubModule("حقوق و دستمزد", "محاسبه، تأیید و پرداخت حقوق", AppScreen.PERSONNEL),
    HubModule("سود و زیان", "گزارش‌های مالی مدیریتی", AppScreen.REPORTS),
)

private val moreGroups = listOf(
    HubGroup(
        "سازمان",
        listOf(
            HubModule("شعب", "تعریف و مدیریت شعب فعال", AppScreen.BRANCHES),
            HubModule("سازمان", "هویت و اطلاعات سازمان", AppScreen.SETTINGS),
            HubModule("کاربران", "کاربران فعال و چرخه دسترسی", AppScreen.SECURITY),
            HubModule("مجوزها", "نقش‌ها و سطح دسترسی", AppScreen.SECURITY),
            HubModule("حسابرسی", "رویدادهای قابل ممیزی سیستم", AppScreen.AUDIT_LOG),
        ),
    ),
    HubGroup(
        "تنظیمات",
        listOf(
            HubModule("تنظیمات", "تنظیمات برنامه و سازمان", AppScreen.SETTINGS),
            HubModule("گزارش‌ها", "مرکز گزارش‌های مدیریتی", AppScreen.REPORTS),
        ),
    ),
)

@Composable
internal fun NavigationHubRoutes(screen: AppScreen, currentUser: AppUserRecord?, navigate: (AppScreen) -> Unit) {
    when (screen) {
        AppScreen.CONTROL_HUB -> GenericHubScreen("کنترل", "مرکز کنترل مدیریتی", "control_hub", AppScreen.CONTROL_HUB, controlModules, currentUser, navigate)
        AppScreen.OPERATIONS_HUB -> GenericHubScreen("عملیات", "عملیات روزانه و زنجیره تأمین", "operations_hub", AppScreen.OPERATIONS_HUB, operationsModules, currentUser, navigate)
        AppScreen.FINANCE_HUB -> GenericHubScreen("مالی", "فروش، مطالبات، خزانه و حسابداری", "finance_hub", AppScreen.FINANCE_HUB, financeModules, currentUser, navigate)
        AppScreen.MORE -> MoreHubScreen(currentUser, navigate)
        else -> error("hub_route_group_mismatch:${screen.name}")
    }
}

@Composable
private fun GenericHubScreen(
    title: String,
    subtitle: String,
    tag: String,
    selected: AppScreen,
    modules: List<HubModule>,
    currentUser: AppUserRecord?,
    navigate: (AppScreen) -> Unit,
) {
    val allowed = modules.filter { canOpenScreen(currentUser, it.screen) }
    val windowClass = currentErpWindowClass()
    Scaffold(
        topBar = { ProfessionalTopBar(title, subtitle, { navigate(AppScreen.DASHBOARD) }) },
        bottomBar = { MainTopLevelNavigation(selected, navigate) },
    ) { padding ->
        if (windowClass == ErpWindowClass.COMPACT) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag(tag),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(allowed, key = { it.title + it.screen.name }) { module -> HubModuleRow(module, navigate) }
                if (allowed.isEmpty()) item { HubEmptyState("برای نقش فعلی بخشی در دسترس نیست.") }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (windowClass == ErpWindowClass.EXPANDED) 3 else 2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).testTag(tag),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(allowed, key = { it.title + it.screen.name }) { module -> HubModuleRow(module, navigate) }
                if (allowed.isEmpty()) item { HubEmptyState("برای نقش فعلی بخشی در دسترس نیست.") }
            }
        }
    }
}

@Composable
private fun MoreHubScreen(currentUser: AppUserRecord?, navigate: (AppScreen) -> Unit) {
    val visibleGroups = moreGroups.mapNotNull { group ->
        val modules = group.modules.filter { canOpenScreen(currentUser, it.screen) }
        if (modules.isEmpty()) null else group.copy(modules = modules)
    }
    Scaffold(
        topBar = { ProfessionalTopBar("بیشتر", "سازمان، تنظیمات و دسترسی", { navigate(AppScreen.DASHBOARD) }) },
        bottomBar = { MainTopLevelNavigation(AppScreen.MORE, navigate) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("more_hub"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            visibleGroups.forEach { group ->
                item(key = "group-${group.title}") {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
                }
                items(group.modules, key = { "${group.title}-${it.screen.name}-${it.title}" }) { module -> HubModuleRow(module, navigate) }
            }
            if (visibleGroups.isEmpty()) item { HubEmptyState("بخشی برای نقش فعلی در دسترس نیست.") }
        }
    }
}

@Composable
private fun HubModuleRow(module: HubModule, navigate: (AppScreen) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("module_${module.screen.name}_${module.title}").clickable { navigate(module.screen) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(module.title, fontWeight = FontWeight.Bold)
                Text(module.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("باز کردن", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HubEmptyState(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(message, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun MainTopLevelNavigation(selected: AppScreen, navigate: (AppScreen) -> Unit) {
    if (currentErpWindowClass() != ErpWindowClass.COMPACT) return
    val selectedTopLevel = selected.topLevelDestination()
    NavigationBar(modifier = Modifier.testTag("main_bottom_navigation")) {
        val destinations = listOf(
            Triple(AppScreen.DASHBOARD, "خانه", Icons.Outlined.Home),
            Triple(AppScreen.CONTROL_HUB, "کنترل", Icons.Outlined.Assessment),
            Triple(AppScreen.OPERATIONS_HUB, "عملیات", Icons.Outlined.Inventory2),
            Triple(AppScreen.FINANCE_HUB, "مالی", Icons.Outlined.AccountBalance),
            Triple(AppScreen.MORE, "بیشتر", Icons.Outlined.MoreHoriz),
        )
        destinations.forEach { (destination, label, icon) ->
            NavigationBarItem(
                selected = selectedTopLevel == destination,
                onClick = { navigate(destination) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
            )
        }
    }
}
