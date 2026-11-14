package ai.firstlook.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ai.firstlook.app.core.designsystem.FirstLookTheme
import ai.firstlook.app.data.JunctionRecordMatch
import ai.firstlook.app.data.JunctionRecordMatcher
import ai.firstlook.app.data.RoadViewPageEvent
import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.JunctionDataSource
import ai.firstlook.app.model.JunctionRecord
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.BriefingSegment
import ai.firstlook.app.model.RouteSource
import org.json.JSONArray
import org.json.JSONObject

private const val ROADVIEW_ORIGIN = "http://localhost:8080"
private const val ROADVIEW_PAGE = "$ROADVIEW_ORIGIN/roadview/"
// Kakao's documented Roadview sample panorama; a fixed development fixture, not route matching.
private const val JUNCTION_FIXTURE = "junctions/hannam-daero-demo-record.json"

@Composable
internal fun RoadViewTestScreen(
    route: RouteSnapshot?,
    candidates: List<BriefingSegment>,
    selectedIndex: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val state = remember { mutableStateOf("Kakao Roadview 개발 테스트를 준비하고 있습니다.") }
    val failureMessage = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedIndex) {
        failureMessage.value = null
        state.value = "후보 ${selectedIndex + 1}/${candidates.size} · Roadview를 불러오고 있습니다."
    }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹  후보 목록", color = FirstLookTheme.colors.white) }
            Text(
                route?.let { "${selectedIndex + 1}/${candidates.size} · ${candidates.getOrNull(selectedIndex)?.name.orEmpty()}" } ?: "고정 OSM fixture 테스트",
                Modifier.weight(1f),
                color = FirstLookTheme.colors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF17202D)),
        ) {
            if (failureMessage.value == null) {
            key(route?.routeId, selectedIndex) {
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
                            failureMessage.value = "이 Android System WebView는 보안 메시지 기능을 지원하지 않습니다."
                            return@apply
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                val sanitized = consoleMessage.message().replace(Regex("(?i)(appkey=)[^&\\s]+"), "$1[redacted]")
                                android.util.Log.d("FirstLookRoadview", sanitized)
                                return true
                            }
                        }

                        val timeoutHandler = Handler(Looper.getMainLooper())
                        var timeout: Runnable? = null
                        WebViewCompat.addWebMessageListener(
                            this,
                            "FirstLookBridge",
                            setOf(ROADVIEW_ORIGIN),
                        ) { view, message, sourceOrigin, isMainFrame, _ ->
                            android.util.Log.d("FirstLookRoadview", "bridge source=$sourceOrigin main=$isMainFrame event=${message.data}")
                            if (!isMainFrame || sourceOrigin.toString() != ROADVIEW_ORIGIN) return@addWebMessageListener
                            val event = RoadViewPageEvent.parse(message.data)
                            when (event) {
                                RoadViewPageEvent.Ready -> {
                                    timeout?.let(timeoutHandler::removeCallbacks)
                                    state.value = if (route != null) "선택한 복잡 구간의 Kakao Roadview coverage를 찾고 있습니다." else "고정 OSM 교차로의 Kakao Roadview를 찾고 있습니다."
                                    try {
                                        WebViewCompat.postWebMessage(
                                            view,
                                            WebMessageCompat(if (route != null) buildRoutePreviewConfig(route, candidates, selectedIndex) else buildJunctionPreviewConfig(view.context)),
                                            Uri.parse(ROADVIEW_ORIGIN),
                                        )
                                    } catch (failure: Exception) {
                                        failureMessage.value = "분석된 경로 데이터를 Roadview에 전달하지 못했습니다: ${failure.message ?: "확인 필요"}"
                                        return@addWebMessageListener
                                    }
                                    val failTimeout = Runnable {
                                        failureMessage.value = if (state.value.startsWith("Kakao SDK 파일을 받았습니다")) {
                                            "Kakao SDK 파일은 왔지만 Maps 초기화 응답이 없습니다. 네트워크 또는 SDK 호환성을 확인하세요."
                                        } else {
                                            "로드뷰 표시 시간이 초과되었습니다. 로컬 서버·네트워크를 확인하세요."
                                        }
                                        view.loadUrl("about:blank")
                                    }
                                    timeout = failTimeout
                                    timeoutHandler.postDelayed(failTimeout, 12_000)
                                }
                                RoadViewPageEvent.ConfigReceived -> state.value = if (route != null) "경로 후보 ${candidates.size}개를 점수순으로 전달했습니다." else "검수된 OSM fixture를 WebView에 전달했습니다."
                                RoadViewPageEvent.SdkLoaded -> state.value = "Kakao SDK 파일을 받았습니다. Maps 초기화를 기다립니다."
                                RoadViewPageEvent.SdkReady -> state.value = "Kakao SDK가 준비됐습니다. 가까운 파노라마를 확인합니다."
                                RoadViewPageEvent.CspBlocked -> state.value = "Kakao SDK 후속 리소스가 페이지 보안 정책에 차단됐습니다."
                                RoadViewPageEvent.Shown -> {
                                    timeout?.let(timeoutHandler::removeCallbacks)
                                    if (!state.value.startsWith("선택 후보")) {
                                        state.value = if (route != null) "Kakao Roadview · 분석 경로 후보" else "Kakao Roadview · OSM fixture"
                                    }
                                }
                                is RoadViewPageEvent.RouteCandidateSelected -> {
                                    val candidate = candidates.getOrNull(event.index)
                                    state.value = candidate?.let { "선택 후보 ${event.index + 1}/${candidates.size} · ${it.name} · 점수 ${it.selectionScore?.times(100)?.toInt() ?: 0}" }
                                        ?: "분석된 경로 후보를 Roadview에 표시했습니다."
                                }
                                RoadViewPageEvent.CandidateNoCoverage -> {
                                    timeout?.let(timeoutHandler::removeCallbacks)
                                    failureMessage.value = "선택한 후보의 50m 범위에서 Roadview를 찾지 못했습니다. 후보 목록으로 돌아가 다른 구간을 고를 수 있습니다."
                                }
                                is RoadViewPageEvent.NativeArrowCandidatesHidden -> state.value = "화살표 후보 DOM ${event.count}개 숨김 · Kakao 기본 요소인지 미검증"
                                RoadViewPageEvent.NativeArrowLayerUnverified -> Unit
                                is RoadViewPageEvent.NativeArrowScaled -> state.value = "Kakao 기본 이동 화살표 ${event.count}개를 원래 크기의 45%로 축소했습니다."
                                is RoadViewPageEvent.RouteOverlayReady -> state.value = "자체 경로 화살표 ${event.count}개 표시 · 개발 검증 상태"
                                is RoadViewPageEvent.JunctionOverlayReady -> state.value = "OSM 경로 매칭 완료 · 교차로 표식 ${event.count}개 표시"
                                is RoadViewPageEvent.OverlaySuppressed -> state.value = "경로 화살표 표시 안 함 · ${event.reason}"
                                is RoadViewPageEvent.Failed -> {
                                    timeout?.let(timeoutHandler::removeCallbacks)
                                    failureMessage.value = when (event.reason) {
                                        RoadViewPageEvent.FailureReason.CONFIG -> "로컬 키 설정을 확인하세요."
                                        RoadViewPageEvent.FailureReason.SDK_LOAD -> "Kakao SDK 로드 실패 · 키, localhost 허용 도메인, 네트워크를 확인하세요."
                                        RoadViewPageEvent.FailureReason.NO_PANO -> "테스트 좌표 반경 50m에 파노라마가 없습니다."
                                        RoadViewPageEvent.FailureReason.TIMEOUT -> "파노라마 조회 시간이 초과되었습니다."
                                        RoadViewPageEvent.FailureReason.RENDER -> "파노라마 화면을 표시하지 못했습니다."
                                    }
                                    view.loadUrl("about:blank")
                                }
                                null -> Unit
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                if (url == "about:blank") return
                                if (!url.startsWith("$ROADVIEW_ORIGIN/roadview")) {
                                    failureMessage.value = "허용되지 않은 페이지 이동을 차단했습니다."
                                    view.stopLoading()
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                return !(uri.scheme == "http" && uri.host == "localhost" && uri.port == 8080 && uri.path in setOf("/roadview", "/roadview/"))
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                if (request.isForMainFrame) failureMessage.value = "로컬 Roadview 페이지에 연결할 수 없습니다. serve.sh 실행과 adb reverse를 확인하세요."
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                failureMessage.value = "WebView 렌더러가 종료되었습니다. 돌아가서 다시 열어 주세요."
                                return true
                            }
                        }
                        val pageLoadTimeout = Runnable {
                            if (state.value == "Kakao Roadview 개발 테스트를 준비하고 있습니다.") {
                                failureMessage.value = "Kakao SDK가 응답하지 않습니다. 로컬 서버와 네트워크를 확인하세요."
                            }
                        }
                        Handler(Looper.getMainLooper()).postDelayed(pageLoadTimeout, 15_000)
                        loadUrl(ROADVIEW_PAGE)
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
            Text(
                state.value,
                Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xE6111B33)).padding(horizontal = 10.dp, vertical = 7.dp),
                color = Color.White,
                fontSize = 11.sp,
            )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("로드뷰를 표시할 수 없습니다", color = Color.White, fontSize = 22.sp)
                    Text(
                        failureMessage.value.orEmpty(),
                        Modifier.padding(top = 12.dp),
                        color = Color(0xFFCED7E5),
                        fontSize = 15.sp,
                    )
                    Text(
                        "Kakao JavaScript 키와 허용 도메인, 로컬 테스트 서버를 확인하세요. FirstLook 홈으로 돌아가 Demo 화면을 계속 사용할 수 있습니다.",
                        Modifier.padding(top = 8.dp),
                        color = Color(0xFF9EACC0),
                        fontSize = 13.sp,
                    )
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) {
                        Text("FirstLook 화면으로 돌아가기", color = Color(0xFF8AB4FF))
                    }
                }
            }
        }
        if (route != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).height(60.dp)
                    .clip(RoundedCornerShape(16.dp)).background(FirstLookTheme.colors.panel)
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
                    Text("‹  이전 후보")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${selectedIndex + 1} / ${candidates.size}", color = FirstLookTheme.colors.white, fontSize = 15.sp)
                    Text("현재 후보", color = FirstLookTheme.colors.muted, fontSize = 10.sp)
                }
                Button(
                    onClick = if (selectedIndex < candidates.lastIndex) onNext else onBack,
                    modifier = Modifier.weight(1.25f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1555C9)),
                ) {
                    Text(if (selectedIndex < candidates.lastIndex) "다음 후보 로드뷰  ›" else "후보 목록으로")
                }
            }
        }
    }
}

