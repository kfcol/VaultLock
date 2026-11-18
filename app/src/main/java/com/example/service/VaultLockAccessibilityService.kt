package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.example.VaultLockApp
import com.example.data.AllowlistScope
import com.example.data.LockSessionEntity
import com.example.data.ScheduleEntity
import com.example.data.VaultLockPreferences
import com.example.domain.AlwaysAllowedPackages
import com.example.domain.LockEvaluator
import com.example.receiver.VaultLockDeviceAdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VaultLockAccessibilityService : AccessibilityService() {

  private var serviceScope: CoroutineScope? = null

  @Volatile private var cachedSession: LockSessionEntity? = null
  @Volatile private var cachedSchedules: List<ScheduleEntity> = emptyList()
  @Volatile private var cachedLockNowAllowed: Set<String> = emptySet()
  @Volatile private var cachedScheduledAllowed: Set<String> = emptySet()
  @Volatile private var cachedPrefs: VaultLockPreferences = VaultLockPreferences()
  @Volatile private var cachedAlwaysAllowed: Set<String> = emptySet()
  @Volatile private var lastAlwaysAllowedRefreshMs: Long = 0L
  @Volatile private var lastLockTriggerElapsedMs: Long = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    isRunning = true

    refreshAlwaysAllowedIfNeeded(force = true)

    serviceScope?.cancel()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    serviceScope = scope

    val repo = (application as VaultLockApp).repository
    scope.launch {
      repo.observeLockSession().collectLatest { cachedSession = it }
    }
    scope.launch {
      repo.observeSchedules().collectLatest { all ->
        cachedSchedules = all.filter { it.enabled }
      }
    }
    scope.launch {
      repo.observeAllowedPackages(AllowlistScope.LOCK_NOW).collectLatest {
        cachedLockNowAllowed = it
      }
    }
    scope.launch {
      repo.observeAllowedPackages(AllowlistScope.SCHEDULED).collectLatest {
        cachedScheduledAllowed = it
      }
    }
    scope.launch {
      repo.preferencesFlow.collectLatest { cachedPrefs = it }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

    val nowMillis = System.currentTimeMillis()
    val sessionSnapshot = cachedSession
    val activeState =
      LockEvaluator.evaluateActiveLock(
        lockSession = sessionSnapshot,
        schedules = cachedSchedules,
        nowMillis = nowMillis,
        separateAllowlists = cachedPrefs.separateAllowlists,
      )

    if (!activeState.isAnyLockActive) {
      if (sessionSnapshot != null && sessionSnapshot.endTimeMillis <= nowMillis) {
        serviceScope?.launch {
          (application as VaultLockApp).repository.clearLockNowSession()
        }
      }
      return
    }

    // Ensure the persistent Allowed Apps Drawer notification is visible during any active lock
    if (!LockSessionForegroundService.isRunning) {
      LockSessionForegroundService.ensureRunning(this)
    }

    refreshAlwaysAllowedIfNeeded(force = false)
    if (packageName == this.packageName || cachedAlwaysAllowed.contains(packageName)) {
      return
    }

    val userAllowedSet =
      when (activeState.effectiveAllowlistScope) {
        AllowlistScope.LOCK_NOW -> cachedLockNowAllowed
        AllowlistScope.SCHEDULED -> cachedScheduledAllowed
      }

    if (userAllowedSet.contains(packageName)) {
      return
    }

    // Blocked app detected! Apply 1.5s cooldown so one event/transition doesn't double-lock.
    val nowElapsed = SystemClock.elapsedRealtime()
    val cooldown = cachedPrefs.cooldownMillis
    if (nowElapsed - lastLockTriggerElapsedMs < cooldown) {
      return
    }
    lastLockTriggerElapsedMs = nowElapsed

    performHomeAction()
    lockDeviceNow()
  }

  fun performHomeAction(): Boolean =
    runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }.getOrDefault(false)

  fun performLockScreenAction(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      runCatching { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }.getOrDefault(false)
    } else {
      false
    }
  }

  private fun lockDeviceNow() {
    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val adminComponent = ComponentName(this, VaultLockDeviceAdminReceiver::class.java)
    val isAdminActive = dpm?.isAdminActive(adminComponent) == true

    if (cachedPrefs.preferDeviceAdminLock && isAdminActive) {
      runCatching { dpm?.lockNow() }
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val ok = performLockScreenAction()
      if (!ok && isAdminActive) {
        runCatching { dpm?.lockNow() }
      }
    } else if (isAdminActive) {
      runCatching { dpm?.lockNow() }
    }
  }

  private fun refreshAlwaysAllowedIfNeeded(force: Boolean) {
    val nowElapsed = SystemClock.elapsedRealtime()
    if (force || nowElapsed - lastAlwaysAllowedRefreshMs > 60_000L) {
      cachedAlwaysAllowed = AlwaysAllowedPackages.resolveAlwaysAllowedPackages(this)
      lastAlwaysAllowedRefreshMs = nowElapsed
    }
  }

  override fun onInterrupt() {
    // No ongoing speech/feedback to interrupt
  }

  override fun onDestroy() {
    isRunning = false
    if (instance === this) {
      instance = null
    }
    serviceScope?.cancel()
    serviceScope = null
    super.onDestroy()
  }

  companion object {
    @Volatile
    var instance: VaultLockAccessibilityService? = null
      private set

    @Volatile
    var isRunning: Boolean = false
      private set
  }
}
