package com.example.domain

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.receiver.VaultLockDeviceAdminReceiver
import com.example.service.VaultLockAccessibilityService

data class SystemStatusSnapshot(
  val isAccessibilityEnabled: Boolean = false,
  val isDeviceAdminEnabled: Boolean = false,
  val isNotificationsGranted: Boolean = false,
  val isIgnoringBatteryOptimizations: Boolean = false,
  val canScheduleExactAlarms: Boolean = true,
  val isDeviceLockSecure: Boolean = true,
  val supportsGlobalLockAction: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
) {
  val canEnforceLock: Boolean
    get() =
      isAccessibilityEnabled &&
        (supportsGlobalLockAction || isDeviceAdminEnabled)
}

object PermissionStatusHelper {

  fun inspect(context: Context): SystemStatusSnapshot {
    return SystemStatusSnapshot(
      isAccessibilityEnabled = isAccessibilityServiceEnabled(context),
      isDeviceAdminEnabled = isDeviceAdminActive(context),
      isNotificationsGranted = areNotificationsGranted(context),
      isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
      canScheduleExactAlarms = canScheduleExactAlarms(context),
      isDeviceLockSecure = isDeviceSecure(context),
      supportsGlobalLockAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
    )
  }

  fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (VaultLockAccessibilityService.isRunning) return true
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val expectedComponent = ComponentName(context, VaultLockAccessibilityService::class.java)
    val enabledList =
      runCatching {
        am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      }.getOrNull()
    if (enabledList?.any { it.resolveInfo?.serviceInfo?.let { si -> si.packageName == expectedComponent.packageName && si.name == expectedComponent.className } == true } == true) {
      return true
    }
    val enabledSetting =
      Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false
    val flattened = expectedComponent.flattenToString()
    val shortFlattened = expectedComponent.flattenToShortString()
    return enabledSetting.split(':').any {
      it.equals(flattened, ignoreCase = true) || it.equals(shortFlattened, ignoreCase = true)
    }
  }

  fun isDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      ?: return false
    val admin = ComponentName(context, VaultLockDeviceAdminReceiver::class.java)
    return dpm.isAdminActive(admin)
  }

  fun areNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!permGranted) return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return am.canScheduleExactAlarms()
  }

  fun isDeviceSecure(context: Context): Boolean {
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return true
    return km.isDeviceSecure
  }
}
