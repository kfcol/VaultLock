package com.example

import com.example.data.AllowlistScope
import com.example.data.LockSessionEntity
import com.example.data.ScheduleEntity
import com.example.domain.LockEvaluator
import com.example.domain.Weekday
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockEvaluatorTest {

  private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
    Calendar.getInstance().apply {
      set(Calendar.YEAR, year)
      set(Calendar.MONTH, month)
      set(Calendar.DAY_OF_MONTH, day)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

  @Test
  fun sameDayScheduleMatchesInsideWindowAndExcludesEndBoundary() {
    // 2026-11-14 is a Saturday
    val schedule =
      ScheduleEntity(
        id = 1,
        label = "Work",
        daysMask = Weekday.SATURDAY.bit,
        startMinutes = 9 * 60,
        endMinutes = 17 * 60,
        enabled = true,
      )

    assertFalse(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 8, 59)))
    assertTrue(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 9, 0)))
    assertTrue(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 16, 59)))
    assertFalse(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 17, 0)))
  }

  @Test
  fun midnightCrossingScheduleMatchesLateNightAndNextMorning() {
    // Enabled ONLY on Friday night 22:00 -> Saturday 07:00
    // 2026-11-13 = Friday, 2026-11-14 = Saturday
    val schedule =
      ScheduleEntity(
        id = 2,
        label = "Sleep",
        daysMask = Weekday.FRIDAY.bit,
        startMinutes = 22 * 60,
        endMinutes = 7 * 60,
        enabled = true,
      )

    assertFalse(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 13, 21, 59)))
    assertTrue(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 13, 22, 0)))
    assertTrue(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 2, 30)))
    assertTrue(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 6, 59)))
    assertFalse(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 7, 0)))
    // Saturday night 22:30 should NOT be active because only Friday bit is set
    assertFalse(LockEvaluator.isScheduleActiveAt(schedule, calendarAt(2026, Calendar.NOVEMBER, 14, 22, 30)))
  }

  @Test
  fun lockNowTakesPriorityOverScheduleAllowlist() {
    val now = calendarAt(2026, Calendar.NOVEMBER, 14, 23, 0).timeInMillis
    val session =
      LockSessionEntity(
        endTimeMillis = now + 10 * 60_000L,
        startedAtMillis = now - 5 * 60_000L,
        durationMinutes = 15,
      )
    val schedule =
      ScheduleEntity(
        id = 3,
        label = "Night",
        daysMask = Weekday.ALL_DAYS_MASK,
        startMinutes = 22 * 60,
        endMinutes = 7 * 60,
        enabled = true,
      )

    val stateWithLockNow =
      LockEvaluator.evaluateActiveLock(session, listOf(schedule), now, separateAllowlists = true)
    assertTrue(stateWithLockNow.isLockNowActive)
    assertNotNull(stateWithLockNow.activeSchedule)
    assertEquals(AllowlistScope.LOCK_NOW, stateWithLockNow.effectiveAllowlistScope)

    val stateScheduleOnly =
      LockEvaluator.evaluateActiveLock(null, listOf(schedule), now, separateAllowlists = true)
    assertFalse(stateScheduleOnly.isLockNowActive)
    assertNotNull(stateScheduleOnly.activeSchedule)
    assertEquals(AllowlistScope.SCHEDULED, stateScheduleOnly.effectiveAllowlistScope)

    val stateSharedAllowlist =
      LockEvaluator.evaluateActiveLock(null, listOf(schedule), now, separateAllowlists = false)
    assertEquals(AllowlistScope.LOCK_NOW, stateSharedAllowlist.effectiveAllowlistScope)
  }

  @Test
  fun computeNextStartTimeFindsFutureOccurrence() {
    val now = calendarAt(2026, Calendar.NOVEMBER, 14, 8, 45).timeInMillis
    val schedule =
      ScheduleEntity(
        id = 4,
        label = "Evening",
        daysMask = Weekday.SATURDAY.bit,
        startMinutes = 22 * 60,
        endMinutes = 23 * 60,
        enabled = true,
      )

    val next = LockEvaluator.computeNextStartTimeMillis(schedule, now)
    assertNotNull(next)
    val expected = calendarAt(2026, Calendar.NOVEMBER, 14, 22, 0).timeInMillis
    assertEquals(expected, next)

    val disabledNext = LockEvaluator.computeNextStartTimeMillis(schedule.copy(enabled = false), now)
    assertNull(disabledNext)
  }
}
