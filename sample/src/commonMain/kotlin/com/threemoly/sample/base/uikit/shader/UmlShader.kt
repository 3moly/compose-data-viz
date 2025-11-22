package com.threemoly.sample.base.uikit.shader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.mikepenz.hypnoticcanvas.RuntimeEffect
import com.mikepenz.hypnoticcanvas.shaders.Shader

object UmlShader : Shader {
    override val name: String
        get() = "uml shader"

    override val authorName: String
        get() = "3moly"

    override val authorUrl: String
        get() = "https://github.com/3moly/"

    override val credit: String
        get() = "https://github.com/3moly"

    override val license: String
        get() = "MIT License"

    override val licenseUrl: String
        get() = "https://opensource.org/license/mit"

    override val speedModifier: Float
        get() = 0.1f

    var userCoordinates: Offset = Offset(0f, 0f)
    var zoom: Float = 1f
    var dotSpacing: Float = 50f
    private var _color: Color = Color.Unspecified

    fun setColor(color: Color) {
        _color = color
    }

    override fun applyUniforms(
        runtimeEffect: RuntimeEffect,
        time: Float,
        width: Float,
        height: Float
    ) {
        super.applyUniforms(runtimeEffect, time, width, height)

        runtimeEffect.setFloatUniform("dotSpacing", dotSpacing)
        runtimeEffect.setFloatUniform("dotColor", floatArrayOf(0.4f, 0.4f, 0.4f, 1f))
        runtimeEffect.setFloatUniform("textColor", floatArrayOf(0.5f, 0.5f, 0.5f, 1f))

        runtimeEffect.setFloatUniform("backgroundColor",
            floatArrayOf(_color.red, _color.green, _color.blue, _color.alpha)
        )
        runtimeEffect.setFloatUniform("userOffset", userCoordinates.x, userCoordinates.y)
        runtimeEffect.setFloatUniform("zoom", zoom)
    }

    override val sksl = """
        uniform float uTime;
        uniform vec3 uResolution;
        uniform float dotSpacing;     // Spacing between dots in pixels
        uniform float4 dotColor;      // Color of the dots
        uniform float4 textColor;     // Color of the coordinate text
        uniform vec2 userOffset;      // Location of user in map, default is (0,0)
        uniform float zoom;  
        uniform float dotRadius;      // Radius of the dots in pixels
        uniform float4 backgroundColor;
        
        int modulo(int x, int y) {
            return x - y * (x / y);
        }
        
        float roundValue(float x) {
            return floor(x + 0.5);
        }
        
        // Helper function to calculate distance between points
        float distance2(vec2 p1, vec2 p2) {
            return sqrt(pow(p1.x - p2.x, 2.0) + pow(p1.y - p2.y, 2.0));
        }
        
        vec4 main(vec2 fragCoord) {
            vec2 screenCenter = uResolution.xy / 2.0;
            float spacing = dotSpacing > 0.0 ? dotSpacing : 10.0;
            float zoomFactor = zoom > 0.0 ? zoom : 1.0;
            vec2 centeredCoord = (fragCoord - screenCenter) / zoomFactor + userOffset;
            vec2 userPos = screenCenter - (userOffset * zoomFactor);
            if (distance(fragCoord, userPos) < 6.0) {
                return vec4(1.0, 0.5, 0.0, 1.0); // Red dot for user position
            }
            float radius = dotRadius > 0.0 ? dotRadius : 1.5;
            float gridX = spacing * roundValue(centeredCoord.x / spacing);
            float gridY = spacing * roundValue(centeredCoord.y / spacing);
            vec2 gridPoint = vec2(gridX, gridY);
            float distToGrid = distance(centeredCoord, gridPoint);
            if (distToGrid < radius) {
                // Use dotColor uniform if provided, otherwise blue
                return dotColor.a > 0.0 ? dotColor : vec4(0.0, 0.0, 1.0, 1.0);
            }
            return backgroundColor; // Use uniform background color
        }
    """
}