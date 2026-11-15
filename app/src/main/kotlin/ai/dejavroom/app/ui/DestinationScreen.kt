package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.data.PHOTON_ATTRIBUTION
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.SuggestionStatus
import ai.dejavroom.app.feature.inputOf
import ai.dejavroom.app.feature.startsFromCurrentLocation
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.SearchField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private object Spacing {
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val touch = 48.dp
}

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

@Composable
private fun DestinationLayout(state: DejaVroomUiState, actions: DestinationActions, compact: Boolean, modifier: Modifier) {
    if (compact) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.s)) {
            DestinationForm(state, actions, Modifier.fillMaxWidth(), dense = true)
            Spacer(Modifier.height(Spacing.l))
            RouteAnalysisPreview(Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.s))
            RouteDataAttribution()
        }
    } else {
        Row(
            modifier.fillMaxSize().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                DestinationForm(state, actions, Modifier.fillMaxWidth(), dense = false)
            }
            Column(Modifier.weight(.95f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                RouteAnalysisPreview(Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.s))
                RouteDataAttribution()
            }
        }
    }
}

@Composable
internal fun DestinationForm(state: DejaVroomUiState, actions: DestinationActions, modifier: Modifier, dense: Boolean) {
    Column(modifier) {
        Eyebrow("DÉJÀ VROOM  ·  ROUTE ANALYSIS")
        Spacer(Modifier.height(Spacing.s))
        Text(
            "처음 가는 길도,\n처음 보는 길이 아니도록.",
            color = DejaVroomTheme.colors.white,
            fontSize = if (dense) 24.sp else 30.sp,
            lineHeight = if (dense) 30.sp else 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.s))
        Text("출발 전에 헷갈리기 쉬운 구간을 먼저 살펴보세요.", color = DejaVroomTheme.colors.muted, fontSize = if (dense) 13.sp else 15.sp)
        Spacer(Modifier.height(Spacing.l))

        // "Next" on the keyboard moves from the origin to the destination.
        val destinationFocus = remember { FocusRequester() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SearchFieldBlock(state, actions, SearchField.ORIGIN, dense, destinationFocus)
                Spacer(Modifier.height(Spacing.s))
                SearchFieldBlock(state, actions, SearchField.DESTINATION, dense, destinationFocus)
            }
            Spacer(Modifier.width(Spacing.s))
            // Swap sits beside the two fields, like most navigation apps.
            Box(
                Modifier.size(Spacing.touch).clip(CircleShape).background(DejaVroomTheme.colors.panelRaised)
                    .border(1.dp, DejaVroomTheme.colors.stroke, CircleShape).clickable(onClick = actions.onSwap),
                contentAlignment = Alignment.Center,
            ) {
                Text("⇅", color = DejaVroomTheme.colors.blueLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (state.errorMessage != null) {
            Spacer(Modifier.height(Spacing.s))
            Text(state.errorMessage, color = DejaVroomTheme.colors.error, fontSize = 13.sp)
        }

        if (state.activeField == null) {
            Spacer(Modifier.height(Spacing.l))
            RecentChips(state.recentSearches, actions)
        }

        Spacer(Modifier.height(Spacing.l))
        // A trip still under way: searching again from where the car is continues it.
        state.resumableTrip?.let { trip ->
            Text(tripResumeLine(trip), color = DejaVroomTheme.colors.blueLight, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Spacer(Modifier.height(Spacing.s))
        }
        PrimaryButton(
            if (state.isSearchingRoute) "복잡 교차로 분석 중" else "복잡 구간 분석",
            if (state.isSearchingRoute) "…" else "›",
            Modifier.fillMaxWidth(),
            actions.onSearch,
            enabled = !state.isSearchingRoute,
        )
    }
}

@Composable
private fun SearchFieldBlock(state: DejaVroomUiState, actions: DestinationActions, field: SearchField, dense: Boolean, destinationFocus: FocusRequester) {
    val isOrigin = field == SearchField.ORIGIN
    if (isOrigin) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { RouteFieldLabel("출발지") }
            TextButton(onClick = actions.onUseCurrentLocation, enabled = !state.isLocating, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(
                    if (state.isLocating) "현재 위치 찾는 중…" else "◎ 현재 위치",
                    color = if (state.isLocating) DejaVroomTheme.colors.muted else DejaVroomTheme.colors.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        RouteFieldLabel("목적지")
        Spacer(Modifier.height(6.dp))
    }
    RouteSearchField(
        icon = if (isOrigin) "⌾" else "⌕",
        value = state.inputOf(field),
        placeholder = if (isOrigin) "출발 장소를 검색하세요" else "목적지를 검색하세요",
        active = state.activeField == field,
        dense = dense,
        imeAction = if (isOrigin) ImeAction.Next else ImeAction.Search,
        onValueChange = { actions.onValueChange(field, it) },
        onFocus = { actions.onFocus(field) },
        onClear = { actions.onClear(field) },
        onImeAction = { if (isOrigin) destinationFocus.requestFocus() else actions.onSearch() },
        focusRequester = destinationFocus.takeUnless { isOrigin },
    )
    if (isOrigin) OriginCaption(state)
    if (state.activeField == field) {
        Spacer(Modifier.height(6.dp))
        SuggestionPanel(state, actions, field)
    }
}

/** Under the origin field: where the car's position is, in the navigation's words, or why it could not be read. */
@Composable
private fun OriginCaption(state: DejaVroomUiState) {
    val notice = state.locationNotice
    val address = state.originPlace?.takeIf { state.startsFromCurrentLocation }?.address.orEmpty()
    if (notice == null && address.isBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(
        notice?.let(::locationNoticeText) ?: address,
        color = if (notice != null) DejaVroomTheme.colors.warning else DejaVroomTheme.colors.muted,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = Spacing.m),
    )
}

@Composable
private fun RouteSearchField(
    icon: String,
    value: String,
    placeholder: String,
    active: Boolean,
    dense: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (dense) 52.dp else 56.dp)
            .background(DejaVroomTheme.colors.panel, shape)
            .border(if (active) 2.dp else 1.dp, if (active) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.stroke, shape)
            .padding(start = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = DejaVroomTheme.colors.blueLight, fontSize = 20.sp)
        Spacer(Modifier.width(Spacing.s))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = DejaVroomTheme.colors.dim, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = DejaVroomTheme.colors.white, fontSize = 17.sp),
                cursorBrush = SolidColor(DejaVroomTheme.colors.blue),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus() },
            )
        }
        // The clear slot is always reserved so the field does not shift when text appears.
        Box(Modifier.size(Spacing.touch), contentAlignment = Alignment.Center) {
            if (value.isNotEmpty()) {
                Box(
                    Modifier.size(Spacing.touch).clip(CircleShape).clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) { Text("×", color = DejaVroomTheme.colors.muted, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun SuggestionPanel(state: DejaVroomUiState, actions: DestinationActions, field: SearchField) {
    val query = state.inputOf(field).trim()
    val suggestions = state.suggestions.takeIf { it.field == field }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(DejaVroomTheme.colors.panel)
            .border(1.dp, DejaVroomTheme.colors.stroke, shape).padding(vertical = 6.dp),
    ) {
        if (query.isEmpty()) {
            RecentList(state.recentSearches, field, actions)
            return@Column
        }
        // Fixed-height status row: loading/empty/error never move the rows below it.
        Row(Modifier.fillMaxWidth().height(Spacing.touch).padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    query.length < 2 -> "두 글자 이상 입력하면 추천 검색어가 나옵니다"
                    suggestions?.status == SuggestionStatus.LOADING -> "추천 검색어 찾는 중…"
                    suggestions?.status == SuggestionStatus.EMPTY -> "추천 결과가 없습니다. 입력한 그대로 검색할 수 있습니다"
                    suggestions?.status == SuggestionStatus.ERROR -> "추천 검색어를 불러오지 못했습니다. 입력한 그대로 검색할 수 있습니다"
                    else -> "추천 검색어"
                },
                color = if (suggestions?.status == SuggestionStatus.ERROR) DejaVroomTheme.colors.error else DejaVroomTheme.colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onDismissSuggestions) { Text("닫기", color = DejaVroomTheme.colors.muted, fontSize = 12.sp) }
        }
        suggestions?.items.orEmpty().take(6).forEach { place ->
            PlaceRow(
                title = highlight(place.name, query),
                subtitle = place.address,
                leading = "⌖",
                onClick = { actions.onSelectPlace(field, place) },
            )
        }
        if (suggestions?.items.orEmpty().isNotEmpty()) {
            Text(PHOTON_ATTRIBUTION, color = DejaVroomTheme.colors.dim, fontSize = 10.sp, modifier = Modifier.padding(horizontal = Spacing.m, vertical = 4.dp))
        }
    }
}

@Composable
private fun RecentList(recents: List<Place>, field: SearchField, actions: DestinationActions) {
    // The panel can always be closed from its own header, with or without anything to list.
    Row(Modifier.fillMaxWidth().height(Spacing.touch).padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Text(if (recents.isEmpty()) "최근 검색이 없습니다" else "최근 검색", color = DejaVroomTheme.colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (recents.isNotEmpty()) TextButton(onClick = actions.onClearRecents) { Text("전체 삭제", color = DejaVroomTheme.colors.muted, fontSize = 12.sp) }
        TextButton(onClick = actions.onDismissSuggestions) { Text("닫기", color = DejaVroomTheme.colors.muted, fontSize = 12.sp) }
    }
    recents.forEach { place ->
        PlaceRow(
            title = AnnotatedString(place.name),
            subtitle = place.address,
            leading = "↺",
            onClick = { actions.onSelectPlace(field, place) },
            onRemove = { actions.onRemoveRecent(place) },
        )
    }
}

@Composable
private fun PlaceRow(title: AnnotatedString, subtitle: String, leading: String, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(leading, color = DejaVroomTheme.colors.blueLight, fontSize = 16.sp)
        Spacer(Modifier.width(Spacing.s))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, color = DejaVroomTheme.colors.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = DejaVroomTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onRemove != null) {
            Box(Modifier.size(Spacing.touch).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Text("×", color = DejaVroomTheme.colors.dim, fontSize = 18.sp)
            }
        } else {
            Spacer(Modifier.width(Spacing.m))
        }
    }
}

