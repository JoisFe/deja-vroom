package ai.dejavroom.app.ui

import android.graphics.BitmapFactory
import android.view.ViewGroup
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.PhotoOverlay
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
        // The card puts its own labels in the top corners and the bottom-start one, so the badge takes the last.
        RoadViewContent.Illustration -> RoadScene(modifier, variant = 0, large = !compact, badgeAlignment = Alignment.BottomEnd)
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
    val type = DejaVroomTheme.type
    val dimens = DejaVroomTheme.dimens
    Box(modifier.background(PhotoOverlay.placeholder)) {
        when (val state = image) {
            ImageLoad.Loading -> RoadViewMessage("사진을 불러오고 있습니다", Modifier.fillMaxSize())
            ImageLoad.Failed -> RoadViewMessage("사진을 불러오지 못했습니다", Modifier.fillMaxSize())
            is ImageLoad.Loaded -> Image(state.bitmap, contentDescription = "도로 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Bottom-end: a summary card writes how far before the point the photo was taken at the bottom-start.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(dimens.spaceS).clip(RoundedCornerShape(dimens.radiusSmall))
                .background(PhotoOverlay.scrim).padding(horizontal = dimens.spaceS, vertical = dimens.spaceXs),
        ) {
            Text(view.attribution.orEmpty(), color = PhotoOverlay.onScrim, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showDetails) {
                Text(
                    listOfNotNull(
                        view.capturedAt?.let { "촬영 $it" },
                        "${content.distanceMeters}m 거리",
                        "방향 차이 ${content.headingDifferenceDegrees}°",
                    ).joinToString(" · "),
                    color = PhotoOverlay.onScrimVariant, style = type.labelSmall, maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun RoadViewMessage(message: String, modifier: Modifier = Modifier) {
    Box(modifier.background(DejaVroomTheme.colors.surfaceVariant).padding(DejaVroomTheme.dimens.spaceM), contentAlignment = Alignment.Center) {
        Text(message, color = DejaVroomTheme.colors.onSurfaceVariant, style = DejaVroomTheme.type.labelMedium, textAlign = TextAlign.Center)
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
                // Without an exact height the page's own height is zero to WebView, so "height: 100%" in the
                // page collapses to its minimum and the picture stays a strip at the top of the card.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(PhotoOverlay.placeholder.toArgb())
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