private fun buildRoutePreviewConfig(route: RouteSnapshot, candidates: List<BriefingSegment>, selectedIndex: Int): String {
    require(route.geometryStatus == RouteGeometryStatus.VERIFIED && route.geometry.size >= 2) { "검증된 실제 경로 geometry가 없습니다." }
    val selected = candidates.getOrNull(selectedIndex) ?: error("선택한 복잡 구간 후보가 없습니다.")
    val eligible = listOf(selected).filter { segment ->
        segment.coordinate != null && segment.headingDegrees != null && segment.routeMatchStatus.name == "VERIFIED"
    }
    require(eligible.isNotEmpty()) { "선택한 구간이 route matcher 검증을 통과하지 못했습니다." }
    val first = eligible.first()
    val candidatesJson = JSONArray().apply {
        eligible.forEach { segment ->
            val coordinate = requireNotNull(segment.coordinate)
            put(JSONObject()
                .put("latitude", coordinate.latitude)
                .put("longitude", coordinate.longitude)
                .put("headingDegrees", segment.headingDegrees)
                .put("name", segment.name)
                .put("score", segment.selectionScore ?: 0.0)
                .put("displayIndex", selectedIndex)
                .put("evidence", JSONArray(segment.selectionEvidence.take(3)))
                .put("routeLanes", JSONArray().apply {
                    segment.routeLanes.forEach { lane ->
                        put(JSONObject().put("indications", JSONArray(lane.indications)).put("validForRoute", lane.validForRoute))
                    }
                }))
        }
    }
    return JSONObject()
        .put("type", "initialize")
        .put("latitude", requireNotNull(first.coordinate).latitude)
        .put("longitude", requireNotNull(first.coordinate).longitude)
        .put("heading", first.headingDegrees)
        .put("routeId", route.routeId)
        .put("routeSource", "OSRM/OSM")
        .put("candidates", candidatesJson)
        .toString()
}

