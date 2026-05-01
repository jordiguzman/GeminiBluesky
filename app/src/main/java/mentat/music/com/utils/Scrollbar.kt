package mentat.music.com.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState

// Pega esto fuera de cualquier clase, es una extensión
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    width: androidx.compose.ui.unit.Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()

    val firstVisibleElementIndex = state.firstVisibleItemIndex
    val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
    val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
    val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

    // Pintamos la barrita a la derecha
    drawRect(
        color = Color.Gray.copy(alpha = 0.5f),
        topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
        size = Size(width.toPx(), scrollbarHeight)
    )
}