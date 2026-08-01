package com.xjtu.toolbox.schedule

import com.xjtu.toolbox.util.currentTimeMillis

/**
 * 自定义课程数据模型（CMP 版本）
 * 存储用户手动添加的实验课、临时排课等
 * 持久化由平台 expect/actual 实现
 */
data class CustomCourseEntity(
    val id: Long = 0,
    val courseName: String,
    val teacher: String = "",
    val location: String = "",
    /** 上课周次位图, e.g. "01111111111111111100"（第1位=第1周） */
    val weekBits: String,
    /** 星期几 1-7 (1=周一) */
    val dayOfWeek: Int,
    /** 开始节次 */
    val startSection: Int,
    /** 结束节次 */
    val endSection: Int,
    /** 所属学期代码, e.g. "2024-2025-2" */
    val termCode: String,
    /** 备注信息 */
    val note: String = "",
    /** 创建时间戳 */
    val createdAt: Long = currentTimeMillis()
) {
    /** 转为 CourseItem（与 API 课程统一展示） */
    fun toCourseItem(): CourseItem = CourseItem(
        courseName = courseName,
        teacher = teacher,
        location = location,
        weekBits = weekBits,
        dayOfWeek = dayOfWeek,
        startSection = startSection,
        endSection = endSection,
        courseCode = "custom_$id",
        courseType = "自定义"
    )
}

/**
 * 自定义课程存储接口（跨平台）
 * Android 端可用 Room 实现，其他平台可用文件/SQLDelight 实现
 */
expect class CustomCourseStore {
    suspend fun getByTerm(termCode: String): List<CustomCourseEntity>
    suspend fun insert(course: CustomCourseEntity): Long
    suspend fun update(course: CustomCourseEntity)
    suspend fun delete(course: CustomCourseEntity)
    suspend fun deleteByTerm(termCode: String)
    suspend fun getAll(): List<CustomCourseEntity>
}
