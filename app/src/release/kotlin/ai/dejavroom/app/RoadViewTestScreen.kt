package ai.dejavroom.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RouteSnapshot

@Composable
internal fun RoadViewTestScreen(
    route: RouteSnapshot?,
    candidates: List<BriefingSegment>,
    selectedIndex: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Text("실제 경로 Roadview 미리보기는 debug 빌드에서만 사용할 수 있습니다.", Modifier.fillMaxSize())
}
