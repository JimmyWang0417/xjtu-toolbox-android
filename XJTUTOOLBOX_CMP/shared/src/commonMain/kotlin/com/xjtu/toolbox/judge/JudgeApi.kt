package com.xjtu.toolbox.judge

import com.xjtu.toolbox.auth.JwxtLogin
import com.xjtu.toolbox.util.safeInt
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import com.xjtu.toolbox.util.safeStringOrNull
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// ==================== 数据类 ====================

data class Questionnaire(
    val BPJS: String, val BPR: String, val DBRS: Int,
    val JSSJ: String, val JXBID: String, val KCH: String,
    val KCM: String, val KSSJ: String, val PCDM: String,
    val PGLXDM: String, val PGNR: String, val WJDM: String,
    val WJMC: String, val XNXQDM: String
)

data class QuestionnaireData(
    val WJDM: String, val CPR: String, val BPR: String,
    val PGNR: String, val ZBDM: String, val PCDM: String,
    val TXDM: String, val JXBID: String, var DA: String,
    val ZBMC: String, val DADM: String,
    var ZGDA: String = "", val SFBT: String = "1",
    val DAXH: String = "1", val FZ: String? = null
) {
    fun getMaxScore(): Int {
        require(TXDM == "03") { "此题目不是分值题" }
        requireNotNull(FZ) { "此题目的分值信息不可用" }
        return FZ.toInt()
    }

    fun setScore(score: Int) {
        require(TXDM == "03") { "此题目不是分值题" }
        requireNotNull(FZ) { "此题目的分值信息不可用" }
        val maxScore = FZ.toInt()
        require(score in 0..maxScore) { "分值必须在 0 到 $maxScore 之间" }
        DA = score.toString()
    }

    fun setOption(options: Map<String, List<QuestionnaireOptionData>>, score: String = "1") {
        require(TXDM == "01") { "此题目不是客观题" }
        val optionList = options[ZBDM]
            ?: throw IllegalArgumentException("无法在输入的答案选项中找到此题目")
        require(optionList.isNotEmpty()) { "此题目没有可选的选项" }

        for (opt in optionList) {
            if (opt.DAPX == score) { DA = opt.DA; return }
        }
        val scoreNum = score.toFloatOrNull() ?: 1f
        var minDiff = 100f
        for (opt in optionList) {
            val diff = kotlin.math.abs((opt.DAPX.toFloatOrNull() ?: 0f) - scoreNum)
            if (diff < minDiff) { minDiff = diff; DA = opt.DA }
        }
    }

    fun setSubjectiveAnswer(data: String) {
        require(TXDM == "02") { "此题目不是主观题" }
        DA = ""; ZGDA = data
    }

    fun toJsonMap(): Map<String, String?> = mapOf(
        "WJDM" to WJDM, "CPR" to CPR, "BPR" to BPR,
        "PGNR" to PGNR, "ZBDM" to ZBDM, "PCDM" to PCDM,
        "TXDM" to TXDM, "JXBID" to JXBID, "DA" to DA,
        "ZBMC" to ZBMC, "DADM" to DADM, "ZGDA" to ZGDA,
        "SFBT" to SFBT, "DAXH" to DAXH, "FZ" to FZ,
        "SFXYTJFJXX" to "", "FJXXSFBT" to "", "FJXX" to ""
    )
}

data class QuestionnaireOptionData(
    val ZBDM: String, val ZBMC: String, val DADM: String,
    val DA: String, val TXDM: String, val DAPX: String, val FZ: String
)

// ==================== API 类 ====================

class JudgeApi(private val login: JwxtLogin) {

    private var cachedTerm: String? = null

    suspend fun getCurrentTerm(): String {
        val body = login.client.submitForm(
            url = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp/modules/xspj/cxxtcs.do",
            formParameters = Parameters.build {
                append("setting",
                    """[{"name":"CSDM","value":"PJGLPJSJ","builder":"equal","linkOpt":"AND"},{"name":"ZCSDM","value":"PJXNXQ","builder":"m_value_equal","linkOpt":"AND"}]""")
            }
        ).bodyAsText()
        val root = body.safeParseJsonObject()
        return root["datas"]!!.jsonObject["cxxtcs"]!!.jsonObject["rows"]!!.jsonArray[0].jsonObject["CSZA"].safeString()
    }

