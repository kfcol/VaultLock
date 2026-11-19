package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VaultLockApp
import com.example.data.AllowlistScope
import com.example.data.InstalledAppInfo
import com.example.data.LockSessionEntity
import com.example.data.ScheduleEntity
import com.example.data.VaultLockPreferences
import com.example.domain.ActiveLockState
import com.example.domain.AlwaysAllowedPackages
import com.example.domain.LockEvaluator
import com.example.domain.PermissionStatusHelper
import com.example.domain.SystemStatusSnapshot
import com.example.service.LockSessionForegroundService
import com.example.service.ScreenLockController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultLockUiState(
  val nowMillis: Long = System.currentTimeMillis(),
  val lockSession: LockSessionEntity? = null,
  val activeLockState: ActiveLockState = ActiveLockState(),
  val schedules: List<ScheduleEntity> = emptyList(),
  val lockNowAllowedPackages: Set<String> = emptySet(),
  val scheduledAllowedPackages: Set<String> = emptySet(),
  val alwaysAllowedPackages: Set<String> = emptySet(),
  val installedApps: List<InstalledAppInfo> = emptyList(),
  val isLoadingApps: Boolean = true,
  val activeAllowlistTab: AllowlistScope = AllowlistScope.LOCK_NOW,
  val allowlistSearchQuery: String = "",
  val showSystemAppsInAllowlist: Boolean = false,
  val showOnlyAllowedInAllowlist: Boolean = false,
  val selectedDurationMinutes: Int = 30,
  val preferences: VaultLockPreferences = VaultLockPreferences(),
  val systemStatus: SystemStatusSnapshot = SystemStatusSnapshot(),
)

private data class FilterState(
  val tab: AllowlistScope = AllowlistScope.LOCK_NOW,
  val query: String = "",
  val showSystem: Boolean = false,
  val onlyAllowed: Boolean = false,
  val selectedDurationMinutes: Int = 30,
)

class VaultLockViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as VaultLockApp
  private val repo = app.repository
  private val appsRepo = app.installedAppsRepository

  private val installedAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
  private val isLoadingAppsFlow = MutableStateFlow(true)
  private val alwaysAllowedFlow = MutableStateFlow<Set<String>>(emptySet())
  private val systemStatusFlow = MutableStateFlow(PermissionStatusHelper.inspect(application))
  private val filterStateFlow = MutableStateFlow(FilterState())

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val messages = _messages.asSharedFlow()

  private val tickerFlow = flow {
    while (true) {
      emit(System.currentTimeMillis())
      delay(1000L)
    }
  }

  private val coreDataFlow =
    combine(
      tickerFlow,
      repo.observeLockSession(),
      repo.observeSchedules(),
      repo.observeAllowedPackages(AllowlistScope.LOCK_NOW),
      repo.observeAllowedPackages(AllowlistScope.SCHEDULED),
      repo.preferencesFlow,
    ) { values ->
      val now = values[0] as Long
      val session = values[1] as LockSessionEntity?
      @Suppress("UNCHECKED_CAST")
      val schedules = values[2] as List<ScheduleEntity>
      @Suppress("UNCHECKED_CAST")
      val lockNowAllowed = values[3] as Set<String>
      @Suppress("UNCHECKED_CAST")
      val scheduledAllowed = values[4] as Set<String>
      val prefs = values[5] as VaultLockPreferences

      val activeState =
        LockEvaluator.evaluateActiveLock(
          lockSession = session,
          schedules = schedules,
          nowMillis = now,
          separateAllowlists = prefs.separateAllowlists,
        )

      CoreDataSnapshot(
        nowMillis = now,
        session = session,
        activeLockState = activeState,
        schedules = schedules,
        lockNowAllowed = lockNowAllowed,
        scheduledAllowed = scheduledAllowed,
        prefs = prefs,
      )
    }

  val uiState: StateFlow<VaultLockUiState> =
    combine(
      coreDataFlow,
      installedAppsFlow,
      isLoadingAppsFlow,
      alwaysAllowedFlow,
      systemStatusFlow,
      filterStateFlow,
    ) { values ->
      val core = values[0] as CoreDataSnapshot
      @Suppress("UNCHECKED_CAST")
      val apps = values[1] as List<InstalledAppInfo>
      val loadingApps = values[2] as Boolean
      @Suppress("UNCHECKED_CAST")
      val alwaysAllowed = values[3] as Set<String>
      val sysStatus = values[4] as SystemStatusSnapshot
      val filter = values[5] as FilterState

      VaultLockUiState(
        nowMillis = core.nowMillis,
        lockSession = core.session,
        activeLockState = core.activeLockState,
        schedules = core.schedules,
        lockNowAllowedPackages = core.lockNowAllowed,
        scheduledAllowedPackages = core.scheduledAllowed,
        alwaysAllowedPackages = alwaysAllowed,
        installedApps = apps,
        isLoadingApps = loadingApps,
        activeAllowlistTab = filter.tab,
        allowlistSearchQuery = filter.query,
        showSystemAppsInAllowlist = filter.showSystem,
        showOnlyAllowedInAllowlist = filter.onlyAllowed,
        selectedDurationMinutes = filter.selectedDurationMinutes,
        preferences = core.prefs,
        systemStatus = sysStatus,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = VaultLockUiState(),
    )

  init {
    refreshInstalledApps()
    refreshSystemStatus(application)
    viewModelScope.launch {
      // Ensure foreground service matches persisted session on launch
      val now = System.currentTimeMillis()
      val session = repo.getLockSession()
      val schedules = repo.getEnabledSchedules()
      val prefs = repo.getPreferences()
      if (session != null && session.endTimeMillis <= now) {
        repo.clearLockNowSession()
      }
      val active =
        LockEvaluator.evaluateActiveLock(session, schedules, now, prefs.separateAllowlists)
      if (active.isAnyLockActive) {
        LockSessionForegroundService.ensureRunning(app)
      }
      ScreenLockController.syncScheduleAlarms(app, schedules, prefs.lockAtScheduleStart)
    }
  }

  fun refreshSystemStatus(context: Context) {
    systemStatusFlow.value = PermissionStatusHelper.inspect(context)
    alwaysAllowedFlow.value = AlwaysAllowedPackages.resolveAlwaysAllowedPackages(context)
  }

  fun refreshInstalledApps() {
    viewModelScope.launch {
      isLoadingAppsFlow.value = true
      installedAppsFlow.value = appsRepo.loadLaunchableApps()
      isLoadingAppsFlow.value = false
    }
  }

  fun setDurationMinutes(minutes: Int) {
    filterStateFlow.value =
      filterStateFlow.value.copy(selectedDurationMinutes = minutes.coerceIn(1, 24 * 60))
  }

  fun startLockNowSession() {
    val minutes = filterStateFlow.value.selectedDurationMinutes
    val status = PermissionStatusHelper.inspect(app)
    systemStatusFlow.value = status

    if (!status.canEnforceLock) {
      val reason =
        if (!status.isAccessibilityEnabled) {
          "Enable VaultLock Accessibility Service in Setup first."
        } else {
          "Android 8 or lower requires Device Admin enabled to lock the screen."
        }
      _messages.tryEmit(reason)
      return
    }

    viewModelScope.launch {
      val prefs = repo.getPreferences()
      repo.startLockNowSession(minutes)
      LockSessionForegroundService.ensureRunning(app)
      val locked =
        ScreenLockController.triggerImmediateLock(
          context = app,
          preferDeviceAdmin = prefs.preferDeviceAdminLock,
          cooldownMillis = prefs.cooldownMillis,
          respectCooldown = false,
        )
      if (!locked) {
        _messages.tryEmit("Session started, but screen lock permission needs attention.")
      }
    }
  }

  fun cancelLockNowSession() {
    viewModelScope.launch {
      repo.clearLockNowSession()
      val schedules = repo.getEnabledSchedules()
      val prefs = repo.getPreferences()
      val active =
        LockEvaluator.evaluateActiveLock(
          lockSession = null,
          schedules = schedules,
          nowMillis = System.currentTimeMillis(),
          separateAllowlists = prefs.separateAllowlists,
        )
      if (!active.isAnyLockActive) {
        LockSessionForegroundService.stop(app)
      }
      _messages.tryEmit("Lock Now session ended.")
    }
  }

  fun setAllowlistTab(scope: AllowlistScope) {
    filterStateFlow.value = filterStateFlow.value.copy(tab = scope)
  }

  fun setAllowlistSearchQuery(query: String) {
    filterStateFlow.value = filterStateFlow.value.copy(query = query)
  }

  fun setShowSystemApps(show: Boolean) {
    filterStateFlow.value = filterStateFlow.value.copy(showSystem = show)
  }

  fun setShowOnlyAllowed(onlyAllowed: Boolean) {
    filterStateFlow.value = filterStateFlow.value.copy(onlyAllowed = onlyAllowed)
  }

  fun toggleAppAllowed(packageName: String, scope: AllowlistScope, allowed: Boolean) {
    viewModelScope.launch {
      repo.setAppAllowed(packageName, scope, allowed)
    }
  }

  fun selectAllFilteredApps(packageNames: List<String>, scope: AllowlistScope) {
    viewModelScope.launch {
      repo.selectAllApps(packageNames, scope)
      _messages.tryEmit("Allowed ${packageNames.size} apps for ${scope.displayTitle}.")
    }
  }

  fun selectNoApps(scope: AllowlistScope) {
    viewModelScope.launch {
      repo.selectNoApps(scope)
      _messages.tryEmit("Cleared ${scope.displayTitle} allowlist.")
    }
  }

  fun copyAllowlist(from: AllowlistScope, to: AllowlistScope) {
    viewModelScope.launch {
      repo.copyAllowlist(from, to)
      _messages.tryEmit("Copied ${from.displayTitle} allowlist to ${to.displayTitle}.")
    }
  }

  fun saveSchedule(schedule: ScheduleEntity) {
    viewModelScope.launch {
      repo.upsertSchedule(schedule)
      val schedules = repo.getEnabledSchedules()
      val prefs = repo.getPreferences()
      ScreenLockController.syncScheduleAlarms(app, schedules, prefs.lockAtScheduleStart)
      val active =
        LockEvaluator.evaluateActiveLock(
          lockSession = repo.getLockSession(),
          schedules = schedules,
          nowMillis = System.currentTimeMillis(),
          separateAllowlists = prefs.separateAllowlists,
        )
      if (active.isAnyLockActive) {
        LockSessionForegroundService.ensureRunning(app)
      }
      if (schedule.reminderMinutesBefore > 0 && !PermissionStatusHelper.areNotificationsGranted(app)) {
        _messages.tryEmit("Schedule saved. Enable Notifications in Setup to receive reminders.")
      } else {
        _messages.tryEmit("Schedule saved.")
      }
    }
  }

  fun toggleScheduleEnabled(id: Long, enabled: Boolean) {
    viewModelScope.launch {
      repo.setScheduleEnabled(id, enabled)
      val schedules = repo.getEnabledSchedules()
      val prefs = repo.getPreferences()
      ScreenLockController.syncScheduleAlarms(app, schedules, prefs.lockAtScheduleStart)
      if (!enabled) {
        ScreenLockController.cancelScheduleAlarm(app, id)
      }
      val active =
        LockEvaluator.evaluateActiveLock(
          lockSession = repo.getLockSession(),
          schedules = schedules,
          nowMillis = System.currentTimeMillis(),
          separateAllowlists = prefs.separateAllowlists,
        )
      if (active.isAnyLockActive) {
        LockSessionForegroundService.ensureRunning(app)
      } else {
        LockSessionForegroundService.stop(app)
      }
    }
  }

  fun deleteSchedule(id: Long) {
    viewModelScope.launch {
      repo.deleteSchedule(id)
      ScreenLockController.cancelScheduleAlarm(app, id)
      val schedules = repo.getEnabledSchedules()
      val prefs = repo.getPreferences()
      ScreenLockController.syncScheduleAlarms(app, schedules, prefs.lockAtScheduleStart)
      _messages.tryEmit("Schedule deleted.")
    }
  }

  fun setSeparateAllowlists(enabled: Boolean) {
    viewModelScope.launch { repo.setSeparateAllowlists(enabled) }
  }

  fun setPreferDeviceAdminLock(enabled: Boolean) {
    viewModelScope.launch { repo.setPreferDeviceAdminLock(enabled) }
  }

  fun setLockAtScheduleStart(enabled: Boolean) {
    viewModelScope.launch {
      repo.setLockAtScheduleStart(enabled)
      val schedules = repo.getEnabledSchedules()
      ScreenLockController.syncScheduleAlarms(app, schedules, enabled)
    }
  }

  fun setCooldownMillis(millis: Long) {
    viewModelScope.launch { repo.setCooldownMillis(millis) }
  }

  private data class CoreDataSnapshot(
    val nowMillis: Long,
    val session: LockSessionEntity?,
    val activeLockState: ActiveLockState,
    val schedules: List<ScheduleEntity>,
    val lockNowAllowed: Set<String>,
    val scheduledAllowed: Set<String>,
    val prefs: VaultLockPreferences,
  )
}
