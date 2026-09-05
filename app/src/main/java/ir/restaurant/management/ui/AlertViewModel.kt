package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.operations.AppAlert
import ir.restaurant.management.domain.operations.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertUiState(
    val alerts: List<AppAlert> = emptyList(),
    val message: String? = null,
    val refreshing: Boolean = false,
)

class AlertViewModel(private val repository: AlertRepository) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    val state: StateFlow<AlertUiState> = combine(repository.alerts(), message, refreshing) { alerts, text, busy ->
        AlertUiState(alerts, text, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertUiState())


    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        message.value = runCatching {
            repository.refresh(currentEpochDay())
            "هشدارها به‌روزرسانی شدند"
        }.getOrElse { it.message ?: "خطا در به‌روزرسانی هشدارها" }
        refreshing.value = false
    }

    fun markRead(id: Long) = viewModelScope.launch { repository.markRead(id) }
    fun markActioned(id: Long) = viewModelScope.launch { repository.markActioned(id) }
    fun resolve(id: Long) = viewModelScope.launch { repository.resolve(id) }
    fun clearMessage() { message.value = null }
    fun dismiss(id: Long) = viewModelScope.launch { repository.dismiss(id) }
    fun snoozeOneDay(id: Long) = viewModelScope.launch {
        repository.snooze(id, System.currentTimeMillis() + 24L * 60L * 60L * 1_000L)
        message.value = "هشدار برای ۲۴ ساعت به تعویق افتاد"
    }
    fun clearDismissed() = viewModelScope.launch {
        repository.clearDismissed()
        message.value = "هشدارهای کنارگذاشته‌شده پاک شدند"
    }

    companion object {
        fun factory(repository: AlertRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AlertViewModel(repository) as T
        }
    }
}