    suspend fun getQuestionnaires(type: String, term: String, finished: Boolean): List<Questionnaire> {
        val body = login.client.submitForm(
            url = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp/modules/xspj/cxdwpj.do",
            formParameters = Parameters.build {
                append("PGLXDM", type)
                append("SFPG", if (finished) "1" else "0")
                append("SFKF", "1")
                append("SFFB", "1")
                append("XNXQDM", term)
            }
        ).bodyAsText()
        val root = body.safeParseJsonObject()
        val rows = root["datas"]!!.jsonObject["cxdwpj"]!!.jsonObject["rows"]!!.jsonArray
        return rows.map { el ->
            val obj = el.jsonObject
            Questionnaire(
                BPJS = obj["BPJS"].safeString(), BPR = obj["BPR"].safeString(),
                DBRS = obj["DBRS"].safeInt(), JSSJ = obj["JSSJ"].safeString(),
                JXBID = obj["JXBID"].safeString(), KCH = obj["KCH"].safeString(),
                KCM = obj["KCM"].safeString(), KSSJ = obj["KSSJ"].safeString(),
                PCDM = obj["PCDM"].safeString(), PGLXDM = obj["PGLXDM"].safeString(),
                PGNR = obj["PGNR"].safeString(), WJDM = obj["WJDM"].safeString(),
                WJMC = obj["WJMC"].safeString(), XNXQDM = obj["XNXQDM"].safeString()
            )
        }
    }

    suspend fun unfinishedQuestionnaires(term: String? = null): List<Questionnaire> {
        val t = term ?: run { if (cachedTerm == null) cachedTerm = getCurrentTerm(); cachedTerm!! }
        return getQuestionnaires("05", t, false) + getQuestionnaires("01", t, false)
    }

    suspend fun finishedQuestionnaires(term: String? = null): List<Questionnaire> {
        val t = term ?: run { if (cachedTerm == null) cachedTerm = getCurrentTerm(); cachedTerm!! }
        return getQuestionnaires("05", t, true) + getQuestionnaires("01", t, true)
    }

    suspend fun getQuestionnaireData(q: Questionnaire, username: String): List<QuestionnaireData> {
        val body = login.client.submitForm(
            url = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp/modules/wj/cxwjzb.do",
            formParameters = Parameters.build {
                append("WJDM", q.WJDM)
                append("JXBID", q.JXBID)
            }
        ).bodyAsText()
        val root = body.safeParseJsonObject()
        val rows = root["datas"]!!.jsonObject["cxwjzb"]!!.jsonObject["rows"]!!.jsonArray
        return rows.map { el ->
            val obj = el.jsonObject
            QuestionnaireData(
                WJDM = obj["WJDM"].safeString(), CPR = username, BPR = q.BPR,
                PGNR = q.PGNR, ZBDM = obj["ZBDM"].safeString(), PCDM = q.PCDM,
                TXDM = obj["TXDM"].safeString(), JXBID = q.JXBID, DA = "",
                ZBMC = obj["ZBMC"].safeString(), DADM = obj["DADM"].safeString(),
                SFBT = obj["SFBT"].safeString("1"), FZ = obj["FZ"].safeStringOrNull()
            )
        }
    }

