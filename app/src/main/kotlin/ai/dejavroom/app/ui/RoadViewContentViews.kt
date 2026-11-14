package ai.dejavroom.app.ui

import android.graphics.BitmapFactory
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.data.HttpImageBytesLoader
import ai.dejavroom.app.data.ImageBytesLoader
import ai.dejavroom.app.data.RoadViewContent
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.data.RoadViewResult

/*
 * Renders road view results by content type only. Nothing here knows which provider produced the result:
 * InteractivePage (Kakao) → WebView, StillImage (Mapillary) → photo, Illustration (demo) → drawn scene.
 */

/** Card-sized preview for the summary screen. */
@Composable
internal fun RoadViewThumbnail(lookup: RoadViewLookupResult?, compact: Boolean, modifier: Modifier = Modifier) {
    val view = (lookup as? RoadViewLookupResult.Available)?.views?.firstOrNull()
    when (val content = view?.content) {
        is RoadViewContent.InteractivePage -> RoadViewPageThumbnail(content, modifier)
        is RoadViewContent.StillImage -> RoadViewPhoto(view, content, modifier, showDetails = !compact)
        RoadViewContent.Illustration -> RoadScene(modifier, variant = 0, large = !compact)
        null -> RoadViewMessage(roadViewStatusMessage(lookup), modifier)
    }
}

/** A provider photo with its required credit line (Mapillary is CC BY-SA). */
@Composable
internal fun RoadViewPhoto(
    view: RoadViewResult,
    content: RoadViewContent.StillImage,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
    loader: ImageBytesLoader = HttpImageBytesLoader,
) {
    val image by produceState<ImageLoad>(ImageLoad.Loading, content.imageUrl) {
        val bytes = loader.load(content.imageUrl)
        value = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
            ?.let(ImageLoad::Loaded) ?: ImageLoad.Failed
    }
    Box(modifier.background(Color(0xFF17202D))) {
        when (val state = image) {
            ImageLoad.Loading -> RoadViewMessage("사진을 불러오고 있습니다", Modifier.fillMaxSize())
            ImageLoad.Failed -> RoadViewMessage("사진을 불러오지 못했습니다", Modifier.fillMaxSize())
            is ImageLoad.Loaded -> Image(state.bitmap, contentDescription = "도로 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0xCC07111D)).padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Text(view.attribution.orEmpty(), color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showDetails) {
                Text(
                    listOfNotNull(
                        view.capturedAt?.let { "촬영 $it" },
                        "${content.distanceMeters}m 거리",
                        "방향 차이 ${content.headingDifferenceDegrees}°",
                    ).joinToString(" · "),
                    color = Color(0xFFE6ECF8), fontSize = 10.sp, maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun RoadViewMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier.background(DejaVroomTheme.colors.panelRaised).padding(12.dp), contentAlignment = Alignment.Center) {
        Text(message, color = DejaVroomTheme.colors.muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** Still, non-interactive page (the provider page's thumbnail mode). Navigation outside the page is blocked. */
@Composable
private fun RoadViewPageThumbnail(content: RoadViewContent.InteractivePage, modifier: Modifier) {
    val url = content.thumbnailUrl
    var loadFailed by remember(url) { mutableStateOf(false) }
    if (loadFailed) {
        RoadViewMessage("로드뷰 페이지에 연결할 수 없습니다", modifier)
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                tag = url
                setBackgroundColor(android.graphics.Color.rgb(29, 41, 57))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !content.endpoint.isRoadViewPage(request.url.toString())

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) loadFailed = true
                    }
                }
                loadUrl(url)
            }
        },
        update = { view -> if (view.tag != url) { view.tag = url; view.loadUrl(url) } },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    data object Failed : ImageLoad
    data class Loaded(val bitmap: ImageBitmap) : ImageLoad
}
