package com.ethran.notable.classes

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toOffset
import com.ethran.notable.TAG
import com.ethran.notable.db.selectImagesAndStrokes
import com.ethran.notable.utils.EditorState
import com.ethran.notable.utils.History
import com.ethran.notable.utils.Mode
import com.ethran.notable.utils.Operation
import com.ethran.notable.utils.PlacementMode
import com.ethran.notable.utils.divideStrokesFromCut
import com.ethran.notable.utils.offsetStroke
import com.ethran.notable.utils.strokeBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.UUID

class EditorControlTower(
    private val scope: CoroutineScope,
    val page: PageView,
    private val history: History,
    val state: EditorState
) {
    private var scrollInProgress = Mutex()
    private var scrollJob: Job? = null


    // returns delta if could not scroll, to be added to next request,
    // this ensures that smooth scroll works reliably even if rendering takes to long
    fun onSingleFingerVerticalSwipe(delta: Int): Int {
        if (delta == 0) return 0
        if (!page.scrollable) return 0
        if (scrollInProgress.isLocked) {
            Log.w(TAG, "Scroll in progress -- skipping")
            return delta
        } // Return unhandled part

        scrollJob = scope.launch(Dispatchers.Main.immediate) {
            scrollInProgress.withLock {
                val scaledDelta = (delta / page.zoomLevel.value).toInt()
                if (state.mode == Mode.Select) {
                    if (state.selectionState.firstPageCut != null) {
                        onOpenPageCut(scaledDelta)
                    } else {
                        onPageScroll(-delta)
                    }
                } else {
                    onPageScroll(-delta)
                }
            }
            DrawCanvas.refreshUi.emit(Unit)
        }
        return 0 // All handled
    }

    fun onPinchToZoom(delta: Float) {
        scope.launch {
            scrollInProgress.withLock {
                onPageZoom(delta)
            }
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    private fun onOpenPageCut(offset: Int) {
        if (offset < 0) return
        val cutLine = state.selectionState.firstPageCut!!

        val (_, previousStrokes) = divideStrokesFromCut(page.strokes, cutLine)

        // calculate new strokes to add to the page
        val nextStrokes = previousStrokes.map { stroke ->
            stroke.copy(points = stroke.points.map { point ->
                point.copy(x = point.x, y = point.y + offset)
            }, top = stroke.top + offset, bottom = stroke.bottom + offset)
        }

        // remove and paste
        page.removeStrokes(strokeIds = previousStrokes.map { it.id })
        page.addStrokes(nextStrokes)

        // commit to history
        history.addOperationsToHistory(
            listOf(
                Operation.DeleteStroke(nextStrokes.map { it.id }),
                Operation.AddStroke(previousStrokes)
            )
        )

        state.selectionState.reset()
        page.drawAreaScreenCoordinates(strokeBounds(previousStrokes + nextStrokes))
    }

    private suspend fun onPageScroll(dragDelta: Int) {
        // scroll is in Page coordinates
        page.updateScroll(dragDelta)
    }

    private suspend fun onPageZoom(delta: Float) {
        page.updateZoom(delta)
    }

    // when selection is moved, we need to redraw canvas
    fun applySelectionDisplace() {
        val operationList = state.selectionState.applySelectionDisplace(page)
        if (!operationList.isNullOrEmpty()) {
            history.addOperationsToHistory(operationList)
        }
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun deleteSelection() {
        val operationList = state.selectionState.deleteSelection(page)
        history.addOperationsToHistory(operationList)
        state.isDrawing = true
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun changeSizeOfSelection(scale: Int) {
        if (!state.selectionState.selectedImages.isNullOrEmpty())
            state.selectionState.resizeImages(scale, scope, page)
        if (!state.selectionState.selectedStrokes.isNullOrEmpty())
            state.selectionState.resizeStrokes(scale, scope, page)
        // Emit a refresh signal to update UI
        scope.launch {
            DrawCanvas.refreshUi.emit(Unit)
        }
    }

    fun duplicateSelection() {
        // finish ongoing movement
        applySelectionDisplace()
        state.selectionState.duplicateSelection()

    }

    fun cutSelectionToClipboard(context: Context) {
        state.clipboard = state.selectionState.selectionToClipboard(page.scroll, context)
        deleteSelection()
        showHint("Content cut to clipboard", scope)
    }

    fun copySelectionToClipboard(context: Context) {
        state.clipboard = state.selectionState.selectionToClipboard(page.scroll, context)
    }


    fun pasteFromClipboard() {
        // finish ongoing movement
        applySelectionDisplace()

        val (strokes, images) = state.clipboard ?: return

        val now = Date()
        val scrollPos = page.scroll
        val addPageScroll = IntOffset(0, scrollPos).toOffset()

        val pastedStrokes = strokes.map {
            offsetStroke(it, offset = addPageScroll).copy(
                // change the pasted strokes' ids - it's a copy
                id = UUID
                    .randomUUID()
                    .toString(),
                createdAt = now,
                // set the pageId to the current page
                pageId = this.page.id
            )
        }

        val pastedImages = images.map {
            it.copy(
                // change the pasted images' ids - it's a copy
                id = UUID
                    .randomUUID()
                    .toString(),
                y = it.y + scrollPos,
                createdAt = now,
                // set the pageId to the current page
                pageId = this.page.id
            )
        }

        history.addOperationsToHistory(
            operations = listOf(
                Operation.DeleteImage(pastedImages.map { it.id }),
                Operation.DeleteStroke(pastedStrokes.map { it.id }),
            )
        )

        selectImagesAndStrokes(scope, page, state, pastedImages, pastedStrokes)
        state.selectionState.placementMode = PlacementMode.Paste

        showHint("Pasted content from clipboard", scope)
    }
}

