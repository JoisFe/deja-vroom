package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.data.ROADVIEW_BRIDGE_NAME
import ai.dejavroom.app.data.RoadViewContent
import ai.dejavroom.app.data.RoadViewEndpoint
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.data.RoadViewPageEvent
import ai.dejavroom.app.model.ApproachPhase
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RouteSnapshot
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private const val TAG = "DejaVroomRoadview"

/** How long the page has to say anything at all, and then, once connected, to show a picture. */
private val PageAnswerTimeout = 15.seconds
private val ShowPictureTimeout = 12.seconds

@Composable
internal fun RoadViewScreen(
    /** Result of the configured provider for the selected point; the screen renders it by content type. */
    lookup: RoadViewLookupResult?,
    route: RouteSnapshot?,
    candidates: List<BriefingSegment>,
    selectedIndex: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹  목록", color = DejaVroomTheme.colors.white) }
            Text(
                route?.let { "${selectedIndex + 1}/${candidates.size} · ${candidates.getOrNull(selectedIndex)?.name.orEmpty()}" } ?: "고정 OSM fixture 테스트",
                Modifier.weight(1f),
                color = DejaVroomTheme.colors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF17202D)),
        ) {
            // Everything a point's road view learns lives and dies with that point. Moving on to the next
            // one starts from nothing, whatever the last one had reached or was still waiting for.
            key(route?.routeId, selectedIndex, lookup) {
                RoadViewPoint(lookup, route, candidates, selectedIndex, onBack)
            }
        }
        if (route != null) PointNavigation(selectedIndex, candidates.size, onBack, onPrevious, onNext)
    }
}

/** One point: its picture (or why there is none), and over it the banner that follows the picture. */
@Composable
private fun BoxScope.RoadViewPoint(
    lookup: RoadViewLookupResult?,
    route: RouteSnapshot?,
    candidates: List<BriefingSegment>,
    selectedIndex: Int,
    onBack: () -> Unit,
) {
    val view = (lookup as? RoadViewLookupResult.Available)?.views?.firstOrNull()
    val endpoint = (view?.content as? RoadViewContent.InteractivePage)?.endpoint
    val photo = view?.content as? RoadViewContent.StillImage
    var playback by remember { mutableStateOf(RoadViewPlayback()) }
    val problem = when {
        endpoint != null || photo != null -> playback.failure?.let(::roadViewFailureText)
        view?.content == RoadViewContent.Illustration -> "데모 제공자는 도로 사진을 보여 주지 않습니다 · config/roadview.yml에서 kakao 또는 mapillary를 선택하세요."
        else -> roadViewStatusMessage(lookup)
    }
    when {
        problem != null -> RoadViewProblem(problem, onBack)
        photo != null && view != null -> RoadViewPhoto(view, photo, Modifier.fillMaxSize())
        endpoint != null -> {
            RoadViewPage(
                endpoint = endpoint,
                config = { context -> if (route != null) buildRoutePreviewConfig(route, candidates, selectedIndex) else buildJunctionPreviewConfig(context) },
                onEvent = { playback = playback.after(it) },
                onFailure = { playback = playback.failed(it) },
            )
            // The deadlines belong to this point: leaving it cancels them, so they can never fail the next one.
            LaunchedEffect(Unit) {
                delay(PageAnswerTimeout)
                playback = playback.afterWaitingForPage()
            }
            LaunchedEffect(playback.isConnected) {
                if (!playback.isConnected) return@LaunchedEffect
                delay(ShowPictureTimeout)
                playback = playback.afterWaitingForPicture()
            }
            if (!playback.isShown) LoadingNotice(Modifier.align(Alignment.BottomCenter))
        }
    }
    val selected = candidates.getOrNull(selectedIndex)
    val cue = selected?.cue
    if (problem == null && route != null && selected != null && cue != null) {
        // Until the page reports where its photo stands, the view is where the preview was planned to start.
        val remaining = playback.remainingMeters ?: selected.approach?.leadMeters
        ArrivalFrame(atPoint = ApproachPhase.at(remaining) == ApproachPhase.AT_POINT, modifier = Modifier.matchParentSize())
        ManeuverBanner(cue = cue, remainingMeters = remaining, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
    }
}

/**
 * The road view page in a WebView. It tells the app what happens through [onEvent] and [onFailure] and
 * nothing else; what that means for the screen is [RoadViewPlayback]'s business. [config] is asked for
 * when the page says it is ready.
 */
@Composable
private fun RoadViewPage(
    endpoint: RoadViewEndpoint,
    config: (Context) -> String,
    onEvent: (RoadViewPageEvent) -> Unit,
    onFailure: (RoadViewFailure) -> Unit,
) {
    // The WebView outlives recompositions, so it always calls the latest of these.
    val currentConfig by rememberUpdatedState(config)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnFailure by rememberUpdatedState(onFailure)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.rgb(23, 32, 45))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }

                if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                    !WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)
                ) {
                    currentOnFailure(RoadViewFailure.NO_SECURE_MESSAGING)
                    return@apply
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        val sanitized = consoleMessage.message().replace(Regex("(?i)(appkey=)[^&\\s]+"), "$1[redacted]")
                        Log.d(TAG, sanitized)
                        return true
                    }
                }

                WebViewCompat.addWebMessageListener(
                    this,
                    ROADVIEW_BRIDGE_NAME,
                    setOf(endpoint.origin),
                ) { view, message, sourceOrigin, isMainFrame, _ ->
                    Log.d(TAG, "bridge source=$sourceOrigin main=$isMainFrame event=${message.data}")
                    if (!isMainFrame || sourceOrigin.toString() != endpoint.origin) return@addWebMessageListener
                    val event = RoadViewPageEvent.parse(message.data) ?: return@addWebMessageListener
                    if (event == RoadViewPageEvent.Ready) {
                        // The page is up: hand it the route and the point to show.
                        try {
                            WebViewCompat.postWebMessage(view, WebMessageCompat(currentConfig(view.context)), Uri.parse(endpoint.origin))
                        } catch (failure: Exception) {
                            Log.w(TAG, "route not delivered to the page: ${failure.javaClass.simpleName}: ${failure.message}")
                            currentOnFailure(RoadViewFailure.ROUTE_NOT_DELIVERED)
                            return@addWebMessageListener
                        }
                    }
                    currentOnEvent(event)
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        if (url == "about:blank") return
                        if (!endpoint.isRoadViewPage(url)) {
                            currentOnFailure(RoadViewFailure.NAVIGATION_BLOCKED)
                            view.stopLoading()
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return !endpoint.isRoadViewPage(request.url.toString())
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) currentOnFailure(RoadViewFailure.PAGE_UNREACHABLE)
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        currentOnFailure(RoadViewFailure.RENDERER_GONE)
                        return true
                    }
                }
                loadUrl(endpoint.detailUrl)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        },
    )
}

