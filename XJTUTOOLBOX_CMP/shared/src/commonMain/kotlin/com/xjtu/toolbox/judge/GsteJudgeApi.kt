package com.xjtu.toolbox.judge

import com.xjtu.toolbox.auth.GsteLogin
import com.xjtu.toolbox.util.safeParseJson
import com.xjtu.toolbox.util.safeParseJsonArray
import com.xjtu.toolbox.util.safeParseJsonObject
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ==================== 数据类 ====================

data class GraduateQuestionnaire(
    val ASSESSMENT: String, val BJID: String, val BJMC: String,
    val DATA_JXB_ID: Int, val DATA_JXB_JS_ID: Int,
    val JSBH: String, val JSXM: String, val JXB_SJ_OK: String,
    val KCBH: String, val KCMC: String, val KCYWMC: String,
    val KKDW: String, val LANG: String, val SKLS_DUTY: String,
    val TERMCODE: String, val TERMNAME: String
)

data class FormQuestion(
    val id: String, val name: String, val view: String,
    val options: List<FormOption>? = null
)

data class FormOption(val id: String, val value: String)

// ==================== API 类 ====================

class GsteJudgeApi(private val login: GsteLogin) {

    suspend fun getQuestionnaires(): List<GraduateQuestionnaire> {
        val body = login.client.get("http://gste.xjtu.edu.cn/app/sshd4Stu/list.do").bodyAsText()
        val jsonArray = body.safeParseJsonArray()
        return jsonArray.map { el ->
            val obj = el.jsonObject
            GraduateQuestionnaire(
                ASSESSMENT = obj["assessment"]?.jsonPrimitive?.content ?: "",
                BJID = obj["bjid"]?.jsonPrimitive?.content ?: "",
                BJMC = obj["bjmc"]?.jsonPrimitive?.content ?: "",
                DATA_JXB_ID = obj["data_jxb_id"]?.jsonPrimitive?.int ?: 0,
                DATA_JXB_JS_ID = obj["data_jxb_js_id"]?.jsonPrimitive?.int ?: 0,
                JSBH = obj["jsbh"]?.jsonPrimitive?.content ?: "",
                JSXM = obj["jsxm"]?.jsonPrimitive?.content ?: "",
                JXB_SJ_OK = obj["jxb_sj_ok"]?.jsonPrimitive?.content ?: "",
                KCBH = obj["kcbh"]?.jsonPrimitive?.content ?: "",
                KCMC = obj["kcmc"]?.jsonPrimitive?.content ?: "",
                KCYWMC = obj["kcywmc"]?.jsonPrimitive?.content ?: "",
                KKDW = obj["kkdw"]?.jsonPrimitive?.content ?: "",
                LANG = obj["lang"]?.jsonPrimitive?.content ?: "",
                SKLS_DUTY = obj["skls_duty"]?.jsonPrimitive?.content ?: "",
                TERMCODE = obj["termcode"]?.jsonPrimitive?.content ?: "",
                TERMNAME = obj["termname"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    suspend fun getQuestionnaireHtml(q: GraduateQuestionnaire): String {
        return login.client.get("http://gste.xjtu.edu.cn/app/student/genForm.do") {
            parameter("assessment", q.ASSESSMENT)
            parameter("bjid", q.BJID)
            parameter("bjmc", q.BJMC)
            parameter("data_jxb_id", q.DATA_JXB_ID.toString())
            parameter("data_jxb_js_id", q.DATA_JXB_JS_ID.toString())
            parameter("jsbh", q.JSBH)
            parameter("jsxm", q.JSXM)
            parameter("jxb_sj_ok", q.JXB_SJ_OK)
            parameter("kcbh", q.KCBH)
            parameter("kcmc", q.KCMC)
            parameter("kcywmc", q.KCYWMC)
            parameter("kkdw", q.KKDW)
            parameter("lang", q.LANG)
            parameter("skls_duty", q.SKLS_DUTY)
            parameter("termcode", q.TERMCODE)
            parameter("termname", q.TERMNAME)
        }.bodyAsText()
    }

    fun parseFormFromHtml(html: String): Pair<Map<String, String>, List<FormQuestion>> {
        val formObj = extractFormObject(html) ?: return Pair(emptyMap(), emptyList())
        val meta = mutableMapOf<String, String>()
        val questions = mutableListOf<FormQuestion>()
        walkFormNode(formObj, meta, questions, null, -1)
        return Pair(meta, questions)
    }

    fun autoFill(
        questions: List<FormQuestion>, meta: Map<String, String>,
        q: GraduateQuestionnaire, score: Int = 3
    ): Map<String, String> {
        val formData = mutableMapOf<String, String>()
        formData.putAll(meta)

        val scoreLabels = arrayOf("不合格", "合格", "良好", "优秀")
        val actualScore = score.coerceIn(0, 3)
        val scoreLabel = scoreLabels[actualScore]

        var firstRadioId: String? = null
        for (question in questions) {
            when (question.view) {
                "radio" -> {
                    if (firstRadioId == null) firstRadioId = question.id
                    chooseOptionValue(question.options, scoreLabel)?.let { formData[question.id] = it }
                }
                "select" -> chooseOptionValue(question.options, scoreLabel)?.let { formData[question.id] = it }
                "textarea" -> formData[question.id] = "无"
                "text" -> {
                    val nameLower = question.name.lowercase()
                    formData[question.id] = when {
                        "课程名称" in nameLower || "课程名" in nameLower -> q.KCMC
                        "教师" in nameLower || "老师" in nameLower -> q.JSXM
                        else -> q.KCMC
                    }
                }
            }
        }

        if (actualScore == 3 && firstRadioId != null) {
            val firstQ = questions.find { it.id == firstRadioId }
            if (firstQ != null) {
                chooseOptionValue(firstQ.options, "良好")?.let { formData[firstRadioId] = it }
            }
        }
        return formData
    }

    suspend fun submitQuestionnaire(q: GraduateQuestionnaire, formData: Map<String, String>): Boolean {
        val body = login.client.submitForm(
            url = "http://gste.xjtu.edu.cn/app/student/saveForm.do",
            formParameters = Parameters.build {
                append("assessment", q.ASSESSMENT); append("bjid", q.BJID)
                append("bjmc", q.BJMC); append("data_jxb_id", q.DATA_JXB_ID.toString())
                append("data_jxb_js_id", q.DATA_JXB_JS_ID.toString())
                append("jsbh", q.JSBH); append("jsxm", q.JSXM)
                append("jxb_sj_ok", q.JXB_SJ_OK); append("kcbh", q.KCBH)
                append("kcmc", q.KCMC); append("kcywmc", q.KCYWMC)
                append("kkdw", q.KKDW); append("lang", q.LANG)
                append("skls_duty", q.SKLS_DUTY); append("termcode", q.TERMCODE)
                append("termname", q.TERMNAME)
                for ((key, value) in formData) { append(key, value) }
            }
        ).bodyAsText()
        val json = body.safeParseJsonObject()
        return json["ok"]?.jsonPrimitive?.boolean ?: false
    }

    // ==================== 内部辅助方法 ====================

    private fun extractFormObject(html: String): Map<*, *>? {
        if (html.isEmpty()) return null
        val anchor = html.indexOf("pjzbApp.form")
        if (anchor < 0) return null
        val eq = html.indexOf("=", anchor)
        if (eq < 0) return null
        val start = html.indexOf("{", eq)
        if (start < 0) return null

        var depth = 0; var inStr = false; var esc = false; var end = -1
        for (i in start until html.length) {
            val ch = html[i]
            if (inStr) {
                if (esc) { esc = false } else if (ch == '\\') { esc = true } else if (ch == '"') { inStr = false }
            } else {
                when (ch) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { end = i + 1; break } }
                }
            }
        }
        if (end < 0) return null

        var objText = html.substring(start, end)
        objText = objText.replace(Regex(""":\s*webix\.rules\.isNotEmpty"""), ": \"isNotEmpty\"")

        return try {
            val parsed = objText.safeParseJson()
            if (parsed is JsonObject) jsonObjectToMap(parsed.jsonObject) else null
        } catch (_: Exception) {
            val cleaned = objText.replace(Regex(""",\s*([}\]])"""), "$1")
            try {
                val parsed = cleaned.safeParseJson()
                if (parsed is JsonObject) jsonObjectToMap(parsed.jsonObject) else null
            } catch (_: Exception) { null }
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for ((key, value) in obj) { map[key] = jsonElementToAny(value) }
        return map
    }

    private fun jsonElementToAny(el: kotlinx.serialization.json.JsonElement): Any? = when {
        el is kotlinx.serialization.json.JsonNull -> null
        el is kotlinx.serialization.json.JsonPrimitive -> {
            el.booleanOrNull ?: el.intOrNull ?: el.content
        }
        el is kotlinx.serialization.json.JsonArray -> el.map { jsonElementToAny(it) }
        el is JsonObject -> jsonObjectToMap(el)
        else -> null
    }

    private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
        get() = if (content == "true") true else if (content == "false") false else null
    private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
        get() = content.toIntOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun walkFormNode(
        node: Any?, meta: MutableMap<String, String>,
        questions: MutableList<FormQuestion>,
        parentCols: List<Any?>?, indexInParent: Int
    ) {
        when (node) {
            is Map<*, *> -> {
                val view = node["view"]?.toString() ?: ""
                val hidden = node["hidden"]
                val isHidden = hidden == true || hidden == "true" || hidden == "True"

                if (isHidden && view in listOf("text", "hidden")) {
                    val key = (node["id"] ?: node["name"])?.toString()
                    if (key != null) meta[key] = node["value"]?.toString() ?: ""
                } else if (view in listOf("radio", "textarea", "text", "select") && !isHidden) {
                    val qid = (node["id"] ?: node["name"])?.toString()
                    var qname = (node["label"] ?: node["value"])?.toString()

                    if ((view == "radio" && qname.isNullOrEmpty()) || view == "textarea") {
                        if (parentCols != null && indexInParent >= 0) {
                            if (view == "textarea" && indexInParent - 1 >= 0) {
                                val prev = parentCols[indexInParent - 1]
                                if (prev is Map<*, *>) qname = (prev["value"] ?: prev["label"])?.toString() ?: qname
                            }
                            if (view == "radio" && qname.isNullOrEmpty()) {
                                for (cand in parentCols) {
                                    if (cand is Map<*, *> && cand["view"] == "label") {
                                        qname = (cand["label"] ?: cand["value"])?.toString()
                                        if (!qname.isNullOrEmpty()) break
                                    }
                                }
                            }
                        }
                    }

                    if (qid != null && !qname.isNullOrEmpty()) {
                        val rawOptions = node["options"]
                        val options = if (rawOptions is List<*>) {
                            rawOptions.mapNotNull { opt ->
                                if (opt is Map<*, *>) FormOption(
                                    id = opt["id"]?.toString() ?: "",
                                    value = opt["value"]?.toString() ?: ""
                                ) else null
                            }
                        } else null
                        questions.add(FormQuestion(id = qid, name = qname, view = view, options = options))
                    }
                }

                for (key in listOf("elements", "rows", "cols")) {
                    val arr = node[key]
                    if (arr is List<*>) {
                        for ((i, child) in arr.withIndex()) {
                            val pc = if (key == "cols") arr else null
                            val idx = if (key == "cols") i else -1
                            walkFormNode(child, meta, questions, pc, idx)
                        }
                    }
                }
            }
            is List<*> -> { for (child in node) walkFormNode(child, meta, questions, null, -1) }
        }
    }

    private fun chooseOptionValue(options: List<FormOption>?, desired: String?): String? {
        if (options.isNullOrEmpty()) return null
        if (!desired.isNullOrEmpty()) {
            for (opt in options) { if (opt.value == desired) return opt.id }
            for (opt in options) { if (desired in opt.value) return opt.id }
        }
        for (label in listOf("优", "是", "有")) {
            for (opt in options) { if (label in opt.value) return opt.id }
        }
        var bestOpt: FormOption? = null; var bestScore: Double? = null
        for (opt in options) {
            val num = opt.id.toDoubleOrNull() ?: opt.value.toDoubleOrNull()
            if (num != null && (bestScore == null || num > bestScore)) { bestScore = num; bestOpt = opt }
        }
        if (bestOpt != null) return bestOpt.id
        return options.firstOrNull()?.id
    }
}
