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

@Composable
internal fun CompleteScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route ?: return
    val layout = if (compact) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
    Column(layout.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(74.dp).background(DejaVroomTheme.colors.successContainer, CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = DejaVroomTheme.colors.green, fontSize = 40.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(18.dp))
        Text("주요 구간을 모두 확인했습니다.", color = DejaVroomTheme.colors.white, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("이제 주행을 시작할 수 있어요.", color = DejaVroomTheme.colors.muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryMetric("확인한 구간", "${route.segments.size}곳", if (compact) Modifier.weight(1f) else Modifier.width(160.dp))
            SummaryMetric("예상 소요 시간", "약 ${route.durationMinutes}분", if (compact) Modifier.weight(1f) else Modifier.width(160.dp))
            SummaryMetric("전체 거리", "${route.distanceKm}km", if (compact) Modifier.weight(1f) else Modifier.width(160.dp))
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("주행 시작", "›", if (compact) Modifier.weight(1f) else Modifier.width(230.dp), vm::startNavigation)
            SecondaryButton("다시 보기", if (compact) Modifier.weight(1f) else Modifier.width(190.dp), onClick = vm::replay)
        }
    }
}

@Composable
internal fun NavigationScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route
    if (compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.Center) {
            Eyebrow("PLEOS NAVIGATION  ·  MOCK")
            Spacer(Modifier.height(10.dp))
            Text("주행을 시작합니다", color = DejaVroomTheme.colors.white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Pleos 내비게이션 연동 전환을 시연하는 화면입니다.", color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            if (route != null) Text("${route.origin}  →  ${route.destination}  ·  약 ${route.durationMinutes}분 · ${route.distanceKm}km", color = DejaVroomTheme.colors.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("실제 목적지 설정·주행 전환 API는 연결되지 않았습니다.", color = DejaVroomTheme.colors.warning, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("브리핑 요약으로 돌아가기", Modifier.fillMaxWidth(), onClick = vm::backToSummary)
            Spacer(Modifier.height(12.dp))
            RoadScene(Modifier.fillMaxWidth().aspectRatio(1.8f), variant = 1, large = true)
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Eyebrow("PLEOS NAVIGATION  ·  MOCK")
            Spacer(Modifier.height(16.dp))
            Text("주행을 시작합니다", color = DejaVroomTheme.colors.white, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Pleos 내비게이션 연동 전환을 시연하는 화면입니다.", color = DejaVroomTheme.colors.muted, fontSize = 16.sp)
            Spacer(Modifier.height(23.dp))
            if (route != null) Text("${route.origin}  →  ${route.destination}  ·  약 ${route.durationMinutes}분 · ${route.distanceKm}km", color = DejaVroomTheme.colors.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            Text("실제 목적지 설정·주행 전환 API는 연결되지 않았습니다.", color = DejaVroomTheme.colors.warning, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            SecondaryButton("브리핑 요약으로 돌아가기", Modifier.width(250.dp), onClick = vm::backToSummary)
        }
        RoadScene(Modifier.weight(1.05f).aspectRatio(1.4f), variant = 1, large = true)
    }
}

@Composable
internal fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier.width(160.dp)) {
    Column(modifier.background(DejaVroomTheme.colors.panel, RoundedCornerShape(15.dp)).border(1.dp, DejaVroomTheme.colors.stroke.copy(alpha = .65f), RoundedCornerShape(15.dp)).padding(15.dp)) {
        Text(label, color = DejaVroomTheme.colors.muted, fontSize = 13.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = DejaVroomTheme.colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
