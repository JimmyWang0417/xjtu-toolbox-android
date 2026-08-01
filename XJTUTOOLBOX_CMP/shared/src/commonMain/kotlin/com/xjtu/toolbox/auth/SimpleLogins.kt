package com.xjtu.toolbox.auth

import io.ktor.client.HttpClient

/** 研究生管理信息系统 (GMIS) 登录 */
class GmisLogin private constructor(val base: XJTULogin) {
    val client: HttpClient get() = base.client

    companion object {
        const val GMIS_LOGIN_URL =
            "https://org.xjtu.edu.cn/openplatform/oauth/authorize?appId=1036&state=abcd1234&redirectUri=http://gmis.xjtu.edu.cn/pyxx/sso/login&responseType=code&scope=user_info"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null
        ): GmisLogin {
            val base = XJTULogin.create(GMIS_LOGIN_URL, session, visitorId)
            return GmisLogin(base)
        }
    }
}

/** 研究生评教系统 (GSTE) 登录 */
class GsteLogin private constructor(val base: XJTULogin) {
    val client: HttpClient get() = base.client

    companion object {
        const val GSTE_LOGIN_URL =
            "https://cas.xjtu.edu.cn/login?TARGET=http%3A%2F%2Fgste.xjtu.edu.cn%2Flogin.do"

        suspend fun create(
            session: HttpClient? = null,
            visitorId: String? = null
        ): GsteLogin {
            val base = XJTULogin.create(GSTE_LOGIN_URL, session, visitorId)
            return GsteLogin(base)
        }
    }
}
