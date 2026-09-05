package ir.restaurant.management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.ui.RestaurantManagementApp
import ir.restaurant.management.ui.ManagementSplash
import ir.restaurant.management.ui.ThemePreference
import ir.restaurant.management.ui.CurrencyUnit
import ir.restaurant.management.ui.FontScalePreference
import ir.restaurant.management.ui.MoneyDisplayPreferences
import ir.restaurant.management.ui.theme.RestaurantManagementTheme
import kotlinx.coroutines.delay
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.os.SystemClock
import ir.restaurant.management.core.SafeErrorLog
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ir.restaurant.management.data.AutomaticBackupScheduler
import ir.restaurant.management.data.AutomaticBackupFrequency
import ir.restaurant.management.data.BackupPolicy
import ir.restaurant.management.data.BackupPolicyStore
import ir.restaurant.management.data.security.DatabaseKeyUnavailableException

class MainActivity : ComponentActivity() {
    private var notificationPermissionGranted by mutableStateOf(Build.VERSION.SDK_INT < 33)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val container = (application as RestaurantManagementApplication).container
        val preferences = getSharedPreferences("restaurant_manager_ui", MODE_PRIVATE)
        val backupPolicyStore = BackupPolicyStore(this)
        var selectedTheme by mutableStateOf(
            preferences.getString("theme", null)
                ?.let { stored -> runCatching { ThemePreference.valueOf(stored) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
        )
        var selectedCurrency by mutableStateOf(
            preferences.getString("currency_unit", null)
                ?.let { stored -> runCatching { CurrencyUnit.valueOf(stored) }.getOrNull() }
                ?: CurrencyUnit.RIAL,
        )
        var selectedFontScale by mutableStateOf(
            preferences.getString("font_scale", null)
                ?.let { stored -> runCatching { FontScalePreference.valueOf(stored) }.getOrNull() }
                ?: FontScalePreference.NORMAL,
        )
        MoneyDisplayPreferences.unit = selectedCurrency
        var backupPolicy by mutableStateOf(backupPolicyStore.load())
        setContent {
            val organizationName by container.organizationSettings.organizationName.collectAsStateWithLifecycle()
            val dark = when (selectedTheme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            RestaurantManagementTheme(darkTheme = dark, fontScaleMultiplier = selectedFontScale.multiplier) {
                var startupResult by remember(container) { mutableStateOf<Result<Unit>?>(null) }
                LaunchedEffect(container) {
                    val startedAt = SystemClock.elapsedRealtime()
                    val result = withContext(Dispatchers.IO) {
                        runCatching { container.initialize() }
                    }
                    val remainingSplashTime = 1_250L - (SystemClock.elapsedRealtime() - startedAt)
                    if (remainingSplashTime > 0L) delay(remainingSplashTime)
                    startupResult = result
                }
                when {
                    startupResult == null -> ManagementSplash(visible = true, organizationName = organizationName)
                    startupResult?.isFailure == true -> StartupFailure(
                        checkNotNull(startupResult?.exceptionOrNull()),
                    )
                    else -> RestaurantManagementApp(
                            container = container,
                            organizationName = organizationName,
                            onOrganizationNameChange = container.organizationSettings::updateOrganizationName,
                            themePreference = selectedTheme,
                            currencyUnit = selectedCurrency,
                            fontScalePreference = selectedFontScale,
                            backupPolicy = backupPolicy,
                            notificationPermissionGranted = notificationPermissionGranted,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onThemePreferenceChange = { value ->
                                selectedTheme = value
                                preferences.edit().putString("theme", value.name).apply()
                            },
                            onCurrencyUnitChange = { value ->
                                selectedCurrency = value
                                MoneyDisplayPreferences.unit = value
                                preferences.edit().putString("currency_unit", value.name).apply()
                            },
                            onFontScalePreferenceChange = { value ->
                                selectedFontScale = value
                                preferences.edit().putString("font_scale", value.name).apply()
                            },
                            onBackupPolicyChange = { value ->
                                backupPolicyStore.save(value)
                                backupPolicy = value.validated()
                                AutomaticBackupScheduler.apply(this, backupPolicy)
                            },
                            onAppPreferencesReset = {
                                preferences.edit().clear().commit()
                                container.organizationSettings.reset()
                                selectedTheme = ThemePreference.SYSTEM
                                selectedCurrency = CurrencyUnit.RIAL
                                MoneyDisplayPreferences.unit = CurrencyUnit.RIAL
                                selectedFontScale = FontScalePreference.NORMAL
                                backupPolicy = BackupPolicy(AutomaticBackupFrequency.OFF, 50)
                                backupPolicyStore.save(backupPolicy)
                                AutomaticBackupScheduler.apply(this, backupPolicy)
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun StartupFailure(error: Throwable) {
    val reference = remember(error) {
        SafeErrorLog.record("ApplicationStartup", "database_initialization_failed", error)
        "%08X".format(System.identityHashCode(error))
    }
    val safeMessage = remember(error) {
        if (generateSequence(error) { it.cause }.any { it is DatabaseKeyUnavailableException }) {
            "کلید امن پایگاه داده در دسترس نیست. فایل پشتیبان قابل‌انتقال را آماده کنید و با پشتیبانی تماس بگیرید."
        } else {
            "اعتبارسنجی یا ارتقای پایگاه داده کامل نشد. برای محافظت از اطلاعات، برنامه دیتابیس را باز نکرد."
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "راه‌اندازی برنامه انجام نشد",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text("برنامه برای جلوگیری از آسیب به اطلاعات متوقف شد. از این پیام عکس بگیرید و کد پیگیری را برای پشتیبانی ارسال کنید.")
            SelectionContainer {
                Text(
                    "$safeMessage\nکد پیگیری: $reference",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
