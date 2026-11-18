package com.example.domain

import com.example.data.AllowlistScope
import com.example.data.LockSessionEntity
import com.example.data.ScheduleEntity
import java.util.Calendar

data class ActiveLockState(
  val isLockNowActive: Boolean = false,
  val lockNowEndTimeMillis: Long = 0L,
  val activeSchedule: ScheduleEntity? = null,
  val effectiveAllowlistScope: AllowlistScope = AllowlistScope.LOCK_NOW,
) {
  val isAnyLockActive: Boolean
    get() = isLockNowActive || activeSchedule != null
}

object LockEvaluator {

  fun isScheduleActiveAt(schedule: ScheduleEntity, calendar: Calendar): Boolean {
    if (!schedule.enabled || schedule.daysMask == 0) return false

    val today = Weekday.fromCalendarDay(calendar.get(Calendar.DAY_OF_WEEK))
    val yesterday = Weekday.previousDay(today)
    val currentMinutes =
      calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    val start = schedule.startMinutes
    val end = schedule.endMinutes

    return when {
      start == end -> Weekday.isDaySelected(schedule.daysMask, today)
      start < end -> {
        Weekday.isDaySelected(schedule.daysMask, today) &&
          currentMinutes >= start &&
          currentMinutes < end
      }
      else -> {
        // Window crosses midnight (e.g., 22:00 -> 07:00)
        val activeFromTodayStart =
          Weekday.isDaySelected(schedule.daysMask, today) && currentMinutes >= start
        val activeFromYesterdayWrap =
          Weekday.isDaySelected(schedule.daysMask, yesterday) && currentMinutes < end
        activeFromTodayStart || activeFromYesterdayWrap
      }
    }
  }

  fun evaluateActiveLock(
    lockSession: LockSessionEntity?,
    schedules: List<ScheduleEntity>,
    nowMillis: Long,
    separateAllowlists: Boolean,
  ): ActiveLockState {
    val isLockNow = lockSession != null && lockSession.endTimeMillis > nowMillis
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val activeSchedule = schedules.firstOrNull { isScheduleActiveAt(it, calendar) }

    val effectiveScope =
      when {
        isLockNow -> AllowlistScope.LOCK_NOW
        activeSchedule != null && separateAllowlists -> AllowlistScope.SCHEDULED
        else -> AllowlistScope.LOCK_NOW
      }

    return ActiveLockState(
      isLockNowActive = isLockNow,
      lockNowEndTimeMillis = if (isLockNow) lockSession!!.endTimeMillis else 0L,
      activeSchedule = activeSchedule,
      effectiveAllowlistScope = effectiveScope,
    )
  }

  fun computeNextStartTimeMillis(schedule: ScheduleEntity, nowMillis: Long): Long? {
    if (!schedule.enabled || schedule.daysMask == 0) return null
    val startHour = schedule.startMinutes / 60
    val startMin = schedule.startMinutes % 60

    val probe =
      Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.HOUR_OF_DAY, startHour)
        set(Calendar.MINUTE, startMin)
      }

    for (dayOffset in 0..7) {
      val candidate =
        (probe.clone() as Calendar).apply {
          if (dayOffset > 0) add(Calendar.DAY_OF_YEAR, dayOffset)
        }
      val weekday = Weekday.fromCalendarDay(candidate.get(Calendar.DAY_OF_WEEK))
      if (
        Weekday.isDaySelected(schedule.daysMask, weekday) &&
          candidate.timeInMillis > nowMillis + 1_000L
      ) {
        return candidate.timeInMillis
      }
    }
    return null
  }
}
