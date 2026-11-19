package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ScheduleEntity
import com.example.domain.Weekday
import com.example.domain.formatMinutesAsTime
import com.example.ui.VaultLockUiState
import com.example.ui.components.StatusPill

@Composable
fun SchedulesScreen(
  uiState: VaultLockUiState,
  onSaveSchedule: (ScheduleEntity) -> Unit,
  onToggleSchedule: (Long, Boolean) -> Unit,
  onDeleteSchedule: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingSchedule by remember { mutableStateOf<ScheduleEntity?>(null) }
  var showEditor by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingSchedule =
            ScheduleEntity(
              label = "Sleep Vault",
              daysMask = Weekday.ALL_DAYS_MASK,
              startMinutes = 22 * 60,
              endMinutes = 7 * 60,
              enabled = true,
              lockAtStart = true,
              reminderMinutesBefore = 10,
            )
          showEditor = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_schedule_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add schedule")
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Schedules",
              style = MaterialTheme.typography.displaySmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text =
                "Recurring windows evaluated live by the Accessibility Service. Overnight windows (e.g. 10 PM – 7 AM) wrap cleanly across midnight.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (uiState.schedules.isEmpty()) {
          item {
            Surface(
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Schedule,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(44.dp),
                )
                Text(
                  text = "No schedules yet",
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  text =
                    "Tap + to add a daily bedtime lock, deep work block, or weekend focus window.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(uiState.schedules, key = { it.id }) { schedule ->
            val isActiveNow = uiState.activeLockState.activeSchedule?.id == schedule.id
            ScheduleCard(
              schedule = schedule,
              isActiveNow = isActiveNow,
              onClick = {
                editingSchedule = schedule
                showEditor = true
              },
              onToggle = { enabled -> onToggleSchedule(schedule.id, enabled) },
              onDelete = { onDeleteSchedule(schedule.id) },
            )
          }
        }

        item { Spacer(Modifier.height(72.dp)) }
      }
    }
  }

  if (showEditor && editingSchedule != null) {
    ScheduleEditorDialog(
      initial = editingSchedule!!,
      notificationsGranted = uiState.systemStatus.isNotificationsGranted,
      onDismiss = { showEditor = false },
      onSave = { updated ->
        onSaveSchedule(updated)
        showEditor = false
      },
    )
  }
}

