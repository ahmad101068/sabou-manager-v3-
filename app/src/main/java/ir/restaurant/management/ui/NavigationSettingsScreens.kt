package ir.restaurant.management.ui

import ir.restaurant.management.BuildConfig
import ir.restaurant.management.domain.search.GlobalSearchResult
import ir.restaurant.management.domain.search.GlobalSearchTarget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.data.AutomaticBackupFrequency
import ir.restaurant.management.data.BackupPolicy
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.operations.SyncSafetyGate

enum class ThemePreference(val title: String, val description: String) {
    SYSTEM("مطابق گوشی", "حالت روشن یا تیره را از تنظیمات دستگاه بگیر"),
    LIGHT("روشن", "پس‌زمینه روشن برای محیط‌های پرنور"),
    DARK("تیره", "نمای تیره برای استفاده شبانه"),
}

enum class FontScalePreference(val title: String, val multiplier: Float) {
    NORMAL("معمولی", 1f),
    LARGE("بزرگ", 1.15f),
    EXTRA_LARGE("خیلی بزرگ", 1.3f),
}

internal enum class SettingsSection(val title: String) {
    GENERAL("عمومی"),
    APPEARANCE("ظاهر"),
    OPERATIONS("عملیات"),
    PRINT("چاپ"),
    NOTIFICATIONS("اعلان‌ها"),
    DATA_BACKUP("داده و پشتیبان"),
    USERS_ACCESS("کاربران و دسترسی"),
    SECURITY_AUDIT("امنیت و حسابرسی"),
    ABOUT("درباره برنامه"),
}

internal data class NavigationDestination(
    val title: String,
    val subtitle: String,
    val keywords: String,
    val screen: AppScreen,
)

internal val navigationDestinations = listOf(
    NavigationDestination("عملیات", "مسیرهای پرتکرار روزانه", "عملیات روزانه", AppScreen.OPERATIONS_HUB),
    NavigationDestination("بیشتر", "همه بخش‌های مجاز", "ماژول بخش بیشتر", AppScreen.MORE),
    NavigationDestination("خرید و فاکتورها", "ثبت، جست‌وجو و تسویه خرید", "خرید فاکتور تامین کننده پرداخت", AppScreen.PURCHASES),
    NavigationDestination("ثبت خرید جدید", "ایجاد فاکتور خرید", "خرید جدید فاکتور", AppScreen.NEW_PURCHASE),
    NavigationDestination("تأمین‌کنندگان", "مدیریت فروشندگان و مانده‌ها", "تامین کننده فروشنده", AppScreen.SUPPLIERS),
    NavigationDestination("انبار", "کالا، موجودی و انبارگردانی", "انبار کالا موجودی شمارش", AppScreen.INVENTORY),
    NavigationDestination("دفتر گردش موجودی", "ورود، خروج، مصرف، ضایعات و اصلاح", "گردش موجودی ورود خروج مصرف ضایعات", AppScreen.STOCK_MOVEMENTS),
    NavigationDestination("ثبت فروش روزانه", "ثبت و کنترل تجمیعی فروش روزانه", "فروش روزانه ثبت عملکرد", AppScreen.SALES),
    NavigationDestination("تولید و رسپی", "محصول و مواد اولیه", "رسپی تولید محصول مواد اولیه", AppScreen.RECIPES),
    NavigationDestination("حسابداری", "اسناد، حساب‌ها و گزارش‌های مالی", "حسابداری سند حساب دفتر تراز سود", AppScreen.ACCOUNTING),
    NavigationDestination("خزانه‌داری", "دریافت، پرداخت، انتقال داخلی، تسویه و مغایرت", "خزانه صندوق بانک دریافت پرداخت تسویه مغایرت", AppScreen.TREASURY),
    NavigationDestination("طرف‌حساب‌ها و مطالبات", "دفتر مشتری، سن مطالبات، سقف اعتبار و ادغام کنترل‌شده", "مشتری حساب دریافتنی بدهکار aging اعتبار ادغام", AppScreen.CRM),
    NavigationDestination("ثبت سند جدید", "سند دستی حسابداری", "سند جدید حسابداری", AppScreen.NEW_JOURNAL),
    NavigationDestination("پرسنل و حقوق", "کارکنان و پرداخت حقوق", "پرسنل کارمند حقوق", AppScreen.PERSONNEL),
    NavigationDestination("دارایی‌های ثابت", "اموال و استهلاک", "دارایی اموال استهلاک", AppScreen.ASSETS),
    NavigationDestination("مرکز هشدارها", "هشدارهای عملیاتی و مالی", "هشدار سررسید موجودی", AppScreen.ALERTS),
    NavigationDestination("گزارش‌های مدیریتی", "شاخص‌های کلیدی کسب‌وکار", "گزارش فروش سود موجودی", AppScreen.REPORTS),
    NavigationDestination("رویدادهای سیستم", "امنیت و حسابرسی", "لاگ حسابرسی رویداد امنیت", AppScreen.AUDIT_LOG),
    NavigationDestination("کاربران و پشتیبان", "امنیت، کاربران و نسخه پشتیبان", "کاربر امنیت بکاپ پشتیبان", AppScreen.SECURITY),
    NavigationDestination("تنظیمات برنامه", "ظاهر و تجربه کاربری", "تنظیمات ظاهر تم", AppScreen.SETTINGS),
)

