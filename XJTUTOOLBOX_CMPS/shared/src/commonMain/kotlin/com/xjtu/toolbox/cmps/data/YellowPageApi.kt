package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.content
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class YellowPageApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun getData(): YellowPageData {
        val listRoot = getJson("$baseUrl/site/schoolePage/getList")
        val data = listRoot["d"]?.jsonObject ?: error("黄页接口缺少数据")
        val categories = data["categories"]?.jsonArray.orEmpty()
            .mapNotNull { item ->
                val obj = item.jsonObject
                YellowPageCategory(
                    id = obj.int("id") ?: return@mapNotNull null,
                    name = obj.str("name"),
                    status = obj.int("status") ?: 0,
                    sort = obj.int("sort") ?: 0,
                )
            }
            .filter { it.status == 1 }
            .sortedWith(compareBy({ it.sort }, { it.id }))
        val departments = data["departments"]?.jsonArray.orEmpty()
            .mapNotNull { item ->
                val obj = item.jsonObject
                YellowPageDepartment(
                    id = obj.int("id") ?: return@mapNotNull null,
                    categoryId = obj.int("categoryId") ?: obj.int("category_id") ?: 0,
                    name = obj.str("name"),
                    phone = obj.str("phone"),
                    sort = obj.int("sort") ?: 0,
                    status = obj.int("status") ?: 0,
                )
            }
            .filter { it.status == 1 }
            .sortedWith(compareBy({ it.sort }, { it.id }))
        val updateTime = runCatching {
            val raw = getJson("$baseUrl/site/schoolePage/getUpdateTime")
                ["d"]?.jsonObject
                ?.str("page_update_time")
                .orEmpty()
            raw.substringBefore("T").replace("-", "年").let {
                val parts = raw.substringBefore("T").split("-")
                if (parts.size == 3) "${parts[0]}年${parts[1]}月${parts[2]}日" else raw
            }
        }.getOrDefault("")
        return YellowPageData(categories, departments, updateTime)
    }

    private suspend fun getJson(url: String): JsonObject {
        val text = client.get(url) {
            header("Accept", "application/json")
            header("Referer", "https://workflow.xjtu.edu.cn/selectpage/page/site/yellowPage")
        }.bodyAsText()
        val root = Json.parseToJsonElement(text).jsonObject
        val code = root["e"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 0) {
            error(root["m"]?.jsonPrimitive?.content.orEmpty().ifBlank { "黄页接口返回错误" })
        }
        return root
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content.orEmpty()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    companion object {
        private const val baseUrl = "https://workflow.xjtu.edu.cn/selectpage"
    }
}
