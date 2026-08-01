package com.xjtu.toolbox.cmps.app

enum class RootTab(val label: String) {
    Home("首页"),
    Schedule("日程"),
    Tools("工具"),
    Profile("我的"),
}

sealed interface AppRoute {
    val title: String

    data object Main : AppRoute { override val title = "岱宗盒子" }
    data object Login : AppRoute { override val title = "统一身份认证" }
    data object Accounts : AppRoute { override val title = "账号管理" }
    data object Settings : AppRoute { override val title = "设置" }
    data object EmptyRoom : AppRoute { override val title = "空闲教室" }
    data object CampusCard : AppRoute { override val title = "校园卡" }
    data object PaymentCode : AppRoute { override val title = "付款码" }
    data object Coupon : AppRoute { override val title = "加餐券" }
    data object Score : AppRoute { override val title = "成绩" }
    data object Transcript : AppRoute { override val title = "成绩单" }
    data object Gmis : AppRoute { override val title = "研究生教务" }
    data object Attendance : AppRoute { override val title = "考勤" }
    data object PostgraduateAttendance : AppRoute { override val title = "研考勤" }
    data object Lms : AppRoute { override val title = "思源" }
    data object ClassReplay : AppRoute { override val title = "课程回放" }
    data object Library : AppRoute { override val title = "图书馆" }
    data object Venue : AppRoute { override val title = "场馆预订" }
    data object Fitness : AppRoute { override val title = "体测查询" }
    data object SchoolCourse : AppRoute { override val title = "课程查询" }
    data object Judge : AppRoute { override val title = "评教" }
    data object Jiaocai : AppRoute { override val title = "教材" }
    data object Notification : AppRoute { override val title = "通知公告" }
    data object YellowPage : AppRoute { override val title = "校园黄页" }
    data object SchoolCalendar : AppRoute { override val title = "校历" }
    data object WebVpn : AppRoute { override val title = "WebVPN" }
    data object MobileJiaoda : AppRoute { override val title = "移动交大" }
    data object Browser : AppRoute { override val title = "内置浏览器" }
    data object Downloads : AppRoute { override val title = "下载管理" }
    data object Cache : AppRoute { override val title = "缓存状态" }
    data object Jiaoxiaozhi : AppRoute { override val title = "交晓智" }
    data object Agent : AppRoute { override val title = "屁岱" }
}

data class ServiceEntry(
    val route: AppRoute,
    val label: String,
    val group: ServiceGroup,
    val loginRequired: Boolean = false,
    val primary: Boolean = false,
)

enum class ServiceGroup(val title: String) {
    Class("上课"),
    Study("学业"),
    Campus("校园生活"),
    Utility("工具与助手"),
}

val serviceCatalog = listOf(
    ServiceEntry(AppRoute.EmptyRoom, "空闲教室", ServiceGroup.Class, primary = true),
    ServiceEntry(AppRoute.Lms, "思源", ServiceGroup.Class, loginRequired = true, primary = true),
    ServiceEntry(AppRoute.ClassReplay, "课程回放", ServiceGroup.Class, loginRequired = true),
    ServiceEntry(AppRoute.SchoolCourse, "课程查询", ServiceGroup.Class, loginRequired = true),
    ServiceEntry(AppRoute.Attendance, "考勤", ServiceGroup.Class, loginRequired = true),
    ServiceEntry(AppRoute.PostgraduateAttendance, "研考勤", ServiceGroup.Class, loginRequired = true),
    ServiceEntry(AppRoute.Score, "成绩", ServiceGroup.Study, loginRequired = true, primary = true),
    ServiceEntry(AppRoute.Gmis, "研究生教务", ServiceGroup.Study, loginRequired = true),
    ServiceEntry(AppRoute.Judge, "评教", ServiceGroup.Study, loginRequired = true),
    ServiceEntry(AppRoute.Jiaocai, "教材", ServiceGroup.Study, loginRequired = true),
    ServiceEntry(AppRoute.Library, "图书馆", ServiceGroup.Study, loginRequired = true, primary = true),
    ServiceEntry(AppRoute.Transcript, "成绩单", ServiceGroup.Study, loginRequired = true),
    ServiceEntry(AppRoute.Notification, "通知公告", ServiceGroup.Study),
    ServiceEntry(AppRoute.CampusCard, "校园卡", ServiceGroup.Campus, loginRequired = true, primary = true),
    ServiceEntry(AppRoute.PaymentCode, "付款码", ServiceGroup.Campus, loginRequired = true),
    ServiceEntry(AppRoute.Coupon, "加餐券", ServiceGroup.Campus, loginRequired = true),
    ServiceEntry(AppRoute.SchoolCalendar, "校历", ServiceGroup.Campus),
    ServiceEntry(AppRoute.Venue, "场馆预订", ServiceGroup.Campus, loginRequired = true),
    ServiceEntry(AppRoute.Fitness, "体测查询", ServiceGroup.Campus, loginRequired = true),
    ServiceEntry(AppRoute.YellowPage, "校园黄页", ServiceGroup.Campus),
    ServiceEntry(AppRoute.WebVpn, "WebVPN", ServiceGroup.Utility),
    ServiceEntry(AppRoute.MobileJiaoda, "移动交大", ServiceGroup.Utility, loginRequired = true),
    ServiceEntry(AppRoute.Browser, "内置浏览器", ServiceGroup.Utility),
    ServiceEntry(AppRoute.Downloads, "下载管理", ServiceGroup.Utility),
    ServiceEntry(AppRoute.Cache, "缓存状态", ServiceGroup.Utility),
    ServiceEntry(AppRoute.Jiaoxiaozhi, "交晓智", ServiceGroup.Utility, loginRequired = true),
    ServiceEntry(AppRoute.Agent, "屁岱", ServiceGroup.Utility, primary = true),
)
