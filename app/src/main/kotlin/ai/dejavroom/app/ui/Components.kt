package ai.dejavroom.app.ui

import ai.dejavroom.app.R
import ai.dejavroom.app.core.designsystem.DejaVroomIcons
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.PhotoOverlay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** One of [DejaVroomIcons], tinted and sized by tokens. */
@Composable
internal fun AppIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = DejaVroomTheme.dimens.icon,
    contentDescription: String? = null,
) {
    Icon(icon, contentDescription, modifier.size(size), tint)
}

/** The three button heights of the Pleos Connect button guide. */
internal enum class ButtonSize { Large, Medium, Small }

@Composable
private fun ButtonSize.height(): Dp = when (this) {
    ButtonSize.Large -> DejaVroomTheme.dimens.buttonLarge
    ButtonSize.Medium -> DejaVroomTheme.dimens.buttonMedium
    ButtonSize.Small -> DejaVroomTheme.dimens.buttonSmall
}

@Composable
private fun ButtonSize.textStyle(): TextStyle = when (this) {
    ButtonSize.Large -> DejaVroomTheme.type.titleSmall
    ButtonSize.Medium -> DejaVroomTheme.type.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    ButtonSize.Small -> DejaVroomTheme.type.labelMedium.copy(fontWeight = FontWeight.SemiBold)
}

/**
 * The one action a screen is for. [loading] keeps the button's color and shows a spinner in place of the
 * trailing icon; the button does not respond until it ends.
 */
@Composable
internal fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    size: ButtonSize = ButtonSize.Large,
    onClick: () -> Unit,
) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val content = if (enabled) colors.onPrimary else colors.onSurfaceVariant
    Row(
        modifier
            .height(size.height())
            .then(
                if (enabled) {
                    Modifier.shadow(
                        dimens.dp(14),
                        CircleShape,
                        clip = false,
                        ambientColor = colors.primary,
                        spotColor = colors.primary,
                    )
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(colors.primaryBrush) else Modifier.background(colors.surfaceVariant))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceXl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            AppIcon(leadingIcon, tint = content)
            Spacer(Modifier.width(dimens.spaceS))
        }
        Text(
            text,
            Modifier.weight(1f, fill = false),
            color = content,
            style = size.textStyle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (loading) {
            Spacer(Modifier.width(dimens.spaceM))
            CircularProgressIndicator(Modifier.size(dimens.icon), color = content, strokeWidth = dimens.dp(4))
        } else if (trailingIcon != null) {
            Spacer(Modifier.width(dimens.spaceS))
            AppIcon(trailingIcon, tint = content)
        }
    }
}

/** An action beside the primary one. */
@Composable
internal fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    size: ButtonSize = ButtonSize.Large,
    onClick: () -> Unit,
) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val content = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
    Row(
        modifier
            .height(size.height())
            .clip(CircleShape)
            .background(if (enabled) colors.surface else colors.surfaceVariant)
            .border(dimens.dp(2), if (enabled) colors.outline else colors.hairline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceXl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            AppIcon(leadingIcon, tint = content)
            Spacer(Modifier.width(dimens.spaceS))
        }
        Text(
            text,
            Modifier.weight(1f, fill = false),
            color = content,
            style = size.textStyle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(dimens.spaceS))
            AppIcon(trailingIcon, tint = content)
        }
    }
}

/** A text-only action inside a row or a header, with a finger-sized touch area. */
@Composable
internal fun TextAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = DejaVroomTheme.colors.accent,
    onClick: () -> Unit,
) {
    val dimens = DejaVroomTheme.dimens
    val content = if (enabled) color else DejaVroomTheme.colors.onSurfaceVariant
    Row(
        modifier
            .heightIn(min = dimens.touchTarget)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, tint = content, size = dimens.iconSmall)
            Spacer(Modifier.width(dimens.spaceXs))
        }
        Text(
            text,
            color = content,
            style = DejaVroomTheme.type.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An icon-only action. [contentDescription] says what it does, because the icon has no label. */
@Composable
internal fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = DejaVroomTheme.colors.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(
            DejaVroomTheme.dimens.touchTarget,
        ).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, tint = tint, contentDescription = contentDescription)
    }
}