@Composable
private fun RecentChips(recents: List<Place>, actions: DestinationActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("최근 목적지", color = DejaVroomTheme.colors.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (recents.isNotEmpty()) TextButton(onClick = actions.onClearRecents) { Text("전체 삭제", color = DejaVroomTheme.colors.muted, fontSize = 12.sp) }
    }
    Spacer(Modifier.height(6.dp))
    if (recents.isEmpty()) {
        Text("검색한 목적지가 여기에 쌓입니다.", color = DejaVroomTheme.colors.dim, fontSize = 13.sp)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        recents.take(3).forEach { place ->
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(DejaVroomTheme.colors.panel)
                    .clickable { actions.onSelectPlace(SearchField.DESTINATION, place) }
                    .padding(horizontal = Spacing.s, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(place.name, color = DejaVroomTheme.colors.white, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(place.address.ifBlank { "최근 검색" }, color = DejaVroomTheme.colors.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    val start = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    val color = DejaVroomTheme.colors.blueLight
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(start, start + query.length)) }
        append(text.substring(start + query.length))
    }
}

@Composable
internal fun RouteAnalysisPreview(modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(DejaVroomTheme.colors.panel).border(1.dp, DejaVroomTheme.colors.stroke, RoundedCornerShape(22.dp)).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text("헷갈리기 쉬운 곳만 골라\n가기 전에 미리 보여 줍니다", color = DejaVroomTheme.colors.white, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        listOf(
            "01" to "출발지와 목적지로 실제 도로 경로를 찾습니다",
            "02" to "놓치면 돌아가기 어려운 갈림길과 회전을 고릅니다",
            "03" to "분기점에 닿기 전 모습을 가는 순서대로 보여 줍니다",
        ).forEach { (number, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number, color = DejaVroomTheme.colors.blueLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(Spacing.s))
                Text(label, color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun RouteDataAttribution() {
    // The one line the map data's licence asks for; which services compute the route is not the driver's concern.
    Text("지도 데이터 © OpenStreetMap contributors", color = DejaVroomTheme.colors.dim, fontSize = 11.sp, lineHeight = 14.sp)
}

@Composable
internal fun RouteFieldLabel(label: String) {
    Text(label, color = DejaVroomTheme.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}
