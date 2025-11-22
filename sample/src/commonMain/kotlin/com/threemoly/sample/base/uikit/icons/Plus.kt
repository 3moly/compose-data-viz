package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Plus: ImageVector
    get() {
        if (_Plus != null) {
            return _Plus!!
        }
        _Plus = ImageVector.Builder(
            name = "Plus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(7f, 2f)
                lineTo(17f, 2f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 7f)
                lineTo(22f, 17f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 22f)
                lineTo(7f, 22f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 17f)
                lineTo(2f, 7f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 2f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 12f)
                horizontalLineTo(15f)
            }
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 9f)
                lineTo(12f, 15f)
            }
        }.build()

        return _Plus!!
    }

@Suppress("ObjectPropertyName")
private var _Plus: ImageVector? = null
