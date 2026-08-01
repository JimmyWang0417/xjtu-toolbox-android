package com.xjtu.toolbox.cmps.data

import com.xjtu.toolbox.cmps.platform.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.content
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JiaocaiApi(
    private val client: HttpClient = platformHttpClient(),
) {
    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): List<JiaocaiBook> {
        val url = "$baseUrl/engine2/search/search-list" +
            "?wfwfid=$fid" +
            "&keyWord=${keyword.encodeURLParameter()}" +
            "&pageIndex=$page" +
            "&pageSize=$pageSize" +
            "&pageId=$pageId" +
            "&searchStrategy=0" +
            "&searchId=$searchId"
        val text = client.get(url) {
            header("Referer", "$baseUrl/")
            header("X-Requested-With", "XMLHttpRequest")
        }.bodyAsText()
        val root = Json.parseToJsonElement(text).jsonObject
        val list = root["data"]?.jsonObject?.get("dataList")?.jsonArray.orEmpty()
        return list.mapNotNull { item ->
            val obj = item.jsonObject
            val rawSummary = obj.str("content")
            JiaocaiBook(
                id = obj.str("id").ifBlank { return@mapNotNull null },
                appId = obj.int("appId") ?: 0,
                engineInstanceId = obj.int("engineInstanceId") ?: 0,
                title = obj.str("title").stripHtml(),
                author = obj.str("author"),
                summary = rawSummary.stripHtml(),
                hasFullText = rawSummary.contains("本地全文") || rawSummary.contains("全文获取"),
            )
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content.orEmpty()

    private fun kotlinx.serialization.json.JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    companion object {
        private const val baseUrl = "https://jiaocai.lib.xjtu.edu.cn"
        private const val fid = "17071"
        private const val pageId = "13858"
        private const val searchId = "10700"
    }
}
