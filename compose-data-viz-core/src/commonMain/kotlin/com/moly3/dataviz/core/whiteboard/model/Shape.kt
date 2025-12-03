package com.moly3.dataviz.core.whiteboard.model

import androidx.compose.ui.geometry.Offset

interface Shape<Id> {
    val id: Id
    val position: Offset
    val size: Offset
}