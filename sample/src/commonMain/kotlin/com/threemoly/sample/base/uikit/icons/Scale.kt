package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Scale: ImageVector
    get() {
        if (_Scale != null) {
            return _Scale!!
        }
        _Scale = ImageVector.Builder(
            name = "Scale",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(19.297f, 7.405f)
                verticalLineTo(17.095f)
                moveTo(17.095f, 5.202f)
                horizontalLineTo(7.405f)
                moveTo(17.095f, 19.297f)
                horizontalLineTo(7.405f)
                moveTo(5.202f, 7.405f)
                verticalLineTo(17.095f)
            }
            path(
                fill = SolidColor(Color(0xFF2B3F6C)),
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(19.298f, 5.202f)
                moveToRelative(-2.202f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.405f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.405f, 0f)
            }
            path(
                fill = SolidColor(Color(0xFF2B3F6C)),
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(5.202f, 5.202f)
                moveToRelative(-2.202f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.405f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.405f, 0f)
            }
            path(
                fill = SolidColor(Color(0xFF2B3F6C)),
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(19.298f, 19.298f)
                moveToRelative(-2.202f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.405f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.405f, 0f)
            }
            path(
                fill = SolidColor(Color(0xFF2B3F6C)),
                stroke = SolidColor(Color(0xFF2B3F6C)),
                strokeLineWidth = 1.5f
            ) {
                moveTo(5.202f, 19.298f)
                moveToRelative(-2.202f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.405f, 0f)
                arcToRelative(2.202f, 2.202f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.405f, 0f)
            }
        }.build()

        return _Scale!!
    }

@Suppress("ObjectPropertyName")
private var _Scale: ImageVector? = null