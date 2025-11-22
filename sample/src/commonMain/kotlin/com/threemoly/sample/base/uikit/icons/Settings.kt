package com.threemoly.sample.base.uikit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Settings: ImageVector
    get() {
        if (_Settings != null) {
            return _Settings!!
        }
        _Settings = ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF2B3F6C)),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(9.355f, 1.578f)
                curveTo(10.202f, 1.364f, 11.088f, 1.25f, 12f, 1.25f)
                curveTo(12.912f, 1.25f, 13.798f, 1.364f, 14.645f, 1.578f)
                curveTo(15.336f, 1.753f, 15.693f, 2.35f, 15.808f, 2.846f)
                curveTo(15.944f, 3.434f, 16.313f, 3.963f, 16.875f, 4.288f)
                curveTo(17.392f, 4.587f, 17.977f, 4.657f, 18.516f, 4.53f)
                curveTo(19.011f, 4.413f, 19.703f, 4.468f, 20.166f, 5.009f)
                curveTo(21.027f, 6.013f, 21.706f, 7.179f, 22.152f, 8.456f)
                curveTo(22.418f, 9.218f, 22.006f, 9.904f, 21.57f, 10.262f)
                curveTo(21.068f, 10.676f, 20.75f, 11.301f, 20.75f, 12f)
                curveTo(20.75f, 12.699f, 21.068f, 13.324f, 21.57f, 13.738f)
                curveTo(22.006f, 14.096f, 22.418f, 14.782f, 22.152f, 15.544f)
                curveTo(21.706f, 16.82f, 21.027f, 17.986f, 20.167f, 18.991f)
                curveTo(19.704f, 19.531f, 19.012f, 19.587f, 18.516f, 19.47f)
                curveTo(17.977f, 19.343f, 17.392f, 19.413f, 16.875f, 19.712f)
                curveTo(16.313f, 20.036f, 15.944f, 20.566f, 15.808f, 21.154f)
                curveTo(15.693f, 21.65f, 15.336f, 22.247f, 14.645f, 22.422f)
                curveTo(13.798f, 22.636f, 12.912f, 22.75f, 12f, 22.75f)
                curveTo(11.088f, 22.75f, 10.202f, 22.636f, 9.355f, 22.422f)
                curveTo(8.664f, 22.247f, 8.307f, 21.65f, 8.192f, 21.154f)
                curveTo(8.056f, 20.566f, 7.688f, 20.036f, 7.125f, 19.712f)
                curveTo(6.608f, 19.413f, 6.023f, 19.343f, 5.484f, 19.47f)
                curveTo(4.989f, 19.587f, 4.297f, 19.531f, 3.833f, 18.991f)
                curveTo(2.973f, 17.986f, 2.294f, 16.82f, 1.848f, 15.544f)
                curveTo(1.582f, 14.782f, 1.994f, 14.096f, 2.43f, 13.738f)
                curveTo(2.932f, 13.324f, 3.25f, 12.699f, 3.25f, 12f)
                curveTo(3.25f, 11.301f, 2.932f, 10.676f, 2.43f, 10.262f)
                curveTo(1.994f, 9.904f, 1.582f, 9.218f, 1.848f, 8.456f)
                curveTo(2.294f, 7.179f, 2.973f, 6.013f, 3.834f, 5.009f)
                curveTo(4.297f, 4.468f, 4.989f, 4.413f, 5.484f, 4.53f)
                curveTo(6.023f, 4.657f, 6.608f, 4.587f, 7.125f, 4.288f)
                curveTo(7.687f, 3.963f, 8.056f, 3.434f, 8.192f, 2.846f)
                curveTo(8.307f, 2.35f, 8.664f, 1.753f, 9.355f, 1.578f)
                close()
                moveTo(12f, 15f)
                curveTo(10.343f, 15f, 9f, 13.657f, 9f, 12f)
                curveTo(9f, 10.343f, 10.343f, 9f, 12f, 9f)
                curveTo(13.657f, 9f, 15f, 10.343f, 15f, 12f)
                curveTo(15f, 13.657f, 13.657f, 15f, 12f, 15f)
                close()
            }
        }.build()

        return _Settings!!
    }

@Suppress("ObjectPropertyName")
private var _Settings: ImageVector? = null