    suspend fun getQuestionnaireOptions(
        q: Questionnaire, username: String, finished: Boolean = false
    ): Map<String, List<QuestionnaireOptionData>> {
        val querySetting = buildJsonArray {
            for ((k, v) in listOf("BPR" to q.BPR, "CPR" to username, "JXBID" to q.JXBID,
                "PGNR" to q.PGNR, "WJDM" to q.WJDM, "PCDM" to q.PCDM)) {
                add(buildJsonObject {
                    put("name", k); put("value", v); put("linkOpt", "AND"); put("builder", "equal")
                })
            }
        }.toString()

        val body = login.client.submitForm(
            url = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp/modules/wj/cxxswjzbxq.do",
            formParameters = Parameters.build {
                append("WJDM", q.WJDM); append("CPR", username)
                append("PCDM", q.PCDM); append("SFPG", if (finished) "1" else "0")
                append("BPR", q.BPR); append("PGNR", q.PGNR)
                append("querySetting", querySetting)
            }
        ).bodyAsText()
        val root = body.safeParseJsonObject()
        val rows = root["datas"]!!.jsonObject["cxxswjzbxq"]!!.jsonObject["rows"]!!.jsonArray

        val result = mutableMapOf<String, MutableList<QuestionnaireOptionData>>()
        for (el in rows) {
            val obj = el.jsonObject
            val zbdm = obj["ZBDM"].safeString()
            result.getOrPut(zbdm) { mutableListOf() }.add(QuestionnaireOptionData(
                ZBDM = zbdm, ZBMC = obj["ZBMC"].safeString(),
                DADM = obj["DADM"].safeString(), DA = obj["DAFXDM"].safeString(),
                TXDM = obj["TXDM"].safeString(), DAPX = obj["DAPX"].safeString(),
                FZ = obj["FZ"].safeString()
            ))
        }
        return result
    }

    suspend fun submitQuestionnaire(q: Questionnaire, data: List<QuestionnaireData>): Pair<Boolean, String> {
        val wjysjgJson = Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.serializer<String>(),
                    kotlinx.serialization.serializer<String?>()
                )
            ),
            data.map { it.toJsonMap() }
        )

        val requestParamStr = buildJsonObject {
            put("WJDM", q.WJDM); put("PCDM", q.PCDM)
            put("PGLY", "1"); put("SFTJ", "1")
            put("WJYSJG", wjysjgJson)
        }.toString()

        val body = login.client.submitForm(
            url = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp/WspjwjController/addXsPgysjg.do",
            formParameters = Parameters.build { append("requestParamStr", requestParamStr) }
        ).bodyAsText()
        val root = body.safeParseJsonObject()
        val code = root["code"].safeString("-1")
        val datasObj = root["datas"]?.jsonObject
        val datasCode = datasObj?.get("code").safeString("-1")
        val msg = datasObj?.get("msg").safeString("未知错误")
        return Pair(code == "0" && datasCode == "0", msg)
    }

    suspend fun editQuestionnaire(q: Questionnaire, username: String): Pair<Boolean, String> {
        val requestParamStr = buildJsonObject {
            put("WJDM", q.WJDM); put("PCDM", q.PCDM); put("CPR", username)
            put("PGLXDM", q.PGLXDM); put("BPR", q.BPR)
            put("JXBID", q.JXBID); put("PGNR", q.PGNR)
        }.toString()

        val body = login.client.submitForm(
            url = "https://jwxt.xjtu.edu.cn/jwapp/sys/wspjyyapp/WspjwjController/updateCprZt.do",
            formParameters = Parameters.build { append("requestParamStr", requestParamStr) }
        ).bodyAsText()
        val root = body.safeParseJsonObject()
        val code = root["code"].safeString("-1")
        val datasObj = root["datas"]?.jsonObject
        val datasCode = datasObj?.get("code").safeString("-1")
        val msg = datasObj?.get("msg").safeString("未知错误")
        return Pair(code == "0" && datasCode == "0", msg)
    }

    suspend fun autoFillQuestionnaire(
        q: Questionnaire, username: String, score: String = "1"
    ): List<QuestionnaireData> {
        val dataList = getQuestionnaireData(q, username)
        val options = getQuestionnaireOptions(q, username, finished = false)

        for (item in dataList) {
            when (item.TXDM) {
                "01" -> item.setOption(options, score)
                "02" -> item.setSubjectiveAnswer("老师授课认真，课程收益良多。")
                "03" -> try { item.setScore(item.getMaxScore()) } catch (_: Exception) { item.setScore(100) }
            }
        }
        return dataList
    }
}
