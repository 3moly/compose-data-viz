package com.moly3.dataviz.graph.ui

import com.moly3.shaders.RuntimeEffect
import com.moly3.shaders.Shader

object GraphShader : Shader {
    override val name: String get() = "graph_node"

    override val sksl: String get() = """
        uniform float uQuality;
        uniform float uBorderWidth;
        uniform half4 uBorderColor;
        uniform float uUseBorderColor;

        half4 main(float2 coord) {
            float dist = length(coord);
            
            // For perfectly smooth edges, aaWidth should ideally be (1.0 / radiusInPixels).
            // If you are locked into using uQuality, we map it to a much softer range 
            // to prevent the hard jagged edges on smaller circles.
            float aaWidth = mix(0.15, 0.02, uQuality); 
            float outerAlpha = 1.0 - smoothstep(1.0 - aaWidth, 1.0, dist);
            
            // No border: solid white disc, vertex color tints it
            if (uBorderWidth <= 0.0) {
                // FIX: Premultiplied alpha. Multiply RGB by alpha.
                return half4(half3(outerAlpha), half(outerAlpha));
            }
            
            float innerRadius = 1.0 - uBorderWidth;
            float innerAlpha = 1.0 - smoothstep(
                innerRadius - aaWidth,
                innerRadius,
                dist
            );
            
            half4 innerCol = half4(1.0, 1.0, 1.0, 1.0);
            half4 derivedBorder = half4(0.6, 0.6, 0.6, 1.0);
            
            half4 borderCol = mix(derivedBorder, uBorderColor, half(uUseBorderColor));
            half4 result = mix(borderCol, innerCol, half(innerAlpha));
            
            // FIX: Premultiplied alpha for the final composite
            float finalAlpha = result.a * outerAlpha;
            return half4(result.rgb * half(finalAlpha), half(finalAlpha));
        }
    """
}