@Composable
private fun LoadingNotice(modifier: Modifier = Modifier) {
    Text(
        "로드뷰를 불러오는 중…",
        modifier.padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xE6111B33)).padding(horizontal = 14.dp, vertical = 9.dp),
        color = Color.White,
        fontSize = 14.sp,
    )
}

@Composable
private fun RoadViewProblem(message: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("로드뷰를 표시할 수 없습니다", color = Color.White, fontSize = 22.sp)
        Text(
            message,
            Modifier.padding(top = 12.dp),
            color = Color(0xFFCED7E5),
            fontSize = 15.sp,
        )
        Text(
            "목록으로 돌아가 다른 지점은 계속 볼 수 있습니다.",
            Modifier.padding(top = 8.dp),
            color = Color(0xFF9EACC0),
            fontSize = 13.sp,
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) {
            Text("목록으로 돌아가기", color = Color(0xFF8AB4FF))
        }
    }
}

/** Previous and next along the route; after the last point "next" goes back to the list. */
@Composable
private fun PointNavigation(selectedIndex: Int, count: Int, onBack: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val last = selectedIndex >= count - 1
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).height(60.dp)
            .clip(RoundedCornerShape(16.dp)).background(DejaVroomTheme.colors.panel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onPrevious,
            enabled = selectedIndex > 0,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF26364A),
                disabledContainerColor = Color(0xFF202A37),
                disabledContentColor = Color(0xFF65758A),
            ),
        ) {
            Text("‹  이전 지점")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${selectedIndex + 1} / $count", color = DejaVroomTheme.colors.white, fontSize = 15.sp)
            Text("가는 순서", color = DejaVroomTheme.colors.muted, fontSize = 10.sp)
        }
        Button(
            onClick = if (last) onBack else onNext,
            modifier = Modifier.weight(1.25f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1555C9)),
        ) {
            Text(if (last) "목록으로" else "다음 지점  ›")
        }
    }
}