/**
 * The number of a place to look at, in driving order. Orange means that one thing everywhere in the app.
 * [passed] greys a place already behind the car; [ring] sets the marker off from a photo under it.
 */
@Composable
internal fun NumberMarker(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = DejaVroomTheme.dimens.dp(64),
    passed: Boolean = false,
    ring: Boolean = false,
    ringColor: Color = PhotoOverlay.chip,
) {
    val colors = DejaVroomTheme.colors
    val digit = with(LocalDensity.current) { (size * DIGIT_SHARE).toSp() }
    Box(
        modifier
            .size(size)
            .background(if (passed) colors.surfaceVariant else colors.marker, CircleShape)
            .then(if (ring) Modifier.border(size * RING_SHARE, ringColor, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$number",
            color = if (passed) colors.onSurfaceVariant else colors.onMarker,
            fontSize = digit,
            lineHeight = 1.em,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

/** A short fact about the thing it sits on. Over a photo, pass the [PhotoOverlay] chip colors. */
@Composable
internal fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = DejaVroomTheme.colors.selectedSurface,
    content: Color = DejaVroomTheme.colors.accent,
    icon: ImageVector? = null,
) {
    val dimens = DejaVroomTheme.dimens
    Row(
        modifier.height(dimens.dp(48)).background(container, CircleShape).padding(horizontal = dimens.dp(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, tint = content, size = dimens.iconSmall)
            Spacer(Modifier.width(dimens.spaceXs))
        }
        Text(
            text,
            color = content,
            style = DejaVroomTheme.type.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Something the driver should know before relying on the screen, such as a function that is not connected yet. */
@Composable
internal fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    Row(
        modifier.background(
            colors.warningContainer,
            RoundedCornerShape(dimens.radiusMedium),
        ).padding(horizontal = dimens.spaceL, vertical = dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(DejaVroomIcons.Warning, tint = colors.warning)
        Spacer(Modifier.width(dimens.spaceS))
        Text(text, color = colors.warning, style = DejaVroomTheme.type.labelMedium)
    }
}

/**
 * A card: surface color, a faint edge and, when [elevated], a soft shadow. Content is clipped to [shape],
 * so a photo can run to the card's edge.
 */
@Composable
internal fun Modifier.cardSurface(
    shape: Shape = RoundedCornerShape(DejaVroomTheme.dimens.radiusLarge),
    elevated: Boolean = true,
    color: Color = DejaVroomTheme.colors.surface,
): Modifier {
    val colors = DejaVroomTheme.colors
    val elevation = DejaVroomTheme.dimens.dp(if (elevated) 18 else 0)
    return this
        .shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
        .clip(shape)
        .background(color)
        .border(1.dp, colors.hairline, shape)
}

@Composable
internal fun Eyebrow(text: String) {
    Text(
        text,
        color = DejaVroomTheme.colors.accent,
        style = DejaVroomTheme.type.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.09.em),
        maxLines = 1,
    )
}

/**
 * The drawn stand-in for a road photo. It does not round its own corners; whatever holds it does. The
 * provenance badge goes where [badgeAlignment] says, so the holder can keep it clear of its own labels.
 */
@Composable
internal fun RoadScene(
    modifier: Modifier = Modifier,
    variant: Int,
    large: Boolean,
    badgeAlignment: Alignment = Alignment.TopStart,
) {
    val dimens = DejaVroomTheme.dimens
    Box(modifier.background(DejaVroomTheme.colors.surfaceVariant)) {
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
        Text(
            if (large) "AI 생성 데모 · 실제 도로 사진 아님" else "AI 생성 데모",
            Modifier.align(
                badgeAlignment,
            ).padding(
                dimens.spaceM,
            ).background(
                PhotoOverlay.scrim,
                CircleShape,
            ).padding(horizontal = dimens.spaceM, vertical = dimens.spaceXs),
            color = PhotoOverlay.onScrim,
            style = DejaVroomTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

private const val DISABLED_ALPHA = 0.6f
private const val DIGIT_SHARE = 0.5f
private const val RING_SHARE = 0.0625f
