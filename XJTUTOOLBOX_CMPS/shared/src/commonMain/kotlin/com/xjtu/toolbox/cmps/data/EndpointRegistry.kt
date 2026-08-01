package com.xjtu.toolbox.cmps.data

enum class CampusEndpoint(val label: String, val url: String, val needsCampusNetwork: Boolean = true) {
    Cas("统一身份认证", "https://login.xjtu.edu.cn/cas/login", false),
    JwApp("教务系统", "https://jwapp.xjtu.edu.cn"),
    JwxtJudge("本科评教", "https://jwxt.xjtu.edu.cn/jwapp"),
    UndergraduateJw("本科教务", "https://ehall.xjtu.edu.cn"),
    Postgraduate("研究生系统", "https://gs.xjtu.edu.cn"),
    GraduateJudge("研究生评教", "http://gste.xjtu.edu.cn"),
    Lms("思源学习空间", "https://lms.xjtu.edu.cn"),
    ClassReplay("课堂回放", "https://class.xjtu.edu.cn"),
    CampusCard("校园卡", "https://card.xjtu.edu.cn"),
    LibrarySeat("图书馆座位", "http://rg.lib.xjtu.edu.cn:8086"),
    Venue("场馆预订", "http://202.117.17.144:8071"),
    WebVpn("WebVPN", "https://webvpn.xjtu.edu.cn", false),
    Ywtb("一网通办", "https://ywtb.xjtu.edu.cn"),
    Fitness("体测查询", "https://tyxylp.xjtu.edu.cn"),
    Attendance("本科考勤", "https://bkkq.xjtu.edu.cn"),
    Coupon("加餐券", "https://egc.xjtu.edu.cn"),
}

data class EndpointHealth(
    val endpoint: CampusEndpoint,
    val reachable: Boolean,
    val viaWebVpn: Boolean = false,
    val latencyMs: Long? = null,
    val message: String = "",
)

class WebVpnUrlRewriter(
    private val webVpnHost: String = CampusEndpoint.WebVpn.url.removePrefix("https://"),
) {
    fun rewriteIfNeeded(endpoint: CampusEndpoint, campusOnline: Boolean): String {
        if (!endpoint.needsCampusNetwork || campusOnline) return endpoint.url
        val normalized = endpoint.url.removePrefix("https://").removePrefix("http://")
        return "https://$webVpnHost/http/$normalized"
    }
}
