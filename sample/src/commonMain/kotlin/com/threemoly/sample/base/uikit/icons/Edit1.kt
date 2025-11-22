package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Edit1: ImageVector
    get() {
        if (_Edit1 != null) {
            return _Edit1!!
        }
        _Edit1 = ImageVector.Builder(
            name = "Edit1",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF2B3F6C)),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(13.514f, 3.806f)
                curveTo(14.587f, 2.731f, 16.327f, 2.731f, 17.401f, 3.805f)
                lineTo(19.893f, 6.296f)
                curveTo(20.958f, 7.361f, 20.969f, 9.085f, 19.918f, 10.163f)
                lineTo(10.685f, 19.637f)
                curveTo(9.979f, 20.36f, 9.012f, 20.769f, 8.001f, 20.769f)
                lineTo(5.249f, 20.768f)
                curveTo(3.97f, 20.768f, 2.948f, 19.702f, 3.002f, 18.423f)
                lineTo(3.12f, 15.614f)
                curveTo(3.16f, 14.675f, 3.55f, 13.785f, 4.214f, 13.12f)
                lineTo(13.514f, 3.806f)
                close()
                moveTo(16.341f, 4.866f)
                curveTo(15.853f, 4.378f, 15.062f, 4.378f, 14.574f, 4.867f)
                lineTo(12.911f, 6.532f)
                lineTo(17.191f, 10.812f)
                lineTo(18.845f, 9.116f)
                curveTo(19.322f, 8.625f, 19.317f, 7.842f, 18.833f, 7.358f)
                lineTo(16.341f, 4.866f)
                close()
                moveTo(5.274f, 14.181f)
                lineTo(11.851f, 7.594f)
                lineTo(16.144f, 11.886f)
                lineTo(9.611f, 18.589f)
                curveTo(9.188f, 19.023f, 8.608f, 19.268f, 8.001f, 19.268f)
                lineTo(5.249f, 19.268f)
                curveTo(4.823f, 19.268f, 4.482f, 18.912f, 4.5f, 18.486f)
                lineTo(4.618f, 15.677f)
                curveTo(4.642f, 15.114f, 4.876f, 14.58f, 5.274f, 14.181f)
                close()
                moveTo(20.515f, 20.695f)
                curveTo(20.929f, 20.695f, 21.264f, 20.359f, 21.264f, 19.945f)
                curveTo(21.264f, 19.531f, 20.929f, 19.195f, 20.515f, 19.195f)
                horizontalLineTo(14.393f)
                curveTo(13.979f, 19.195f, 13.643f, 19.531f, 13.643f, 19.945f)
                curveTo(13.643f, 20.359f, 13.979f, 20.695f, 14.393f, 20.695f)
                horizontalLineTo(20.515f)
                close()
            }
        }.build()

        return _Edit1!!
    }

@Suppress("ObjectPropertyName")
private var _Edit1: ImageVector? = null
