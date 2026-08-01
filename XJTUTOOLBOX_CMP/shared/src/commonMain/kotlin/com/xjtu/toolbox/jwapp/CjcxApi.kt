package com.xjtu.toolbox.jwapp

import com.xjtu.toolbox.auth.JwxtLogin
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.currentTimeMillis
import com.xjtu.toolbox.util.safeDouble
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import com.xjtu.toolbox.util.safeStringOrNull
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "CjcxApi"

class CjcxApi(private val login: JwxtLogin) {

    private val baseUrl = "https://jwxt.xjtu.edu.cn/jwapp/sys/cjcx"

    private var lastSessionTime = 0L
    private val sessionTtl = 5 * 60 * 1000L

    data class CjcxScore(
        val courseName: String, val termCode: String,
        val zcj: Double, val xfjd: Double, val xf: Double,
        val djcjlxdm: String, val djcjmc: String?,
        val pscj: Double?, val pscjxs: String?,
        val qmcj: Double?, val qmcjxs: String?,
        val kch: String, val jxbid: String,
        val passFlag: Boolean, val examProp: String, val kclbdm: String,
    )

    private suspend fun ensureSession() {
        val now = currentTimeMillis()
        if (now - lastSessionTime < sessionTtl) return
        try {
            login.client.get("$baseUrl/*default/index.do")
            lastSessionTime = now
        } catch (e: Exception) {
            Logger.w(TAG, "session init: ${e.message}")
        }
    }

    suspend fun getAllScores(): List<CjcxScore> {
        ensureSession()
        val all = mutableListOf<CjcxScore>()
        var page = 1
        val pageSize = 100

        while (true) {
            val querySetting = """[{"name":"SFYX","caption":"是否有效","linkOpt":"AND","builderList":"cbl_m_List","builder":"m_value_equal","value":"1","value_display":"是"}]"""
            val body = login.client.submitForm(
                url = "$baseUrl/modules/cjcx/xscjcx.do",
                formParameters = Parameters.build {
                    append("querySetting", querySetting)
                    append("pageSize", pageSize.toString())
                    append("pageNumber", page.toString())
                }
            ) {
                header("X-Requested-With", "XMLHttpRequest")
            }.bodyAsText()

            val root = body.safeParseJsonObject()
            if (root["code"]?.jsonPrimitive?.content != "0") {
                throw RuntimeException("xscjcx.do 业务错误: ${root["code"]}")
            }

            val xscjcx = root["datas"]!!.jsonObject["xscjcx"]!!.jsonObject
            val totalSize = xscjcx["totalSize"]!!.jsonPrimitive.int
            val rows = xscjcx["rows"]!!.jsonArray

            for (el in rows) {
                val o = el.jsonObject
                all.add(CjcxScore(
                    courseName = o["KCM"].safeString(),
                    termCode = o["XNXQDM"].safeString(),
                    zcj = o["ZCJ"].safeDouble(),
                    xfjd = o["XFJD"].safeDouble(),
                    xf = o["XF"].safeDouble(),
                    djcjlxdm = o["DJCJLXDM"].safeString(),
                    djcjmc = o["DJCJMC"].safeStringOrNull()?.takeIf { it.isNotBlank() },
                    pscj = o["PSCJ"].safeDouble(-999.0).takeIf { it > -100 },
                    pscjxs = o["PSCJXS"].safeStringOrNull()?.takeIf { it.isNotBlank() && it != "0" },
                    qmcj = o["QMCJ"].safeDouble(-999.0).takeIf { it > -100 },
                    qmcjxs = o["QMCJXS"].safeStringOrNull()?.takeIf { it.isNotBlank() && it != "0" },
                    kch = o["KCH"].safeString(),
                    jxbid = o["JXBID"].safeString(),
                    passFlag = o["SFJG"].safeString() == "1",
                    examProp = o["CXCKDM_DISPLAY"].safeString(),
                    kclbdm = o["KCLBDM_DISPLAY"].safeString(),
                ))
            }

            if (all.size >= totalSize || rows.size < pageSize) break
            page++
            if (page > 50) break
        }

        Logger.d(TAG, "获取 ${all.size} 门精确成绩")
        return all
    }

    fun buildLookup(scores: List<CjcxScore>): Map<String, CjcxScore> =
        scores.associateBy { "${it.termCode}|${normalizeName(it.courseName)}" }

    companion object {
        fun normalizeName(name: String): String = name.trim()
            .replace("\u3000", " ").replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .replace("（", "(").replace("）", ")")
            .replace("＋", "+").replace("－", "-")
            .replace("Ⅰ", "I").replace("Ⅱ", "II").replace("Ⅲ", "III")
            .replace("Ⅳ", "IV").replace("Ⅴ", "V").replace("Ⅵ", "VI")
            .replace("ⅰ", "I").replace("ⅱ", "II").replace("ⅲ", "III")
            .replace("ⅳ", "IV").replace("ⅴ", "V").replace("ⅵ", "VI")
            .replace(Regex("[◇◆◎○●★☆※▲△▼▽]"), "")
            .replace(Regex("\\([A-Z]{2,}\\d{4,}\\)$"), "")
            .trim().lowercase()
    }
}