@Composable
private fun ScheduleCard(
  schedule: ScheduleEntity,
  isActiveNow: Boolean,
  onClick: () -> Unit,
  onToggle: (Boolean) -> Unit,
  onDelete: () -> Unit,
) {
  val borderColor =
    if (isActiveNow) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.outlineVariant

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, borderColor),
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("schedule_card_${schedule.id}"),
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
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text(
            text = schedule.label.ifBlank { "Focus Schedule" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (isActiveNow) {
            StatusPill(
              text = "ACTIVE",
              containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
              dotColor = MaterialTheme.colorScheme.tertiary,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDelete) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete schedule",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = schedule.enabled,
            onCheckedChange = onToggle,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text =
            "${formatMinutesAsTime(schedule.startMinutes)} → ${formatMinutesAsTime(schedule.endMinutes)}",
          style = MaterialTheme.typography.titleLarge,
          color =
            if (schedule.enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (schedule.crossesMidnight) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.NightsStay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "Next day",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      // 7-day pill row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (day in Weekday.entries) {
          val selected = Weekday.isDaySelected(schedule.daysMask, day)
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(30.dp)
                .clip(CircleShape)
                .background(
                  if (selected && schedule.enabled) MaterialTheme.colorScheme.primary
                  else if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                  else MaterialTheme.colorScheme.surface
                ),
          ) {
            Text(
              text = day.singleChar,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color =
                if (selected && schedule.enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (schedule.reminderMinutesBefore > 0) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Reminder ${formatReminderLabel(schedule.reminderMinutesBefore)} before lock",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorDialog(
  initial: ScheduleEntity,
  notificationsGranted: Boolean,
  onDismiss: () -> Unit,
  onSave: (ScheduleEntity) -> Unit,
) {
  var label by remember { mutableStateOf(initial.label) }
  var daysMask by remember { mutableIntStateOf(initial.daysMask) }
  var startMinutes by remember { mutableIntStateOf(initial.startMinutes) }
  var endMinutes by remember { mutableIntStateOf(initial.endMinutes) }
  var lockAtStart by remember { mutableStateOf(initial.lockAtStart) }
  var reminderMinutesBefore by remember { mutableIntStateOf(initial.reminderMinutesBefore.coerceIn(0, 120)) }
  var pickingStart by remember { mutableStateOf<Boolean?>(null) }

  val reminderPresets = listOf(0, 5, 10, 15, 30, 60)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = if (initial.id == 0L) "New Lock Schedule" else "Edit Lock Schedule",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("Schedule Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("schedule_label_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { pickingStart = true },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Start", style = MaterialTheme.typography.labelSmall)
              Text(formatMinutesAsTime(startMinutes), style = MaterialTheme.typography.titleMedium)
            }
          }
          OutlinedButton(
            onClick = { pickingStart = false },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("End", style = MaterialTheme.typography.labelSmall)
              Text(formatMinutesAsTime(endMinutes), style = MaterialTheme.typography.titleMedium)
            }
          }
        }

        if (startMinutes > endMinutes) {
          Text(
            text = "Crosses midnight: active until ${formatMinutesAsTime(endMinutes)} the following morning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            TextButton(onClick = { daysMask = Weekday.ALL_DAYS_MASK }) { Text("Every day") }
            TextButton(onClick = { daysMask = Weekday.WEEKDAYS_MASK }) { Text("Weekdays") }
            TextButton(onClick = { daysMask = Weekday.WEEKENDS_MASK }) { Text("Weekends") }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            for (day in Weekday.entries) {
              val selected = Weekday.isDaySelected(daysMask, day)
              FilterChip(
                selected = selected,
                onClick = { daysMask = Weekday.toggleDay(daysMask, day) },
                label = { Text(day.shortName) },
              )
            }
          }
        }

        // Pre-Lockdown Reminder configuration
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(18.dp),
                )
                Text("Pre-Lock Reminder", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text =
                  if (reminderMinutesBefore == 0) "Off"
                  else "${formatReminderLabel(reminderMinutesBefore)} before",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("reminder_value_text"),
              )
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              for (preset in reminderPresets) {
                FilterChip(
                  selected = reminderMinutesBefore == preset,
                  onClick = { reminderMinutesBefore = preset },
                  label = { Text(if (preset == 0) "Off" else formatReminderLabel(preset)) },
                  modifier = Modifier.testTag("reminder_preset_$preset"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              IconButton(
                onClick = { reminderMinutesBefore = (reminderMinutesBefore - 1).coerceAtLeast(0) },
                modifier = Modifier.testTag("reminder_decrease_button"),
              ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease reminder by 1 minute")
              }
              Slider(
                value = reminderMinutesBefore.toFloat(),
                onValueChange = { reminderMinutesBefore = it.roundToInt().coerceIn(0, 120) },
                valueRange = 0f..120f,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = { reminderMinutesBefore = (reminderMinutesBefore + 1).coerceAtMost(120) },
                modifier = Modifier.testTag("reminder_increase_button"),
              ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase reminder by 1 minute")
              }
            }

            if (reminderMinutesBefore > 0 && !notificationsGranted) {
              Text(
                text = "Notifications are currently disabled — grant permission in Setup to receive alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Lock right at start time", style = MaterialTheme.typography.bodyLarge)
            Text(
              "Uses AlarmManager to lock immediately even if screen is already open.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(checked = lockAtStart, onCheckedChange = { lockAtStart = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            initial.copy(
              label = label.trim().ifBlank { "Focus Schedule" },
              daysMask = if (daysMask == 0) Weekday.ALL_DAYS_MASK else daysMask,
              startMinutes = startMinutes,
              endMinutes = endMinutes,
              lockAtStart = lockAtStart,
              reminderMinutesBefore = reminderMinutesBefore.coerceIn(0, 120),
            )
          )
        },
        modifier = Modifier.testTag("save_schedule_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )

  val isPickingStart = pickingStart
  if (isPickingStart != null) {
    val initialMin = if (isPickingStart) startMinutes else endMinutes
    val timeState =
      rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = false,
      )
    AlertDialog(
      onDismissRequest = { pickingStart = null },
      title = { Text(if (isPickingStart) "Select Start Time" else "Select End Time") },
      text = { TimePicker(state = timeState) },
      confirmButton = {
        Button(
          onClick = {
            val total = timeState.hour * 60 + timeState.minute
            if (isPickingStart) startMinutes = total else endMinutes = total
            pickingStart = null
          }
        ) {
          Text("OK")
        }
      },
      dismissButton = {
        TextButton(onClick = { pickingStart = null }) { Text("Cancel") }
      },
    )
  }
}

private fun formatReminderLabel(minutes: Int): String {
  val hrs = minutes / 60
  val mins = minutes % 60
  return when {
    hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
    hrs > 0 -> "${hrs}h"
    else -> "${mins}m"
  }
}
