package ai.dejavroom.app.ui

import ai.dejavroom.app.BuildConfig
import ai.dejavroom.app.R
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.dejaVroomColors
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.data.RoutePreviewFailure
import ai.dejavroom.app.feature.DejaVroomScreen
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.DejaVroomViewModel
import ai.dejavroom.app.feature.SearchProblem
import ai.dejavroom.app.model.ApproachPhase
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.ManeuverCue
import ai.dejavroom.app.model.SegmentType
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Why the route search did not end in cards, in the driver's words: never a service name or an error code. */
internal fun searchProblemText(problem: SearchProblem): String = when (problem) {
    is SearchProblem.RouteFailed -> routeFailureText(problem.reason)
    // The setup hint says what to fix; without one, the driver is asked for a clearer place.
    is SearchProblem.PlaceSearchUnavailable -> problem.hint?.takeIf { it.isNotBlank() } ?: routeFailureText(RoutePreviewFailure.PLACE_NOT_FOUND)
}

private fun routeFailureText(reason: RoutePreviewFailure): String = when (reason) {
    RoutePreviewFailure.MISSING_ORIGIN -> "출발지를 입력하세요."
    RoutePreviewFailure.MISSING_DESTINATION -> "목적지를 입력하세요."
    RoutePreviewFailure.PLACE_NOT_FOUND -> "장소를 찾지 못했습니다. 출발지와 목적지를 구체적으로 입력해 주세요."
    RoutePreviewFailure.NO_ROUTE -> "목적지까지 가는 길을 찾지 못했습니다."
    RoutePreviewFailure.NOTHING_TO_PREVIEW -> "이 경로에서 미리 볼 지점을 찾지 못했습니다. 다른 목적지를 입력해 주세요."
    RoutePreviewFailure.OFFLINE -> "인터넷에 연결되지 않았습니다. 연결을 확인한 뒤 다시 시도해 주세요."
    RoutePreviewFailure.SERVICE_UNAVAILABLE -> "지금은 경로를 불러올 수 없습니다. 잠시 뒤 다시 시도해 주세요."
    RoutePreviewFailure.UNEXPECTED -> "경로를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요."
}

internal fun roadViewStatusMessage(result: RoadViewLookupResult?): String = when (result) {
    null -> "도로 사진 정보를 확인 중입니다."
    RoadViewLookupResult.NoCoverage -> "이 구간은 사진 제공 범위 밖입니다. 실제 경로 안내를 확인하세요."
    is RoadViewLookupResult.NotConfigured -> result.message
    is RoadViewLookupResult.Unavailable -> when (result.reason) {
        ai.dejavroom.app.data.RoadViewUnavailableReason.OFFLINE -> "네트워크 연결을 확인할 수 없습니다."
        ai.dejavroom.app.data.RoadViewUnavailableReason.TIMEOUT -> "사진 조회 시간이 초과되었습니다."
        ai.dejavroom.app.data.RoadViewUnavailableReason.QUOTA -> "사진 조회 한도에 도달했습니다."
        ai.dejavroom.app.data.RoadViewUnavailableReason.PROVIDER_ERROR -> "사진 정보를 불러오지 못했습니다."
    }
    is RoadViewLookupResult.Available -> if (result.views.isEmpty()) "이 구간의 사진이 없습니다." else "사진 정보를 확인 중입니다."
}

/** Top two human-readable reasons; source and provenance tags are left out. */
internal fun selectionReasons(segment: BriefingSegment): String = segment.selectionEvidence
    .filterNot { it.startsWith("도로 그래프") || it.startsWith("경로와 거리") || it == "경로 형상만으로 추정" }
    .take(2)
    .joinToString(" · ")

/**
 * The one line under a card title: what to do and what to look for ("오른쪽 진출로 · 표지 양재").
 * Scores, coordinates and how the point was found are not for the driver and never appear here.
 */
internal fun cardSubtitle(segment: BriefingSegment): String {
    val finalApproach = segment.selectionEvidence.firstOrNull { it.startsWith("도착 ") && it.endsWith("진입") }
    val parts = listOfNotNull(segment.cue?.summary, finalApproach)
    if (parts.isNotEmpty()) return parts.joinToString(" · ")
    // Where the data came from is not the driver's concern; with nothing else to say, say what kind of place it is.
    return selectionReasons(segment).ifBlank { segment.type.label }
}

/**
 * "140m" for a view that far before its point, rounded to the nearest 10 m; null when it already stands
 * on the point (or past it), where a distance would only be noise.
 */
internal fun leadLabel(leadMeters: Int?): String? =
    leadMeters?.takeIf { ApproachPhase.at(it) == ApproachPhase.APPROACHING }?.let { "${(it + 5) / 10 * 10}m" }

/**
 * Headline of the banner over a road view. It counts down ("300m 앞 · 오른쪽 진출로"), announces the point
 * itself ("바로 여기 · 오른쪽 진출로") and then lets go ("지나옴 · 오른쪽 진출로"). A destination is
 * arrived at rather than acted on: "도착 80m 전 · 목적지는 오른쪽", then "도착 · 목적지는 오른쪽".
 */
internal fun bannerTitle(cue: ManeuverCue, remainingMeters: Int?): String = when (ApproachPhase.at(remainingMeters)) {
    ApproachPhase.APPROACHING -> when (val distance = leadLabel(remainingMeters)) {
        null -> cue.action
        else -> if (cue.arrival) "도착 $distance 전 · ${cue.action}" else "$distance 앞 · ${cue.action}"
    }
    ApproachPhase.AT_POINT -> if (cue.arrival) "도착 · ${cue.action}" else "바로 여기 · ${cue.action}"
    ApproachPhase.PASSED -> "지나옴 · ${cue.action}"
}

/** One line per point on the final screen: "출발 후 30.0km · 오른쪽 진출로 · 표지 양재". */
internal fun recapLine(segment: BriefingSegment): String =
    "출발 후 ${String.format(java.util.Locale.KOREA, "%.1f", segment.distanceFromStartKm)}km · ${cardSubtitle(segment)}"
