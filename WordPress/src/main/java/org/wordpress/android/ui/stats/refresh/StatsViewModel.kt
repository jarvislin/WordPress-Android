package org.wordpress.android.ui.stats.refresh

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.analytics.AnalyticsTracker.Stat.STATS_INSIGHTS_ACCESSED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.STATS_PERIOD_DAYS_ACCESSED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.STATS_PERIOD_MONTHS_ACCESSED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.STATS_PERIOD_WEEKS_ACCESSED
import org.wordpress.android.analytics.AnalyticsTracker.Stat.STATS_PERIOD_YEARS_ACCESSED
import org.wordpress.android.fluxc.network.utils.StatsGranularity
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.push.NotificationType
import org.wordpress.android.push.NotificationsProcessingService.ARG_NOTIFICATION_TYPE
import org.wordpress.android.ui.notifications.SystemNotificationsTracker
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.stats.StatsTimeframe
import org.wordpress.android.ui.stats.StatsTimeframe.DAY
import org.wordpress.android.ui.stats.StatsTimeframe.MONTH
import org.wordpress.android.ui.stats.StatsTimeframe.WEEK
import org.wordpress.android.ui.stats.StatsTimeframe.YEAR
import org.wordpress.android.ui.stats.refresh.StatsActivity.StatsLaunchedFrom
import org.wordpress.android.ui.stats.refresh.StatsModuleActivateRequestState.Failure.NetworkUnavailable
import org.wordpress.android.ui.stats.refresh.StatsModuleActivateRequestState.Failure.RemoteRequestFailure
import org.wordpress.android.ui.stats.refresh.StatsModuleActivateRequestState.Success
import org.wordpress.android.ui.stats.refresh.lists.BaseListUseCase
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.ANNUAL_STATS
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.DAYS
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.DETAIL
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.INSIGHTS
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.MONTHS
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.WEEKS
import org.wordpress.android.ui.stats.refresh.lists.StatsListViewModel.StatsSection.YEARS
import org.wordpress.android.ui.stats.refresh.lists.sections.granular.SelectedDateProvider
import org.wordpress.android.ui.stats.refresh.utils.NewsCardHandler
import org.wordpress.android.ui.stats.refresh.utils.SelectedSectionManager
import org.wordpress.android.ui.stats.refresh.utils.StatsSiteProvider
import org.wordpress.android.ui.stats.refresh.utils.toStatsGranularity
import org.wordpress.android.ui.stats.refresh.utils.trackGranular
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.util.config.MySiteDashboardTodaysStatsCardFeatureConfig
import org.wordpress.android.util.mapNullable
import org.wordpress.android.util.mergeNotNull
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

