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
internal fun DestinationScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    if (compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            DestinationForm(state, vm, Modifier.fillMaxWidth(), dense = true)
            Spacer(Modifier.height(16.dp))
            RouteAnalysisPreview(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            RouteDataAttribution()
        }
    } else {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            DestinationForm(state, vm, Modifier.weight(1.05f).fillMaxHeight(), dense = false)
            Column(Modifier.weight(.95f), verticalArrangement = Arrangement.Center) {
                RouteAnalysisPreview(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                RouteDataAttribution()
            }
        }
    }
}

@Composable
internal fun DestinationForm(state: DejaVroomUiState, vm: DejaVroomViewModel, modifier: Modifier, dense: Boolean) {
    Column(modifier, verticalArrangement = if (dense) Arrangement.Top else Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("FIRSTLOOK  ·  ROUTE ANALYSIS")
            }
            Spacer(Modifier.height(if (dense) 7.dp else 13.dp))
            Text("처음 가는 길도,\n처음 보는 길이 아니도록.", color = DejaVroomTheme.colors.white, fontSize = if (dense) 24.sp else 31.sp, lineHeight = if (dense) 30.sp else 39.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("출발 전에 헷갈리기 쉬운 구간을 먼저 살펴보세요.", color = DejaVroomTheme.colors.muted, fontSize = if (dense) 13.sp else 16.sp)
            Spacer(Modifier.height(if (dense) 10.dp else 18.dp))
            RouteFieldLabel("출발지")
            RouteSearchField(
                icon = "⌾",
                value = state.originInput,
                placeholder = "출발 장소를 검색하세요",
                onValueChange = vm::updateOrigin,
                dense = dense,
            )
            Spacer(Modifier.height(10.dp))
            RouteFieldLabel("목적지")
            RouteSearchField(
                icon = "⌕",
                value = state.destinationInput,
                placeholder = "목적지를 검색하세요",
                onValueChange = vm::updateDestination,
                dense = dense,
            )
            if (state.errorMessage != null) Text(state.errorMessage, color = DejaVroomTheme.colors.error, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(if (dense) 8.dp else 13.dp))
            Text("최근 목적지", color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("강남역", "코엑스", "서울역").forEach { place ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(DejaVroomTheme.colors.panel).clickable { vm.updateDestination(place) }.padding(horizontal = 10.dp, vertical = 9.dp)) {
                        Text("●  $place", color = DejaVroomTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (place == "강남역") "서울 강남구" else "최근 검색", color = DejaVroomTheme.colors.dim, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(if (dense) 8.dp else 12.dp))
            PrimaryButton(
                if (state.isSearchingRoute) "복잡 교차로 분석 중" else "복잡 구간 분석",
                if (state.isSearchingRoute) "…" else "›",
                Modifier.fillMaxWidth(),
                vm::searchRoute,
                enabled = !state.isSearchingRoute,
            )
    }
}

@Composable
internal fun RouteSearchField(
    icon: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    dense: Boolean,
) {
    Box(
        Modifier.fillMaxWidth()
            .padding(top = 7.dp)
            .background(DejaVroomTheme.colors.panel, RoundedCornerShape(15.dp))
            .border(1.dp, DejaVroomTheme.colors.stroke, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = if (dense) 12.dp else 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = DejaVroomTheme.colors.blueLight, fontSize = 21.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = DejaVroomTheme.colors.dim, fontSize = 17.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = DejaVroomTheme.colors.white, fontSize = 17.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun RouteAnalysisPreview(modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(DejaVroomTheme.colors.panel).border(1.dp, DejaVroomTheme.colors.stroke, RoundedCornerShape(22.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("경로 속 복잡 교차로를 찾아\n로드뷰로 바로 연결합니다", color = DejaVroomTheme.colors.white, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        listOf(
            "01" to "목적지 검색 후 실제 도로 경로 계산",
            "02" to "교차로 갈래 수와 분기 형태 분석·점수화",
            "03" to "점수가 높은 구간 중 로드뷰가 있는 곳 표시",
        ).forEach { (number, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number, color = DejaVroomTheme.colors.blueLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(label, color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun RouteDataAttribution() {
    Text("경로·도로 그래프: OpenStreetMap / OSRM 개발 서비스 · 복잡도는 차로·현재 위험도 의미가 아닙니다.", color = DejaVroomTheme.colors.dim, fontSize = 11.sp, lineHeight = 14.sp)
}

@Composable
internal fun RouteFieldLabel(label: String) {
    Text(label, color = DejaVroomTheme.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

