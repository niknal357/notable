package com.ethran.notable.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.createBitmap
import com.ethran.notable.classes.ClipboardContent
import com.ethran.notable.classes.PageView
import com.ethran.notable.classes.showHint
import com.ethran.notable.datastore.EditorSettingCacheManager
import com.ethran.notable.db.Image
import com.ethran.notable.db.Stroke
import kotlinx.coroutines.CoroutineScope

enum class Mode {
    Draw, Erase, Select, Line
}

class EditorState(val bookId: String? = null, val pageId: String, val pageView: PageView) {

    private val persistedEditorSettings = EditorSettingCacheManager.getEditorSettings()

    var mode by mutableStateOf(persistedEditorSettings?.mode ?: Mode.Draw) // should save
    var pen by mutableStateOf(persistedEditorSettings?.pen ?: Pen.BALLPEN) // should save
    var eraser by mutableStateOf(persistedEditorSettings?.eraser ?: Eraser.PEN) // should save
    var isDrawing by mutableStateOf(true)
    var isToolbarOpen by mutableStateOf(
        persistedEditorSettings?.isToolbarOpen ?: false
    ) // should save
    var penSettings by mutableStateOf(
        persistedEditorSettings?.penSettings ?: mapOf(
            Pen.BALLPEN.penName to PenSetting(5f, Color.BLACK),
            Pen.REDBALLPEN.penName to PenSetting(5f, Color.RED),
            Pen.BLUEBALLPEN.penName to PenSetting(5f, Color.BLUE),
            Pen.GREENBALLPEN.penName to PenSetting(5f, Color.GREEN),
            Pen.PENCIL.penName to PenSetting(5f, Color.BLACK),
            Pen.BRUSH.penName to PenSetting(5f, Color.BLACK),
            Pen.MARKER.penName to PenSetting(40f, Color.LTGRAY),
            Pen.FOUNTAIN.penName to PenSetting(5f, Color.BLACK)
        )
    )

    val selectionState = SelectionState()

    private var _clipboard by mutableStateOf(Clipboard.content)
    var clipboard
        get() = _clipboard
        set(value) {
            this._clipboard = value

            // The clipboard content must survive the EditorState, so we store a copy in
            // a singleton that lives outside of the EditorState
            Clipboard.content = value
        }
}

// if state is Move then applySelectionDisplace() will delete original strokes and images
enum class PlacementMode {
    Move,
    Paste
}

class SelectionState {
    var firstPageCut by mutableStateOf<List<SimplePointF>?>(null)
    var secondPageCut by mutableStateOf<List<SimplePointF>?>(null)
    var selectedStrokes by mutableStateOf<List<Stroke>?>(null)
    var selectedImages by mutableStateOf<List<Image>?>(null)
    var selectedBitmap by mutableStateOf<Bitmap?>(null)
    var selectionStartOffset by mutableStateOf<IntOffset?>(null)
    var selectionDisplaceOffset by mutableStateOf<IntOffset?>(null)
    var selectionRect by mutableStateOf<Rect?>(null)
    var placementMode by mutableStateOf<PlacementMode?>(null)

    fun reset() {
        selectedStrokes = null
        selectedImages = null
        secondPageCut = null
        firstPageCut = null
        selectedBitmap = null
        selectionStartOffset = null
        selectionRect = null
        selectionDisplaceOffset = null
        placementMode = null
    }

    fun isResizable(): Boolean {
        return selectedImages?.count() == 1 && selectedStrokes.isNullOrEmpty()
    }

    fun resizeImages(scale: Int, scope: CoroutineScope, page: PageView){
        val selectedImagesCopy = selectedImages?.map { image ->
            image.copy(
                height = image.height + (image.height * scale / 100),
                width = image.width + (image.width * scale / 100)
            )
        }

        // Ensure selected images are not null or empty
        if (selectedImagesCopy.isNullOrEmpty()) {
            showHint("For now, strokes cannot be resized", scope)
            return
        }

        selectedImages = selectedImagesCopy
        // Adjust displacement offset by half the size change
        val sizeChange = selectedImagesCopy.firstOrNull()?.let { image ->
            IntOffset(
                x = (image.width * scale / 200),
                y = (image.height * scale / 200)
            )
        } ?: IntOffset.Zero

        val pageBounds = imageBoundsInt(selectedImagesCopy)
        selectionRect = pageAreaToCanvasArea(pageBounds, page.scroll)

        selectionDisplaceOffset =
            selectionDisplaceOffset?.let { it - sizeChange }
                ?: IntOffset.Zero

        val selectedBitmapNew = createBitmap(pageBounds.width(), pageBounds.height())
        val selectedCanvas = Canvas(selectedBitmapNew)
        selectedImagesCopy.forEach {
            drawImage(
                page.context,
                selectedCanvas,
                it,
                IntOffset(-it.x, -it.y)
            )
        }

        // set state
        selectedBitmap = selectedBitmapNew
    }
    fun resizeStrokes(scale: Int, scope: CoroutineScope, page: PageView){
        //TODO: implement this
    }
}

object Clipboard {
    var content: ClipboardContent? = null;
}