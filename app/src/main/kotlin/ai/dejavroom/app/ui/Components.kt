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
internal fun RouteMiniMap(activeIndex: Int, modifier: Modifier = Modifier.fillMaxWidth().height(116.dp)) {
    val colors = DejaVroomTheme.colors
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(colors.panelRaised).border(1.dp, colors.stroke.copy(alpha = .7f), RoundedCornerShape(12.dp)).padding(9.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * .08f, size.height * .78f)
                cubicTo(size.width * .30f, size.height * .72f, size.width * .20f, size.height * .16f, size.width * .48f, size.height * .30f)
                cubicTo(size.width * .73f, size.height * .43f, size.width * .66f, size.height * .82f, size.width * .91f, size.height * .20f)
            }
            drawPath(path, colors.blue.copy(alpha = .35f), style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, colors.blue, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            listOf(Offset(size.width * .24f, size.height * .69f), Offset(size.width * .48f, size.height * .30f), Offset(size.width * .72f, size.height * .47f)).forEachIndexed { index, point ->
                drawCircle(if (index == activeIndex) colors.warning else colors.white, radius = 5.dp.toPx(), center = point)
            }
        }
        Text("경로 위치 · 데모", Modifier.align(Alignment.TopStart), color = colors.muted, fontSize = 10.sp)
    }
}

@Composable
internal fun RoadScene(modifier: Modifier = Modifier, variant: Int, large: Boolean) {
    Box(modifier.clip(RoundedCornerShape(if (large) 20.dp else 0.dp)).background(DejaVroomTheme.colors.panelRaised)) {
        val illustration = when (variant) {
            1 -> R.drawable.demo_highway
            2 -> R.drawable.demo_destination
            else -> R.drawable.demo_intersection
        }
        Image(
            painter = painterResource(illustration),
            contentDescription = "가상의 도로 장면 데모 이미지",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Keep the provenance badge static over photos so its contrast never depends on the image.
        Text(if (large) "AI 생성 데모 · 실제 도로 사진 아님" else "AI 생성 데모", Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xCC111B33), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontSize = if (large) 11.sp else 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun InfoBanner(icon: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().background(DejaVroomTheme.colors.panel, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = DejaVroomTheme.colors.blueLight, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = DejaVroomTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = DejaVroomTheme.colors.muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text, color = DejaVroomTheme.colors.blueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
}

@Composable
internal fun PrimaryButton(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(54.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = DejaVroomTheme.colors.blue, contentColor = DejaVroomTheme.colors.onPrimary)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(icon, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp).background(if (enabled) DejaVroomTheme.colors.panelRaised else DejaVroomTheme.colors.panel, RoundedCornerShape(28.dp)).border(1.dp, DejaVroomTheme.colors.stroke.copy(alpha = .8f), RoundedCornerShape(28.dp))) {
        Text(text, color = if (enabled) DejaVroomTheme.colors.white else DejaVroomTheme.colors.dim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
