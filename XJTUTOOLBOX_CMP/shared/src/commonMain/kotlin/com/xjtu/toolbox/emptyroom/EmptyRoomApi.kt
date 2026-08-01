package com.xjtu.toolbox.emptyroom

import com.xjtu.toolbox.util.safeInt
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RoomInfo(
    val name: String,
    val size: Int,
    val status: List<Int>
)

val CAMPUS_BUILDINGS = mapOf(
    "兴庆校区" to listOf(
        "主楼A", "主楼B", "主楼C", "主楼D", "中2", "中3",
        "西2东", "西2西", "外文楼A", "外文楼B", "东1东", "东2",
        "仲英楼", "东1西", "教2楼", "中1", "主楼E座",
        "工程馆", "工程坊A区", "文管", "计教中心", "田家炳"
    ),
    "雁塔校区" to listOf(
        "东配楼", "微免楼", "综合楼", "教学楼", "药学楼", "解剖楼",
        "生化楼", "病理楼", "西配楼", "一附院科教楼", "二院教学楼",
        "护理楼", "卫法楼"
    ),
    "曲江校区" to listOf("西一楼", "西五楼", "西四楼", "西六楼"),
    "创新港校区" to listOf("1", "2", "3", "4", "5", "9", "18", "19", "20", "21"),
    "苏州校区" to listOf("公共学院5号楼")
)

class NoDataException(message: String) : Exception(message)

class EmptyRoomApi(private val client: HttpClient) {

    private val cdnBaseUrl = "https://gh-release.xjtutoolbox.com/"

    private var cachedDate: String? = null
    private var cachedData: JsonObject? = null

    private suspend fun fetchDayData(date: String): JsonObject {
        if (date == cachedDate && cachedData != null) return cachedData!!

        val response = client.get("${cdnBaseUrl}?file=static/empty_room/${date}.json")

        if (response.status.value == 404) {
            throw NoDataException("当天暂无空闲教室数据，请稍后再试")
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("请求失败: HTTP ${response.status.value}")
        }

        val body = response.bodyAsText()
        val json = body.safeParseJsonObject()
        cachedDate = date
        cachedData = json
        return json
    }

    suspend fun getEmptyRoomsMulti(
        campusName: String,
        buildingNames: Set<String>,
        date: String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
    ): List<RoomInfo> {
        if (buildingNames.isEmpty()) return emptyList()
        val data = fetchDayData(date)
        val campusData = data[campusName]?.jsonObject
            ?: throw NoDataException("暂无 $campusName 的数据")
        return buildingNames.flatMap { buildingName ->
            val buildingData = campusData[buildingName]?.jsonObject ?: return@flatMap emptyList()
            buildingData.entries
                .filter { (key, value) -> key != "null" && key.isNotBlank() }
                .mapNotNull { (roomName, roomJson) ->
                    try {
                        val obj = roomJson.jsonObject
                        val status = obj["status"]!!.jsonArray.map { it.jsonPrimitive.int }
                        val size = try { obj["size"]?.jsonPrimitive?.int ?: 0 } catch (_: Exception) { 0 }
                        RoomInfo(name = roomName, size = size, status = status)
                    } catch (_: Exception) { null }
                }
        }.sortedBy { it.name }
    }

    suspend fun getEmptyRooms(
        campusName: String,
        buildingName: String,
        date: String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
    ): List<RoomInfo> {
        val data = fetchDayData(date)
        val campusData = data[campusName]?.jsonObject
            ?: throw NoDataException("暂无 $campusName 的数据")
        val buildingData = campusData[buildingName]?.jsonObject
            ?: throw NoDataException("暂无 $campusName - $buildingName 的数据")

        return buildingData.entries
            .filter { (key, _) -> key != "null" && key.isNotBlank() }
            .mapNotNull { (roomName, roomJson) ->
                try {
                    val obj = roomJson.jsonObject
                    val status = obj["status"]!!.jsonArray.map { it.jsonPrimitive.int }
                    val size = try { obj["size"]?.jsonPrimitive?.int ?: 0 } catch (_: Exception) { 0 }
                    RoomInfo(name = roomName, size = size, status = status)
                } catch (_: Exception) { null }
            }.sortedBy { it.name }
    }

    fun getAvailableDates(): List<String> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        return listOf(today.toString(), tomorrow.toString())
    }

    suspend fun getRoomSeatCount(location: String): Int? {
        if (location.isBlank()) return null
        val date = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val data = try { fetchDayData(date) } catch (_: Exception) { return null }

        for ((_, campusJson) in data.entries) {
            val campusObj = try { campusJson.jsonObject } catch (_: Exception) { continue }
            for ((buildingName, buildingJson) in campusObj.entries) {
                val buildingObj = try { buildingJson.jsonObject } catch (_: Exception) { continue }
                if (location.startsWith(buildingName)) {
                    val roomPart = location.removePrefix(buildingName).trimStart('-', ' ', '/')
                    if (roomPart.isNotBlank() && buildingObj.containsKey(roomPart)) {
                        val size = try { buildingObj[roomPart]!!.jsonObject["size"]?.jsonPrimitive?.int } catch (_: Exception) { null }
                        if (size != null && size > 0) return size
                    }
                }
                if (buildingObj.containsKey(location)) {
                    val size = try { buildingObj[location]!!.jsonObject["size"]?.jsonPrimitive?.int } catch (_: Exception) { null }
                    if (size != null && size > 0) return size
                }
            }
        }
        return null
    }
}
