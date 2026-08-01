package com.xjtu.toolbox.ui.screen

import androidx.compose.runtime.*
import com.tencent.kuikly.compose.foundation.layout.*
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.xjtu.toolbox.ui.miuix.*
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.input.PasswordVisualTransformation
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.xjtu.toolbox.LocalNavigation
import com.xjtu.toolbox.auth.*
import com.xjtu.toolbox.ui.components.BackButton
import com.xjtu.toolbox.util.Logger
import kotlinx.coroutines.launch

/**
 * 登录类型枚举 (KuiklyUI CMP)
 */
enum class LoginTarget(val label: String, val description: String) {
    ATTENDANCE("考勤系统", "本科生考勤查询"),
    JWXT("教务系统", "课表/考试/评教"),
    JWAPP("移动教务", "成绩查询"),
    YWTB("一网通办", "个人信息/学期"),
    LIBRARY("图书馆", "座位预约"),
    CAMPUS_CARD("校园卡", "余额/账单查询"),
    DZPZ("电子打印证", "成绩单下载"),
    VENUE("体育场馆", "运动场地预订"),
    CLASS("课程平台", "课程回放 · TronClass"),
    LMS("思源学堂", "课程 · 作业 · 回放"),
    GMIS("研究生系统", "课表/成绩"),
    GSTE("研究生评教", "GSTE评教系统");

    suspend fun performLogin(
        username: String,
        password: String,
        onStatus: (String) -> Unit
    ): LoginResult {
        onStatus("正在初始化${label}...")
        val base = when (this) {
            ATTENDANCE -> {
                val login = AttendanceLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            JWXT -> {
                val login = JwxtLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            JWAPP -> {
                val login = JwappLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            YWTB -> {
                val login = YwtbLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            LIBRARY -> {
                val login = LibraryLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            CAMPUS_CARD -> {
                val login = CampusCardLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            DZPZ -> {
                val login = DzpzLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            VENUE -> {
                val login = VenueLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            CLASS -> {
                val login = com.xjtu.toolbox.classreplay.ClassLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            LMS -> {
                val login = com.xjtu.toolbox.lms.LmsLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            GMIS -> {
                val login = GmisLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
            GSTE -> {
                val login = GsteLogin.create()
                onStatus("正在验证身份...")
                return login.base.login(username, password)
            }
        }
    }
}

@Composable
fun LoginScreen() {
    val nav = LocalNavigation.current
    val loginTypeName = nav.routeArgs["loginType"] as? String
    val loginTarget = loginTypeName?.let {
        try { LoginTarget.valueOf(it) } catch (_: Exception) { null }
    } ?: LoginTarget.YWTB

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    MiuixScaffold(
        topBar = {
            MiuixSmallTopAppBar(
                title = "登录 · ${loginTarget.label}",
                navigationIcon = { BackButton { nav.goBack() } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MiuixText("西安交通大学", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            MiuixText("统一身份认证 · ${loginTarget.description}", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

            Spacer(modifier = Modifier.height(32.dp))

            MiuixTextField(
                value = username,
                onValueChange = { v -> username = v },
                label = "学号 / 手机号",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            MiuixTextField(
                value = password,
                onValueChange = { v -> password = v },
                label = "密码",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(8.dp))

            errorMessage?.let {
                MiuixText(it, color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (statusMessage.isNotEmpty()) {
                MiuixText(statusMessage, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            MiuixButton(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "请输入学号和密码"
                        return@MiuixButton
                    }
                    isLoading = true
                    errorMessage = null
                    statusMessage = "正在连接统一认证..."

                    scope.launch {
                        try {
                            val result = loginTarget.performLogin(username, password) { msg -> statusMessage = msg }

                            when (result.state) {
                                LoginState.SUCCESS -> {
                                    statusMessage = "登录成功！"
                                    // TODO: save credentials via CredentialStore, then navigate back
                                    nav.goBack()
                                }
                                LoginState.FAIL -> {
                                    errorMessage = result.message.ifEmpty { "登录失败" }
                                    statusMessage = ""
                                }
                                LoginState.REQUIRE_MFA -> {
                                    errorMessage = "需要手机验证码（MFA），暂不支持"
                                    statusMessage = ""
                                }
                                LoginState.REQUIRE_CAPTCHA -> {
                                    errorMessage = "需要验证码，暂不支持"
                                    statusMessage = ""
                                }
                                LoginState.REQUIRE_ACCOUNT_CHOICE -> {
                                    statusMessage = "多身份场景，暂选本科生身份..."
                                    // TODO: handle account choice UI
                                    errorMessage = "需要选择身份，暂不支持自动选择"
                                    statusMessage = ""
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Logger.e("LoginScreen", "login error", e)
                            val msg = e.message ?: "未知错误"
                            errorMessage = when {
                                msg.contains("Connect") || msg.contains("connect") -> "无法连接校内网络\n请确认已连接交大校园网或 WebVPN"
                                msg.contains("Timeout") || msg.contains("timeout") -> "连接超时\n请检查网络或确认已连接校园网"
                                else -> "网络错误: $msg"
                            }
                            statusMessage = ""
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    MiuixCircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    MiuixText("登录中...", fontSize = 16.sp)
                } else {
                    MiuixText("登录", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MiuixText(
                "使用西安交通大学统一身份认证登录\n密码仅在本地加密后发送至学校服务器",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.outline
            )
        }
    }
}
