package com.xjtu.toolbox.util

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 西安交通大学作息时间表
 * 冬季（10月-次年4月）和夏季（5月-9月）时间不同
 * 移植自 XJTUToolBox 的 xjtu_time.py
 */
object XjtuTime {

    /** 时间点（时:分） */
    data class TimePoint(val hour: Int, val minute: Int) {
        override fun toString(): String =
            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    data class ClassTime(
        val start: TimePoint,
        val end: TimePoint,
        val attendanceStart: TimePoint,
        val attendanceEnd: TimePoint
    )

    /** 判断当前是否为夏季时间（5-9月） */
    fun isSummerTime(month: Int = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).monthNumber): Boolean =
        month in 5..9

    private fun t(h: Int, m: Int) = TimePoint(h, m)

    /** 冬季课表（10月-4月），每节50分钟 */
    private val WINTER_SCHEDULE = mapOf(
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
        11 to ClassTime(t(21, 10), t(22, 0), t(20, 35), t(21, 15))
    )

    /** 夏季课表（5月-9月）：1-4节与冬季相同，5-11节各推迟30分钟 */
    private val SUMMER_SCHEDULE = mapOf(
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
        11 to ClassTime(t(21, 40), t(22, 30), t(21, 5), t(21, 45))
    )

    /** 获取指定节次的上课时间 */
    fun getClassTime(section: Int, summer: Boolean = isSummerTime()): ClassTime? =
        if (summer) SUMMER_SCHEDULE[section] else WINTER_SCHEDULE[section]

    /** 获取上课开始时间字符串 (如 "08:00") */
    fun getClassStartStr(section: Int, summer: Boolean = isSummerTime()): String =
        getClassTime(section, summer)?.start?.toString() ?: "--:--"

    /** 获取上课结束时间字符串 (如 "08:50") */
    fun getClassEndStr(section: Int, summer: Boolean = isSummerTime()): String =
        getClassTime(section, summer)?.end?.toString() ?: "--:--"

    /** 获取节次时间范围字符串 (如 "08:00-09:50") */
    fun getTimeRangeStr(startSection: Int, endSection: Int, summer: Boolean = isSummerTime()): String {
        val start = getClassTime(startSection, summer)?.start?.toString() ?: "--:--"
        val end = getClassTime(endSection, summer)?.end?.toString() ?: "--:--"
        return "$start-$end"
    }

    /** 全天时间表（用于 UI 侧栏显示） */
    fun getAllTimes(summer: Boolean = isSummerTime()): List<Pair<Int, ClassTime>> {
        val schedule = if (summer) SUMMER_SCHEDULE else WINTER_SCHEDULE
        return schedule.entries.sortedBy { it.key }.map { it.key to it.value }
    }
}
