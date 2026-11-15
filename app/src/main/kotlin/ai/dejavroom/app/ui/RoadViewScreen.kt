package ai.dejavroom.app.ui

import ai.dejavroom.app.BuildConfig
import ai.dejavroom.app.core.designsystem.DejaVroomIcons
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.PhotoOverlay
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

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
    val dimens = DejaVroomTheme.dimens
    // The shell pads the content area, so the screen adds no outer padding of its own.
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        Row(
            Modifier.fillMaxWidth().height(dimens.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackPill(onBack)
            Spacer(Modifier.width(dimens.spaceM))
            Text(
                route?.let {
                    "${selectedIndex + 1}/${candidates.size} · ${candidates.getOrNull(selectedIndex)?.name.orEmpty()}"
                } ?: "고정 OSM fixture 테스트",
                Modifier.weight(1f),
                color = DejaVroomTheme.colors.onSurfaceVariant,
                style = DejaVroomTheme.type.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radiusLarge))
                .background(PhotoOverlay.placeholder),
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

        view?.content == RoadViewContent.Illustration ->
            "데모 제공자는 도로 사진을 보여 주지 않습니다 · config/roadview.yml에서 kakao 또는 mapillary를 선택하세요."

        else -> roadViewStatusMessage(lookup)
    }
    when {
        problem != null -> RoadViewProblem(problem, onBack)

        photo != null && view != null -> RoadViewPhoto(view, photo, Modifier.fillMaxSize())

        endpoint != null -> {
            RoadViewPage(
                endpoint = endpoint,
                config = { context ->
                    if (route != null) {
                        buildRoutePreviewConfig(route, candidates, selectedIndex)
                    } else {
                        buildJunctionPreviewConfig(context)
                    }
                },
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
        ArrivalFrame(
            atPoint = ApproachPhase.at(remaining) == ApproachPhase.AT_POINT,
            modifier = Modifier.matchParentSize(),
        )
        ManeuverBanner(
            cue = cue,
            remainingMeters = remaining,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = DejaVroomTheme.dimens.spaceL),
        )
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
                setBackgroundColor(PhotoOverlay.placeholder.toArgb())
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
                        // Page logs can carry keys and places; they are for a developer's machine only.
                        if (BuildConfig.DEBUG) {
                            val sanitized =
                                consoleMessage.message().replace(Regex("(?i)(appkey=)[^&\\s]+"), "$1[redacted]")
                            Log.d(TAG, sanitized)
                        }
                        return true
                    }
                }

                WebViewCompat.addWebMessageListener(
                    this,
                    ROADVIEW_BRIDGE_NAME,
                    setOf(endpoint.origin),
                ) { view, message, sourceOrigin, isMainFrame, _ ->
                    if (!isMainFrame || sourceOrigin.toString() != endpoint.origin) return@addWebMessageListener
                    val event = RoadViewPageEvent.parse(message.data) ?: return@addWebMessageListener
                    // Only the kind of event: the raw message carries coordinates.
                    if (BuildConfig.DEBUG) Log.d(TAG, "bridge event=${event::class.simpleName}")
                    if (event == RoadViewPageEvent.Ready) {
                        // The page is up: hand it the route and the point to show.
                        try {
                            WebViewCompat.postWebMessage(
                                view,
                                WebMessageCompat(currentConfig(view.context)),
                                Uri.parse(endpoint.origin),
                            )
                        } catch (failure: Exception) {
                            Log.w(
                                TAG,
                                "route not delivered to the page: ${failure.javaClass.simpleName}: ${failure.message}",
                            )
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

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !endpoint.isRoadViewPage(request.url.toString())

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

/**
 * Back to the list. The pill is drawn at the small button height, and the row around it takes the touch
 * as well, so the finger gets the full touch target.
 */
@Composable
private fun BackPill(onBack: () -> Unit) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    // Shared, so the press shows on the pill and not on the taller area that listens for it.
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxHeight().clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onBack,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(dimens.buttonSmall)
                .cardSurface(CircleShape, elevated = false)
                .indication(interaction, LocalIndication.current)
                .padding(start = dimens.spaceM, end = dimens.spaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(DejaVroomIcons.ChevronLeft, tint = colors.onSurface)
            Text(
                "목록",
                color = colors.onSurface,
                style = DejaVroomTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoadingNotice(modifier: Modifier = Modifier) {
    val dimens = DejaVroomTheme.dimens
    Text(
        "로드뷰를 불러오는 중…",
        modifier
            .padding(dimens.spaceM)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(PhotoOverlay.scrim)
            .padding(horizontal = dimens.spaceL, vertical = dimens.spaceS),
        color = PhotoOverlay.onScrim,
        style = DejaVroomTheme.type.labelMedium,
    )
}

/** Drawn on the dark placeholder of the photo box, so its colors do not follow the theme either. */
@Composable
private fun RoadViewProblem(message: String, onBack: () -> Unit) {
    val type = DejaVroomTheme.type
    val dimens = DejaVroomTheme.dimens
    Column(
        Modifier.fillMaxSize().padding(dimens.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("로드뷰를 표시할 수 없습니다", color = PhotoOverlay.onScrim, style = type.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            Modifier.padding(top = dimens.spaceL),
            color = PhotoOverlay.onScrimVariant,
            style = type.bodySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "목록으로 돌아가 다른 지점은 계속 볼 수 있습니다.",
            Modifier.padding(top = dimens.spaceM),
            color = PhotoOverlay.onScrimVariant,
            style = type.labelMedium,
            textAlign = TextAlign.Center,
        )
        TextAction("목록으로 돌아가기", Modifier.padding(top = dimens.dp(40)), color = PhotoOverlay.onScrim, onClick = onBack)
    }
}

/** Previous and next along the route; after the last point "next" goes back to the list. */
@Composable
private fun PointNavigation(
    selectedIndex: Int,
    count: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val last = selectedIndex >= count - 1
    val colors = DejaVroomTheme.colors
    val type = DejaVroomTheme.type
    val dimens = DejaVroomTheme.dimens
    Row(
        Modifier.fillMaxWidth().height(dimens.buttonLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceL),
    ) {
        SecondaryButton(
            "이전 지점",
            Modifier.weight(1f),
            enabled = selectedIndex > 0,
            leadingIcon = DejaVroomIcons.ChevronLeft,
            onClick = onPrevious,
        )
        // No fixed width for the count: in the narrow window the buttons need that room for their labels.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${selectedIndex + 1} / $count", color = colors.onSurface, style = type.bodyLarge, maxLines = 1)
            Text("가는 순서", color = colors.onSurfaceVariant, style = type.labelSmall, maxLines = 1)
        }
        PrimaryButton(
            if (last) "목록으로" else "다음 지점",
            Modifier.weight(1.25f),
            trailingIcon = if (last) null else DejaVroomIcons.ChevronRight,
            onClick = if (last) onBack else onNext,
        )
    }
}
