package com.olup.notable.components

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.olup.notable.AppRepository
import com.olup.notable.AppSettings
import com.olup.notable.DrawCanvas
import com.olup.notable.EditorControlTower
import com.olup.notable.EditorState
import com.olup.notable.History
import com.olup.notable.Mode
import com.olup.notable.SimplePoint
import com.olup.notable.SimplePointF
import com.olup.notable.SnackConf
import com.olup.notable.SnackState
import com.olup.notable.TAG
import com.olup.notable.UndoRedoType
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs


data class GestureState(
    val initialPositions: MutableMap<PointerId, Offset> = mutableMapOf(),
    val lastPositions: MutableMap<PointerId, Offset> = mutableMapOf(),
    var initialTimestamp: Long = System.currentTimeMillis(),
    var lastTimestamp: Long = initialTimestamp,
) {
    fun getElapsedTime(): Long {
        return lastTimestamp - initialTimestamp
    }

    fun calculateTotalDelta(): Float {
        return initialPositions.keys.sumOf { id ->
            val initial = initialPositions[id] ?: Offset.Zero
            val last = lastPositions[id] ?: Offset.Zero
            (initial - last).getDistance().toDouble()
        }.toFloat()
    }

    fun getFirstPosition(): SimplePoint? {
        return initialPositions.values.firstOrNull()?.let { point ->
            SimplePoint(point.x.toInt(), point.y.toInt())
        }
    }

    fun getFirstPositionF(): SimplePointF? {
        return initialPositions.values.firstOrNull()?.let { point ->
            SimplePointF(point.x, point.y)
        }
    }

    fun getLastPositionIO(): IntOffset? {
        return lastPositions.values.firstOrNull()?.let { point ->
            IntOffset(point.x.toInt(), point.y.toInt())
        }
    }

    fun calculateRectangleBounds(): Rect? {
        if (initialPositions.isEmpty() || lastPositions.isEmpty()) return null

        val firstPosition = initialPositions.values.firstOrNull() ?: return null
        val lastPosition = lastPositions.values.firstOrNull() ?: return null

        return Rect(
            firstPosition.x.coerceAtMost(lastPosition.x).toInt(),
            firstPosition.y.coerceAtMost(lastPosition.y).toInt(),
            firstPosition.x.coerceAtLeast(lastPosition.x).toInt(),
            firstPosition.y.coerceAtLeast(lastPosition.y).toInt()
        )
    }

    // Insert a position for the given pointer ID
    fun insertPosition(input: PointerInputChange) {
        lastTimestamp = System.currentTimeMillis()
        if (initialPositions.containsKey(input.id)) {
            // Update last position if the pointer ID already exists in initial positions
            lastPositions[input.id] = input.position

        } else {
            // Add to initial positions if the pointer ID is new
            initialPositions[input.id] = input.position
        }
    }

    // Get the current number of active inputs
    fun getInputCount(): Int {
        return initialPositions.size
    }

    //return smallest horizontal movement, or 0, if movement is not horizontal
    fun getHorizontalDrag(): Float {
        if (initialPositions.isEmpty() || lastPositions.isEmpty()) return 0f

        var minHorizontalMovement: Float? = null

        for ((id, initial) in initialPositions) {
            val last = lastPositions[id] ?: continue
            val delta = last - initial

            // Check if the movement is more horizontal than vertical
            if (abs(delta.x) <= abs(delta.y)) return 0f

            // Track the smallest horizontal movement
            if (minHorizontalMovement == null || abs(delta.x) < abs(minHorizontalMovement)) {
                minHorizontalMovement = delta.x
            }
        }

        return minHorizontalMovement ?: 0f
    }

    //return smallest vertical movement, or 0, if movement is not vertical
    fun getVerticalDrag(): Float {
        if (initialPositions.isEmpty() || lastPositions.isEmpty()) return 0f

        var minVerticalMovement: Float? = null

        for ((id, initial) in initialPositions) {
            val last = lastPositions[id] ?: continue
            val delta = last - initial

            // Check if the movement is more vertical than horizontal
            if (abs(delta.y) <= abs(delta.x)) return 0f

            // Track the smallest vertical movement
            if (minVerticalMovement == null || abs(delta.y) < abs(minVerticalMovement)) {
                minVerticalMovement = delta.y
            }
        }
        return minVerticalMovement ?: 0f
    }
}

