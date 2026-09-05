package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.personnel.PerformanceGoalDraft
import ir.restaurant.management.domain.personnel.PerformanceGoalRecord
import ir.restaurant.management.domain.personnel.PerformanceRepository
import ir.restaurant.management.domain.personnel.PerformanceReviewDraft
import ir.restaurant.management.domain.personnel.PerformanceReviewRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PerformanceUiState(
    val goals: List<PerformanceGoalRecord> = emptyList(),
    val reviews: List<PerformanceReviewRecord> = emptyList(),
    val message: String? = null,
)

class PerformanceViewModel(private val repository: PerformanceRepository) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val state: StateFlow<PerformanceUiState> = combine(repository.goals, repository.reviews, message) { goals, reviews, text -> PerformanceUiState(goals, reviews, text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerformanceUiState())

    fun saveGoal(id: Long?, draft: PerformanceGoalDraft) = run("هدف عملکرد ذخیره شد.") { repository.saveGoal(id, draft) }
    fun deactivateGoal(id: Long) = run("هدف عملکرد بسته شد.") { repository.deactivateGoal(id) }
    fun submitReview(draft: PerformanceReviewDraft) = run("ارزیابی عملکرد ثبت شد.") { repository.submitReview(draft) }
    fun clearMessage() { message.value = null }

    private fun run(success: String, action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }.onSuccess { message.value = success }.onFailure { message.value = UiErrorHandler.message("PerformanceViewModel", it) }
    }

    companion object {
        fun factory(repository: PerformanceRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PerformanceViewModel(repository) as T
        }
    }
}
