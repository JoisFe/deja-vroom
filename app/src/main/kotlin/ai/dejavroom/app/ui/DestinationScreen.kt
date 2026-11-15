package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomIcons
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.SuggestionStatus
import ai.dejavroom.app.feature.inputOf
import ai.dejavroom.app.feature.startsFromCurrentLocation
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.SearchField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.min

/** Everything the destination screen can ask for. The screen never sees the ViewModel. */
internal data class DestinationActions(
    val onValueChange: (SearchField, String) -> Unit,
    val onFocus: (SearchField) -> Unit,
    val onClear: (SearchField) -> Unit,
    val onSwap: () -> Unit,
    val onUseCurrentLocation: () -> Unit,
    val onSelectPlace: (SearchField, Place) -> Unit,
    val onRemoveRecent: (Place) -> Unit,
    val onClearRecents: () -> Unit,
    val onDismissSuggestions: () -> Unit,
    val onSearch: () -> Unit,
)

@Composable
internal fun DestinationScreen(state: DejaVroomUiState, actions: DestinationActions, compact: Boolean) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Everything that ends typing also puts the keyboard away: a car screen has no back key to do it.
    val formActions = remember(actions, focusManager, keyboard) {
        val leaveFields = {
            focusManager.clearFocus()
            keyboard?.hide()
        }
        actions.copy(
            onSelectPlace = { field, place -> leaveFields(); actions.onSelectPlace(field, place) },
            onUseCurrentLocation = { leaveFields(); actions.onUseCurrentLocation() },
            onDismissSuggestions = { leaveFields(); actions.onDismissSuggestions() },
            onSearch = { leaveFields(); actions.onSearch() },
        )
    }
    BackHandler(enabled = state.activeField != null, onBack = formActions.onDismissSuggestions)
    // A touch anywhere that is not a field, a row or a button means the driver is done typing.
    val doneOnOutsideTouch = Modifier.pointerInput(formActions) { detectTapGestures(onTap = { formActions.onDismissSuggestions() }) }
    DestinationLayout(state, formActions, compact, doneOnOutsideTouch)
}

// The shell pads the content area, so neither layout adds outer padding of its own.
@Composable
private fun DestinationLayout(state: DejaVroomUiState, actions: DestinationActions, compact: Boolean, modifier: Modifier) {
    val dimens = DejaVroomTheme.dimens
    if (compact) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DestinationForm(state, actions, Modifier.fillMaxWidth(), dense = true, suggestionsInline = true)
            Spacer(Modifier.height(dimens.spaceXl))
            RouteAnalysisPreview(searching = state.isSearchingRoute, dense = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(dimens.spaceS))
            RouteDataAttribution()
        }
    } else {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dimens.dp(56))) {
            // Top-aligned, not centered: the suggestion panel changes the form's height, and a centered form
            // would move the field the driver has just touched.
            Column(Modifier.weight(1.12f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                DestinationForm(state, actions, Modifier.fillMaxWidth(), dense = false, suggestionsInline = false)
            }
            Column(Modifier.weight(0.88f).fillMaxHeight()) {
                val typing = state.activeField
                if (typing != null) {
                    // Beside the fields, not under them: the keyboard covers the lower half of the window.
                    // Up here every suggestion stays above it, and the fields keep their place.
                    SuggestionPanel(state, actions, typing)
                } else {
                    RouteAnalysisPreview(searching = state.isSearchingRoute, dense = false, modifier = Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(dimens.spaceS))
                    RouteDataAttribution()
                }
            }
        }
    }
}

