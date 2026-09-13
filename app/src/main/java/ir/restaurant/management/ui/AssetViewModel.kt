package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetRecord
import ir.restaurant.management.application.assets.AssetUseCases
import ir.restaurant.management.domain.assets.DepreciationDraft
import ir.restaurant.management.domain.assets.DepreciationRecord
import ir.restaurant.management.domain.assets.DepreciationReversalDraft
import ir.restaurant.management.domain.assets.AssetTransferDraft
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.assets.AssetImpairmentDraft
import ir.restaurant.management.domain.assets.AssetSaleDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetUiState(
    val assets: List<AssetRecord> = emptyList(),
    val depreciations: List<DepreciationRecord> = emptyList(),
    val message: String? = null,
)

class AssetViewModel(
    private val useCases: AssetUseCases,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AssetUiState> = combine(
        useCases.assets,
        useCases.depreciations,
        message,
    ) { assets, depreciations, currentMessage ->
        AssetUiState(assets, depreciations, currentMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetUiState(),
    )

    fun save(id: Long?, draft: AssetDraft) = launch("دارایی ذخیره شد.") {
        useCases.save(id, draft)
    }

    fun recognize(id: Long) = launch("دارایی قدیمی با سند مانده افتتاحیه به دفتر متصل شد.") {
        useCases.recognizeImported(id)
    }

    fun dispose(id: Long) = launch("دارایی از چرخه بهره‌برداری خارج شد.") {
        useCases.dispose(id)
    }

    fun depreciate(draft: DepreciationDraft) = launch("استهلاک ثبت، Audit و سند حسابداری صادر شد.") {
        useCases.depreciate(draft)
    }

    fun reverseDepreciation(draft: DepreciationReversalDraft) = launch("برگشت استهلاک و سند معکوس ثبت شد.") {
        useCases.reverseDepreciation(draft)
    }

    fun transfer(draft: AssetTransferDraft) = launch("انتقال دارایی با تاریخچه و Audit ثبت شد.") {
        useCases.transfer(draft)
    }

    fun maintenance(draft: AssetMaintenanceDraft) = launch("سرویس دارایی و هزینه آن ثبت شد.") {
        useCases.maintenance(draft)
    }

    fun impair(draft: AssetImpairmentDraft) = launch("کاهش ارزش و سند حسابداری ثبت شد.") {
        useCases.impair(draft)
    }

    fun sell(draft: AssetSaleDraft) = launch("فروش دارایی، سود/زیان و سند حسابداری ثبت شد.") {
        useCases.sell(draft)
    }

    fun clearMessage() {
        message.value = null
    }

    private fun launch(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { message.value = success }
            .onFailure { error -> message.value = UiErrorHandler.message("AssetViewModel", error) }
    }

    companion object {
        fun factory(useCases: AssetUseCases) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AssetViewModel(useCases) as T
        }
    }
}
