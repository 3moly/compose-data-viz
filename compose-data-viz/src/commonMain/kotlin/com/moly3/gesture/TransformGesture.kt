package com.moly3.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.time.Clock

private const val DEFAULT_DOUBLE_CLICK_TIMEOUT_MS = 300L
private const val DEFAULT_DOUBLE_CLICK_THRESHOLD = 100f
private const val DEGREES_IN_HALF_CIRCLE = 180f

suspend fun PointerInputScope.detectPointerTransformGestures(
    panZoomLock: Boolean = false,
    numberOfPointers: Int = 1,
    pass: PointerEventPass = PointerEventPass.Main,
    requisite: PointerRequisite = PointerRequisite.None,
    consume: Boolean = true,
    doubleClickDelay: Long? = null,
    onClick: (Offset) -> Unit = {},
    onDoubleClick: (Offset) -> Unit = {},
    onCursorMove: (Offset) -> Unit = {},
    onScrollChange: (delta: Offset) -> Unit = {},
    onGestureStart: (PointerInputChange) -> Unit = {},
    onGesture: (
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotation: Float,
        mainPointer: PointerInputChange,
        changes: List<PointerInputChange>,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onGestureEnd: (PointerInputChange) -> Unit = {},
    onGestureCancel: () -> Unit = {},
) {
    require(numberOfPointers >= 0) {
        "Number of minimum pointers should be greater than 0"
    }

    // Wrap in coroutineScope to safely launch parallel listeners on the same PointerInput stream
    coroutineScope {
        // Listener for cursor movement and scrolling (runs alongside the main gesture)
        launch {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { pointerInputChange ->
                        onCursorMove(pointerInputChange.position)
                        pointerInputChange.scrollDelta.let {
                            if (it.x != 0f || it.y != 0f) {
                                onScrollChange(Offset(-it.x, -it.y))
                            }
                        }
                    }
                }
            }
        }

        // Main gesture loop
        launch {
            // Double-click detection variables
            var lastClickTime = 0L
            var lastClickPosition = Offset.Zero
            val doubleClickTimeoutMs = doubleClickDelay ?: DEFAULT_DOUBLE_CLICK_TIMEOUT_MS
            val doubleClickThreshold = DEFAULT_DOUBLE_CLICK_THRESHOLD

            awaitEachGesture {
                var rotation = 0f
                var zoom = 1f
                var pan = Offset.Zero
                var pastTouchSlop = false
                val touchSlop = viewConfiguration.touchSlop
                var lockedToPanZoom = false
                var gestureStarted = false

                val down: PointerInputChange =
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = pass,
                    )
                onGestureStart(down)

                var pointer = down
                var pointerId = down.id

                do {
                    val event = awaitPointerEvent(pass = pass)

                    // BUG FIX: Actually count the pressed pointers
                    val downPointerCount = event.changes.size

                    val requirementFulfilled =
                        when (requisite) {
                            PointerRequisite.LessThan -> downPointerCount < numberOfPointers
                            PointerRequisite.EqualTo -> downPointerCount == numberOfPointers
                            PointerRequisite.GreaterThan -> downPointerCount > numberOfPointers
                            else -> true
                        }

                    val canceled = event.changes.any { it.isConsumed }

                    if (!canceled && requirementFulfilled) {
                        gestureStarted = true

                        val pointerInputChange =
                            event.changes.lastOrNull { it.id == pointerId }
                                ?: event.changes.first()

                        pointerId = pointerInputChange.id
                        pointer = pointerInputChange

                        val zoomChange = event.calculateZoom()
                        val rotationChange = event.calculateRotation()
                        val panChange = event.calculatePan()

                        if (!pastTouchSlop) {
                            zoom *= zoomChange
                            rotation += rotationChange
                            pan += panChange

                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = abs(1 - zoom) * centroidSize
                            val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / DEGREES_IN_HALF_CIRCLE)
                            val panMotion = pan.getDistance()

                            if (zoomMotion > touchSlop ||
                                rotationMotion > touchSlop ||
                                panMotion > touchSlop
                            ) {
                                pastTouchSlop = true
                                lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                            }
                        }

                        if (pastTouchSlop) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange

                            // Emit accumulated pan on the slop-crossing frame, then per-frame delta after
                            val panToEmit =
                                if (pan != Offset.Zero) {
                                    val p = pan
                                    pan = Offset.Zero // consume the accumulated buffer once
                                    p
                                } else {
                                    panChange
                                }

                            val zoomToEmit =
                                if (zoom != 1f) {
                                    val z = zoom
                                    zoom = 1f
                                    z
                                } else {
                                    zoomChange
                                }

                            if (effectiveRotation != 0f || zoomToEmit != 1f || panToEmit != Offset.Zero) {
                                onGesture(
                                    centroid,
                                    panToEmit,
                                    zoomToEmit,
                                    effectiveRotation,
                                    pointer,
                                    event.changes,
                                )
                            }

                            if (consume) {
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        }
                    }
                } while (!canceled && event.changes.any { it.pressed })

                if (gestureStarted) {
                    onGestureEnd(pointer)
                } else {
                    onGestureCancel()
                }

                // BUG FIX: Correctly check for double clicks and update the tracking variables
                if (!pastTouchSlop && !pointer.isConsumed && !pointer.pressed) {
                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    val timeDiff = currentTime - lastClickTime
                    val distanceDiff = (pointer.position - lastClickPosition).getDistance()

                    if (timeDiff <= doubleClickTimeoutMs && distanceDiff <= doubleClickThreshold) {
                        onDoubleClick(pointer.position)
                        // Reset to prevent triple-clicks registering as another double-click
                        lastClickTime = 0L
                    } else {
                        onClick(pointer.position)
                        lastClickTime = currentTime
                        lastClickPosition = pointer.position
                    }
                }
            }
        }
    }
}
