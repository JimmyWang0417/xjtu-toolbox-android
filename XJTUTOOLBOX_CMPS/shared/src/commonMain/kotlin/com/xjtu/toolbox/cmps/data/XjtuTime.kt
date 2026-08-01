package com.xjtu.toolbox.cmps.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object XjtuTime {
    data class TimePoint(val hour: Int, val minute: Int) {
        override fun toString(): String =
            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    data class ClassTime(
        val start: TimePoint,
        val end: TimePoint,
        val attendanceStart: TimePoint,
        val attendanceEnd: TimePoint,
    )

    fun isSummerTime(
        month: Int = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).monthNumber,
    ): Boolean = month in 5..9

    private fun t(h: Int, m: Int) = TimePoint(h, m)

    private val winterSchedule = mapOf(
        1 to ClassTime(t(8, 0), t(8, 50), t(7, 20), t(8, 5)),
        2 to ClassTime(t(9, 0), t(9, 50), t(8, 20), t(9, 5)),
        3 to ClassTime(t(10, 10), t(11, 0), t(9, 35), t(10, 15)),
        4 to ClassTime(t(11, 10), t(12, 0), t(10, 35), t(11, 15)),
        5 to ClassTime(t(14, 0), t(14, 50), t(13, 20), t(14, 5)),
        6 to ClassTime(t(15, 0), t(15, 50), t(14, 20), t(15, 5)),
        7 to ClassTime(t(16, 10), t(17, 0), t(15, 35), t(16, 15)),
        8 to ClassTime(t(17, 10), t(18, 0), t(16, 35), t(17, 15)),
        9 to ClassTime(t(19, 10), t(20, 0), t(18, 30), t(19, 15)),
        10 to ClassTime(t(20, 10), t(21, 0), t(19, 35), t(20, 15)),
        11 to ClassTime(t(21, 10), t(22, 0), t(20, 35), t(21, 15)),
    )

    private val summerSchedule = mapOf(
        1 to ClassTime(t(8, 0), t(8, 50), t(7, 20), t(8, 5)),
        2 to ClassTime(t(9, 0), t(9, 50), t(8, 20), t(9, 5)),
        3 to ClassTime(t(10, 10), t(11, 0), t(9, 35), t(10, 15)),
        4 to ClassTime(t(11, 10), t(12, 0), t(10, 35), t(11, 15)),
        5 to ClassTime(t(14, 30), t(15, 20), t(13, 50), t(14, 35)),
        6 to ClassTime(t(15, 30), t(16, 20), t(14, 50), t(15, 35)),
        7 to ClassTime(t(16, 40), t(17, 30), t(16, 5), t(16, 45)),
        8 to ClassTime(t(17, 40), t(18, 30), t(17, 5), t(17, 45)),
        9 to ClassTime(t(19, 40), t(20, 30), t(19, 0), t(19, 45)),
        10 to ClassTime(t(20, 40), t(21, 30), t(20, 5), t(20, 45)),
        11 to ClassTime(t(21, 40), t(22, 30), t(21, 5), t(21, 45)),
    )

    fun getClassTime(section: Int, summer: Boolean = isSummerTime()): ClassTime? =
        if (summer) summerSchedule[section] else winterSchedule[section]

    fun getTimeRangeStr(startSection: Int, endSection: Int, summer: Boolean = isSummerTime()): String {
        val start = getClassTime(startSection, summer)?.start?.toString() ?: "--:--"
        val end = getClassTime(endSection, summer)?.end?.toString() ?: "--:--"
        return "$start-$end"
    }

    fun getAllSlots(summer: Boolean = isSummerTime()): List<ClassTimeSlot> {
        val schedule = if (summer) summerSchedule else winterSchedule
        return schedule.entries.sortedBy { it.key }.map { (section, time) ->
            ClassTimeSlot(
                section = section,
                start = time.start.toString(),
                end = time.end.toString(),
                attendanceStart = time.attendanceStart.toString(),
                attendanceEnd = time.attendanceEnd.toString(),
            )
        }
    }
}