@Composable
internal fun DestinationForm(state: DejaVroomUiState, actions: DestinationActions, modifier: Modifier, dense: Boolean, suggestionsInline: Boolean) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    Column(modifier) {
        Eyebrow("DÉJÀ VROOM  ·  ROUTE ANALYSIS")
        Spacer(Modifier.height(dimens.spaceS))
        Text(
            "처음 가는 길도,\n처음 보는 길이 아니도록.",
            color = colors.onSurface,
            style = if (dense) type.headlineSmall else type.headline,
        )
        Spacer(Modifier.height(dimens.spaceS))
        Text("출발 전에 헷갈리기 쉬운 구간을 먼저 살펴보세요.", color = colors.onSurfaceVariant, style = type.bodyMedium)
        Spacer(Modifier.height(dimens.dp(30)))

        // "Next" on the keyboard moves from the origin to the destination.
        val destinationFocus = remember { FocusRequester() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SearchFieldBlock(state, actions, SearchField.ORIGIN, destinationFocus, suggestionsInline)
                Spacer(Modifier.height(dimens.spaceM))
                SearchFieldBlock(state, actions, SearchField.DESTINATION, destinationFocus, suggestionsInline)
            }
            Spacer(Modifier.width(dimens.spaceM))
            // Swap sits beside the two fields, like most navigation apps.
            Box(
                Modifier.size(dimens.dp(84)).cardSurface(RoundedCornerShape(dimens.radiusMedium)).clickable(onClick = actions.onSwap),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(DejaVroomIcons.Swap, tint = colors.accent, contentDescription = "출발지와 목적지 바꾸기")
            }
        }

        state.searchProblem?.let { problem ->
            Spacer(Modifier.height(dimens.spaceS))
            Text(searchProblemText(problem), color = colors.error, style = type.labelMedium)
        }

        if (state.activeField == null) {
            Spacer(Modifier.height(dimens.spaceS))
            RecentChips(state.recentSearches, actions)
        }

        Spacer(Modifier.height(dimens.spaceL))
        // A trip still under way: searching again from where the car is continues it.
        state.resumableTrip?.let { trip ->
            Text(tripResumeLine(trip), color = colors.accent, style = type.labelMedium, maxLines = 2)
            Spacer(Modifier.height(dimens.spaceS))
        }
        PrimaryButton(
            if (state.isSearchingRoute) "복잡 교차로 분석 중" else "복잡 구간 분석",
            Modifier.fillMaxWidth(),
            loading = state.isSearchingRoute,
            leadingIcon = DejaVroomIcons.Sparkle,
            trailingIcon = DejaVroomIcons.ArrowForward,
            onClick = actions.onSearch,
        )
    }
}

@Composable
private fun SearchFieldBlock(state: DejaVroomUiState, actions: DestinationActions, field: SearchField, destinationFocus: FocusRequester, suggestionsInline: Boolean) {
    val dimens = DejaVroomTheme.dimens
    val isOrigin = field == SearchField.ORIGIN
    if (isOrigin) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { RouteFieldLabel("출발지") }
            TextAction(
                if (state.isLocating) "현재 위치 찾는 중…" else "현재 위치",
                enabled = !state.isLocating,
                icon = DejaVroomIcons.MyLocation.takeUnless { state.isLocating },
                onClick = actions.onUseCurrentLocation,
            )
        }
    } else {
        RouteFieldLabel("목적지")
        Spacer(Modifier.height(dimens.spaceM))
    }
    RouteSearchField(
        icon = if (isOrigin) DejaVroomIcons.MyLocation else DejaVroomIcons.Search,
        value = state.inputOf(field),
        placeholder = if (isOrigin) "출발 장소를 검색하세요" else "목적지를 검색하세요",
        active = state.activeField == field,
        imeAction = if (isOrigin) ImeAction.Next else ImeAction.Search,
        onValueChange = { actions.onValueChange(field, it) },
        onFocus = { actions.onFocus(field) },
        onClear = { actions.onClear(field) },
        onImeAction = { if (isOrigin) destinationFocus.requestFocus() else actions.onSearch() },
        focusRequester = destinationFocus.takeUnless { isOrigin },
    )
    if (isOrigin) OriginCaption(state)
    // The wide layout shows the suggestions in the column beside the form instead.
    if (suggestionsInline && state.activeField == field) {
        Spacer(Modifier.height(dimens.spaceXs))
        SuggestionPanel(state, actions, field)
    }
}

/** Under the origin field: where the car's position is, in the navigation's words, or why it could not be read. */
@Composable
private fun OriginCaption(state: DejaVroomUiState) {
    val dimens = DejaVroomTheme.dimens
    val notice = state.locationNotice
    val address = state.originPlace?.takeIf { state.startsFromCurrentLocation }?.address.orEmpty()
    if (notice == null && address.isBlank()) return
    Spacer(Modifier.height(dimens.spaceXs))
    Text(
        notice?.let(::locationNoticeText) ?: address,
        color = if (notice != null) DejaVroomTheme.colors.warning else DejaVroomTheme.colors.onSurfaceVariant,
        style = DejaVroomTheme.type.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = dimens.spaceL),
    )
}

