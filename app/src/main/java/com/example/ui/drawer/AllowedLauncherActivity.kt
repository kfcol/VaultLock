package com.example.ui.drawer

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.VaultLockApp
import com.example.data.InstalledAppInfo
import com.example.domain.LockEvaluator
import com.example.domain.formatRemainingMillis
import com.example.ui.components.AppIconThumbnail
import com.example.ui.theme.VaultLockTheme
import kotlinx.coroutines.delay

class AllowedLauncherActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }

    val app = application as VaultLockApp
    setContent {
      VaultLockTheme(darkTheme = true) {
        AllowedDrawerContent(
          app = app,
          onLaunchPackage = { pkg -> launchPackageCleanly(pkg) },
          onLaunchDialer = { launchDialerCleanly() },
          onLaunchCamera = { launchCameraCleanly() },
          onClose = { finish() },
        )
      }
    }
  }

  private fun launchPackageCleanly(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    launchWithOptionalKeyguardDismiss(launchIntent)
  }

  private fun launchDialerCleanly() {
    val dialIntent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launchWithOptionalKeyguardDismiss(dialIntent)
  }

  private fun launchCameraCleanly() {
    val cameraIntent =
      Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(cameraIntent) }.onSuccess { finish() }
  }

  private fun launchWithOptionalKeyguardDismiss(intent: Intent) {
    val km = getSystemService(KeyguardManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km?.isKeyguardLocked == true) {
      km.requestDismissKeyguard(
        this,
        object : KeyguardManager.KeyguardDismissCallback() {
          override fun onDismissSucceeded() {
            runCatching { startActivity(intent) }
            finish()
          }

          override fun onDismissCancelled() {
            // Stay in drawer if user cancelled unlock
          }

          override fun onDismissError() {
            runCatching { startActivity(intent) }
            finish()
          }
        },
      )
    } else {
      runCatching { startActivity(intent) }
      finish()
    }
  }
}

@Composable
private fun AllowedDrawerContent(
  app: VaultLockApp,
  onLaunchPackage: (String) -> Unit,
  onLaunchDialer: () -> Unit,
  onLaunchCamera: () -> Unit,
  onClose: () -> Unit,
) {
  var allowedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
  var statusLabel by remember { mutableStateOf("Allowed Apps Drawer") }
  var searchQuery by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    val allApps = app.installedAppsRepository.loadLaunchableApps()
    while (true) {
      val now = System.currentTimeMillis()
      val session = app.repository.getLockSession()
      val schedules = app.repository.getEnabledSchedules()
      val prefs = app.repository.getPreferences()
      val activeState =
        LockEvaluator.evaluateActiveLock(
          lockSession = session,
          schedules = schedules,
          nowMillis = now,
          separateAllowlists = prefs.separateAllowlists,
        )
      val allowedSet = app.repository.getAllowedPackages(activeState.effectiveAllowlistScope)
      allowedApps = allApps.filter { allowedSet.contains(it.packageName) }

      statusLabel =
        when {
          activeState.isLockNowActive -> {
            val rem = (activeState.lockNowEndTimeMillis - now).coerceAtLeast(0L)
            "Lock Now • ${formatRemainingMillis(rem)} left"
          }
          activeState.activeSchedule != null -> {
            "${activeState.activeSchedule.label.ifBlank { "Scheduled Lock" }} Active"
          }
          else -> "Allowed Apps Preview"
        }
      delay(1000L)
    }
  }

  val filteredApps =
    remember(allowedApps, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) allowedApps
      else allowedApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
            )
            Column {
              Text("Allowed Apps", style = MaterialTheme.typography.titleLarge)
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(onClick = onClose, modifier = Modifier.testTag("close_drawer_button")) {
            Icon(Icons.Filled.Close, contentDescription = "Close drawer")
          }
        }

        // Essential Phone + Camera quick actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FilledTonalButton(
            onClick = onLaunchDialer,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Phone / Emergency")
          }
          FilledTonalButton(
            onClick = onLaunchCamera,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Camera")
          }
        }

        if (allowedApps.size > 6) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter allowed apps...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        if (filteredApps.isEmpty()) {
          Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text("No custom allowed apps", style = MaterialTheme.typography.titleMedium)
              Text(
                text =
                  "Only core system apps (Phone, Emergency, Launcher, Keyboard) are active right now. Add exceptions in the Allowlist tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(filteredApps, key = { it.packageName }) { appInfo ->
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier =
                  Modifier.clip(MaterialTheme.shapes.medium)
                    .clickable { onLaunchPackage(appInfo.packageName) }
                    .testTag("drawer_app_${appInfo.packageName}"),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  AppIconThumbnail(
                    packageName = appInfo.packageName,
                    contentDescription = appInfo.label,
                    size = 48.dp,
                  )
                  Text(
                    text = appInfo.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
