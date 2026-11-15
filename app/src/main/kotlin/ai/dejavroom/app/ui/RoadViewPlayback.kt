package ai.dejavroom.app.ui

import ai.dejavroom.app.data.RoadViewPageEvent

/** Why the road view page could not show a picture. */
internal enum class RoadViewFailure {
    /** This WebView cannot exchange messages with the page safely. */
    NO_SECURE_MESSAGING,
    ROUTE_NOT_DELIVERED,

    /** The page never said anything. */
    PAGE_SILENT,

    /** The SDK file arrived but the SDK never came up. */
    SDK_STALLED,
    TIMED_OUT,
    NO_PHOTOS_NEARBY,
    KEY_MISSING,
    SDK_NOT_LOADED,
    NO_PANORAMA,
    PANORAMA_LOOKUP_TIMED_OUT,
    NOT_RENDERED,
    NAVIGATION_BLOCKED,
    PAGE_UNREACHABLE,
    RENDERER_GONE;

    companion object {
        fun of(reason: RoadViewPageEvent.FailureReason): RoadViewFailure = when (reason) {
            RoadViewPageEvent.FailureReason.CONFIG -> KEY_MISSING
            RoadViewPageEvent.FailureReason.SDK_LOAD -> SDK_NOT_LOADED
            RoadViewPageEvent.FailureReason.NO_PANO -> NO_PANORAMA
            RoadViewPageEvent.FailureReason.TIMEOUT -> PANORAMA_LOOKUP_TIMED_OUT
            RoadViewPageEvent.FailureReason.RENDER -> NOT_RENDERED
        }
    }
}

/**
 * What one point's road view page has told the app so far, reduced to what the screen shows. Each point
 * starts with a fresh one, so nothing a point learned (how far it got, what went wrong) leaks into the next.
 */
internal data class RoadViewPlayback(
    val stage: Stage = Stage.PREPARING,
    /** How far the point still is from the photo on screen; negative once past. Null until the page says. */
    val remainingMeters: Int? = null,
    val failure: RoadViewFailure? = null,
) {
    enum class Stage { PREPARING, CONNECTED, SDK_LOADED, SDK_READY, SHOWN }

    val isShown: Boolean get() = stage == Stage.SHOWN
    val isConnected: Boolean get() = stage >= Stage.CONNECTED

    /** The state after the page reported [event]. A failure is final: the page is unloaded after it. */
    fun after(event: RoadViewPageEvent): RoadViewPlayback = if (failure != null) this else when (event) {
        RoadViewPageEvent.Ready -> reaching(Stage.CONNECTED)
        RoadViewPageEvent.SdkLoaded -> reaching(Stage.SDK_LOADED)
        RoadViewPageEvent.SdkReady -> reaching(Stage.SDK_READY)
        RoadViewPageEvent.Shown, is RoadViewPageEvent.RouteCandidateSelected -> reaching(Stage.SHOWN)
        is RoadViewPageEvent.DriveProgress -> copy(remainingMeters = event.remainingMeters)
        RoadViewPageEvent.CandidateNoCoverage -> copy(failure = RoadViewFailure.NO_PHOTOS_NEARBY)
        is RoadViewPageEvent.Failed -> copy(failure = RoadViewFailure.of(event.reason))
        else -> this
    }

    fun failed(reason: RoadViewFailure): RoadViewPlayback = if (failure != null) this else copy(failure = reason)

    /** The page had this long to say anything at all. */
    fun afterWaitingForPage(): RoadViewPlayback = if (stage == Stage.PREPARING) failed(RoadViewFailure.PAGE_SILENT) else this

    /** The page had this long, once connected, to show a picture. How far it got says what went wrong. */
    fun afterWaitingForPicture(): RoadViewPlayback = when {
        isShown -> this
        stage == Stage.SDK_LOADED -> failed(RoadViewFailure.SDK_STALLED)
        else -> failed(RoadViewFailure.TIMED_OUT)
    }

    /** Stages only move forward; a late or repeated message never takes the screen back. */
    private fun reaching(next: Stage): RoadViewPlayback = if (next > stage) copy(stage = next) else this
}

/** What to tell the person in front of the screen. Setup problems name what to check. */
internal fun roadViewFailureText(failure: RoadViewFailure): String = when (failure) {
    RoadViewFailure.NO_SECURE_MESSAGING -> "이 Android System WebView는 보안 메시지 기능을 지원하지 않습니다."
    RoadViewFailure.ROUTE_NOT_DELIVERED -> "경로 정보를 로드뷰에 전달하지 못했습니다."
    RoadViewFailure.PAGE_SILENT -> "로드뷰 페이지가 응답하지 않습니다. 네트워크와 로드뷰 주소 설정을 확인하세요."
    RoadViewFailure.SDK_STALLED -> "로드뷰를 준비하다 멈췄습니다. 네트워크를 확인하세요."
    RoadViewFailure.TIMED_OUT -> "로드뷰 표시 시간이 초과되었습니다. 네트워크를 확인하세요."
    RoadViewFailure.NO_PHOTOS_NEARBY -> "이 지점 근처에는 로드뷰 사진이 없습니다."
    RoadViewFailure.KEY_MISSING -> "Kakao JavaScript 키가 페이지에 없습니다. docs/roadview-setup.md를 확인하세요."
    RoadViewFailure.SDK_NOT_LOADED -> "Kakao 로드뷰를 불러오지 못했습니다. 키, Web 플랫폼 도메인 등록, 네트워크를 확인하세요."
    RoadViewFailure.NO_PANORAMA -> "선택 지점 근처에 로드뷰 사진이 없습니다."
    RoadViewFailure.PANORAMA_LOOKUP_TIMED_OUT -> "로드뷰 사진 조회 시간이 초과되었습니다."
    RoadViewFailure.NOT_RENDERED -> "로드뷰 화면을 표시하지 못했습니다."
    RoadViewFailure.NAVIGATION_BLOCKED -> "허용되지 않은 페이지 이동을 차단했습니다."
    RoadViewFailure.PAGE_UNREACHABLE -> "로드뷰 페이지에 연결할 수 없습니다. 네트워크와 로드뷰 주소 설정을 확인하세요."
    RoadViewFailure.RENDERER_GONE -> "로드뷰 화면이 종료되었습니다. 돌아가서 다시 열어 주세요."
}
