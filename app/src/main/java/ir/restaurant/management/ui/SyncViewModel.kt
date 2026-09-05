package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.data.repository.LocalSyncRepository
import ir.restaurant.management.data.AppContainer
import ir.restaurant.management.domain.operations.CloudSyncConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ir.restaurant.management.domain.operations.SyncHealth
import ir.restaurant.management.domain.operations.SyncStatusCalculator
import ir.restaurant.management.domain.operations.SyncStatusSummary
import ir.restaurant.management.domain.operations.SyncQueueReport
import ir.restaurant.management.domain.operations.SyncQueueReporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SyncUiState(val summary: SyncStatusSummary = SyncStatusSummary(0, 0, 0, 0, null, SyncHealth.HEALTHY),val queue:SyncQueueReport=SyncQueueReport(0,0,0,0,null),val config:CloudSyncConfig=CloudSyncConfig("","",false),val issues:List<ir.restaurant.management.domain.operations.SyncEnvelope> = emptyList(),val message:String?=null,val running:Boolean=false)

class SyncViewModel(private val repository: LocalSyncRepository,private val container:AppContainer) : ViewModel() {
    private val transient=MutableStateFlow(Triple(container.syncConfig(),null as String?,false))
    val state: StateFlow<SyncUiState> = combine(repository.changes,transient) { changes,extra->SyncUiState(SyncStatusCalculator.summarize(changes),SyncQueueReporter.summarize(changes),extra.first,changes.filter{it.state in setOf(ir.restaurant.management.domain.operations.SyncState.CONFLICT,ir.restaurant.management.domain.operations.SyncState.REJECTED,ir.restaurant.management.domain.operations.SyncState.DEAD_LETTER)},extra.second,extra.third) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())
    fun save(endpoint:String,organizationId:String,enabled:Boolean,accessToken:String,refreshToken:String){val previous=container.syncConfig();val value=CloudSyncConfig(endpoint,organizationId,enabled,accessToken.trim(),refreshToken.trim(),if(accessToken.isBlank())0 else System.currentTimeMillis()+15*60_000L,previous.deviceId);viewModelScope.launch{transient.value=runCatching{container.saveSyncConfig(value);Triple(value,"تنظیمات و توکن کوتاه‌عمر همگام‌سازی ذخیره شد.",false)}.getOrElse{Triple(value,it.message,false)}}}
    fun runNow(){if(transient.value.third)return;viewModelScope.launch{transient.value=transient.value.copy(third=true);val text=runCatching{container.runSyncAuthorized()}.fold({"${it.uploaded} تغییر همگام شد؛ ${it.conflicts} تعارض"},{it.message?:"همگام‌سازی انجام نشد."});transient.value=transient.value.copy(second=text,third=false)}}
    fun requeueDeadLetters(){viewModelScope.launch{val count=repository.requeueDeadLetters();transient.value=transient.value.copy(second="${ErpDisplayFormatters.integer(count)} پیام ناموفق برای تلاش مجدد آماده شد.")}}
    fun resolveIssue(changeId:String,keepLocal:Boolean){viewModelScope.launch{runCatching{container.resolveSyncIssueAuthorized(changeId,keepLocal)}.onSuccess{transient.value=transient.value.copy(second=if(keepLocal)"نسخه محلی برای ارسال مجدد انتخاب شد." else "وضعیت سرور پذیرفته شد.")}.onFailure{transient.value=transient.value.copy(second=it.message)}}}
    fun clearMessage(){transient.value=transient.value.copy(second=null)}

    companion object {
        fun factory(repository: LocalSyncRepository,container:AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = SyncViewModel(repository,container) as T
        }
    }
}