@Composable
private fun RouteSearchField(
    icon: ImageVector,
    value: String,
    placeholder: String,
    active: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    val shape = RoundedCornerShape(dimens.radiusMedium)
    Row(
        Modifier.fillMaxWidth().height(dimens.fieldHeight)
            .background(colors.surface, shape)
            .border(if (active) dimens.dp(3) else dimens.dp(2), if (active) colors.primary else colors.outline, shape)
            .padding(start = dimens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, tint = colors.accent)
        Spacer(Modifier.width(dimens.spaceM))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = colors.onSurfaceVariant,
                    style = type.bodyLarge.copy(fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = type.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus() },
            )
        }
        // The clear slot is always reserved so the field does not shift when text appears.
        Box(Modifier.size(dimens.touchTarget), contentAlignment = Alignment.Center) {
            if (value.isNotEmpty()) IconAction(DejaVroomIcons.Close, contentDescription = "입력 지우기", onClick = onClear)
        }
    }
}

@Composable
private fun SuggestionPanel(state: DejaVroomUiState, actions: DestinationActions, field: SearchField) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    val query = state.inputOf(field).trim()
    val suggestions = state.suggestions.takeIf { it.field == field }
    Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(dimens.radiusMedium)).padding(vertical = dimens.spaceXs)) {
        if (query.isEmpty()) {
            RecentList(state.recentSearches, field, actions)
            return@Column
        }
        // Fixed-height status row: loading/empty/error never move the rows below it.
        Row(Modifier.fillMaxWidth().height(dimens.touchTarget).padding(start = dimens.spaceL), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    query.length < 2 -> "두 글자 이상 입력하면 추천 검색어가 나옵니다"
                    suggestions?.status == SuggestionStatus.LOADING -> "추천 검색어 찾는 중…"
                    suggestions?.status == SuggestionStatus.EMPTY -> "추천 결과가 없습니다. 입력한 그대로 검색할 수 있습니다"
                    suggestions?.status == SuggestionStatus.ERROR -> suggestions.problem ?: "추천 검색어를 불러오지 못했습니다. 입력한 그대로 검색할 수 있습니다"
                    else -> "추천 검색어"
                },
                color = if (suggestions?.status == SuggestionStatus.ERROR) colors.error else colors.onSurfaceVariant,
                style = type.labelMedium,
                // Two lines are all the fixed row holds at this size.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ClosePanelAction(actions.onDismissSuggestions)
        }
        suggestions?.items.orEmpty().take(6).forEach { place ->
            PlaceRow(
                title = highlight(place.name, query),
                subtitle = place.address,
                leading = DejaVroomIcons.Place,
                onClick = { actions.onSelectPlace(field, place) },
            )
        }
        if (suggestions?.items.orEmpty().isNotEmpty() && state.placeSearchAttribution.isNotBlank()) {
            Text(
                state.placeSearchAttribution,
                color = colors.onSurfaceVariant,
                style = type.labelSmall,
                modifier = Modifier.padding(horizontal = dimens.spaceL, vertical = dimens.spaceXs),
            )
        }
    }
}

@Composable
private fun RecentList(recents: List<Place>, field: SearchField, actions: DestinationActions) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    // The panel can always be closed from its own header, with or without anything to list.
    Row(Modifier.fillMaxWidth().height(dimens.touchTarget).padding(start = dimens.spaceL), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (recents.isEmpty()) "최근 검색이 없습니다" else "최근 검색",
            color = colors.onSurfaceVariant,
            style = DejaVroomTheme.type.labelMedium,
            modifier = Modifier.weight(1f),
        )
        if (recents.isNotEmpty()) TextAction("전체 삭제", color = colors.onSurfaceVariant, onClick = actions.onClearRecents)
        ClosePanelAction(actions.onDismissSuggestions)
    }
    recents.forEach { place ->
        PlaceRow(
            title = AnnotatedString(place.name),
            subtitle = place.address,
            leading = DejaVroomIcons.History,
            onClick = { actions.onSelectPlace(field, place) },
            onRemove = { actions.onRemoveRecent(place) },
        )
    }
}

