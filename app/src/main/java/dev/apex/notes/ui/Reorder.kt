package dev.apex.notes.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Minimal drag-to-reorder for a LazyColumn where items are keyed by a stable [Long] key.
 * Only the dragged item translates; the list model is reordered as the finger crosses neighbours.
 */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (movingKey: Long, targetKey: Long) -> Unit,
) {
    var draggingKey by mutableStateOf<Long?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    fun start(key: Long) {
        draggingKey = key
        offset = 0f
    }

    fun drag(delta: Float, reorderableKeys: Set<Long>) {
        val key = draggingKey ?: return
        offset += delta
        val visible = listState.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == key } ?: return
        val centre = me.offset + offset + me.size / 2f
        val target = visible.firstOrNull { other ->
            other.key != key && (other.key as? Long) in reorderableKeys &&
                centre > other.offset && centre < other.offset + other.size
        } ?: return
        onMove(key, target.key as Long)
        // The dragged item now sits where the target was: compensate so it stays under the finger.
        offset -= (target.offset - me.offset)
    }

    fun end() {
        draggingKey = null
        offset = 0f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (Long, Long) -> Unit): ReorderState {
    val latest by rememberUpdatedState(onMove)
    return remember(listState) { ReorderState(listState) { a, b -> latest(a, b) } }
}

/** Apply to the whole row so it lifts and follows the finger while dragged. */
fun Modifier.reorderableItem(state: ReorderState, key: Long): Modifier {
    val dragging = state.draggingKey == key
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.offset else 0f
            shadowElevation = if (dragging) 8f else 0f
        }
}

/** Apply to the drag handle. */
fun Modifier.reorderHandle(state: ReorderState, key: Long, reorderableKeys: () -> Set<Long>): Modifier =
    pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y, reorderableKeys())
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