@Suppress("TooManyFunctions", "LongParameterList")
class StatsViewModel
@Inject constructor(
    @Named(LIST_STATS_USE_CASES) private val listUseCases: Map<StatsSection, BaseListUseCase>,
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    private val selectedDateProvider: SelectedDateProvider,
    private val statsSectionManager: SelectedSectionManager,
    private val analyticsTracker: AnalyticsTrackerWrapper,
    private val networkUtilsWrapper: NetworkUtilsWrapper,
    private val statsSiteProvider: StatsSiteProvider,
    newsCardHandler: NewsCardHandler,
    private val statsModuleActivateUseCase: StatsModuleActivateUseCase,
    private val notificationsTracker: SystemNotificationsTracker,
    private val todaysStatsCardFeatureConfig: MySiteDashboardTodaysStatsCardFeatureConfig
) : ScopedViewModel(mainDispatcher) {
    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private var isInitialized = false

    private val _showSnackbarMessage = mergeNotNull(
            listUseCases.values.map { it.snackbarMessage },
            distinct = true,
            singleEvent = true
    )
    val showSnackbarMessage: LiveData<SnackbarMessageHolder> = _showSnackbarMessage

    val siteChanged = statsSiteProvider.siteChanged

    val toolbarHasShadow: LiveData<Boolean> = statsSectionManager.liveSelectedSection.mapNullable { it == INSIGHTS }

    val hideToolbar = newsCardHandler.hideToolbar

    val selectedSection = statsSectionManager.liveSelectedSection

    private val _statsModuleUiModel = MediatorLiveData<Event<StatsModuleUiModel>>()
    val statsModuleUiModel: LiveData<Event<StatsModuleUiModel>> = _statsModuleUiModel

    fun start(intent: Intent, restart: Boolean = false) {
        val localSiteId = intent.getIntExtra(WordPress.LOCAL_SITE_ID, 0)

        val launchedFrom = intent.getSerializableExtra(StatsActivity.ARG_LAUNCHED_FROM)
        val launchedFromWidget = launchedFrom == StatsLaunchedFrom.STATS_WIDGET
        val initialTimeFrame = getInitialTimeFrame(intent)
        val initialSelectedPeriod = intent.getStringExtra(StatsActivity.INITIAL_SELECTED_PERIOD_KEY)
        val notificationType = intent.getSerializableExtra(ARG_NOTIFICATION_TYPE) as? NotificationType
        start(localSiteId, launchedFromWidget, initialTimeFrame, initialSelectedPeriod, restart, notificationType)
    }

    fun onSaveInstanceState(outState: Bundle) {
        selectedDateProvider.onSaveInstanceState(outState)
    }

    fun onRestoreInstanceState(savedState: Bundle?) {
        if (savedState != null) {
            selectedDateProvider.onRestoreInstanceState(savedState)
        } else {
            selectedDateProvider.clear()
            statsSiteProvider.reset()
        }
    }

    private fun getInitialTimeFrame(intent: Intent): StatsSection? {
        return when (intent.getSerializableExtra(StatsActivity.ARG_DESIRED_TIMEFRAME)) {
            StatsTimeframe.INSIGHTS -> INSIGHTS
            DAY -> DAYS
            WEEK -> WEEKS
            MONTH -> MONTHS
            YEAR -> YEARS
            else -> null
        }
    }

    fun start(
        localSiteId: Int,
        launchedFromWidget: Boolean,
        initialSection: StatsSection?,
        initialSelectedPeriod: String?,
        restart: Boolean,
        notificationType: NotificationType?
    ) {
        if (restart) {
            selectedDateProvider.clear()
        }
        // Check if VM is not already initialized
        if (!isInitialized || restart) {
            isInitialized = true

            // Added today's stats feature config to check whether that card is enabled when stats screen is accessed
            analyticsTracker.track(
                    stat = AnalyticsTracker.Stat.STATS_ACCESSED,
                    site = statsSiteProvider.siteModel,
                    feature = todaysStatsCardFeatureConfig
            )

            initialSection?.let { statsSectionManager.setSelectedSection(it) }
            trackSectionSelected(initialSection ?: INSIGHTS)

            val initialGranularity = initialSection?.toStatsGranularity()
            if (initialGranularity != null && initialSelectedPeriod != null) {
                selectedDateProvider.setInitialSelectedPeriod(initialGranularity, initialSelectedPeriod)
            }

            if (launchedFromWidget) {
                analyticsTracker.track(AnalyticsTracker.Stat.STATS_WIDGET_TAPPED, statsSiteProvider.siteModel)
            }

            if (notificationType != null) {
                notificationsTracker.trackTappedNotification(notificationType)
            }
        }

        _statsModuleUiModel.value = Event(buildShowStatsEnabledViewUiModel())

        val siteChanged = statsSiteProvider.start(localSiteId)
        if (!isStatsModuleEnabled()) {
            _statsModuleUiModel.value = Event(buildShowStatsDisabledViewUiModel())
        } else {
            if (restart && siteChanged) {
                launch {
                    listUseCases.forEach { useCase ->
                        useCase.value.onCleared()
                        useCase.value.refreshData(true)
                    }
                }
            }
        }
    }

    private fun isStatsModuleEnabled() =
            statsSiteProvider.siteModel.isActiveModuleEnabled("stats") || statsSiteProvider.siteModel.isWPCom

    private fun loadData(executeLoading: suspend () -> Unit) = launch {
        _isRefreshing.value = true

        executeLoading()

        _isRefreshing.value = false
    }

    fun onPullToRefresh() {
        _showSnackbarMessage.value = null
        statsSiteProvider.clear()
        if (networkUtilsWrapper.isNetworkAvailable()) {
            loadData {
                val baseListUseCase = listUseCases[statsSectionManager.getSelectedSection()]
                baseListUseCase?.refreshTypes()
                baseListUseCase?.refreshData(true)
            }
        } else {
            _isRefreshing.value = false
            _showSnackbarMessage.value = SnackbarMessageHolder(UiStringRes(R.string.no_network_title))
        }
    }

    fun onSiteChanged() {
        if (!isStatsModuleEnabled()) {
            _statsModuleUiModel.value = Event(buildShowStatsDisabledViewUiModel())
        } else {
            _statsModuleUiModel.value = Event(buildShowStatsEnabledViewUiModel())
            loadData {
                listUseCases.values.forEach {
                    it.refreshData(true)
                }
            }
        }
    }

    fun onSectionSelected(statsSection: StatsSection) {
        statsSectionManager.setSelectedSection(statsSection)

        listUseCases[statsSection]?.onListSelected()

        trackSectionSelected(statsSection)
    }

    private fun trackSectionSelected(statsSection: StatsSection) {
        when (statsSection) {
            INSIGHTS -> analyticsTracker.track(STATS_INSIGHTS_ACCESSED)
            DAYS -> analyticsTracker.trackGranular(STATS_PERIOD_DAYS_ACCESSED, StatsGranularity.DAYS)
            WEEKS -> analyticsTracker.trackGranular(STATS_PERIOD_WEEKS_ACCESSED, StatsGranularity.WEEKS)
            MONTHS -> analyticsTracker.trackGranular(STATS_PERIOD_MONTHS_ACCESSED, StatsGranularity.MONTHS)
            YEARS -> analyticsTracker.trackGranular(STATS_PERIOD_YEARS_ACCESSED, StatsGranularity.YEARS)
            ANNUAL_STATS, DETAIL -> {
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _showSnackbarMessage.value = null
    }

    fun onEnableStatsModuleClick() {
        _statsModuleUiModel.value = Event(buildShowStatsActivatingViewUiModel())
        launch {
            when (statsModuleActivateUseCase.postActivateStatsModule(statsSiteProvider.siteModel)) {
                is NetworkUnavailable -> {
                    _statsModuleUiModel.value = Event(buildShowStatsDisabledViewUiModel())
                    _showSnackbarMessage.value = SnackbarMessageHolder(UiStringRes(R.string.no_network_title))
                }
                is RemoteRequestFailure -> {
                    _statsModuleUiModel.value = Event(buildShowStatsDisabledViewUiModel())
                    _showSnackbarMessage.value =
                            SnackbarMessageHolder(UiStringRes(R.string.stats_disabled_enable_stats_error_message))
                }
                is Success -> {
                    _statsModuleUiModel.value = Event(buildShowStatsEnabledViewUiModel())
                }
            }
        }
    }

    private fun buildShowStatsEnabledViewUiModel() = StatsModuleUiModel(disabledStatsViewVisible = false)

    private fun buildShowStatsDisabledViewUiModel() =
            StatsModuleUiModel(disabledStatsViewVisible = true, disabledStatsProgressVisible = false)

    private fun buildShowStatsActivatingViewUiModel() =
            StatsModuleUiModel(disabledStatsViewVisible = true, disabledStatsProgressVisible = true)

    data class DateSelectorUiModel(
        val isVisible: Boolean = false,
        val date: String? = null,
        val timeZone: String? = null,
        val enableSelectPrevious: Boolean = false,
        val enableSelectNext: Boolean = false
    )

    data class StatsModuleUiModel(
        val disabledStatsViewVisible: Boolean = false,
        val disabledStatsProgressVisible: Boolean = false
    )
}