/** "닫기" is two letters, so on its own the text action would be narrower than a finger. */
@Composable
private fun ClosePanelAction(onClick: () -> Unit) {
    TextAction(
        "닫기",
        Modifier.widthIn(min = DejaVroomTheme.dimens.touchTarget),
        color = DejaVroomTheme.colors.onSurfaceVariant,
        onClick = onClick,
    )
}

@Composable
private fun PlaceRow(title: AnnotatedString, subtitle: String, leading: ImageVector, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = dimens.dp(88)).clickable(onClick = onClick).padding(start = dimens.spaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(leading, tint = colors.accent)
        Spacer(Modifier.width(dimens.spaceM))
        Column(Modifier.weight(1f).padding(vertical = dimens.spaceXs)) {
            Text(title, color = colors.onSurface, style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = colors.onSurfaceVariant, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onRemove != null) {
            IconAction(DejaVroomIcons.Close, contentDescription = "최근 검색에서 지우기", onClick = onRemove)
        } else {
            Spacer(Modifier.width(dimens.spaceL))
        }
    }
}

@Composable
private fun RecentChips(recents: List<Place>, actions: DestinationActions) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    // As tall as its text action, so the title does not move when the last recent destination is removed.
    Row(Modifier.heightIn(min = dimens.touchTarget), verticalAlignment = Alignment.CenterVertically) {
        Text("최근 목적지", color = colors.onSurfaceVariant, style = type.labelMedium, modifier = Modifier.weight(1f))
        if (recents.isNotEmpty()) TextAction("전체 삭제", color = colors.onSurfaceVariant, onClick = actions.onClearRecents)
    }
    if (recents.isEmpty()) {
        Text("검색한 목적지가 여기에 쌓입니다.", color = colors.onSurfaceVariant, style = type.labelMedium)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        recents.take(3).forEach { place ->
            Row(
                Modifier.weight(1f).height(dimens.dp(108))
                    .cardSurface(RoundedCornerShape(dimens.radiusMedium), elevated = false)
                    .clickable { actions.onSelectPlace(SearchField.DESTINATION, place) }
                    .padding(horizontal = dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(dimens.dp(60)).background(colors.selectedSurface, RoundedCornerShape(dimens.radiusSmall)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(DejaVroomIcons.History, tint = colors.accent)
                }
                Spacer(Modifier.width(dimens.spaceS))
                Column(Modifier.weight(1f)) {
                    Text(place.name, color = colors.onSurface, style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(place.address.ifBlank { "최근 검색" }, color = colors.onSurfaceVariant, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    val start = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    val color = DejaVroomTheme.colors.accent
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(start, start + query.length)) }
        append(text.substring(start + query.length))
    }
}

/**
 * What the app does, under a drawing of a route. In the wide layout the drawing takes the height the text
 * leaves; in the narrow one ([dense]) the panel sits in a scrolling column, so the drawing has a fixed height.
 * [searching] puts a spinner over the drawing: the app does not report analysis steps, so none are shown.
 */
@Composable
internal fun RouteAnalysisPreview(searching: Boolean, dense: Boolean, modifier: Modifier = Modifier) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    Column(modifier.cardSurface()) {
        RouteIllustration(searching, Modifier.fillMaxWidth().then(if (dense) Modifier.height(dimens.dp(300)) else Modifier.weight(1f)))
        Column(
            Modifier.padding(start = dimens.dp(36), top = dimens.dp(32), end = dimens.dp(36), bottom = dimens.dp(36)),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceM),
        ) {
            Text("헷갈리기 쉬운 곳만 골라\n가기 전에 미리 보여 줍니다", color = colors.onSurface, style = type.titleMedium)
            listOf(
                "01" to "출발지와 목적지로 실제 도로 경로를 찾습니다",
                "02" to "놓치면 돌아가기 어려운 갈림길과 회전을 고릅니다",
                "03" to "분기점에 닿기 전 모습을 가는 순서대로 보여 줍니다",
            ).forEach { (number, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(dimens.dp(52)).background(colors.selectedSurface, RoundedCornerShape(dimens.radiusSmall)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(number, color = colors.accent, style = type.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.width(dimens.spaceM))
                    Text(label, color = colors.onSurfaceVariant, style = type.bodySmall)
                }
            }
        }
    }
}

// The route drawing is laid out in a box of this size; every number in it is in these units.
private const val ILLUSTRATION_WIDTH = 600f
private const val ILLUSTRATION_HEIGHT = 560f
private const val ROUTE_MARKER_DIAMETER = 52f
private val RouteStart = Offset(70f, 480f)
private val RouteMarkerCenters = listOf(Offset(202f, 390f), Offset(320f, 300f), Offset(429f, 208f))
private val RoutePinHead = Offset(530f, 71f)

/** A route with three places to look at and the destination, scaled as one piece to fit and centered. */
@Composable
private fun RouteIllustration(searching: Boolean, modifier: Modifier = Modifier) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    BoxWithConstraints(
        modifier.background(Brush.verticalGradient(listOf(colors.selectedSurface, colors.surfaceVariant))),
        contentAlignment = Alignment.Center,
    ) {
        // One unit of the drawing, as large as still shows all of it.
        val unit = min(maxWidth / ILLUSTRATION_WIDTH, maxHeight / ILLUSTRATION_HEIGHT)
        // A picture, not content: a screen reader should not read out "1, 2, 3".
        Box(Modifier.size(unit * ILLUSTRATION_WIDTH, unit * ILLUSTRATION_HEIGHT).clearAndSetSemantics { }) {
            Canvas(Modifier.fillMaxSize()) {
                scale(scale = size.width / ILLUSTRATION_WIDTH, pivot = Offset.Zero) {
                    val road = Path().apply {
                        moveTo(RouteStart.x, RouteStart.y)
                        cubicTo(230f, 480f, 180f, 300f, 320f, 300f)
                        cubicTo(460f, 300f, 400f, 120f, 530f, 100f)
                    }
                    drawPath(road, colors.surface, style = Stroke(width = 38f, cap = StrokeCap.Round))
                    drawPath(road, colors.primary, style = Stroke(width = 12f, cap = StrokeCap.Round))
                    drawCircle(colors.surface, radius = 14f, center = RouteStart)
                    drawCircle(colors.primary, radius = 14f, center = RouteStart, style = Stroke(width = 8f))
                    // The destination pin: a round head, and a triangle from the tip to where its sides touch the head.
                    val pinPoint = Path().apply {
                        moveTo(530f, 104f)
                        lineTo(514.5f, 82f)
                        lineTo(545.5f, 82f)
                        close()
                    }
                    drawPath(pinPoint, colors.violet)
                    drawCircle(colors.violet, radius = 19f, center = RoutePinHead)
                    drawCircle(colors.surface, radius = 7f, center = RoutePinHead)
                }
            }
            RouteMarkerCenters.forEachIndexed { index, center ->
                NumberMarker(
                    index + 1,
                    Modifier.offset(unit * (center.x - ROUTE_MARKER_DIAMETER / 2), unit * (center.y - ROUTE_MARKER_DIAMETER / 2)),
                    size = unit * ROUTE_MARKER_DIAMETER,
                    ring = true,
                    ringColor = colors.surface,
                )
            }
        }
        // In the corner the route leaves empty, so it never covers a marker.
        if (searching) {
            CircularProgressIndicator(Modifier.align(Alignment.TopStart).padding(dimens.spaceXl).size(dimens.dp(72)), color = colors.primary, strokeWidth = dimens.dp(8))
        }
    }
}

@Composable
internal fun RouteDataAttribution() {
    // The one line the map data's licence asks for; which services compute the route is not the driver's concern.
    Text("지도 데이터 © OpenStreetMap contributors", color = DejaVroomTheme.colors.onSurfaceVariant, style = DejaVroomTheme.type.labelSmall)
}

@Composable
internal fun RouteFieldLabel(label: String) {
    Text(label, color = DejaVroomTheme.colors.onSurfaceVariant, style = DejaVroomTheme.type.labelMedium)
}
