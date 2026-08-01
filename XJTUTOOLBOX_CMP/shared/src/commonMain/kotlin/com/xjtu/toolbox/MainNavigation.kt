package com.xjtu.toolbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xjtu.toolbox.ui.screen.*

object Routes {
    const val HOME = "home"
    const val SCHEDULE = "schedule"
    const val CAMPUS_CARD = "campus_card"
    const val SCORE = "score"
    const val LIBRARY = "library"
    const val EMPTY_ROOM = "empty_room"
    const val CALENDAR = "calendar"
    const val CLASS_REPLAY = "class_replay"
    const val ATTENDANCE = "attendance"
    const val VENUE = "venue"
    const val NOTIFICATION = "notification"
    const val JUDGE = "judge"
    const val GSTE_JUDGE = "gste_judge"
    const val LMS = "lms"
    const val DZPZ = "dzpz"
    const val PAYMENT_CODE = "payment_code"
    const val GMIS = "gmis"
    const val YWTB = "ywtb"
    const val NSA = "nsa"
    const val BROWSER = "browser"
    const val LOGIN = "login"
    const val JWAPP_SCORE = "jwapp_score"
    const val VIDEO_PLAYER = "video_player"
    const val SCHOOL_COURSE = "school_course"
}

class NavigationState {
    var currentRoute by mutableStateOf(Routes.HOME)
        private set
    private val backStack = mutableListOf<String>()
    var routeArgs = mutableMapOf<String, Any?>()
        private set

    fun navigate(route: String, args: Map<String, Any?> = emptyMap()) {
        backStack.add(currentRoute)
        routeArgs = args.toMutableMap()
        currentRoute = route
    }

    fun goBack(): Boolean {
        if (backStack.isEmpty()) return false
        routeArgs.clear()
        currentRoute = backStack.removeLast()
        return true
    }

    fun popToRoot() {
        backStack.clear()
        routeArgs.clear()
        currentRoute = Routes.HOME
    }

    val canGoBack: Boolean get() = backStack.isNotEmpty()
}

@Composable
fun MainNavigation(startRoute: String = Routes.HOME) {
    val nav = remember { NavigationState().apply { if (startRoute != Routes.HOME) navigate(startRoute) } }

    CompositionLocalProvider(LocalNavigation provides nav) {
        when (nav.currentRoute) {
            Routes.HOME -> HomeScreen()
            Routes.LOGIN -> LoginScreen()
            Routes.SCHEDULE -> ScheduleScreen()
            Routes.CAMPUS_CARD -> CampusCardScreen()
            Routes.SCORE -> ScoreReportScreen()
            Routes.LIBRARY -> LibraryScreen()
            Routes.EMPTY_ROOM -> EmptyRoomScreen()
            Routes.CALENDAR -> SchoolCalendarScreen()
            Routes.CLASS_REPLAY -> ClassScreen()
            Routes.ATTENDANCE -> AttendanceScreen()
            Routes.VENUE -> VenueScreen()
            Routes.NOTIFICATION -> NotificationScreen()
            Routes.JUDGE -> JudgeScreen()
            Routes.GSTE_JUDGE -> GsteJudgeScreen()
            Routes.LMS -> LmsScreen()
            Routes.DZPZ -> TranscriptScreen()
            Routes.PAYMENT_CODE -> PaymentCodeScreen()
            Routes.GMIS -> GmisScreen()
            Routes.YWTB -> YwtbScreen()
            Routes.NSA -> NsaScreen()
            Routes.BROWSER -> BrowserScreen()
            Routes.JWAPP_SCORE -> JwappScoreScreen()
            Routes.VIDEO_PLAYER -> VideoPlayerScreen()
            Routes.SCHOOL_COURSE -> SchoolCourseScreen()
        }
    }
}
