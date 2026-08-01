package com.xjtu.toolbox.schedule

import com.xjtu.toolbox.util.FileSystem
import com.xjtu.toolbox.util.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

private const val TAG = "CustomCourseStore"

actual class CustomCourseStore(private val storagePath: String) {

    private val filePath = "$storagePath/custom_courses.json"
    private var nextId: Long = 1L

    init {
        FileSystem.mkdirs(storagePath)
        // 初始化 nextId
        val all = readAll()
        if (all.isNotEmpty()) {
            nextId = all.maxOf { it.id } + 1
        }
    }

    actual suspend fun getByTerm(termCode: String): List<CustomCourseEntity> {
        return readAll()
            .filter { it.termCode == termCode }
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
    }

    actual suspend fun insert(course: CustomCourseEntity): Long {
        val all = readAll().toMutableList()
        val id = nextId++
        all.add(course.copy(id = id))
        writeAll(all)
        return id
    }

    actual suspend fun update(course: CustomCourseEntity) {
        val all = readAll().toMutableList()
        val idx = all.indexOfFirst { it.id == course.id }
        if (idx >= 0) {
            all[idx] = course
            writeAll(all)
        }
    }

    actual suspend fun delete(course: CustomCourseEntity) {
        val all = readAll().toMutableList()
        all.removeAll { it.id == course.id }
        writeAll(all)
    }

    actual suspend fun deleteByTerm(termCode: String) {
        val all = readAll().toMutableList()
        all.removeAll { it.termCode == termCode }
        writeAll(all)
    }

    actual suspend fun getAll(): List<CustomCourseEntity> {
        return readAll().sortedWith(
            compareByDescending<CustomCourseEntity> { it.termCode }
                .thenBy { it.dayOfWeek }
                .thenBy { it.startSection }
        )
    }

    private fun readAll(): List<CustomCourseEntity> {
        return try {
            val json = FileSystem.readText(filePath) ?: return emptyList()
            val arr = Json.parseToJsonElement(json).jsonArray
            arr.mapNotNull { elem ->
                try {
                    val obj = elem.jsonObject
                    CustomCourseEntity(
                        id = obj["id"]!!.jsonPrimitive.long,
                        courseName = obj["courseName"]!!.jsonPrimitive.content,
                        teacher = obj["teacher"]?.jsonPrimitive?.content ?: "",
                        location = obj["location"]?.jsonPrimitive?.content ?: "",
                        weekBits = obj["weekBits"]!!.jsonPrimitive.content,
                        dayOfWeek = obj["dayOfWeek"]!!.jsonPrimitive.content.toInt(),
                        startSection = obj["startSection"]!!.jsonPrimitive.content.toInt(),
                        endSection = obj["endSection"]!!.jsonPrimitive.content.toInt(),
                        termCode = obj["termCode"]!!.jsonPrimitive.content,
                        note = obj["note"]?.jsonPrimitive?.content ?: "",
                        createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: 0L
                    )
                } catch (e: Exception) {
                    Logger.w(TAG, "readAll: skip bad entry: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "readAll failed: ${e.message}")
            emptyList()
        }
    }

    private fun writeAll(courses: List<CustomCourseEntity>) {
        try {
            val arr = buildJsonArray {
                courses.forEach { c ->
                    add(buildJsonObject {
                        put("id", c.id)
                        put("courseName", c.courseName)
                        put("teacher", c.teacher)
                        put("location", c.location)
                        put("weekBits", c.weekBits)
                        put("dayOfWeek", c.dayOfWeek)
                        put("startSection", c.startSection)
                        put("endSection", c.endSection)
                        put("termCode", c.termCode)
                        put("note", c.note)
                        put("createdAt", c.createdAt)
                    })
                }
            }
            FileSystem.writeText(filePath, arr.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "writeAll failed", e)
        }
    }
}
