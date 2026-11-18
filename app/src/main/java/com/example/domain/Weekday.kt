package com.example.domain

import java.util.Calendar
import java.util.Locale

enum class Weekday(
  val bit: Int,
  val calendarDay: Int,
  val singleChar: String,
  val shortName: String,
) {
  MONDAY(1 shl 0, Calendar.MONDAY, "M", "Mon"),
  TUESDAY(1 shl 1, Calendar.TUESDAY, "T", "Tue"),
  WEDNESDAY(1 shl 2, Calendar.WEDNESDAY, "W", "Wed"),
  THURSDAY(1 shl 3, Calendar.THURSDAY, "T", "Thu"),
  FRIDAY(1 shl 4, Calendar.FRIDAY, "F", "Fri"),
  SATURDAY(1 shl 5, Calendar.SATURDAY, "S", "Sat"),
  SUNDAY(1 shl 6, Calendar.SUNDAY, "S", "Sun");

  companion object {
    const val ALL_DAYS_MASK: Int = (1 shl 7) - 1
    const val WEEKDAYS_MASK: Int = (1 shl 0) or (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4)
    const val WEEKENDS_MASK: Int = (1 shl 5) or (1 shl 6)

    fun fromCalendarDay(calendarDay: Int): Weekday =
      entries.firstOrNull { it.calendarDay == calendarDay } ?: MONDAY

    fun previousDay(weekday: Weekday): Weekday {
      val idx = (weekday.ordinal - 1 + entries.size) % entries.size
      return entries[idx]
    }

    fun isDaySelected(mask: Int, weekday: Weekday): Boolean = (mask and weekday.bit) != 0

    fun toggleDay(mask: Int, weekday: Weekday): Int = mask xor weekday.bit

    fun formatDaysSummary(mask: Int): String {
      return when (mask) {
        ALL_DAYS_MASK -> "Every day"
        WEEKDAYS_MASK -> "Weekdays"
        WEEKENDS_MASK -> "Weekends"
        0 -> "No days"
        else -> entries.filter { (mask and it.bit) != 0 }.joinToString(", ") { it.shortName }
      }
    }
  }
}

fun formatMinutesAsTime(totalMinutes: Int): String {
  val clamped = ((totalMinutes % 1440) + 1440) % 1440
  val hour24 = clamped / 60
  val minute = clamped % 60
  val amPm = if (hour24 < 12) "AM" else "PM"
  val hour12 =
    when {
      hour24 == 0 -> 12
      hour24 > 12 -> hour24 - 12
      else -> hour24
    }
  return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
}

fun formatRemainingMillis(remainingMillis: Long): String {
  if (remainingMillis <= 0L) return "00:00"
  val totalSeconds = (remainingMillis + 999L) / 1000L
  val hours = totalSeconds / 3600L
  val minutes = (totalSeconds % 3600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) {
    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.US, "%02d:%02d", minutes, seconds)
  }
}