private const val HOLD_THRESHOLD_MS = 300
private const val ONE_FINGER_TOUCH_TAP_TIME = 100L
private const val TAP_MOVEMENT_TOLERANCE = 15f
private const val SWIPE_THRESHOLD = 200f
private const val DOUBLE_TAP_TIMEOUT_MS = 170L
private const val TWO_FINGER_TOUCH_TAP_TIME = 200L
private const val TWO_FINGER_TAP_MOVEMENT_TOLERANCE = 20f


@Composable
@ExperimentalComposeUiApi
fun EditorGestureReceiver(
    goToNextPage: () -> Unit,
    goToPreviousPage: () -> Unit,
    controlTower: EditorControlTower,
    state: EditorState
) {

    val coroutineScope = rememberCoroutineScope()
    val appSettings by AppRepository(LocalContext.current)
        .kvProxy
        .observeKv("APP_SETTINGS", AppSettings.serializer(), AppSettings(version = 1))
        .observeAsState()

    var crossPosition by remember { mutableStateOf<IntOffset?>(null) }
    var rectangleBounds by remember { mutableStateOf<Rect?>(null) }
    var redrawTrigger by remember { mutableStateOf(0) }
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val gestureState = GestureState()
                    var isSelection = false
                    // Detect initial touch
                    val down = awaitFirstDown()

                    // Ignore non-touch input
                    if (down.type != PointerType.Touch) {
                        Log.i(TAG, "Ignoring non-touch input")
                        return@awaitEachGesture
                    }
                    gestureState.initialTimestamp = System.currentTimeMillis()
                    gestureState.insertPosition(down)

                    do {
                        // wait for second gesture
                        val event = withTimeoutOrNull(1000L) { awaitPointerEvent() }

                        if (event != null) {
                            val fingerChange = event.changes.filter { it.type == PointerType.Touch }

                            // is already consumed return
                            if (fingerChange.find { it.isConsumed } != null) {
                                Log.i(TAG, "Canceling gesture - already consumed")
                                crossPosition = null
                                rectangleBounds = null
                                redrawTrigger = 2
                                return@awaitEachGesture
                            }
                            fingerChange.forEach { change ->
                                // Consume changes and update positions
                                change.consume()
                                gestureState.insertPosition(change)
                            }
                            if (fingerChange.any { !it.pressed }) {
                                gestureState.lastTimestamp = System.currentTimeMillis()
                                break
                            }
                        }
                        // events are only send on change, so we need to check for holding in place separately
                        gestureState.lastTimestamp = System.currentTimeMillis()
                        if (isSelection) {
                            crossPosition = gestureState.getLastPositionIO()
                            rectangleBounds = gestureState.calculateRectangleBounds()
                        } else if (gestureState.getElapsedTime() >= HOLD_THRESHOLD_MS && gestureState.getInputCount() == 1) {
                            Log.i(TAG, "Held for ${gestureState.getElapsedTime()}ms")
                            if (gestureState.calculateTotalDelta() < TAP_MOVEMENT_TOLERANCE) {
                                isSelection = true
                                crossPosition = gestureState.getLastPositionIO()
                                rectangleBounds = gestureState.calculateRectangleBounds()
                                coroutineScope.launch {
                                    SnackState.globalSnackFlow.emit(
                                        SnackConf(
                                            text = "Selection mode!",
                                            duration = 1500,
                                        )
                                    )
                                }
                            }

                        }
                    } while (true)

                    if (isSelection) {
                        resolveGesture(
                            settings = appSettings,
                            default = AppSettings.defaultHoldAction,
                            override = AppSettings::holdAction,
                            state = state,
                            scope = coroutineScope,
                            previousPage = goToPreviousPage,
                            nextPage = goToNextPage,
                            rectangle = rectangleBounds!!
                        )
                        crossPosition = null
                        rectangleBounds = null
                        return@awaitEachGesture
                    }
                    // Calculate the total delta (movement distance) for all pointers
                    val totalDelta = gestureState.calculateTotalDelta()
                    val gestureDuration = gestureState.getElapsedTime()
                    Log.i(
                        TAG,
                        "Leaving gesture. totalDelta: ${totalDelta}, gestureDuration: $gestureDuration "
                    )

                    if (gestureState.getInputCount() == 1) {
                        if (totalDelta < TAP_MOVEMENT_TOLERANCE && gestureDuration < ONE_FINGER_TOUCH_TAP_TIME) {
                            if (withTimeoutOrNull(DOUBLE_TAP_TIMEOUT_MS) {
                                    val secondDown = awaitFirstDown()
                                    coroutineScope.launch {
                                        SnackState.globalSnackFlow.emit(
                                            SnackConf(
                                                text = "double click! delta: $totalDelta, time between: ${System.currentTimeMillis() - gestureState.lastTimestamp}",
                                                duration = 500,
                                            )
                                        )
                                    }
                                    Log.i(
                                        TAG,
                                        "Second down detected: ${secondDown.type}, position: ${secondDown.position}"
                                    )
                                    if (secondDown.type != PointerType.Touch) {
                                        Log.i(
                                            TAG,
                                            "Ignoring non-touch input during double-tap detection"
                                        )
                                        return@withTimeoutOrNull null
                                    }
                                    resolveGesture(
                                        settings = appSettings,
                                        default = AppSettings.defaultDoubleTapAction,
                                        override = AppSettings::doubleTapAction,
                                        state = state,
                                        scope = coroutineScope,
                                        previousPage = goToPreviousPage,
                                        nextPage = goToNextPage,
                                    )


                                } != null) return@awaitEachGesture
                        }
                    } else if (gestureState.getInputCount() == 2) {
                        coroutineScope.launch {
                            SnackState.globalSnackFlow.emit(
                                SnackConf(
                                    text = "Two finger tap, delta: $totalDelta, duration $gestureDuration",
                                    duration = 1000,
                                )
                            )
                        }
                        if (totalDelta < TWO_FINGER_TAP_MOVEMENT_TOLERANCE && gestureDuration < TWO_FINGER_TOUCH_TAP_TIME) {
                            resolveGesture(
                                settings = appSettings,
                                default = AppSettings.defaultTwoFingerTapAction,
                                override = AppSettings::twoFingerTapAction,
                                state = state,
                                scope = coroutineScope,
                                previousPage = goToPreviousPage,
                                nextPage = goToNextPage,
                            )
                        }
                    }

                    val horizontalDrag = gestureState.getHorizontalDrag()
                    val verticalDrag = gestureState
                        .getVerticalDrag()
                        .toInt()

                    Log.i(TAG, "horizontalDrag $horizontalDrag, verticalDrag $verticalDrag")
                    when {
                        horizontalDrag < -SWIPE_THRESHOLD -> {
                            resolveGesture(
                                settings = appSettings,
                                default = if (gestureState.getInputCount() == 1) AppSettings.defaultSwipeLeftAction else AppSettings.defaultTwoFingerSwipeLeftAction,
                                override = if (gestureState.getInputCount() == 1) AppSettings::swipeLeftAction else AppSettings::twoFingerSwipeLeftAction,
                                state = state,
                                scope = coroutineScope,
                                previousPage = goToPreviousPage,
                                nextPage = goToNextPage,
                            )
                        }

                        horizontalDrag > SWIPE_THRESHOLD -> {
                            resolveGesture(
                                settings = appSettings,
                                default = if (gestureState.getInputCount() == 1) AppSettings.defaultSwipeRightAction else AppSettings.defaultTwoFingerSwipeRightAction,
                                override = if (gestureState.getInputCount() == 1) AppSettings::swipeRightAction else AppSettings::twoFingerSwipeRightAction,
                                state = state,
                                scope = coroutineScope,
                                previousPage = goToPreviousPage,
                                nextPage = goToNextPage,
                            )
                        }

                    }

                    if (abs(verticalDrag) > SWIPE_THRESHOLD && gestureState.getInputCount() == 1) {
                        controlTower.onSingleFingerVerticalSwipe(
                            gestureState.getFirstPositionF()!!,
                            verticalDrag
                        )
                    }

                }
            }
            .fillMaxWidth()
            .fillMaxHeight()
    ) {


        val density = LocalDensity.current
        // Resolves an issue where selection visuals break after switching from finger touch to stylus input.
        // When this occurs, the UI fails to reflect subsequent gestures (e.g., selection rectangles) until
        // a UI refresh  ig successful gesture forces a refresh.
        // TODO: Investigate and implement a cleaner solution to better handle the touch-to-stylus transition without extra refreshes.
        if (redrawTrigger > 1) {
            coroutineScope.launch {
                DrawCanvas.refreshUi.emit(Unit)
            }
            --redrawTrigger
        }
        // enable drawing of next rectangle
        if (redrawTrigger > 0 && crossPosition != null) {
            coroutineScope.launch {
                DrawCanvas.refreshUi.emit(Unit)
            }
            --redrawTrigger
        }

        // Draw cross where finger is touching
        crossPosition?.let { pos ->
            val crossSizePx = with(density) { 100.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            pos.x - (crossSizePx / 2).toInt(),
                            pos.y
                        )
                    } // Horizontal bar centered
                    .size(width = 100.dp, height = 2.dp)
                    .background(Color.Black)
            )
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            pos.x,
                            pos.y - (crossSizePx / 2).toInt()
                        )
                    } // Vertical bar centered
                    .size(width = 2.dp, height = 100.dp)
                    .background(Color.Black)
            )
        }

        // Draw the rectangle while dragging
        rectangleBounds?.let { bounds ->
            // Draw the rectangle
            Box(
                Modifier
                    .offset { IntOffset(bounds.left, bounds.top) }
                    .size(
                        width = with(density) { (bounds.right - bounds.left).toDp() },
                        height = with(density) { (bounds.bottom - bounds.top).toDp() }
                    )
                    // Is there rendering speed difference between colors?
                    .background(Color(0x55000000))
                    .border(1.dp, Color.Black)
            )
        }


    }
}

