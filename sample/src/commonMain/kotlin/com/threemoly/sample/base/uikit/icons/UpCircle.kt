package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val UpCircle: ImageVector
    get() {
        if (_UpCircle != null) {
            return _UpCircle!!
        }
        _UpCircle = ImageVector.Builder(
            name = "UpCircle",
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
                moveTo(9f, 11f)
                lineTo(11.293f, 8.707f)
                curveTo(11.683f, 8.317f, 12.317f, 8.317f, 12.707f, 8.707f)
                lineTo(15f, 11f)
                moveTo(12f, 9f)
                verticalLineTo(16f)
                moveTo(2f, 12f)
                curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
                curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
                curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
                curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
                close()
            }
        }.build()

        return _UpCircle!!
    }

@Suppress("ObjectPropertyName")
private var _UpCircle: ImageVector? = null