@Composable
internal fun GlobalSearchScreen(
    currentUser: ir.restaurant.management.domain.operations.AppUserRecord?,
    state: GlobalSearchUiState,
    onSearch: (String) -> Unit,
    onOpen: (AppScreen) -> Unit,
    onOpenEntity: (GlobalSearchResult) -> Unit,
    onBack: () -> Unit,
) {
    val normalized = state.query.trim()
    val allowed = navigationDestinations.filter { destination ->
        canOpenScreen(currentUser, destination.screen)
    }
    val destinations = if (normalized.isBlank()) allowed else allowed.filter {
        businessTextMatches(normalized, it.title, it.subtitle, it.keywords)
    }
    val entityResults = state.results
    var entityPage by remember(normalized, entityResults.size) { mutableStateOf(0) }
    val entityPageWindow = uiPageWindow(entityResults.size, entityPage, pageSize = 20)
    val visibleEntityResults = entityResults.page(entityPageWindow)

    Scaffold(topBar = { ProfessionalTopBar("جست‌وجوی سراسری", "جست‌وجوی مستقیم و مجاز در پایگاه داده", onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("نام، کد، فاکتور یا عملیات") },
                    placeholder = { Text("مثلاً برنج، ۱۲۳، تأمین‌کننده یا نام پرسنل") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
            }
            item {
                Text(
                    when {
                        state.loading -> "در حال جست‌وجوی پایگاه داده…"
                        state.query.length < 2 && state.query.isNotBlank() -> "برای جست‌وجوی داده‌ها حداقل دو نویسه وارد کنید"
                        state.query.isBlank() -> "همه میان‌برها"
                        else -> "${ErpDisplayFormatters.integer(destinations.size + entityResults.size)} نتیجه در بخش‌ها و داده‌ها"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.error?.let { error ->
                item { MessageCard(error, isError = true) }
            }
            if (!state.loading && destinations.isEmpty() && entityResults.isEmpty()) {
                item { EmptyStatePanel("نتیجه‌ای پیدا نشد", "عبارت کوتاه‌تری وارد کنید یا نام/کد را تغییر دهید.") }
            } else {
                if (entityResults.isNotEmpty()) {
                    val grouped = visibleEntityResults.groupBy { globalSearchGroupTitle(it.target) }
                    grouped.forEach { (group, groupResults) ->
                        item(key = "group-$group") { SectionHeading(group, "${ErpDisplayFormatters.integer(groupResults.size)} نتیجه در این صفحه") }
                        items(groupResults, key = { it.stableKey }) { result ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenEntity(result) },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .25f)),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(result.title, fontWeight = FontWeight.ExtraBold)
                                    Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (entityPageWindow.pageCount > 1) {
                        item(key = "global_search_pager") { UiPagingBar(entityPageWindow) { entityPage = it } }
                    }
                }
                if (destinations.isNotEmpty()) item { SectionHeading("میان‌برها", "بخش‌های مرتبط با عبارت جست‌وجو") }
                items(destinations, key = { "destination-${it.screen.name}" }) { destination ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(destination.screen) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.ArrowOutward,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp).size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(destination.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(destination.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

private fun globalSearchGroupTitle(target: GlobalSearchTarget): String = when (target) {
    is GlobalSearchTarget.InventoryItem -> "کالاها"
    is GlobalSearchTarget.StockMovement -> "گردش موجودی"
    is GlobalSearchTarget.Purchase -> "فاکتورهای خرید"
    is GlobalSearchTarget.Account -> "حساب‌ها"
    is GlobalSearchTarget.Journal -> "اسناد حسابداری"
    is GlobalSearchTarget.Employee -> "پرسنل"
    is GlobalSearchTarget.Customer -> "مشتریان"
}

@Composable
internal fun SettingsScreen(
    organizationName: String,
    onOrganizationNameChange: (String) -> Unit,
    themePreference: ThemePreference,
    currencyUnit: CurrencyUnit,
    fontScalePreference: FontScalePreference,
    backupPolicy: BackupPolicy,
    syncState: SyncUiState,
    onSaveSync: (String, String, Boolean, String, String) -> Unit,
    onRunSync: () -> Unit,
    onRequeueSync: () -> Unit,
    onResolveSyncIssue: (String, Boolean) -> Unit,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onCurrencyUnitChange: (CurrencyUnit) -> Unit,
    onFontScalePreferenceChange: (FontScalePreference) -> Unit,
    onBackupPolicyChange: (BackupPolicy) -> Unit,
    onFactoryReset: (String) -> Unit,
    sensitiveActionBusy: Boolean,
    sensitiveActionMessage: String?,
    onOpenScreen: (AppScreen) -> Unit,
    showSensitiveSettings: Boolean,
    canFactoryReset: Boolean,
    canAuditView: Boolean,
    onBack: () -> Unit,
) {
    var organizationNameDraft by remember(organizationName) { mutableStateOf(organizationName) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.GENERAL) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(syncState.message, sensitiveActionMessage) {
        (sensitiveActionMessage ?: syncState.message)?.let { snackbarHostState.showSnackbar(it, withDismissAction = true) }
    }
    Scaffold(
        topBar = { ProfessionalTopBar("تنظیمات", "مدیریت برنامه، دسترسی و حسابرسی", onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("settings_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SettingsSectionSelector(selectedSection) { selectedSection = it } }
            when (selectedSection) {
                SettingsSection.GENERAL -> {
                    item {
                        FormSection("نام مجموعه", "عنوان اصلی برنامه و چاپ") {
                            OutlinedTextField(
                                value = organizationNameDraft,
                                onValueChange = { organizationNameDraft = it.take(80) },
                                label = { Text("نام رستوران / مجموعه") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(onClick = { onOrganizationNameChange(organizationNameDraft.trim()) }, enabled = organizationNameDraft.trim().length in 2..80, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
                        }
                    }
                    item {
                        FormSection("واحد پول", "مبالغ در دیتابیس همچنان به ریال ذخیره می‌شوند") {
                            CurrencyUnit.entries.forEach { unit ->
                                OutlinedButton(onClick = { onCurrencyUnitChange(unit) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (currencyUnit == unit) "✓ ${currencyUnitLabel(unit)}" else currencyUnitLabel(unit))
                                }
                            }
                        }
                    }
                }
                SettingsSection.APPEARANCE -> {
                    item {
                        FormSection("ظاهر", "حالت نمایش و اندازه نوشته‌ها") {
                            ThemePreference.entries.forEach { preference ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { onThemePreferenceChange(preference) },
                                    colors = CardDefaults.cardColors(containerColor = if (themePreference == preference) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, if (themePreference == preference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(preference.title, fontWeight = FontWeight.Bold)
                                        Text(preference.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Text("اندازه نوشته‌ها", fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FontScalePreference.entries.forEach { preference ->
                                    OutlinedButton(onClick = { onFontScalePreferenceChange(preference) }, modifier = Modifier.weight(1f)) {
                                        Text(if (fontScalePreference == preference) "✓ ${preference.title}" else preference.title)
                                    }
                                }
                            }
                        }
                    }
                }
                SettingsSection.OPERATIONS -> {
                    item { FormSection("تنظیمات عملیاتی", "وضعیت تنظیمات عملیاتی هر بخش از همان ماژول قابل مشاهده و مدیریت است") {
                        SettingsLink("فروش و مالیات/سرویس", "ورود به تنظیمات و عملیات فروش", AppScreen.SALES, onOpenScreen)
                        SettingsLink("خرید و شماره‌گذاری", "گردش خرید و اسناد", AppScreen.PURCHASES, onOpenScreen)
                        SettingsLink("واحدها و دسته‌بندی انبار", "مرجع کالا و واحدها", AppScreen.INVENTORY, onOpenScreen)
                    } }
                }
                SettingsSection.PRINT -> {
                    item { FormSection("چاپ", "چاپ A4/حرارتی از مسیر گزارش و سند واقعی انجام می‌شود") {
                        SettingsLink("مرکز گزارش و چاپ", "پیش‌نمایش و چاپ اسناد", AppScreen.REPORTS, onOpenScreen)
                    } }
                }
                SettingsSection.NOTIFICATIONS -> {
                    item { FormSection("اعلان‌ها", "کمبود موجودی، سررسیدها و رویدادهای عملیاتی") {
                        SettingsLink("مرکز اعلان‌ها", "خوانده‌شده، اقدام‌شده و حل‌شده", AppScreen.ALERTS, onOpenScreen)
                    } }
                }
                SettingsSection.DATA_BACKUP -> {
                    if (showSensitiveSettings) {
                        item {
                            FormSection("پشتیبان‌گیری خودکار محلی", "زیرساخت موجود حفظ شده و فقط جایگاه آن در تنظیمات اصلاح شده است") {
                                AutomaticBackupFrequency.entries.forEach { frequency ->
                                    OutlinedButton(onClick = { onBackupPolicyChange(backupPolicy.copy(frequency = frequency)) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (backupPolicy.frequency == frequency) "✓ ${frequency.title}" else frequency.title)
                                    }
                                }
                                Text("حداکثر فایل‌ها: ${backupPolicy.maxFiles}", style = MaterialTheme.typography.bodySmall)
                                SettingsLink("مدیریت پشتیبان و بازیابی", "خروجی، ورود و بازیابی", AppScreen.SECURITY, onOpenScreen)
                            }
                        }
                        item { SyncHealthPanel(syncState) }
                        item { SyncConfigurationPanel(syncState, onSaveSync, onRunSync, onRequeueSync, onResolveSyncIssue) }
                    } else {
                        item { SettingsRestrictedPanel("دسترسی به داده و پشتیبان برای نقش فعلی مجاز نیست.") }
                    }
                }
                SettingsSection.USERS_ACCESS -> {
                    item { FormSection("کاربران و دسترسی", "کاربران، نقش‌ها، مجوزها و نشست") {
                        SettingsLink("مدیریت کاربران", "نقش، PIN و نشست کاربری", AppScreen.SECURITY, onOpenScreen)
                    } }
                }
                SettingsSection.SECURITY_AUDIT -> {
                    item { FormSection("امنیت و حسابرسی", "مسیر اصلی رویدادهای سیستم") {
                        if (canAuditView) {
                            SettingsLink("رویدادهای سیستم", "فیلتر، فارسی‌سازی و جزئیات Audit", AppScreen.AUDIT_LOG, onOpenScreen, "settings_security_audit")
                        } else {
                            Text("مجوز مشاهده رویدادهای سیستم برای این نقش فعال نیست.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingsLink("کاربران و نشست‌ها", "امنیت حساب و دسترسی", AppScreen.SECURITY, onOpenScreen)
                        if (canFactoryReset) {
                            OutlinedButton(onClick = { showResetConfirmation = true }, enabled = !sensitiveActionBusy, modifier = Modifier.fillMaxWidth()) {
                                Text("بازنشانی کامل اطلاعات", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } }
                }
                SettingsSection.ABOUT -> {
                    item { FormSection("درباره برنامه") {
                        SettingsInfoRow("نام نسخه", BuildConfig.VERSION_NAME)
                        SettingsInfoRow("کد نسخه", BuildConfig.VERSION_CODE.toString())
                        SettingsInfoRow("نسخه پایگاه داده", ir.restaurant.management.data.db.APP_DATABASE_SCHEMA_VERSION.toString())
                        Text("مدیریت رستوران", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    } }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (showResetConfirmation) {
        SensitiveActionConfirmationDialog(
            title = "بازنشانی کامل برنامه",
            description = "همه اطلاعات، کاربران، تنظیمات اتصال و فایل‌های پشتیبان محلی پاک می‌شوند. این عملیات قابل بازگشت نیست.",
            confirmLabel = "پاک‌کردن همه اطلاعات",
            busy = sensitiveActionBusy,
            message = sensitiveActionMessage,
            onDismiss = { showResetConfirmation = false },
            onConfirm = { pin -> showResetConfirmation = false; onFactoryReset(pin) },
        )
    }
}

@Composable
private fun SettingsSectionSelector(selected: SettingsSection, onSelected: (SettingsSection) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth().testTag("settings_sections"),
    ) {
        items(SettingsSection.entries, key = { it.name }) { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelected(section) },
                label = { Text(section.title, maxLines = 1) },
                modifier = Modifier.testTag("settings_section_${section.name}"),
            )
        }
    }
}

@Composable
private fun SettingsLink(
    title: String,
    subtitle: String,
    screen: AppScreen,
    onOpenScreen: (AppScreen) -> Unit,
    testTag: String = "settings_link_${screen.name}",
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag).clickable { onOpenScreen(screen) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("باز کردن", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsRestrictedPanel(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(message, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SyncConfigurationPanel(
    state: SyncUiState,
    onSave: (String, String, Boolean, String, String) -> Unit,
    onRun: () -> Unit,
    onRequeue: () -> Unit,
    onResolveIssue: (String, Boolean) -> Unit,
) {
    var endpoint by remember(state.config.endpoint) { mutableStateOf(state.config.endpoint) }
    var organizationId by remember(state.config.organizationId) { mutableStateOf(state.config.organizationId) }
    var accessToken by remember(state.config.accessToken) { mutableStateOf(state.config.accessToken) }
    var refreshToken by remember(state.config.refreshToken) { mutableStateOf(state.config.refreshToken) }
    FormSection("همگام‌سازی آزمایشی", "این قابلیت تا تکمیل دریافت و اعمال دادهٔ سرور برای استفاده عملیاتی مسدود است") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f))) {
            Text(
                SyncSafetyGate.blockedReason,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("نشانی سرویس") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(organizationId, { organizationId = it }, label = { Text("شناسه مجموعه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            accessToken,
            { accessToken = it },
            label = { Text("توکن دسترسی") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            refreshToken,
            { refreshToken = it },
            label = { Text("توکن نوسازی") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Text("شناسه دستگاه: ${state.config.deviceId.ifBlank { "پس از ذخیره ساخته می‌شود" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("فعال‌سازی همگام‌سازی")
            Switch(checked = false, onCheckedChange = null, enabled = false)
        }
        Button(onClick = { onSave(endpoint, organizationId, false, accessToken, refreshToken) }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره تنظیمات غیرفعال") }
        OutlinedButton(onClick = onRun, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.running) "در حال اجرا…" else "همگام‌سازی اکنون")
        }
        if (state.queue.deadLetters > 0) OutlinedButton(onClick = onRequeue, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
            Text("بازگردانی ${ErpDisplayFormatters.integer(state.queue.deadLetters)} پیام ناموفق")
        }
        state.issues.forEach { issue ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${issue.entityType} #${issue.entityId}", fontWeight = FontWeight.Bold)
                    Text("وضعیت: ${issue.state.name} · بازبینی ${issue.revision}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onResolveIssue(issue.changeId, true) }, modifier = Modifier.fillMaxWidth()) { Text("نگهداری نسخه محلی در صف") }
                }
            }
        }
    }
}

@Composable
private fun SyncHealthPanel(state: SyncUiState) {
    val summary = state.summary
    val (title, color) = when (summary.health) {
        ir.restaurant.management.domain.operations.SyncHealth.HEALTHY -> "همگام‌سازی سالم" to MaterialTheme.colorScheme.primary
        ir.restaurant.management.domain.operations.SyncHealth.PENDING_WORK -> "تغییر در انتظار همگام‌سازی" to MaterialTheme.colorScheme.secondary
        ir.restaurant.management.domain.operations.SyncHealth.NEEDS_ATTENTION -> "نیازمند بررسی تعارض" to MaterialTheme.colorScheme.error
    }
    FormSection("وضعیت همگام‌سازی", "پایش صف تغییرات آفلاین دستگاه") {
        StatusPill(title, containerColor = color.copy(alpha = .14f), contentColor = color)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactInfoRow("کل تغییرات", summary.total.toString())
            CompactInfoRow("در انتظار", summary.pending.toString())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactInfoRow("همگام‌شده", summary.synced.toString())
            CompactInfoRow("تعارض", summary.conflicts.toString())
        }
        if (state.queue.deadLetters > 0) CompactInfoRow("پیام‌های ناموفق", ErpDisplayFormatters.integer(state.queue.deadLetters), true)
    }
}
