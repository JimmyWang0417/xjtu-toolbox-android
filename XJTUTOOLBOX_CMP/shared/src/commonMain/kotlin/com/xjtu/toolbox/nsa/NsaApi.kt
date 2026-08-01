package com.xjtu.toolbox.nsa

import com.xjtu.toolbox.lms.NsaStudentProfile
import com.xjtu.toolbox.util.Base64Utils
import com.xjtu.toolbox.util.Logger
import com.xjtu.toolbox.util.safeParseJsonObject
import com.xjtu.toolbox.util.safeString
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 学工系统 (nsa.xjtu.edu.cn) API
 *
 * ### 认证架构
 * NSA 使用 **OAuth2** 认证（非直接 CAS ServiceTicket）：
 * ```
 * pd.zf → org.xjtu.edu.cn/oauth → CAS OAuth2 → ST → callbackAuthorize → getUserInfoByAccessToken.zf → session
 * ```
 * 需要 CAS TGC cookie 有效；TGC 失效时通过 [casRefresher] 回调重新认证。
 *
 * @param client       共享 HttpClient（含 CAS TGC cookies）
 * @param casRefresher 刷新 CAS TGC 的回调，返回 true 表示成功
 */
class NsaApi(
    private val client: HttpClient,
    private val casRefresher: (suspend () -> Boolean)? = null
) {

    companion object {
        private const val TAG = "NsaApi"
        private const val BASE = "https://nsa.xjtu.edu.cn/zftal-xgxt-web"

        private var sessionValid = false
        private var cachedStudentId: String? = null
        private var cachedStudentName: String? = null
        private var cachedRoleDm: String? = null
        private val sessionMutex = Mutex()

        /** 登出时清除会话缓存 */
        suspend fun clearSession() {
            sessionMutex.withLock {
                sessionValid = false
                cachedStudentId = null
                cachedStudentName = null
                cachedRoleDm = null
            }
        }
    }

    // ══════════════════════════════════════
    // 会话管理
    // ══════════════════════════════════════

    suspend fun ensureSession(): Boolean = sessionMutex.withLock {
        if (sessionValid) {
            Logger.d(TAG, "ensureSession: cached session valid (id=$cachedStudentId)")
            return true
        }

        // Step 1: 快速检查现有 cookie
        if (checkSession()) {
            Logger.d(TAG, "ensureSession: existing cookies valid")
            switchRole()
            sessionValid = true
            return true
        }

        // Step 2: 获取 OAuth 入口 URL
        val oauthUrl = getOAuthUrl()
        if (oauthUrl == null) {
            Logger.e(TAG, "ensureSession: pd.zf did not return OAuth URL")
            return false
        }
        Logger.d(TAG, "ensureSession: oauthUrl=${oauthUrl.take(120)}...")

        // Step 3: 跟随 OAuth 重定向链
        if (followOAuthAndVerify(oauthUrl)) {
            switchRole()
            sessionValid = true
            Logger.d(TAG, "ensureSession: OAuth succeeded (id=$cachedStudentId)")
            return true
        }

        // Step 4: TGC 过期，尝试刷新
        if (casRefresher != null) {
            Logger.d(TAG, "ensureSession: refreshing CAS TGC...")
            val ok = try { casRefresher.invoke() } catch (e: Exception) {
                Logger.w(TAG, "casRefresher failed: ${e.message}"); false
            }
            if (ok) {
                Logger.d(TAG, "ensureSession: TGC refreshed, retrying OAuth...")
                if (followOAuthAndVerify(oauthUrl)) {
                    switchRole()
                    sessionValid = true
                    Logger.d(TAG, "ensureSession: OAuth succeeded after TGC refresh")
                    return true
                }
            }
        }

        Logger.e(TAG, "ensureSession: all attempts failed")
        return false
    }

    // ── OAuth 内部方法 ──

    private suspend fun getOAuthUrl(): String? = try {
        val resp = client.get("$BASE/teacher/xtgl/index/pd.zf")
        val body = resp.bodyAsText()
        val json = body.safeParseJsonObject()
        json["data"]?.jsonObject?.get("rzdldz")?.jsonPrimitive?.content
            ?.replaceFirst("http://", "https://")
    } catch (e: Exception) {
        Logger.e(TAG, "getOAuthUrl failed: ${e.message}"); null
    }

    private suspend fun followOAuthAndVerify(oauthUrl: String): Boolean = try {
        val resp = client.get(oauthUrl)
        val finalUrl = resp.call.request.url.toString()
        resp.bodyAsText() // 消费 body

        Logger.d(TAG, "followOAuth: finalUrl=${finalUrl.take(120)}")
        if (finalUrl.contains("login.xjtu.edu.cn/cas/login")) {
            Logger.w(TAG, "followOAuth: stopped at CAS login page → TGC invalid")
            false
        } else {
            checkSession()
        }
    } catch (e: Exception) {
        Logger.e(TAG, "followOAuth failed: ${e.message}"); false
    }

    private suspend fun checkSession(): Boolean = try {
        val resp = client.get("$BASE/teacher/xtgl/index/getUserRoleInfo.zf")
        val body = resp.bodyAsText()
        val json = body.safeParseJsonObject()
        val code = json["code"]?.jsonPrimitive?.content?.toIntOrNull()
        if (code == 0) {
            val data = json["data"]?.jsonObject
            cachedStudentId = data?.get("zgh")?.jsonPrimitive?.content
            cachedStudentName = data?.get("xm")?.jsonPrimitive?.content?.trim()
            cachedRoleDm = data?.get("mrjsdm")?.jsonPrimitive?.content
            Logger.d(TAG, "checkSession: OK (id=$cachedStudentId, name=$cachedStudentName)")
            true
        } else {
            Logger.d(TAG, "checkSession: not auth (code=$code)")
            false
        }
    } catch (e: Exception) {
        Logger.w(TAG, "checkSession failed: ${e.message}"); false
    }

    private suspend fun switchRole() {
        val dm = cachedRoleDm ?: return
        try {
            client.submitForm(
                url = "$BASE/teacher/xtgl/login/switchRole.zf",
                formParameters = parameters { append("jsdm", dm) }
            ).bodyAsText() // 消费
            Logger.d(TAG, "switchRole: done")
        } catch (e: Exception) {
            Logger.w(TAG, "switchRole failed (non-fatal): ${e.message}")
        }
    }

    // ══════════════════════════════════════
    // 公开 API
    // ══════════════════════════════════════

    val studentId: String? get() = cachedStudentId
    val studentName: String? get() = cachedStudentName

    suspend fun getProfile(): NsaStudentProfile? {
        if (!ensureSession()) return null

        var name = cachedStudentName ?: ""
        var studentId = cachedStudentId ?: ""
        var college = ""
        var major = ""
        try {
            val body = client.get("$BASE/teacher/xtgl/index/getGrkpInfo.zf").bodyAsText()
            Logger.d(TAG, "getGrkpInfo: ${body.take(500)}")
            val json = body.safeParseJsonObject()
            val code = json["code"]?.jsonPrimitive?.content?.toIntOrNull()
            if (code == 0) {
                val data = json["data"]?.jsonObject
                if (data != null) {
                    name = data["xm"].safeString().trim().ifEmpty { name }
                    studentId = data["zgh"].safeString().ifEmpty { studentId }
                    college = data["bmmc"].safeString()
                    major = data["zymc"].safeString()
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "getGrkpInfo failed: ${e.message}")
        }

        if (name.isEmpty() && studentId.isEmpty()) return null
        return NsaStudentProfile(
            name = name,
            studentId = studentId,
            college = college,
            major = major
        )
    }

    // ── 展示字段白名单 ──
    private val userInfoFields = linkedMapOf(
        "xbdm"   to "性别",
        "csrq"   to "出生日期",
        "mzdm"   to "民族",
        "zzmmdm" to "政治面貌",
        "xxdm"   to "血型",
        "jgdm"   to "籍贯",
        "sg"     to "身高",
        "tz"     to "体重"
    )
    private val zxxxFields = linkedMapOf(
        "nj"     to "年级",
        "pycc"   to "培养层次",
        "sydm"   to "书院",
        "xqdm"   to "校区",
        "rxrq"   to "入学时间",
        "xz"     to "学制",
        "qsh"    to "寝室号",
        "xjztdm" to "学籍状态"
    )

    suspend fun getPersonalDetails(): List<Pair<String, String>> {
        if (!ensureSession()) return emptyList()

        val result = mutableListOf<Pair<String, String>>()

        // ── userInfo ──
        try {
            val body = client.get("$BASE/dynamic/form/group/userInfo/default.zf?dataId=null").bodyAsText()
            val json = body.safeParseJsonObject()
            val code = json["code"]?.jsonPrimitive?.content?.toIntOrNull()
            if (code == 0) {
                val fields = extractFormFields(json)
                for ((fieldCode, label) in userInfoFields) {
                    val f = fields[fieldCode] ?: continue
                    val value = resolveFieldValue(f)
                    if (value.isNotEmpty()) {
                        val display = when (fieldCode) {
                            "sg" -> "$value cm"
                            "tz" -> "$value kg"
                            else -> value
                        }
                        result.add(label to display)
                    }
                }
            }
            Logger.d(TAG, "userInfo: parsed ${result.size} fields")
        } catch (e: Exception) {
            Logger.w(TAG, "userInfo failed: ${e.message}")
        }

        // ── zxxx ──
        val zxxxStart = result.size
        try {
            val body = client.get("$BASE/dynamic/form/group/zxxx/default.zf?dataId=null").bodyAsText()
            val json = body.safeParseJsonObject()
            val code = json["code"]?.jsonPrimitive?.content?.toIntOrNull()
            if (code == 0) {
                val fields = extractFormFields(json)
                for ((fieldCode, label) in zxxxFields) {
                    val f = fields[fieldCode] ?: continue
                    val value = resolveFieldValue(f)
                    if (value.isNotEmpty()) {
                        val display = when (fieldCode) {
                            "nj" -> "${value}级"
                            "xz" -> "${value}年"
                            else -> value
                        }
                        result.add(label to display)
                    }
                }
            }
            Logger.d(TAG, "zxxx: parsed ${result.size - zxxxStart} fields")
        } catch (e: Exception) {
            Logger.w(TAG, "zxxx failed: ${e.message}")
        }

        Logger.d(TAG, "getPersonalDetails: total ${result.size} fields")
        return result
    }

    private fun extractFormFields(json: JsonObject): Map<String, JsonObject> {
        val map = mutableMapOf<String, JsonObject>()
        val data = json["data"]?.jsonObject ?: return map
        val groupsEl = data["groupFields"]
        if (groupsEl == null || groupsEl is JsonNull) return map
        val groups = try { groupsEl.jsonArray } catch (_: Exception) { return map }
        for (group in groups) {
            val go = group.jsonObject
            val fieldsEl = go["fields"]
            if (fieldsEl == null || fieldsEl is JsonNull) continue
            val fields = try { fieldsEl.jsonArray } catch (_: Exception) { continue }
            for (field in fields) {
                val fo = field.jsonObject
                val code = fo["fieldCode"]?.jsonPrimitive?.content ?: continue
                map[code] = fo
            }
        }
        return map
    }

    private fun resolveFieldValue(field: JsonObject): String {
        val raw = field["defaultValue"]?.let {
            if (it is JsonNull) return ""
            it.jsonPrimitive.content.trim()
        } ?: return ""
        if (raw.isEmpty()) return ""

        val optionsEl = field["options"]
        if (optionsEl != null && optionsEl !is JsonNull) {
            val options = try { optionsEl.jsonArray } catch (_: Exception) { JsonArray(emptyList()) }
            if (options.isNotEmpty()) {
                for (opt in options) {
                    val o = opt.jsonObject
                    if (o["value"]?.jsonPrimitive?.content == raw) {
                        return o["label"]?.jsonPrimitive?.content ?: raw
                    }
                }
            }
        }
        return raw
    }

    /**
     * 学生证照片（JPEG bytes）
     */
    suspend fun getStudentPhoto(): ByteArray? {
        if (!ensureSession()) return null
        val id = cachedStudentId ?: return null
        val url = "$BASE/xsxx/xsxx/xsgl/getXszp.zf?yhm=$id"
        Logger.d(TAG, "getStudentPhoto: $url")
        return try {
            val resp = client.get(url)
            if (resp.status.value != 200) return null
            val bodyText = resp.bodyAsText()
            val bytes = bodyText.encodeToByteArray()
            if (bytes.size < 100) return null

            val ct = resp.headers["Content-Type"] ?: ""
            if (ct.contains("image", true) || ct.contains("octet-stream", true)) {
                Logger.d(TAG, "getStudentPhoto: binary ${bytes.size}B")
                return bytes
            }

            val text = bodyText.trim()
            when {
                text.startsWith("/9j/") || text.startsWith("iVBOR") -> {
                    Base64Utils.decode(text).also {
                        Logger.d(TAG, "getStudentPhoto: base64 → ${it.size}B")
                    }
                }
                text.startsWith("data:image") -> {
                    Base64Utils.decode(text.substringAfter(",")).also {
                        Logger.d(TAG, "getStudentPhoto: dataURI → ${it.size}B")
                    }
                }
                else -> {
                    Logger.w(TAG, "getStudentPhoto: unexpected: ${text.take(200)}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "getStudentPhoto failed", e); null
        }
    }

    /** 使会话失效 */
    suspend fun invalidateSession() = sessionMutex.withLock { sessionValid = false }
}
