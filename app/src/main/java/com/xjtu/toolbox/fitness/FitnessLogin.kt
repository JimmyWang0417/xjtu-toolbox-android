package com.xjtu.toolbox.fitness

import com.xjtu.toolbox.auth.XJTULogin
import com.xjtu.toolbox.util.safeParseJsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class FitnessLogin(
    session: OkHttpClient? = null,
    visitorId: String? = null,
    cachedRsaKey: String? = null,
) : XJTULogin(LOGIN_URL, session, visitorId, cachedRsaKey) {

    /**
     * 登录落地地址，业务请求当 Referer 用。
     *
     * **必须是计算属性。** 超类 [XJTULogin] 的构造函数里就会调用虚方法 `postLogin()`，
     * 那时子类的属性初始化器还没执行——写成 `var refererUrl = H5_HOME_URL` 的话，
     * postLogin 里赋的真实回调地址会被随后的初始化器重新覆盖回 H5_HOME_URL，
     * 于是 `localToken["referer_url"]` 永远是那个静态首页而不是真实落地页。
     * 超类的 `finalUrl` 在超类构造期间赋值，不受子类初始化顺序影响。
     */
    val refererUrl: String
        get() = finalUrl.ifBlank { H5_HOME_URL }

    override fun postLogin(response: Response) {
        val callbackUrl = response.request.url
        // 走 WebVPN 时 host 是 webvpn.xjtu.edu.cn、域名段被 AES 加密，明文匹配必然失败。
        if (!com.xjtu.toolbox.util.WebVpnUtil.isAtTargetSite(callbackUrl.toString(), TARGET_HOST)) {
            throw RuntimeException("体测登录回调异常")
        }

        // 真实链路是：xjtuLogin 先建立 PHPSESSID 并进入 H5 首页；随后前端页面
        // 在带 token/sign 的详情页里再调用 checkLogin。若回调没有这些参数，
        // 说明 CAS 会话已建立，后续 fitnessYear/getStudentScore 可直接用该会话。
        val params = callbackUrl.queryParameterNames.associateWith {
            callbackUrl.queryParameter(it).orEmpty()
        }.filterKeys { it in CHECK_LOGIN_FIELDS }

        if (params["token"].isNullOrBlank() || params["sign"].isNullOrBlank()) {
            return
        }

        val form = FormBody.Builder().apply {
            CHECK_LOGIN_FIELDS.forEach { add(it, params[it].orEmpty()) }
        }.build()
        client.newCall(
            Request.Builder()
                .url(CHECK_LOGIN_URL)
                .header("Origin", ORIGIN)
                .header("Referer", callbackUrl.toString())
                .header("X-Requested-With", "XMLHttpRequest")
                .post(form)
                .build()
        ).execute().use {
            val body = it.body?.string().orEmpty()
            val ok = runCatching {
                body.safeParseJsonObject().get("status")?.asInt == 1
            }.getOrDefault(false)
            if (!it.isSuccessful || !ok) {
                throw RuntimeException("体测会话初始化失败")
            }
        }
    }

    // 同样不能用 `private var sessionReady`：postLogin 在超类构造期间把它置 true，
    // 随后子类初始化器又把它置回 false，validateLogin 会永远返回 false。
    // 直接看落地地址是否已到体测站，兼容直连与 WebVPN。
    override fun validateLogin(): Boolean =
        com.xjtu.toolbox.util.WebVpnUtil.isAtTargetSite(finalUrl, TARGET_HOST)

    companion object {
        const val TARGET_HOST = "tyxylp.xjtu.edu.cn"
        const val LOGIN_URL =
            "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/public/index.php/index/login/xjtuLogin"
        const val API_ROOT =
            "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/public/index.php/index"
        const val H5_HOME_URL =
            "https://tyxylp.xjtu.edu.cn/bdlp_h5_fitness_test/view/h5xajt/#/pages/index/index"
        const val ORIGIN = "https://tyxylp.xjtu.edu.cn"
        private const val CHECK_LOGIN_URL = "$API_ROOT/Index/checkLogin"
        private val CHECK_LOGIN_FIELDS = listOf(
            "timestamp", "nonce", "course_id", "uid", "card_id", "login_type",
            "type", "school_id", "student_num", "user_type", "token", "sign", "term_id", "id"
        )
    }
}