private fun resolveGesture(
    settings: AppSettings?,
    default: AppSettings.GestureAction,
    override: AppSettings.() -> AppSettings.GestureAction?,
    state: EditorState,
    scope: CoroutineScope,
    previousPage: () -> Unit,
    nextPage: () -> Unit,
    rectangle: Rect = Rect()
) {
    when (if (settings != null) override(settings) else default) {
        null -> Log.i(TAG, "No Action")
        AppSettings.GestureAction.PreviousPage -> previousPage()
        AppSettings.GestureAction.NextPage -> nextPage()

        AppSettings.GestureAction.ChangeTool ->
            state.mode = if (state.mode == Mode.Draw) Mode.Erase else Mode.Draw

        AppSettings.GestureAction.ToggleZen ->
            state.isToolbarOpen = !state.isToolbarOpen

        AppSettings.GestureAction.Undo -> {
            Log.i(TAG, "Undo")
            scope.launch {
                History.moveHistory(UndoRedoType.Undo)
//                moved to history operation - avoids unnecessary refresh, and ensures that it will be done after drawing.
//                DrawCanvas.refreshUi.emit(Unit)
            }
        }

        AppSettings.GestureAction.Redo -> {
            Log.i(TAG, "Redo")
            scope.launch {
                History.moveHistory(UndoRedoType.Redo)
//                DrawCanvas.refreshUi.emit(Unit)
            }
        }

        AppSettings.GestureAction.Select -> {
            Log.i(TAG, "select")
            scope.launch {
                DrawCanvas.rectangleToSelect.emit(rectangle)
            }
        }
    }
}

