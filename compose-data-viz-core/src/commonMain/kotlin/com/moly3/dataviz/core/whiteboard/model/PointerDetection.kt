package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.input.pointer.PointerIcon

data class PointerDetection(
    val pointerIcon: PointerIcon,
    val detectionType: DetectionType? = null
)