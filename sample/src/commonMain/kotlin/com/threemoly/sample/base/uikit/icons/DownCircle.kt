package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val DownCircle: ImageVector
    get() {
        if (_DownCircle != null) {
            return _DownCircle!!
        }
        _DownCircle = ImageVector.Builder(
            name = "DownCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(9f, 13f)
                lineTo(11.293f, 15.293f)
                curveTo(11.683f, 15.683f, 12.317f, 15.683f, 12.707f, 15.293f)
                lineTo(15f, 13f)
                moveTo(12f, 15f)
                verticalLineTo(8f)
                moveTo(2f, 12f)
                curveTo(2f, 17.523f, 6.477f, 22f, 12f, 22f)
                curveTo(17.523f, 22f, 22f, 17.523f, 22f, 12f)
                curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
                curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
                close()
            }
        }.build()

        return _DownCircle!!
    }

@Suppress("ObjectPropertyName")
private var _DownCircle: ImageVector? = null
