package com.xjtu.toolbox.schedule

import com.xjtu.toolbox.util.XjtuTime
import kotlinx.datetime.LocalDate

/**
 * 课表导出工具（CMP 版本）
 * 纯文本生成逻辑：ICS 日历、CSV 表格
 * 平台特定的文件分享和图片渲染由各平台自行实现
 */
object ScheduleExport {

    // ════════════════════════════════════════════
    //  ICS 日历文件导出
    // ════════════════════════════════════════════

    /**
     * 生成 ICS 日历内容
     * @param courses 课程列表
     * @param startOfTerm 学期第一周的周一日期 (yyyy-MM-dd)
     * @param termName 学期名称（用于日历名）
     */
    fun generateIcs(courses: List<CourseItem>, startOfTerm: String, termName: String): String {
        val startDate = LocalDate.parse(startOfTerm)
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//XJTUToolBox//Schedule//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("X-WR-CALNAME:$termName 课表")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")

        // 嵌入时区定义
        sb.appendLine("BEGIN:VTIMEZONE")
        sb.appendLine("TZID:Asia/Shanghai")
        sb.appendLine("BEGIN:STANDARD")
        sb.appendLine("DTSTART:19700101T000000")
        sb.appendLine("TZOFFSETFROM:+0800")
        sb.appendLine("TZOFFSETTO:+0800")
        sb.appendLine("END:STANDARD")
        sb.appendLine("END:VTIMEZONE")

        for (course in courses) {
            val weeks = course.getWeeks()
            if (weeks.isEmpty()) continue

            for (week in weeks) {
                // 计算该周该天的具体日期
                val daysOffset = (week - 1) * 7 + (course.dayOfWeek - 1)
                val courseDate = LocalDate.fromEpochDays(startDate.toEpochDays() + daysOffset)

                val startTime = sectionToTime(course.startSection, isStart = true, courseDate.monthNumber)
                val endTime = sectionToTime(course.endSection, isStart = false, courseDate.monthNumber)

                val dtStart = formatIcsDateTime(courseDate, startTime.first, startTime.second)
                val dtEnd = formatIcsDateTime(courseDate, endTime.first, endTime.second)

                val uid = "${courseDate}-${course.courseCode}-${course.startSection}@xjtu-toolbox"

                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:$uid")
                sb.appendLine("DTSTART;TZID=Asia/Shanghai:$dtStart")
                sb.appendLine("DTEND;TZID=Asia/Shanghai:$dtEnd")
                sb.appendLine("SUMMARY:${escapeIcs(course.courseName)}")
                sb.appendLine("LOCATION:${escapeIcs(course.location)}")
                val desc = buildString {
                    append("教师: ${course.teacher}")
                    if (course.courseType.isNotEmpty()) append("\\n类型: ${course.courseType}")
                    append("\\n节次: 第${course.startSection}-${course.endSection}节")
                    append("\\n周次: 第${week}周")
                }
                sb.appendLine("DESCRIPTION:$desc")
                sb.appendLine("END:VEVENT")
            }
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun sectionToTime(section: Int, isStart: Boolean, month: Int): Pair<Int, Int> {
        val isSummer = XjtuTime.isSummerTime(month)
        val ct = XjtuTime.getClassTime(section, isSummer)
        return if (ct != null) {
            val t = if (isStart) ct.start else ct.end
            t.hour to t.minute
        } else {
            if (isStart) 8 to 0 else 8 to 50
        }
    }

    private fun formatIcsDateTime(date: LocalDate, hour: Int, minute: Int): String {
        return "${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}${minute.toString().padStart(2, '0')}00"
    }

    private fun escapeIcs(text: String): String =
        text.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    // ════════════════════════════════════════════
    //  CSV 表格导出
    // ════════════════════════════════════════════

    fun generateCsv(courses: List<CourseItem>): String {
        val sb = StringBuilder()
        sb.appendLine("课程名称,教师,教室,星期,开始节次,结束节次,上课周次,课程类型,课程代码")
        for (c in courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))) {
            val dayName = when (c.dayOfWeek) {
                1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
                5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "?"
            }
            val weeks = formatWeeksCompact(c.getWeeks())
            sb.appendLine("${csvEscape(c.courseName)},${csvEscape(c.teacher)},${csvEscape(c.location)},${csvEscape(dayName)},${c.startSection},${c.endSection},${csvEscape(weeks)},${csvEscape(c.courseType)},${csvEscape(c.courseCode)}")
        }
        return sb.toString()
    }

    private fun csvEscape(text: String): String {
        val s = text.replace("\"", "\"\"")
        return if (s.contains(",") || s.contains("\"") || s.contains("\n")) "\"$s\"" else s
    }

    private fun formatWeeksCompact(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val ranges = mutableListOf<String>()
        var start = sorted[0]; var end = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] == end + 1) end = sorted[i]
            else { ranges.add(if (start == end) "$start" else "$start-$end"); start = sorted[i]; end = sorted[i] }
        }
        ranges.add(if (start == end) "$start" else "$start-$end")
        return ranges.joinToString(",")
    }
}
