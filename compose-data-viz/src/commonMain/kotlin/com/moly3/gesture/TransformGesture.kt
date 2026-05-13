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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Single-event-loop pointer transform gesture detector.
 *
 * Structure (important):
 *
 *   coroutineScope {                       // unrestricted; can launch jobs here
 *     val outerScope = this
 *     awaitEachGesture {                   // restricted AwaitPointerEventScope;
 *       ...                                // ONE gesture per invocation, the
 *     }                                    // function loops internally
 *   }
 *
 * Earlier attempts wrapped `awaitEachGesture` in `awaitPointerEventScope { while(true) ... }`,
 * which is wrong on two counts:
 *  1. `awaitEachGesture` is a `PointerInputScope` extension and opens its own
 *     `awaitPointerEventScope` — nesting them is invalid.
 *  2. Inside `awaitPointerEventScope` you cannot call regular suspend functions
 *     because the scope is `@RestrictsSuspension`.
 *
 * `awaitEachGesture` already loops internally over gesture lifecycles, so no
 * outer `while (true)` is needed — calling it once is enough.
 *
 * For the deferred-click timer we need `delay`/`launch`, which the restricted
 * scope forbids. We capture an unrestricted `CoroutineScope` via the outer
 * `coroutineScope { }` block and route `launch` through it explicitly.
 *
 * Fixes vs the version you originally posted:
 *  - Pan no longer drops the first `touchSlop` pixels (accumulator flushed on
 *    the slop-crossing event).
 *  - Pointer count uses `count { it.pressed }`, not `event.changes.size`
 *    (the change list contains released pointers on up-events).
 *  - Double-click cancels the deferred single-click instead of firing both.
 *  - Scroll only fires for `PointerEventType.Scroll` events.
 *  - Cursor-move and scroll callbacks are folded into the same event loop, so
 *    we no longer run two competing `awaitPointerEventScope` coroutines.
 */
