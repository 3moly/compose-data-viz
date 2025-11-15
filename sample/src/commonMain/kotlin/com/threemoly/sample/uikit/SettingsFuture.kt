package com.threemoly.sample.uikit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SettingsFuture: ImageVector
    get() {
        if (_SettingsFuture != null) {
            return _SettingsFuture!!
        }
        _SettingsFuture = ImageVector.Builder(
            name = "SettingsFuture",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13.601f, 21.076f)
                lineTo(19.061f, 17.924f)
                curveTo(19.644f, 17.587f, 19.935f, 17.419f, 20.146f, 17.183f)
                curveTo(20.334f, 16.975f, 20.476f, 16.73f, 20.563f, 16.463f)
                curveTo(20.66f, 16.163f, 20.66f, 15.827f, 20.66f, 15.157f)
                verticalLineTo(8.843f)
                curveTo(20.66f, 8.173f, 20.66f, 7.837f, 20.563f, 7.536f)
                curveTo(20.476f, 7.27f, 20.334f, 7.024f, 20.146f, 6.816f)
                curveTo(19.935f, 6.582f, 19.645f, 6.414f, 19.067f, 6.08f)
                lineTo(13.6f, 2.924f)
                curveTo(13.017f, 2.587f, 12.726f, 2.419f, 12.416f, 2.353f)
                curveTo(12.142f, 2.295f, 11.858f, 2.295f, 11.584f, 2.353f)
                curveTo(11.274f, 2.419f, 10.983f, 2.587f, 10.4f, 2.924f)
                lineTo(4.938f, 6.077f)
                curveTo(4.356f, 6.413f, 4.065f, 6.581f, 3.854f, 6.816f)
                curveTo(3.666f, 7.024f, 3.524f, 7.27f, 3.438f, 7.536f)
                curveTo(3.34f, 7.838f, 3.34f, 8.174f, 3.34f, 8.847f)
                verticalLineTo(15.152f)
                curveTo(3.34f, 15.825f, 3.34f, 16.162f, 3.438f, 16.463f)
                curveTo(3.524f, 16.73f, 3.666f, 16.975f, 3.854f, 17.183f)
                curveTo(4.065f, 17.419f, 4.357f, 17.587f, 4.939f, 17.924f)
                lineTo(10.4f, 21.076f)
                curveTo(10.983f, 21.413f, 11.274f, 21.581f, 11.584f, 21.646f)
                curveTo(11.858f, 21.705f, 12.142f, 21.705f, 12.416f, 21.646f)
                curveTo(12.726f, 21.581f, 13.018f, 21.413f, 13.601f, 21.076f)
                close()
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 12f)
                curveTo(9f, 13.657f, 10.343f, 15f, 12f, 15f)
                curveTo(13.657f, 15f, 15f, 13.657f, 15f, 12f)
                curveTo(15f, 10.343f, 13.657f, 9f, 12f, 9f)
                curveTo(10.343f, 9f, 9f, 10.343f, 9f, 12f)
                close()
            }
        }.build()

        return _SettingsFuture!!
    }

@Suppress("ObjectPropertyName")
private var _SettingsFuture: ImageVector? = null