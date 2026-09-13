package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.branch.BranchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BranchManagementUiState(
    val branches: List<BranchRecord> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class BranchManagementViewModel(private val repository: BranchRepository) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<BranchManagementUiState> = combine(repository.branches, busy, message) { rows, isBusy, currentMessage ->
        BranchManagementUiState(rows, isBusy, currentMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BranchManagementUiState())

    fun create(name: String, code: String?, done: () -> Unit = {}) = runCommand("شعبه ایجاد شد.", done) {
        repository.create(BranchDraft(name = name, code = code))
    }

    fun rename(id: Long, name: String, done: () -> Unit = {}) = runCommand("نام شعبه تغییر کرد.", done) {
        repository.rename(id, name)
    }

    fun setActive(id: Long, active: Boolean) = runCommand(if (active) "شعبه فعال شد." else "شعبه غیرفعال شد.") {
        repository.setActive(id, active)
    }

    fun clearMessage() { message.value = null }

    private fun runCommand(success: String, done: () -> Unit = {}, action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                action()
                message.value = success
                done()
            } catch (error: Exception) {
                message.value = UiErrorHandler.message("BranchManagementViewModel", error)
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(repository: BranchRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BranchManagementViewModel(repository) as T
        }
    }
}
