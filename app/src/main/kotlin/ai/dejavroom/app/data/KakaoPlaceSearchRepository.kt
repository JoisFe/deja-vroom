package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Place search by Kakao. The questions go to a page of the road view site (web/roadview/places.html),
 * loaded in a WebView that is never put on screen; the page asks Kakao and posts the places back.
 *
 * The Kakao JavaScript key stays in that page, where it only works on the site's registered domain, so
 * the app carries no key of its own. It needs what the Kakao road view needs and nothing more: the page
 * address (`kakao.baseUrl` / `roadviewBaseUrl`). See docs/place-search-setup.md.
 */
class KakaoPlaceSearchRepository(
    context: Context,
    private val site: RoadViewEndpoint,
    private val pageTimeout: Duration = 10.seconds,
    private val answerTimeout: Duration = 5.seconds,
) : PlaceSearchRepository {
    private val appContext = context.applicationContext
    private val connecting = Mutex()
    private var page: Page? = null

    override val attribution: String get() = KAKAO_PLACES_ATTRIBUTION

    /** Kakao ranks by how well the name matches; where the driver is plays no part and is not sent. */
    override suspend fun suggest(query: String, bias: GeoCoordinate?): List<Place> = withContext(Dispatchers.Main.immediate) {
        val text = query.trim().take(MAX_QUERY_LENGTH)
        if (text.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        val answer = connected().ask(text)
        try {
            withTimeoutOrNull(answerTimeout) { answer.await() } ?: throw PlaceSearchUnavailableException(NO_ANSWER_MESSAGE)
        } finally {
            // Whether it arrived, timed out or was typed over, nobody is waiting for this answer now.
            answer.cancel()
        }
    }

    override suspend fun prepare() {
        try {
            withContext(Dispatchers.Main.immediate) { connected() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Kakao 장소 검색을 준비하지 못했다: ${failure.message}")
        }
    }

    override fun release() {
        page?.close()
        page = null
    }

    /** The page, loaded and ready to take questions. A page that failed is dropped so the next try starts over. */
    private suspend fun connected(): Page = connecting.withLock {
        val current = page ?: Page(appContext, site).also { page = it }
        try {
            withTimeoutOrNull(pageTimeout) { current.ready.await() } ?: throw PlaceSearchUnavailableException(PAGE_SILENT_MESSAGE)
            current
        } catch (unavailable: PlaceSearchUnavailableException) {
            if (page === current) release()
            throw unavailable
        }
    }

    /** One loaded copy of the place search page and the questions it has not answered yet. */
    @SuppressLint("SetJavaScriptEnabled")
    private class Page(context: Context, private val site: RoadViewEndpoint) {
        val ready = CompletableDeferred<Unit>()
        private val waiting = mutableMapOf<Int, CompletableDeferred<List<Place>>>()
        private var nextId = 1
        private val webView: WebView?

        init {
            webView = if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)
            ) {
                fail(NO_SECURE_MESSAGING_MESSAGE)
                null
            } else {
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    WebViewCompat.addWebMessageListener(this, ROADVIEW_BRIDGE_NAME, setOf(site.origin)) { _, message, sourceOrigin, isMainFrame, _ ->
                        if (isMainFrame && sourceOrigin.toString() == site.origin) receive(KakaoPlacesMessage.parse(message.data))
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            !site.isPlacesPage(request.url.toString())

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) fail(PAGE_UNREACHABLE_MESSAGE)
                        }

                        /** A site that answers but has no such page (an older copy of it) is just as unreachable. */
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                            if (request.isForMainFrame) fail(PAGE_UNREACHABLE_MESSAGE)
                        }
                    }
                    loadUrl(site.placesUrl)
                }
            }
        }

        /** Sends the question and returns where its answer will arrive. */
        fun ask(query: String): CompletableDeferred<List<Place>> {
            val id = nextId++
            val answer = CompletableDeferred<List<Place>>()
            waiting[id] = answer
            // An answer nobody waits for any more (timed out, typed over) is forgotten.
            answer.invokeOnCompletion { waiting.remove(id) }
            val view = webView
            if (view == null) answer.completeExceptionally(PlaceSearchUnavailableException(NO_SECURE_MESSAGING_MESSAGE))
            else WebViewCompat.postWebMessage(view, WebMessageCompat(KakaoPlacesMessage.searchRequest(id, query)), Uri.parse(site.origin))
            return answer
        }

        fun close() {
            fail(CLOSED_MESSAGE)
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
        }

        private fun receive(message: KakaoPlacesMessage?) {
            when (message) {
                KakaoPlacesMessage.Ready -> ready.complete(Unit)
                is KakaoPlacesMessage.Results -> waiting[message.id]?.complete(message.places)
                is KakaoPlacesMessage.SearchFailed -> waiting[message.id]?.completeExceptionally(PlaceSearchUnavailableException(SEARCH_FAILED_MESSAGE))
                is KakaoPlacesMessage.Failed -> fail(
                    when (message.reason) {
                        KakaoPlacesMessage.Reason.KEY_MISSING -> KEY_MISSING_MESSAGE
                        KakaoPlacesMessage.Reason.SDK_NOT_LOADED -> SDK_NOT_LOADED_MESSAGE
                    },
                )
                null -> Unit
            }
        }

        /** Nothing more will come from this page: whoever is waiting is told why. */
        private fun fail(message: String) {
            val failure = PlaceSearchUnavailableException(message)
            ready.completeExceptionally(failure)
            waiting.values.toList().forEach { it.completeExceptionally(failure) }
        }
    }

    companion object {
        private const val TAG = "DejaVroomPlaces"
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 80

        const val SETUP_MESSAGE = "Kakao 장소 검색 주소가 설정되지 않았습니다 · docs/place-search-setup.md 참고"
        private const val PAGE_SILENT_MESSAGE = "Kakao 장소 검색 페이지가 응답하지 않습니다. 네트워크와 로드뷰 주소 설정을 확인하세요."
        private const val PAGE_UNREACHABLE_MESSAGE = "Kakao 장소 검색 페이지에 연결할 수 없습니다. 네트워크와 로드뷰 주소 설정을 확인하세요."
        private const val KEY_MISSING_MESSAGE = "Kakao JavaScript 키가 페이지에 없습니다 · docs/place-search-setup.md 참고"
        private const val SDK_NOT_LOADED_MESSAGE = "Kakao 장소 검색을 불러오지 못했습니다. 키, Web 플랫폼 도메인 등록, 네트워크를 확인하세요."
        private const val NO_SECURE_MESSAGING_MESSAGE = "이 Android System WebView로는 Kakao 장소 검색을 쓸 수 없습니다."
        private const val SEARCH_FAILED_MESSAGE = "Kakao 장소 검색이 오류를 돌려주었습니다. 잠시 뒤 다시 시도해 주세요."
        private const val NO_ANSWER_MESSAGE = "Kakao 장소 검색이 응답하지 않습니다. 네트워크를 확인하세요."
        private const val CLOSED_MESSAGE = "Kakao 장소 검색을 닫았습니다."
    }
}
