package com.ethran.notable.classes

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntOffset
import com.ethran.notable.modals.GlobalAppSettings
import com.ethran.notable.utils.SimplePointF
import kotlin.math.abs
import kotlin.math.sqrt


const val HOLD_THRESHOLD_MS = 300
private const val ONE_FINGER_TOUCH_TAP_TIME = 100L
private const val TAP_MOVEMENT_TOLERANCE = 15f
private const val SWIPE_THRESHOLD_SMOOTH = 100f
private const val TWO_FINGER_TOUCH_TAP_MAX_TIME = 200L
private const val TWO_FINGER_TOUCH_TAP_MIN_TIME = 20L
private const val TWO_FINGER_TAP_MOVEMENT_TOLERANCE = 20f
private const val PINCH_ZOOM_THRESHOLD = 0.5f
private const val SWIPE_THRESHOLD = 200f
const val DOUBLE_TAP_TIMEOUT_MS = 170L
const val DOUBLE_TAP_MIN_MS = 20L

enum class GestureMode {
    Selection,
    Scroll,
    Normal
}


data class GestureState(
    val initialPositions: MutableMap<PointerId, Offset> = mutableMapOf(),
    val lastPositions: MutableMap<PointerId, Offset> = mutableMapOf(),
    var initialTimestamp: Long = System.currentTimeMillis(),
    var lastTimestamp: Long = initialTimestamp,
    var gestureMode: GestureMode = GestureMode.Normal,
) {
    private var lastCheckForMovementPosition: Offset? = null

    fun getElapsedTime(): Long {
        return lastTimestamp - initialTimestamp
    }

    private fun calculateTotalDelta(): Float {
        return initialPositions.keys.sumOf { id ->
            val initial = initialPositions[id] ?: Offset.Zero
            val last = lastPositions[id] ?: initial
            (initial - last).getDistance().toDouble()
        }.toFloat()
    }

    fun getFirstPosition(): IntOffset? {
        return initialPositions.values.firstOrNull()?.let { point ->
            IntOffset(point.x.toInt(), point.y.toInt())
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
        } ?: getFirstPosition()
    }

    fun calculateRectangleBounds(): Rect? {
        if (initialPositions.isEmpty() && lastPositions.isEmpty()) return null

        val firstPosition = initialPositions.values.firstOrNull() ?: return null
        val lastPosition = lastPositions.values.firstOrNull() ?: firstPosition

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
    fun getHorizontalDrag(): Int {
        if (initialPositions.isEmpty() || lastPositions.isEmpty()) return 0

        var minHorizontalMovement: Float? = null

        for ((id, initial) in initialPositions) {
            val last = lastPositions[id] ?: continue
            val delta = last - initial

            // Check if the movement is more horizontal than vertical
            if (abs(delta.x) <= abs(delta.y)) return 0

            // Track the smallest horizontal movement
            if (minHorizontalMovement == null || abs(delta.x) < abs(minHorizontalMovement)) {
                minHorizontalMovement = delta.x
            }
        }
        if (abs(minHorizontalMovement ?: 0f) < SWIPE_THRESHOLD)
            return 0
        return minHorizontalMovement?.toInt() ?: 0
    }

    //return smallest vertical movement, or 0, if movement is not vertical
    fun getVerticalDrag(): Int {
        if (initialPositions.isEmpty() || lastPositions.isEmpty()) return 0

        var minVerticalMovement: Float? = null

        for ((id, initial) in initialPositions) {
            val last = lastPositions[id] ?: continue
            val delta = last - initial

            // Check if the movement is more vertical than horizontal
            if (abs(delta.y) <= abs(delta.x)) return 0

            // Track the smallest vertical movement
            if (minVerticalMovement == null || abs(delta.y) < abs(minVerticalMovement)) {
                minVerticalMovement = delta.y
            }
        }
        if (abs(minVerticalMovement ?: 0f) < SWIPE_THRESHOLD)
            return 0
        return minVerticalMovement?.toInt() ?: 0
    }

    // returns the delta from last request
    fun getVerticalDragDelta(): Int {
        if (lastPositions.isEmpty()) return 0
        val currentPosition = lastPositions.values.lastOrNull() ?: return 0
        if (lastCheckForMovementPosition == null) {
            lastCheckForMovementPosition = currentPosition
            return 0
        }
        val initial = lastCheckForMovementPosition?.y ?: return 0
        val last = currentPosition.y
        val delta = (last - initial).toInt()
        lastCheckForMovementPosition = currentPosition
        return delta
    }

    // returns value to be added or subtracted to zoom
    fun getPinchDelta(): Float {
        if (lastPositions.size < 2 || initialPositions.size < 2) return 1.0f

        val currentPointers = lastPositions.values.toList()
        val initialPointers = initialPositions.values.toList()

        val currentDx = currentPointers[0].x - currentPointers[1].x
        val currentDy = currentPointers[0].y - currentPointers[1].y
        val currentDistance = sqrt(currentDx * currentDx + currentDy * currentDy)

        val initialDx = initialPointers[0].x - initialPointers[1].x
        val initialDy = initialPointers[0].y - initialPointers[1].y
        val initialDistance = sqrt(initialDx * initialDx + initialDy * initialDy)

        if (initialDistance == 0f) return 0.0f
        val pinchDelta = currentDistance / initialDistance - 1.0f
        return if (abs(pinchDelta) > PINCH_ZOOM_THRESHOLD)
            pinchDelta
        else
            0.0f

    }


    fun isHolding(): Boolean {
        return if (getElapsedTime() >= HOLD_THRESHOLD_MS && getInputCount() == 1)
            if (calculateTotalDelta() < TAP_MOVEMENT_TOLERANCE)
                true
            else
                false
        else
            false
    }

    fun checkSmoothScrolling(): Boolean {
        return if (GlobalAppSettings.current.smoothScroll && abs(getVerticalDrag()) > SWIPE_THRESHOLD_SMOOTH && getInputCount() == 1) {
            gestureMode = GestureMode.Scroll
            true
        } else
            false
    }

    fun isOneFinger(): Boolean {
        return getInputCount() == 1
    }

    fun isTwoFingers(): Boolean {
        return getInputCount() == 2
    }

    fun isOneFingerTap(): Boolean {
        val totalDelta = calculateTotalDelta()
        val gestureDuration = getElapsedTime()
        return totalDelta < TAP_MOVEMENT_TOLERANCE && gestureDuration < ONE_FINGER_TOUCH_TAP_TIME
    }

    fun isTwoFingersTap(): Boolean {
        val totalDelta = calculateTotalDelta()
        val gestureDuration = getElapsedTime()
        return totalDelta < TWO_FINGER_TAP_MOVEMENT_TOLERANCE &&
                gestureDuration < TWO_FINGER_TOUCH_TAP_MAX_TIME &&
                gestureDuration > TWO_FINGER_TOUCH_TAP_MIN_TIME
    }
}