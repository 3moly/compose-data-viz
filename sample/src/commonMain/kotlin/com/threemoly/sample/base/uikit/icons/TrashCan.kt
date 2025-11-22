package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TrashCan: ImageVector
    get() {
        if (_TrashCan != null) {
            return _TrashCan!!
        }
        _TrashCan = ImageVector.Builder(
            name = "TrashCan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(5.051f, 8.734f)
                curveTo(4.206f, 7.608f, 5.01f, 6f, 6.418f, 6f)
                horizontalLineTo(17.582f)
                curveTo(18.99f, 6f, 19.794f, 7.608f, 18.949f, 8.734f)
                curveTo(18.333f, 9.556f, 18f, 10.555f, 18f, 11.582f)
                verticalLineTo(18f)
                curveTo(18f, 20.209f, 16.209f, 22f, 14f, 22f)
                horizontalLineTo(10f)
                curveTo(7.791f, 22f, 6f, 20.209f, 6f, 18f)
                verticalLineTo(11.582f)
                curveTo(6f, 10.555f, 5.667f, 9.556f, 5.051f, 8.734f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14f, 17f)
                lineTo(14f, 11f)
            }
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 17f)
                lineTo(10f, 11f)
            }
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(16f, 6f)
                lineTo(15.456f, 4.368f)
                curveTo(15.184f, 3.551f, 14.419f, 3f, 13.559f, 3f)
                horizontalLineTo(10.441f)
                curveTo(9.581f, 3f, 8.816f, 3.551f, 8.544f, 4.368f)
                lineTo(8f, 6f)
            }
        }.build()

        return _TrashCan!!
    }

@Suppress("ObjectPropertyName")
private var _TrashCan: ImageVector? = null
