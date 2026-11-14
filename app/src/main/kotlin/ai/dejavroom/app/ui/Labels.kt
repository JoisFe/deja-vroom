package ai.dejavroom.app.ui

import ai.dejavroom.app.BuildConfig
import ai.dejavroom.app.R
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.dejaVroomColors
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.feature.DejaVroomScreen
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.DejaVroomViewModel
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RoadViewContentOrigin
import ai.dejavroom.app.model.RouteMatchStatus
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentSelectionSource
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

internal fun voiceLabel(state: DejaVroomUiState): String = when {
    !state.voiceEnabled -> "음성 안내 OFF"
    state.voiceOutputAvailable -> "음성 안내 ON"
    else -> "음성 요청 ON · TTS 미연동"
}

internal fun selectionSourceLabel(source: SegmentSelectionSource): String = when (source) {
    SegmentSelectionSource.ROUTE_ANALYSIS -> "경로 후보 분석"
    SegmentSelectionSource.PUBLIC_DATA -> "공공 데이터 후보"
    SegmentSelectionSource.CURATED_DEMO -> "사전 구성 데모"
}

internal fun contentOriginLabel(origin: RoadViewContentOrigin): String = when (origin) {
    RoadViewContentOrigin.LIVE_PROVIDER -> "공급자 도로 사진"
    RoadViewContentOrigin.CURATED -> "등록 이미지"
    RoadViewContentOrigin.DEMO_ILLUSTRATION -> "AI 생성 데모 · 실제 사진 아님"
}

internal fun routeMatchLabel(status: RouteMatchStatus): String = when (status) {
    RouteMatchStatus.VERIFIED -> "경로 일치 확인"
    RouteMatchStatus.UNVERIFIED -> "경로 일치 미확인"
    RouteMatchStatus.MISMATCH -> "경로 불일치"
    RouteMatchStatus.STALE -> "오래된 정보"
    RouteMatchStatus.NO_COVERAGE -> "사진 없음"
    RouteMatchStatus.NOT_APPLICABLE -> "실경로 매칭 미적용"
}

internal fun roadViewStatusMessage(result: RoadViewLookupResult?): String = when (result) {
    null -> "도로 사진 정보를 확인 중입니다."
    RoadViewLookupResult.NoCoverage -> "이 구간은 사진 제공 범위 밖입니다. 실제 경로 안내를 확인하세요."
    RoadViewLookupResult.NotAuthorized -> "도로 사진 제공자가 연결되지 않았습니다. Demo 그림만 표시합니다."
    is RoadViewLookupResult.Unavailable -> when (result.reason) {
        ai.dejavroom.app.data.RoadViewUnavailableReason.OFFLINE -> "네트워크 연결을 확인할 수 없습니다."
        ai.dejavroom.app.data.RoadViewUnavailableReason.TIMEOUT -> "사진 조회 시간이 초과되었습니다."
        ai.dejavroom.app.data.RoadViewUnavailableReason.QUOTA -> "사진 조회 한도에 도달했습니다."
        ai.dejavroom.app.data.RoadViewUnavailableReason.PROVIDER_ERROR -> "사진 정보를 불러오지 못했습니다."
    }
    is RoadViewLookupResult.Available -> if (result.views.isEmpty()) "이 구간의 사진이 없습니다." else "사진 정보를 확인 중입니다."
}

internal fun routeSourceLabel(source: RouteSource): String = when (source) {
    RouteSource.PLEOS_NAVIGATION -> "Pleos 경로"
    RouteSource.EXTERNAL_ROUTE -> "OSRM/OSM 경로 · 개발 서비스"
    RouteSource.CURATED_DEMO -> "Demo 경로"
}
