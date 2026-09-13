@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.restaurant.management.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole

private enum class SecuritySection { USERS, BACKUPS }
private sealed interface PortableBackupAction {
    data class Export(val backupName: String, val destination: android.net.Uri) : PortableBackupAction
    data class Drive(val destination: android.net.Uri) : PortableBackupAction
    data class Import(val source: android.net.Uri) : PortableBackupAction
}

@Composable
private fun BackupPasswordDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = !importing && confirmation.isNotEmpty() && confirmation != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (importing) "رمز فایل پشتیبان" else "رمز پشتیبان قابل‌انتقال") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (importing) "رمزی را وارد کنید که هنگام ساخت این فایل تعیین شده است."
                    else "حداقل ۱۰ نویسه انتخاب کنید. بدون این رمز، بازیابی روی دستگاه دیگر ممکن نیست.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("رمز پشتیبان") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!importing) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("تکرار رمز") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text("تکرار رمز یکسان نیست.") }) else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password.toCharArray()) },
                enabled = password.length >= 10 && (importing || password == confirmation),
            ) { Text(if (importing) "بازکردن فایل" else "ساخت فایل امن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun SecurityScreen(
    state: SecurityUiState,
    onSave: (Long?, UserDraft, () -> Unit) -> Unit,
    onDeactivate: (Long) -> Unit,
    onSwitch: (Long, String, () -> Unit) -> Unit,
    onSetRecoveryCode: (Long, String, () -> Unit) -> Unit,
    onRecoverPin: (Long, String, String, () -> Unit) -> Unit,
    onLogout: () -> Unit,
    onBackup: () -> Unit,
    onBackupToDrive: (android.net.Uri, CharArray) -> Unit,
    onExport: (String, android.net.Uri, CharArray) -> Unit,
    onImport: (android.net.Uri, CharArray) -> Unit,
    onRestore: (String, String) -> Unit,
    onDeleteBackup: (String) -> Unit,
    canManageUsers: Boolean,
    canBackup: Boolean,
    onBack: () -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    var switchId by remember { mutableStateOf<Long?>(null) }
    var recoverySetupId by remember { mutableStateOf<Long?>(null) }
    var recoveryResetId by remember { mutableStateOf<Long?>(null) }
    var exportName by remember { mutableStateOf<String?>(null) }
    var deleteBackupName by remember { mutableStateOf<String?>(null) }
    var restoreBackupName by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf(SecuritySection.USERS) }
    var portableAction by remember { mutableStateOf<PortableBackupAction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val canCreateUser = state.users.isEmpty() || canManageUsers
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it, withDismissAction = true) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val selected = exportName
        exportName = null
        if (uri != null && selected != null) portableAction = PortableBackupAction.Export(selected, uri)
    }
    val driveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) portableAction = PortableBackupAction.Drive(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) portableAction = PortableBackupAction.Import(uri) }

    portableAction?.let { action ->
        BackupPasswordDialog(
            importing = action is PortableBackupAction.Import,
            onDismiss = { portableAction = null },
            onConfirm = { password ->
                when (action) {
                    is PortableBackupAction.Export -> onExport(action.backupName, action.destination, password)
                    is PortableBackupAction.Drive -> onBackupToDrive(action.destination, password)
                    is PortableBackupAction.Import -> onImport(action.source, password)
                }
                portableAction = null
            },
        )
    }

    Scaffold(
        topBar = {
            ProfessionalTopBar(
                title = "امنیت و پشتیبان‌گیری",
                subtitle = "کاربران، سطح دسترسی و نسخه‌های امن اطلاعات",
                onBack = onBack,
                actionLabel = if (canCreateUser && selectedSection == SecuritySection.USERS) "کاربر جدید" else null,
                onAction = if (canCreateUser && selectedSection == SecuritySection.USERS) ({ showNew = true }) else null,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp).testTag("security_root"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .testTag(if (state.users.isEmpty()) "security_users_empty" else "security_users_loaded"),
                ) {
                    CurrentUserPanel(state, onLogout)
                }
            }
            item {
                SecuritySectionSelector(
                    selected = selectedSection,
                    canBackup = canBackup,
                    onSelected = { selectedSection = it },
                )
            }
            if (selectedSection == SecuritySection.USERS) {
                item { SectionHeading("کاربران", "ورود و دسترسی هر نقش را از این بخش مدیریت کنید.") }
                if (state.users.isEmpty()) {
                    item { EmptyStatePanel("کاربری ثبت نشده", "برای شروع، یک کاربر جدید بسازید.") }
                } else {
                    items(state.users, key = { it.id }) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("security_user_${user.id}"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                    Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                StatusPill(if (user.isActive) user.role.title else "غیرفعال")
                            }
                            Text(
                                roleDescription(user.role),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!user.hasRecoveryCode) {
                                Text(
                                    "کد بازیابی برای این کاربر تعیین نشده است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (user.isActive) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f).testTag("security_switch_${user.id}"),
                                        onClick = { switchId = user.id },
                                        enabled = !state.busy && state.currentUser?.id != user.id,
                                    ) { Text(if (state.currentUser?.id == user.id) "کاربر فعال" else "ورود با این کاربر") }
                                }
                                if (canManageUsers && user.role != UserRole.OWNER && user.isActive) {
                                    TextButton(
                                        onClick = { onDeactivate(user.id) },
                                        enabled = !state.busy,
                                    ) { Text("غیرفعال‌کردن", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                            if (canManageUsers && user.isActive) {
                                TextButton(
                                    onClick = { recoverySetupId = user.id },
                                    enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (user.hasRecoveryCode) "تغییر کد بازیابی" else "تنظیم کد بازیابی") }
                            }
                        }
                    }
                    }
                }
            }
            if (canBackup && selectedSection == SecuritySection.BACKUPS) {
                item { SectionHeading("پشتیبان‌گیری محلی و صدور امن", "نسخه رمزگذاری‌شده بسازید و یک کپی را خارج از این گوشی نگه دارید.") }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f))) {
                        Text(
                            "پشتیبان‌های فهرست‌شده داخل همین گوشی هستند و در خرابی یا بازنشانی دستگاه ممکن است همراه اطلاعات اصلی از بین بروند.",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                item {
                    Button(
                        onClick = onBackup,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.busy) "در حال انجام…" else "ساخت نسخه پشتیبان رمزگذاری‌شده") }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            driveBackupLauncher.launch("restaurant-manager-backup-${System.currentTimeMillis()}.restaurant-management")
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ساخت و صدور به Drive یا فایل‌ها") }
                }
                item {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("واردکردن فایل از Drive یا دستگاه") }
                }
                if (state.backups.isEmpty()) {
                    item { EmptyStatePanel("نسخه پشتیبان ندارید", "پس از ساخت، فایل‌های پشتیبان در این قسمت نمایش داده می‌شوند.") }
                } else {
                    val latestBackup = state.backups.maxByOrNull { it.modifiedAtEpochMillis }
                    latestBackup?.let { latest ->
                        item {
                            ErpDashboardHero(
                                eyebrow = "آخرین پشتیبان",
                                value = ErpDisplayFormatters.timestampDateTime(latest.modifiedAtEpochMillis),
                                caption = if (latest.integrityVerified) "فایل بررسی شده و سالم است" else "سلامت فایل نیازمند بررسی است",
                                metrics = listOf(
                                    ErpKpiItem("اندازه", ErpDisplayFormatters.fileSize(latest.sizeBytes)),
                                    ErpKpiItem("نسخه‌ها", ErpDisplayFormatters.integer(state.backups.size)),
                                ),
                            )
                        }
                    }
                    items(state.backups, key = { it.name }) { backup ->
                        val name = backup.name
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPill(
                                        text = if (backup.integrityVerified) "سالم" else "نیازمند بررسی",
                                        containerColor = if (backup.integrityVerified) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                                        contentColor = if (backup.integrityVerified) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(ErpDisplayFormatters.fileSize(backup.sizeBytes), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "تاریخ: ${ErpDisplayFormatters.timestampDateTime(backup.modifiedAtEpochMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { restoreBackupName = name },
                                    enabled = !state.busy && backup.integrityVerified,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (backup.integrityVerified) "بازیابی این نسخه" else "بازیابی غیرفعال؛ فایل معتبر نیست") }
                                TextButton(
                                    onClick = {
                                        exportName = name
                                        exportLauncher.launch(name.removeSuffix(".db") + ".restaurant-management")
                                    },
                                    enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("ذخیره یک کپی در دستگاه") }
                                TextButton(
                                    onClick = { deleteBackupName = name },
                                    enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("حذف این نسخه", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        UserEditorDialog(
            busy = state.busy,
            bootstrapOwner = state.users.isEmpty(),
            onDismiss = { showNew = false },
            onSave = { draft -> onSave(null, draft) { showNew = false } },
        )
    }
    switchId?.let { id ->
        PinDialog(
            busy = state.busy,
            recoveryAvailable = state.users.firstOrNull { it.id == id }?.hasRecoveryCode == true,
            onDismiss = { switchId = null },
            onConfirm = { pin -> onSwitch(id, pin) { switchId = null } },
            onForgot = {
                switchId = null
                recoveryResetId = id
            },
        )
    }
    recoverySetupId?.let { id ->
        RecoveryCodeSetupDialog(
            busy = state.busy,
            onDismiss = { recoverySetupId = null },
            onConfirm = { code -> onSetRecoveryCode(id, code) { recoverySetupId = null } },
        )
    }
    recoveryResetId?.let { id ->
        ForgotPinDialog(
            busy = state.busy,
            onDismiss = { recoveryResetId = null },
            onConfirm = { code, newPin -> onRecoverPin(id, code, newPin) { recoveryResetId = null } },
        )
    }
    deleteBackupName?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteBackupName = null },
            title = { Text("حذف نسخه پشتیبان") },
            text = { Text("فایل «$name» برای همیشه از حافظه داخلی برنامه حذف شود؟") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteBackupName = null
                        onDeleteBackup(name)
                    },
                    enabled = !state.busy,
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteBackupName = null }) { Text("انصراف") }
            },
        )
    }
    restoreBackupName?.let { name ->
        SensitiveActionConfirmationDialog(
            title = "بازیابی نسخه پشتیبان",
            description = "پایگاه داده فعلی در اجرای بعدی با «$name» جایگزین می‌شود. رمز کاربر جاری برای تأیید مجدد الزامی است.",
            confirmLabel = "تأیید و زمان‌بندی بازیابی",
            busy = state.busy,
            message = state.message,
            onDismiss = { restoreBackupName = null },
            onConfirm = { pin ->
                restoreBackupName = null
                onRestore(name, pin)
            },
        )
    }
}

