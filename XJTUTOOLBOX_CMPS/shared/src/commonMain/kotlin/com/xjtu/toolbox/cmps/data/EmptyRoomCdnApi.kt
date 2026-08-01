package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

data class RoomInfo(
    val name: String,
    val size: Int,
    val status: List<Int>,
)

class EmptyRoomNoDataException(message: String) : Exception(message)

val campusBuildings: Map<String, List<String>> = linkedMapOf(
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
    "创新港校区" to listOf(
        "1号巨构", "2号巨构", "3号巨构", "4号巨构", "5号巨构",
        "9号巨构", "18号巨构", "19号巨构", "20号巨构", "21号巨构",
        "图书馆", "2号绿楔", "3号绿楔", "主楼运动场", "工程博物馆-创新港"
    ),
    "苏州校区" to listOf("公共学院5号楼"),
)

class EmptyRoomCdnApi(
    private val client: HttpClient = platformHttpClient(),
) {
    private val cdnBaseUrl = "https://gh-release.xjtutoolbox.com/"
    private var cachedDate: String? = null
    private var cachedData: JsonObject? = null

    suspend fun emptyRooms(query: EmptyRoomQuery): List<EmptyRoomItem> {
        val date = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        return getEmptyRoomsMulti(query.campus, query.buildings, date)
            .filter { room ->
                val start = (query.sections.first - 1).coerceAtLeast(0)
                val end = (query.sections.last - 1).coerceAtLeast(start)
                (start..end).all { room.status.getOrNull(it) == 0 }
            }
            .map { room ->
                EmptyRoomItem(
                    roomName = room.name,
                    buildingName = query.buildings.firstOrNull { room.name.startsWith(it) } ?: "",
                    capacity = room.size.takeIf { it > 0 },
                    availableSections = summarizeFreeSections(room.status),
                    source = DataSource.Cache,
                )
            }
    }

    suspend fun getEmptyRoomsMulti(campusName: String, buildingNames: Set<String>, date: String): List<RoomInfo> {
        if (buildingNames.isEmpty()) return emptyList()
        val campusData = fetchDayData(date)[campusName]?.jsonObject
            ?: throw EmptyRoomNoDataException("暂无 $campusName 的数据")
        return buildingNames.flatMap { buildingName ->
            val buildingData = campusData[buildingName]?.jsonObject ?: return@flatMap emptyList()
            buildingData.entries
                .filter { (key, _) -> key != "null" && key.isNotBlank() }
                .mapNotNull { (roomName, roomJson) ->
                    runCatching {
                        val obj = roomJson.jsonObject
                        val status = obj.getValue("status").jsonArray.map { it.jsonPrimitive.int }
                        val size = runCatching { obj["size"]?.jsonPrimitive?.int ?: 0 }.getOrDefault(0)
                        RoomInfo(roomName, size, status)
                    }.getOrNull()
                }
        }.sortedBy { it.name }
    }

    private suspend fun fetchDayData(date: String): JsonObject {
        if (date == cachedDate && cachedData != null) return cachedData ?: JsonObject(emptyMap())
        val response = client.get("${cdnBaseUrl}?file=static/empty_room/$date.json")
        if (response.status.value == 404) throw EmptyRoomNoDataException("当天暂无空闲教室数据，请稍后再试")
        if (response.status.value !in 200..299) throw RuntimeException("请求失败: HTTP ${response.status.value}")
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        cachedDate = date
        cachedData = json
        return json
    }

    private fun summarizeFreeSections(status: List<Int>): String {
        val ranges = mutableListOf<IntRange>()
        var start: Int? = null
        status.forEachIndexed { index, value ->
            val section = index + 1
            if (value == 0 && start == null) start = section
            if ((value != 0 || index == status.lastIndex) && start != null) {
                val end = if (value == 0 && index == status.lastIndex) section else section - 1
                ranges += start..end
                start = null
            }
        }
        return ranges.joinToString("、") { if (it.first == it.last) "第${it.first}节" else "第${it.first}-${it.last}节" }
            .ifBlank { "暂无连续空闲" }
    }
}
