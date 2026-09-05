package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.data.repository.DashboardRepository
import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.brief.DailyManagementBriefService
import ir.restaurant.management.domain.brief.DailyManagementKpiReadModel
import ir.restaurant.management.domain.brief.DailyManagementKpiReadModelFactory
import ir.restaurant.management.domain.operations.AppUserRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val repository: DashboardRepository,
    private val dailyBriefService: DailyManagementBriefService,
    private val epochDay: () -> Long = ::currentLocalEpochDay,
    private val rolloverPollMillis: Long = 60_000L,
) : ViewModel() {
    private val period = MutableStateFlow(DashboardPeriod.TODAY)
    private val customRange = MutableStateFlow(0L to 0L)
    private val selectedBranchId = MutableStateFlow<Long?>(null)
    private val warehouseLocationId = MutableStateFlow<Long?>(null)
    private val dashboardContext = MutableStateFlow(DashboardContext(null, ""))
    private val currentDay = flow {
        while (true) {
            emit(epochDay())
            delay(rolloverPollMillis)
        }
    }.distinctUntilChanged()

    private val range = combine(currentDay, period, customRange) { today, selected, custom ->
        val selectedRange = DashboardPeriodRanges.currentRange(today, selected, custom)
        Triple(selectedRange.fromEpochDay, selectedRange.toEpochDay, selected)
    }

    private val query = combine(range, selectedBranchId, warehouseLocationId) { selectedRange, branch, warehouse ->
        DashboardQuery(selectedRange.first, selectedRange.second, selectedRange.third, branch, warehouse)
    }

    val state: StateFlow<DashboardSnapshot> = query.flatMapLatest { selected ->
        repository.observeRange(
            selected.fromEpochDay,
            selected.toEpochDay,
            selected.period,
            selected.branchId,
            selected.warehouseLocationId,
        ).catch {
            emit(selected.emptySnapshot())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardSnapshot(),
    )

    private val comparisonState: StateFlow<DashboardComparisonLoadState> = query.flatMapLatest { selected ->
        comparisonFlow(selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardComparisonLoadState.Loading(DashboardPeriod.TODAY),
    )

    val homeState: StateFlow<DashboardUiState> = combine(comparisonState, dashboardContext) { comparison, context ->
        when (comparison) {
            is DashboardComparisonLoadState.Loading -> DashboardUxComposer.loading(
                comparison.period,
                context.user,
                context.organizationName,
            )
            is DashboardComparisonLoadState.Loaded -> DashboardUxComposer.compose(
                comparison.current,
                comparison.previous,
                context.user,
                context.organizationName,
            )
            is DashboardComparisonLoadState.Error -> DashboardUxComposer.error(
                comparison.period,
                context.user,
                context.organizationName,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    private val managementRefresh = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    val managementOverview: StateFlow<HomeManagementOverviewUiState> = combine(
        query,
        state,
        managementRefresh,
    ) { selected, snapshot, _ ->
        val effectiveBranchId = selected.branchId
        when {
            selected.period != DashboardPeriod.TODAY -> HomeManagementOverviewRequest.Unavailable(
                "شاخص‌های مدیریتی قطعی برای صفحه اصلی در نمای امروز ارائه می‌شوند.",
            )
            effectiveBranchId == null -> HomeManagementOverviewRequest.Unavailable(
                "برای مشاهده شاخص‌های مدیریتی، ابتدا یک شعبه فعال انتخاب کنید.",
            )
            else -> HomeManagementOverviewRequest.Load(effectiveBranchId, selected.toEpochDay)
        }
    }.flatMapLatest { request ->
        when (request) {
            is HomeManagementOverviewRequest.Unavailable -> flow {
                emit(HomeManagementOverviewUiState(unavailableMessage = request.reason))
            }
            is HomeManagementOverviewRequest.Load -> flow {
                emit(HomeManagementOverviewUiState(loading = true))
                emit(
                    runCatching {
                        val readModels = (6L downTo 0L).map { offset ->
                            DailyManagementKpiReadModelFactory.from(
                                dailyBriefService.compose(request.branchId, request.businessEpochDay - offset),
                            )
                        }
                        HomeManagementOverviewUiState(
                            readModel = readModels.last(),
                            revenueTrend = readModels.map {
                                HomeRevenueTrendPoint(it.businessEpochDay, it.revenueRial)
                            },
                            asOfEpochDay = readModels.last().businessEpochDay,
                        )
                    }.getOrElse {
                        HomeManagementOverviewUiState(
                            unavailableMessage = "دریافت شاخص‌های مدیریتی قطعی ناموفق بود. دوباره تلاش کنید.",
                            isError = true,
                        )
                    },
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeManagementOverviewUiState(loading = true),
    )

    fun setContext(user: AppUserRecord?, organizationName: String) {
        dashboardContext.value = DashboardContext(user, organizationName.trim())
    }

    fun today() { period.value = DashboardPeriod.TODAY }
    fun week() { period.value = DashboardPeriod.WEEK }
    fun month() { period.value = DashboardPeriod.MONTH }
    fun custom(fromEpochDay: Long, toEpochDay: Long) {
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay)
        customRange.value = fromEpochDay to toEpochDay
        period.value = DashboardPeriod.CUSTOM
    }

    fun selectBranch(branchId: Long?) {
        require(branchId == null || branchId > 0)
        selectedBranchId.value = branchId
    }

    fun warehouse(locationId: Long?) {
        require(locationId == null || locationId > 0)
        warehouseLocationId.value = locationId
    }

    private fun comparisonFlow(selected: DashboardQuery): Flow<DashboardComparisonLoadState> {
        val currentRange = DashboardEpochRange(selected.fromEpochDay, selected.toEpochDay)
        val previousRange = DashboardPeriodRanges.previousRange(currentRange, selected.period)
        val currentFlow = repository.observeRange(
            currentRange.fromEpochDay,
            currentRange.toEpochDay,
            selected.period,
            selected.branchId,
            selected.warehouseLocationId,
        )
        val previousFlow = repository.observeRange(
            previousRange.fromEpochDay,
            previousRange.toEpochDay,
            selected.period,
            selected.branchId,
            selected.warehouseLocationId,
        )
        return combine(currentFlow, previousFlow) { current, previous ->
            DashboardComparisonLoadState.Loaded(current, previous) as DashboardComparisonLoadState
        }.onStart {
            emit(DashboardComparisonLoadState.Loading(selected.period))
        }.catch {
            emit(DashboardComparisonLoadState.Error(selected.period))
        }
    }

    private data class DashboardContext(
        val user: AppUserRecord?,
        val organizationName: String,
    )

    private data class DashboardQuery(
        val fromEpochDay: Long,
        val toEpochDay: Long,
        val period: DashboardPeriod,
        val branchId: Long?,
        val warehouseLocationId: Long?,
    ) {
        fun emptySnapshot(): DashboardSnapshot = DashboardSnapshot(
            fromEpochDay = fromEpochDay,
            toEpochDay = toEpochDay,
            period = period,
            selectedBranchId = branchId,
            selectedWarehouseLocationId = warehouseLocationId,
        )
    }

    private sealed interface DashboardComparisonLoadState {
        data class Loading(val period: DashboardPeriod) : DashboardComparisonLoadState
        data class Loaded(val current: DashboardSnapshot, val previous: DashboardSnapshot) : DashboardComparisonLoadState
        data class Error(val period: DashboardPeriod) : DashboardComparisonLoadState
    }

    private sealed interface HomeManagementOverviewRequest {
        data class Load(val branchId: Long, val businessEpochDay: Long) : HomeManagementOverviewRequest
        data class Unavailable(val reason: String) : HomeManagementOverviewRequest
    }

    companion object {
        fun factory(
            repository: DashboardRepository,
            dailyBriefService: DailyManagementBriefService,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
                    return DashboardViewModel(repository, dailyBriefService) as T
                }
            }
    }
}

data class HomeRevenueTrendPoint(
    val businessEpochDay: Long,
    val revenueRial: Long,
)

data class HomeManagementOverviewUiState(
    val loading: Boolean = false,
    val readModel: DailyManagementKpiReadModel? = null,
    val revenueTrend: List<HomeRevenueTrendPoint> = emptyList(),
    val unavailableMessage: String? = null,
    val asOfEpochDay: Long? = null,
    val isError: Boolean = false,
)
