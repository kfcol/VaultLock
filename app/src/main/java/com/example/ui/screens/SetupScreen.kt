package com.example.ui.screens

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.receiver.VaultLockDeviceAdminReceiver
import com.example.ui.VaultLockUiState
import com.example.ui.components.StatusPill

@Composable
fun SetupScreen(
  uiState: VaultLockUiState,
  onRefreshStatus: () -> Unit,
  onSetSeparateAllowlists: (Boolean) -> Unit,
  onSetLockAtScheduleStart: (Boolean) -> Unit,
  onSetPreferDeviceAdminLock: (Boolean) -> Unit,
  onSetCooldownMillis: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val status = uiState.systemStatus
  var showBatteryGuide by remember { mutableStateOf(false) }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      onRefreshStatus()
    }

  val deviceAdminLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      onRefreshStatus()
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Setup & Guard",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Configure system permissions, lock method, and background reliability.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // 1. Accessibility Service
      item {
        PermissionCard(
          icon = Icons.Filled.AccessibilityNew,
          title = "Accessibility Service",
          badge = if (status.isAccessibilityEnabled) "ENABLED" else "REQUIRED",
          isGranted = status.isAccessibilityEnabled,
          description =
            "Detects foreground app launches (TYPE_WINDOW_STATE_CHANGED), sends you Home, and locks the screen on Android 9+.",
          actionLabel = if (status.isAccessibilityEnabled) "Manage" else "Enable Service",
          testTag = "setup_accessibility_button",
          onAction = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
          },
        )
      }

      // 2. Device Admin
      item {
        PermissionCard(
          icon = Icons.Filled.AdminPanelSettings,
          title = "Device Admin (Device Guard)",
          badge =
            when {
              status.isDeviceAdminEnabled -> "ENABLED"
              !status.supportsGlobalLockAction -> "REQUIRED (API < 28)"
              else -> "RECOMMENDED"
            },
          isGranted = status.isDeviceAdminEnabled,
          description =
            "Provides DevicePolicyManager.lockNow() fallback on Android 7–8 and protects VaultLock from accidental uninstall while active.",
          actionLabel = if (status.isDeviceAdminEnabled) "Deactivate" else "Enable Admin",
          testTag = "setup_device_admin_button",
          onAction = {
            val adminComponent = ComponentName(context, VaultLockDeviceAdminReceiver::class.java)
            if (status.isDeviceAdminEnabled) {
              val dpm = context.getSystemService(DevicePolicyManager::class.java)
              runCatching { dpm?.removeActiveAdmin(adminComponent) }
              onRefreshStatus()
            } else {
              val intent =
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                  putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                  putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Used to lock the screen immediately and protect against accidental uninstall.",
                  )
                }
              deviceAdminLauncher.launch(intent)
            }
          },
        )
      }

      // 3. Notifications
      item {
        PermissionCard(
          icon = Icons.Filled.Notifications,
          title = "Notifications",
          badge = if (status.isNotificationsGranted) "ENABLED" else "REQUIRED",
          isGranted = status.isNotificationsGranted,
          description =
            "Shows the live Lock Now countdown and gives you one-tap access to the Allowed Apps Drawer from the lock screen.",
          actionLabel = if (status.isNotificationsGranted) "Settings" else "Grant Permission",
          testTag = "setup_notifications_button",
          onAction = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.isNotificationsGranted) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                  putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
              context.startActivity(intent)
            }
          },
        )
      }

      // 4. Ignore Battery Optimization
      item {
        PermissionCard(
          icon = Icons.Filled.BatteryChargingFull,
          title = "Unrestricted Battery",
          badge = if (status.isIgnoringBatteryOptimizations) "UNRESTRICTED" else "RECOMMENDED",
          isGranted = status.isIgnoringBatteryOptimizations,
          description =
            "Prevents Android Doze from killing the foreground countdown service or Accessibility enforcement.",
          actionLabel =
            if (status.isIgnoringBatteryOptimizations) "Battery Settings" else "Disable Optimization",
          testTag = "setup_battery_button",
          onAction = {
            val intent =
              if (!status.isIgnoringBatteryOptimizations) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
              } else {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
              }
            runCatching { context.startActivity(intent) }
              .onFailure {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
              }
          },
        )
      }

      // 5. Exact Alarms (Android 12+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item {
          PermissionCard(
            icon = Icons.Filled.Alarm,
            title = "Exact Schedule Alarms",
            badge = if (status.canScheduleExactAlarms) "ALLOWED" else "OPTIONAL",
            isGranted = status.canScheduleExactAlarms,
            description =
              "Locks the phone right when a scheduled window begins instead of waiting for your next app open.",
            actionLabel = if (status.canScheduleExactAlarms) "Alarm Settings" else "Allow Exact Alarms",
            testTag = "setup_alarm_button",
            onAction = {
              val intent =
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
              runCatching { context.startActivity(intent) }
            },
          )
        }
      }

      // 6. Screen lock security check
      item {
        PermissionCard(
          icon = Icons.Filled.Lock,
          title = "Secure Screen Lock (PIN / Biometric)",
          badge = if (status.isDeviceLockSecure) "SECURE" else "ACTION NEEDED",
          isGranted = status.isDeviceLockSecure,
          description =
            if (status.isDeviceLockSecure) {
              "Your device uses a PIN, pattern, password, or biometric lock. Locking truly blocks access."
            } else {
              "Swipe or None screen lock is active! Locking the screen will still allow swiping back in — set a PIN or fingerprint in Security Settings."
            },
          actionLabel = "Security Settings",
          testTag = "setup_security_button",
          onAction = {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
          },
        )
      }

      // Behavior Preferences
      item {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              text = "Enforcement Preferences",
              style = MaterialTheme.typography.titleLarge,
            )

            PreferenceToggleRow(
              title = "Separate Lock Now & Scheduled allowlists",
              subtitle = "Keep different allowed app lists for instant locks vs recurring schedules.",
              checked = uiState.preferences.separateAllowlists,
              onCheckedChange = onSetSeparateAllowlists,
            )

            PreferenceToggleRow(
              title = "Lock right at schedule start time",
              subtitle = "Trigger an immediate screen lock when a schedule window opens.",
              checked = uiState.preferences.lockAtScheduleStart,
              onCheckedChange = onSetLockAtScheduleStart,
            )

            PreferenceToggleRow(
              title = "Prefer Device Admin lockNow()",
              subtitle = "Use DevicePolicyManager instead of Accessibility lock action when Admin is enabled.",
              checked = uiState.preferences.preferDeviceAdminLock,
              onCheckedChange = onSetPreferDeviceAdminLock,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Lock Event Cooldown",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Prevents duplicate locks from rapid window transitions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1000L to "1.0s", 1500L to "1.5s", 2000L to "2.0s").forEach { (ms, label) ->
                  FilterChip(
                    selected = uiState.preferences.cooldownMillis == ms,
                    onClick = { onSetCooldownMillis(ms) },
                    label = { Text(label) },
                  )
                }
              }
            }
          }
        }
      }

      // OEM Battery & Sideload Guide
      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .clickable { showBatteryGuide = !showBatteryGuide },
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "OEM Battery & Sideload Guide",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Tips for Android 13+ Restricted Settings & aggressive task killers",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Icon(
                imageVector =
                  if (showBatteryGuide) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = "Toggle guide",
              )
            }

            AnimatedVisibility(visible = showBatteryGuide) {
              Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                GuideParagraph(
                  title = "Sideloaded APK & Restricted Settings (Android 13+)",
                  body =
                    "If Android grays out the Accessibility toggle, open System Settings → Apps → VaultLock → tap the 3-dot menu in the top right → 'Allow restricted settings'.",
                )
                GuideParagraph(
                  title = "Samsung (One UI)",
                  body =
                    "Settings → Apps → VaultLock → Battery → Unrestricted. Also remove VaultLock from 'Sleeping apps' and 'Deep sleeping apps' under Device Care.",
                )
                GuideParagraph(
                  title = "Xiaomi / POCO (HyperOS / MIUI)",
                  body =
                    "Enable Autostart for VaultLock in Security → Permissions → Autostart, and set Battery Saver to 'No restrictions'.",
                )
                GuideParagraph(
                  title = "OnePlus / OPPO / Realme",
                  body =
                    "App Info → Battery usage → Allow background activity + Allow auto launch. Lock the app in Recents if needed.",
                )
              }
            }
          }
        }
      }

      item { Spacer(Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun PermissionCard(
  icon: ImageVector,
  title: String,
  badge: String,
  isGranted: Boolean,
  description: String,
  actionLabel: String,
  testTag: String,
  onAction: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border =
      BorderStroke(
        1.dp,
        if (isGranted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = if (isGranted) Icons.Filled.CheckCircle else icon,
            contentDescription = null,
            tint =
              if (isGranted) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.primary,
          )
          Text(text = title, style = MaterialTheme.typography.titleMedium)
        }

        StatusPill(
          text = badge,
          containerColor =
            if (isGranted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
          contentColor =
            if (isGranted) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onTertiaryContainer,
          dotColor =
            if (isGranted) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.tertiary,
        )
      }

      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (isGranted) {
        OutlinedButton(
          onClick = onAction,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag(testTag),
        ) {
          Text(actionLabel)
        }
      } else {
        Button(
          onClick = onAction,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag(testTag),
        ) {
          Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
private fun PreferenceToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun GuideParagraph(title: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
