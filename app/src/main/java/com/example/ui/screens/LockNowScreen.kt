package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.formatMinutesAsTime
import com.example.domain.formatRemainingMillis
import com.example.ui.VaultLockUiState
import com.example.ui.components.HoldToActionButton
import com.example.ui.components.StatusPill
import com.example.ui.drawer.AllowedLauncherActivity
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val DURATION_PRESETS = listOf(15, 30, 45, 60, 120, 240, 480)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockNowScreen(
  uiState: VaultLockUiState,
  onDurationChange: (Int) -> Unit,
  onStartLockNow: () -> Unit,
  onCancelLockNow: () -> Unit,
  onOpenSetup: () -> Unit,
  onOpenAllowlist: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val activeState = uiState.activeLockState
  val isLocked = activeState.isAnyLockActive
  val primaryColor = MaterialTheme.colorScheme.primary
  val crimsonColor = MaterialTheme.colorScheme.error
  val amberColor = MaterialTheme.colorScheme.tertiary

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "VaultLock",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "True hardware screen-lock focus guard",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          text =
            when {
              activeState.isLockNowActive -> "LOCKED"
              activeState.activeSchedule != null -> "SCHEDULED"
              uiState.systemStatus.canEnforceLock -> "ARMED"
              else -> "SETUP"
            },
          containerColor =
            when {
              activeState.isLockNowActive -> MaterialTheme.colorScheme.errorContainer
              activeState.activeSchedule != null -> MaterialTheme.colorScheme.tertiaryContainer
              uiState.systemStatus.canEnforceLock -> MaterialTheme.colorScheme.secondaryContainer
              else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
          contentColor =
            when {
              activeState.isLockNowActive -> MaterialTheme.colorScheme.onErrorContainer
              activeState.activeSchedule != null -> MaterialTheme.colorScheme.onTertiaryContainer
              uiState.systemStatus.canEnforceLock -> MaterialTheme.colorScheme.onSecondaryContainer
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
          dotColor =
            when {
              activeState.isLockNowActive -> crimsonColor
              activeState.activeSchedule != null -> amberColor
              uiState.systemStatus.canEnforceLock -> MaterialTheme.colorScheme.secondary
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }

      // Setup needed warning
      AnimatedVisibility(visible = !uiState.systemStatus.canEnforceLock) {
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
            ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Warning,
              contentDescription = "Setup required",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Accessibility Service Required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Text(
                text = "Enable VaultLock in Accessibility Settings to detect blocked apps and lock the screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
              )
            }
            TextButton(
              onClick = onOpenSetup,
              modifier = Modifier.testTag("go_to_setup_button"),
            ) {
              Text("Setup", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Swipe / No PIN warning
      AnimatedVisibility(visible = !uiState.systemStatus.isDeviceLockSecure) {
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
            ),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = "Screen lock warning",
              tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
              text =
                "No PIN, pattern, or fingerprint detected. Set a secure screen lock in Android Settings so locking truly blocks access.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Hero Active / Ready Card
      val glowColor =
        when {
          activeState.isLockNowActive -> crimsonColor
          activeState.activeSchedule != null -> amberColor
          else -> primaryColor
        }

      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, glowColor.copy(alpha = 0.35f)),
        modifier =
          Modifier.fillMaxWidth().drawBehind {
            drawRect(
              brush =
                Brush.radialGradient(
                  colors = listOf(glowColor.copy(alpha = 0.16f), Color.Transparent),
                  center = Offset(size.width * 0.85f, size.height * 0.15f),
                  radius = size.maxDimension * 0.9f,
                )
            )
          },
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (activeState.isLockNowActive) {
            val remaining = (activeState.lockNowEndTimeMillis - uiState.nowMillis).coerceAtLeast(0L)
            val endTimeText =
              DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(Date(activeState.lockNowEndTimeMillis))

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Lock active",
                tint = crimsonColor,
              )
              Text(
                text = "LOCK NOW SESSION ACTIVE",
                style = MaterialTheme.typography.labelLarge,
                color = crimsonColor,
              )
            }

            Text(
              text = formatRemainingMillis(remaining),
              style = MaterialTheme.typography.displayLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("active_countdown_text"),
            )

            Text(
              text =
                "Unlocks at $endTimeText • ${uiState.lockNowAllowedPackages.size} allowed apps (+ core system)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  context.startActivity(Intent(context, AllowedLauncherActivity::class.java))
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("open_drawer_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Allowed Drawer")
              }

              OutlinedButton(
                onClick = onStartLockNow,
                modifier = Modifier.height(48.dp).testTag("relock_now_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lock Screen")
              }
            }

            HoldToActionButton(
              idleText = "Hold 2.5s for Emergency Stop",
              holdingText = "Keep holding to end session...",
              onCompleted = onCancelLockNow,
              modifier = Modifier.testTag("hold_cancel_lock_button"),
            )
          } else if (activeState.activeSchedule != null) {
            val sched = activeState.activeSchedule
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = "Schedule active",
                tint = amberColor,
              )
              Text(
                text = "SCHEDULED LOCK ACTIVE",
                style = MaterialTheme.typography.labelLarge,
                color = amberColor,
              )
            }
            Text(
              text = sched.label.ifBlank { "Scheduled Focus Window" },
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                "Active until ${formatMinutesAsTime(sched.endMinutes)} • Launch allowed apps via the drawer or notification.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = {
                context.startActivity(Intent(context, AllowedLauncherActivity::class.java))
              },
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Open Allowed Apps Drawer")
            }
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector =
                  if (uiState.systemStatus.canEnforceLock) Icons.Filled.CheckCircle
                  else Icons.Filled.LockOpen,
                contentDescription = null,
                tint =
                  if (uiState.systemStatus.canEnforceLock) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text =
                  if (uiState.systemStatus.canEnforceLock) "READY TO ENFORCE"
                  else "SETUP INCOMPLETE",
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (uiState.systemStatus.canEnforceLock) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Text(
              text = formatDurationHuman(uiState.selectedDurationMinutes),
              style = MaterialTheme.typography.displayLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("selected_duration_text"),
            )

            Text(
              text =
                "Locks the phone immediately. Any attempt to open a non-allowed app sends you Home and re-locks the screen.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Duration selector (shown when Lock Now isn't running)
      if (!activeState.isLockNowActive) {
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Lock Duration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = { onDurationChange(uiState.selectedDurationMinutes - 5) },
                  modifier = Modifier.testTag("decrease_duration_button"),
                ) {
                  Icon(Icons.Filled.Remove, contentDescription = "Decrease 5 minutes")
                }
                Text(
                  text = "${uiState.selectedDurationMinutes}m",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                  onClick = { onDurationChange(uiState.selectedDurationMinutes + 5) },
                  modifier = Modifier.testTag("increase_duration_button"),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = "Increase 5 minutes")
                }
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              for (preset in DURATION_PRESETS) {
                val selected = uiState.selectedDurationMinutes == preset
                FilterChip(
                  selected = selected,
                  onClick = { onDurationChange(preset) },
                  label = {
                    Text(
                      text = formatPresetChip(preset),
                      style = MaterialTheme.typography.labelMedium,
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
              }
            }

            Slider(
              value = uiState.selectedDurationMinutes.toFloat().coerceIn(5f, 480f),
              onValueChange = { onDurationChange((it / 5f).roundToInt() * 5) },
              valueRange = 5f..480f,
              colors =
                SliderDefaults.colors(
                  thumbColor = MaterialTheme.colorScheme.primary,
                  activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Button(
              onClick = onStartLockNow,
              modifier =
                Modifier.fillMaxWidth().height(56.dp).testTag("start_lock_now_button"),
              shape = RoundedCornerShape(16.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
              Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(10.dp))
              Text(
                text = "Lock Phone Now",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      // Quick overview card for allowlist & notification access
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            ) {
              Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
              Column {
                Text(
                  text = "Quick Drawer Notification",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text =
                    "While locked, tap the persistent notification to open allowed apps directly.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onOpenAllowlist,
              modifier = Modifier.weight(1f).height(48.dp),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Edit Allowed (${uiState.lockNowAllowedPackages.size})")
            }
            OutlinedButton(
              onClick = {
                context.startActivity(Intent(context, AllowedLauncherActivity::class.java))
              },
              modifier = Modifier.weight(1f).height(48.dp),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Test Drawer")
            }
          }
        }
      }

      if (isLocked && uiState.schedules.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
      }
    }
  }
}

private fun formatPresetChip(minutes: Int): String =
  if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"

private fun formatDurationHuman(minutes: Int): String {
  val h = minutes / 60
  val m = minutes % 60
  return when {
    h > 0 && m > 0 -> "${h}h ${m}m"
    h > 0 -> "${h}h 00m"
    else -> "${m}m"
  }
}
