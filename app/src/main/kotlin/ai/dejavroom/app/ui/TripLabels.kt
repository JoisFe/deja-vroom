package ai.dejavroom.app.ui

import ai.dejavroom.app.domain.Trip
import ai.dejavroom.app.domain.TripCheckpoint
import ai.dejavroom.app.feature.LocationNotice

/** The headline of the screen after "주행 시작": what the list beside it is. */
internal const val NAVIGATION_HEADLINE = "미리 본 곳을 가는 순서대로 정리했습니다"

/** Why the origin could not be set to where the car is, and what the driver can do about it. */
internal fun locationNoticeText(notice: LocationNotice): String = when (notice) {
    LocationNotice.NO_NAVIGATION -> "차량 내비게이션에서 현재 위치를 받지 못했습니다. 출발지를 직접 입력해 주세요."
    LocationNotice.NO_ANSWER -> "내비게이션이 현재 위치를 알려 주지 않았습니다. 잠시 뒤 다시 눌러 주세요."
    LocationNotice.NO_POSITION -> "내비게이션이 아직 현재 위치를 잡지 못했습니다. 잠시 뒤 다시 눌러 주세요."
    LocationNotice.NO_PERMISSION -> "현재 위치를 읽을 권한이 없습니다. 출발지를 직접 입력해 주세요."
    LocationNotice.OUTSIDE_SERVICE_AREA -> "현재 위치가 경로를 찾을 수 있는 지역 밖입니다. 출발지를 직접 입력해 주세요."
}

/** One line on the search screen for a trip that can be picked up again. */
internal fun tripResumeLine(trip: Trip): String {
    val passed = trip.passed.size
    val total = passed + trip.upcoming.size
    val heading = "${trip.destination.name}까지 가던 길"
    return when {
        total == 0 -> heading
        passed == 0 -> "$heading · 미리 본 ${total}곳이 남았습니다"
        else -> "$heading · 미리 본 ${total}곳 중 ${passed}곳을 지나왔습니다"
    }
}

/** The line above the cards for what is already behind the car: "지나온 곳 2 · 중부대로 갈림길 · 영덕교 갈림길". */
internal fun passedLine(passed: List<TripCheckpoint>): String =
    "지나온 곳 ${passed.size} · ${passed.joinToString(" · ") { it.name }}"
