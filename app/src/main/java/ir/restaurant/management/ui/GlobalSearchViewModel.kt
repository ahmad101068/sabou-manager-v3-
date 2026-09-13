package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.search.GlobalSearchRepository
import ir.restaurant.management.domain.search.GlobalSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn


data class GlobalSearchUiState(
    val query: String = "",
    val results: List<GlobalSearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class GlobalSearchViewModel(private val repository: GlobalSearchRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<GlobalSearchUiState> = query
        .debounce(220)
        .distinctUntilChanged()
        .flatMapLatest { value ->
            flow {
                if (value.trim().length < 2) {
                    emit(GlobalSearchUiState(query = value))
                } else {
                    emit(GlobalSearchUiState(query = value, loading = true))
                    emit(GlobalSearchUiState(query = value, results = repository.search(value)))
                }
            }.catch { error ->
                emit(GlobalSearchUiState(query = value, error = UiErrorHandler.message("GlobalSearchViewModel.search", error)))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun search(value: String) {
        query.value = value.take(120)
    }

    companion object {
        fun factory(repository: GlobalSearchRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GlobalSearchViewModel::class.java))
                return GlobalSearchViewModel(repository) as T
            }
        }
    }
}
