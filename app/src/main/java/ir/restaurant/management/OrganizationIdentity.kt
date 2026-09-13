package ir.restaurant.management

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_ORGANIZATION_TITLE = "مدیریت رستوران"
private const val UI_PREFERENCES = "restaurant_management_ui"
private const val ORGANIZATION_NAME_KEY = "organization_name"

class OrganizationSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE)
    private val _organizationName = MutableStateFlow(preferences.getString(ORGANIZATION_NAME_KEY, "").orEmpty().trim())
    val organizationName: StateFlow<String> = _organizationName.asStateFlow()

    fun updateOrganizationName(value: String) {
        val normalized = value.trim()
        require(normalized.length in 2..80) { "نام رستوران / مجموعه باید بین ۲ تا ۸۰ کاراکتر باشد." }
        check(preferences.edit().putString(ORGANIZATION_NAME_KEY, normalized).commit()) { "ذخیره نام مجموعه انجام نشد." }
        _organizationName.value = normalized
    }

    fun reset() {
        check(preferences.edit().remove(ORGANIZATION_NAME_KEY).commit()) { "بازنشانی نام مجموعه انجام نشد." }
        _organizationName.value = ""
    }

    fun displayName(): String = organizationDisplayTitle(_organizationName.value)
}

fun organizationDisplayTitle(value: String): String = value.trim().ifBlank { DEFAULT_ORGANIZATION_TITLE }

/** Synchronous projection for print/notification code; persistence remains owned by OrganizationSettingsStore. */
fun Context.organizationDisplayName(): String = OrganizationSettingsStore(this).displayName()