@Composable
private fun SecuritySectionSelector(
    selected: SecuritySection,
    canBackup: Boolean,
    onSelected: (SecuritySection) -> Unit,
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == SecuritySection.USERS,
                onClick = { onSelected(SecuritySection.USERS) },
                label = { Text("کاربران") },
                modifier = Modifier.weight(1f),
            )
            if (canBackup) {
                FilterChip(
                    selected = selected == SecuritySection.BACKUPS,
                    onClick = { onSelected(SecuritySection.BACKUPS) },
                    label = { Text("پشتیبان‌ها") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CurrentUserPanel(state: SecurityUiState, onLogout: () -> Unit) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("نشست فعال", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                state.currentUser?.displayName ?: "کاربر مشخص نیست",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                state.currentUser?.let { "${it.role.title} · @${it.username}" } ?: "برای ادامه یک کاربر را انتخاب کنید.",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f),
            )
            if (state.currentUser != null) {
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !state.busy,
                    modifier = Modifier.testTag("security_logout"),
                ) {
                    Text("قفل‌کردن و خروج از نشست")
                }
            }
        }
    }
}

@Composable
private fun UserEditorDialog(
    busy: Boolean,
    bootstrapOwner: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserDraft) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }
    var role by remember(bootstrapOwner) { mutableStateOf(if (bootstrapOwner) UserRole.OWNER else UserRole.CASHIER) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساخت کاربر جدید") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { MessageCard(it, isError = true) }
                OutlinedTextField(username, { username = it.trimStart() }, label = { Text("نام کاربری") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("نام نمایشی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("رمز عددی ۶ تا ۱۲ رقم") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = { recoveryCode = it.filter(Char::isDigit).take(16) },
                    label = { Text(if (bootstrapOwner) "کد بازیابی ۸ تا ۱۶ رقم" else "کد بازیابی (اختیاری)") },
                    supportingText = { Text("این کد را جدا از رمز ورود و در محل امن نگه دارید.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (bootstrapOwner) {
                    Text("اولین کاربر با نقش مالک ساخته می‌شود.", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("نقش: ${role.title}") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        UserRole.entries.filterNot { it == UserRole.RESTRICTED }.forEach { item ->
                            DropdownMenuItem(text = { Text(item.title) }, onClick = { role = item; expanded = false })
                        }
                    }
                }
                Text(roleDescription(role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val normalizedUsername = username.trim()
                    val normalizedName = name.trim()
                    when {
                        normalizedUsername.length < 3 -> error = "نام کاربری باید حداقل ۳ نویسه باشد."
                        normalizedName.length < 2 -> error = "نام نمایشی معتبر نیست."
                        pin.length !in 6..12 -> error = "رمز عددی باید بین ۶ تا ۱۲ رقم باشد."
                        bootstrapOwner && recoveryCode.length !in 8..16 -> error = "برای مالک، کد بازیابی ۸ تا ۱۶ رقمی الزامی است."
                        recoveryCode.isNotBlank() && recoveryCode.length !in 8..16 -> error = "کد بازیابی باید بین ۸ تا ۱۶ رقم باشد."
                        recoveryCode.isNotBlank() && recoveryCode == pin -> error = "کد بازیابی نباید با رمز ورود یکسان باشد."
                        else -> onSave(UserDraft(normalizedUsername, normalizedName, pin, role, recoveryCode))
                    }
                },
            ) { Text("ذخیره کاربر") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PinDialog(
    busy: Boolean,
    recoveryAvailable: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onForgot: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ورود با کاربر انتخاب‌شده") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = { Text("رمز عددی") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("security_login_pin"),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = !busy && pin.isNotBlank(),
                modifier = Modifier.testTag("security_login_confirm"),
            ) { Text("ورود") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onForgot, enabled = !busy && recoveryAvailable) {
                    Text(if (recoveryAvailable) "رمز را فراموش کرده‌ام" else "کد بازیابی تنظیم نشده")
                }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
}

@Composable
private fun RecoveryCodeSetupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم کد بازیابی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("کد بازیابی فقط برای تعیین رمز جدید استفاده می‌شود. آن را خارج از گوشی و در محل امن نگه دارید.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(16) },
                    label = { Text("کد بازیابی ۸ تا ۱۶ رقم") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(code) }, enabled = !busy && code.length in 8..16) { Text("ذخیره کد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ForgotPinDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var recoveryCode by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeatedPin by remember { mutableStateOf("") }
    val valid = recoveryCode.length in 8..16 && newPin.length in 6..12 && newPin == repeatedPin && recoveryCode != newPin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازیابی رمز ورود") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = { recoveryCode = it.filter(Char::isDigit).take(16) },
                    label = { Text("کد بازیابی") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(12) },
                    label = { Text("رمز جدید ۶ تا ۱۲ رقم") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repeatedPin,
                    onValueChange = { repeatedPin = it.filter(Char::isDigit).take(12) },
                    label = { Text("تکرار رمز جدید") },
                    isError = repeatedPin.isNotEmpty() && repeatedPin != newPin,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(recoveryCode, newPin) }, enabled = !busy && valid) { Text("تغییر رمز و ورود") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun roleDescription(role: UserRole): String = when (role) {
    UserRole.OWNER -> "دسترسی کامل به تمام بخش‌ها و تنظیمات برنامه"
    UserRole.MANAGER -> "مدیریت عملیات، گزارش‌ها، حسابداری و منابع انسانی"
    UserRole.CASHIER -> "ثبت فروش و مشاهده گزارش‌های مرتبط"
    UserRole.INVENTORY -> "مشاهده و مدیریت موجودی انبار"
    UserRole.STOREKEEPER -> "مدیریت خرید، تأمین‌کننده، انبار و رسپی‌ها"
    UserRole.ACCOUNTANT -> "حسابداری، گزارش‌ها، اسناد، پرسنل و دارایی‌ها"
    UserRole.RESTRICTED -> "نقش محدود یا ناشناخته؛ هیچ دسترسی عملیاتی ندارد"
}