/** Loads the generated OSM fixture and matches it against a synthetic, route-scoped path.
 * This only exists in the debug source set; the fixed eligibility signals are not vehicle inputs. */
private fun buildJunctionPreviewConfig(context: android.content.Context): String {
    val json = context.assets.open(JUNCTION_FIXTURE).bufferedReader().use { JSONObject(it.readText()) }
    fun coordinate(array: JSONArray, index: Int = 0) = GeoCoordinate(array.getDouble(index), array.getDouble(index + 1))
    fun path(name: String): List<GeoCoordinate> = json.getJSONArray(name).let { array ->
        (0 until array.length()).map { coordinate(array.getJSONArray(it)) }
    }

    val source = json.getJSONObject("source")
    val ways = source.getJSONObject("ways")
    val review = json.getJSONObject("review")
    val record = JunctionRecord(
        id = json.getString("id"),
        routeId = json.getString("routeId"),
        junction = coordinate(json.getJSONArray("junction")),
        approachPoint = coordinate(json.getJSONArray("at")),
        panDegrees = json.getDouble("pan"),
        cautionPoint = coordinate(json.getJSONArray("caution")),
        message = json.getString("message"),
        detourCost = if (json.isNull("cost")) null else json.getString("cost"),
        keepPath = path("keepPath"),
        trapPath = path("trapPath"),
        verifiedLaneMessage = if (json.isNull("verifiedLaneMessage")) null else json.getString("verifiedLaneMessage"),
        laneGuidanceVerified = json.getBoolean("laneGuidanceVerified"),
        source = JunctionDataSource(
            provider = source.getString("provider"),
            attribution = source.getString("attribution"),
            license = source.getString("license"),
            retrievedAt = source.optString("retrievedAt").takeIf { it.isNotBlank() },
            approachWayId = ways.getJSONObject("approach").getString("id"),
            keepWayId = ways.getJSONObject("keep").getString("id"),
            trapWayId = ways.getJSONObject("trap").getString("id"),
        ),
        manuallyVerified = review.getBoolean("manuallyVerified"),
        roadViewCoverageVerified = review.getBoolean("roadviewCoverageVerified"),
    )
    check(record.mayUseRoadView && json.getBoolean("useRoadview")) { "교차로와 Roadview 검수가 완료되지 않았습니다." }

    val route = RouteSnapshot(
        origin = "한남대로 북측 접근로",
        destination = "한남대로 남측",
        durationMinutes = 1,
        distanceKm = 1,
        segments = emptyList(),
        routeId = record.routeId,
        source = RouteSource.EXTERNAL_ROUTE,
        geometryStatus = RouteGeometryStatus.VERIFIED,
        geometry = listOf(record.approachPoint, record.junction) + record.keepPath,
    )
    val match = JunctionRecordMatcher().match(route, record)
    check(match is JunctionRecordMatch.Verified) { "교차로가 경로와 일치하지 않습니다: $match" }

    fun jsonPath(points: List<GeoCoordinate>) = JSONArray().apply {
        points.forEach { put(JSONObject().put("latitude", it.latitude).put("longitude", it.longitude)) }
    }
    val webRecord = JSONObject()
        .put("manuallyVerified", record.manuallyVerified)
        .put("roadViewCoverageVerified", record.roadViewCoverageVerified)
        .put("caution", JSONObject().put("latitude", record.cautionPoint.latitude).put("longitude", record.cautionPoint.longitude))
        .put("keepPath", jsonPath(record.keepPath))
        .put("trapPath", jsonPath(record.trapPath))

    return JSONObject()
        .put("type", "initialize")
        .put("latitude", record.approachPoint.latitude)
        .put("longitude", record.approachPoint.longitude)
        .put("heading", record.panDegrees)
        .put("routeVerified", true)
        .put("photoMatchVerified", true)
        .put("overlayAllowed", true)
        .put("developerFixture", true)
        .put("junctionRecord", webRecord)
        .toString()
}