@OptIn(ExperimentalTime::class)
suspend fun PointerInputScope.detectPointerTransformGestures(
    panZoomLock: Boolean = false,
    numberOfPointers: Int = 1,
    pass: PointerEventPass = PointerEventPass.Main,
    requisite: PointerRequisite = PointerRequisite.None,
    consume: Boolean = true,
    doubleClickDelay: Long = 280L,
    doubleClickThresholdPx: Float = 24f,
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
        changes: List<PointerInputChange>
    ) -> Unit = { _, _, _, _, _, _ -> },
    onGestureEnd: (PointerInputChange) -> Unit = {},
    onGestureCancel: () -> Unit = {},
) {
    require(numberOfPointers >= 0) { "numberOfPointers must be >= 0" }

    coroutineScope {
        // Unrestricted scope captured before entering the restricted gesture block.
        val outerScope: CoroutineScope = this
        var pendingClickJob: Job? = null
        var lastTapTime = 0L
        var lastTapPosition = Offset.Zero

        // `awaitEachGesture` opens its own AwaitPointerEventScope AND loops
        // internally for the lifetime of the pointerInput modifier. Call it once.
        awaitEachGesture {
            var rotation = 0f
            var zoomAccum = 1f
            var panAccum = Offset.Zero
            var pastTouchSlop = false
            val touchSlop = viewConfiguration.touchSlop
            var lockedToPanZoom = false
            var gestureStarted = false
            var maxPointersDuringGesture = 1

            val down: PointerInputChange = awaitFirstDown(
                requireUnconsumed = false,
                pass = pass
            )
            onGestureStart(down)
            onCursorMove(down.position)

            var pointer = down
            var pointerId = down.id
            var canceled = false

            do {
                val event = awaitPointerEvent(pass = pass)

                // Cursor move (also fires for hover on desktop).
                event.changes.forEach { change ->
                    onCursorMove(change.position)
                }

                // Scroll deltas — only on Scroll events.
                if (event.type == PointerEventType.Scroll) {
                    event.changes.forEach { change ->
                        val sd = change.scrollDelta
                        if (sd.x != 0f || sd.y != 0f) {
                            onScrollChange(Offset(-sd.x, -sd.y))
                        }
                    }
                }

                // Pointers actually pressed (changes list contains released
                // pointers on the up-event).
                val pressedCount = event.changes.count { it.pressed }
                maxPointersDuringGesture =
                    maxOf(maxPointersDuringGesture, pressedCount)

                val requirementFulfilled = when (requisite) {
                    PointerRequisite.LessThan -> pressedCount < numberOfPointers
                    PointerRequisite.EqualTo -> pressedCount == numberOfPointers
                    PointerRequisite.GreaterThan -> pressedCount > numberOfPointers
                    else -> true
                }

                canceled = event.changes.any { it.isConsumed }

                if (!canceled && requirementFulfilled) {
                    gestureStarted = true

                    val tracked = event.changes.lastOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull { it.pressed }
                        ?: event.changes.first()
                    pointerId = tracked.id
                    pointer = tracked

                    val zoomChange = event.calculateZoom()
                    val rotationChange = event.calculateRotation()
                    val panChange = event.calculatePan()

                    if (!pastTouchSlop) {
                        zoomAccum *= zoomChange
                        rotation += rotationChange
                        panAccum += panChange

                        val centroidSize =
                            event.calculateCentroidSize(useCurrent = false)
                        val zoomMotion = abs(1 - zoomAccum) * centroidSize
                        val rotationMotion =
                            abs(rotation * PI.toFloat() * centroidSize / 180f)
                        val panMotion = panAccum.getDistance()

                        if (zoomMotion > touchSlop ||
                            rotationMotion > touchSlop ||
                            panMotion > touchSlop
                        ) {
                            pastTouchSlop = true
                            lockedToPanZoom =
                                panZoomLock && rotationMotion < touchSlop

                            // Flush the pre-slop accumulator so we don't lose
                            // the first touchSlop pixels of pan/zoom.
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val effectiveRotation =
                                if (lockedToPanZoom) 0f else rotation
                            onGesture(
                                centroid,
                                panAccum,
                                zoomAccum,
                                effectiveRotation,
                                pointer,
                                event.changes
                            )

                            if (consume) {
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }
                        }
                    } else {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val effectiveRotation =
                            if (lockedToPanZoom) 0f else rotationChange
                        if (effectiveRotation != 0f ||
                            zoomChange != 1f ||
                            panChange != Offset.Zero
                        ) {
                            onGesture(
                                centroid,
                                panChange,
                                zoomChange,
                                effectiveRotation,
                                pointer,
                                event.changes
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

            if (gestureStarted && !canceled) {
                onGestureEnd(pointer)
            } else if (canceled) {
                onGestureCancel()
            }

            // Tap / double-tap recognition. Only count as a tap if:
            //  - never crossed touch-slop
            //  - single-pointer for the whole gesture
            //  - up-event not consumed
            if (!pastTouchSlop &&
                maxPointersDuringGesture == 1 &&
                !pointer.isConsumed
            ) {
                val now = Clock.System.now().toEpochMilliseconds()
                val dt = now - lastTapTime
                val dx = (pointer.position - lastTapPosition).getDistance()
                val tapPos = pointer.position

                if (dt <= doubleClickDelay && dx <= doubleClickThresholdPx) {
                    // Second tap in time — cancel pending click, fire double.
                    pendingClickJob?.cancel()
                    pendingClickJob = null
                    onDoubleClick(tapPos)
                    lastTapTime = 0L
                    lastTapPosition = Offset.Zero
                } else {
                    // First tap — defer single-click in case a second follows.
                    // `launch` runs on the UNRESTRICTED outerScope captured above.
                    lastTapTime = now
                    lastTapPosition = tapPos
                    pendingClickJob?.cancel()
                    pendingClickJob = outerScope.launch {
                        delay(doubleClickDelay + 10L)
                        onClick(tapPos)
                    }
                }
            }
        }
    }
}