package com.moly3.dataviz.graph.ui

import com.moly3.shaders.RuntimeEffect
import com.moly3.shaders.Shader

object GraphShader : Shader {
    override val name: String get() = "graph_node"

    override val sksl: String get() = """
        uniform shader uAtlas;
        uniform float uUseAtlas;
        uniform float uTileSize;
        uniform float uColumns;       
        uniform float uQuality;
        uniform float uBorderWidth;
        uniform half4 uBorderColor;
        uniform float uUseBorderColor;

        half4 main(float2 coord) {    
            float2 localCoord;
            half4 texCol = half4(0.0); 
            
            // If the coordinates are highly negative, we know it's a background quad!
            bool isBackground = coord.x < -50.0;

            if (isBackground) {
                // Restore the coordinates to exactly [-1, 1] to draw the circle
                localCoord = coord + 100.0; 
            } else if (uUseAtlas > 0.5) {
                // It's an icon quad, apply atlas mapping
                localCoord = fract(coord / uTileSize) * 2.0 - 1.0;
                texCol = uAtlas.eval(coord); 
                if (texCol.a > 0.0) {
                    texCol.rgb = texCol.rgb / texCol.a;
                }
            } else {
                localCoord = coord; 
            }

            float dist = length(localCoord);
            float aaWidth = mix(0.15, 0.02, uQuality); 
            float outerAlpha = 1.0 - smoothstep(1.0 - aaWidth, 1.0, dist);
            
            if (dist > 1.0) return half4(0.0);
            
            half4 finalCol;

            if (!isBackground) {
                // LAYER 2: ICON 
                finalCol = texCol;
            } else {
                // LAYER 1: BASE BACKGROUND
                finalCol = half4(1.0);
                if (uBorderWidth > 0.0) {
                    float innerRadius = 1.0 - uBorderWidth;
                    float innerAlpha = 1.0 - smoothstep(innerRadius - aaWidth, innerRadius, dist);
                    half4 border = mix(half4(0.6, 0.6, 0.6, 1.0), uBorderColor, half(uUseBorderColor));
                    finalCol = mix(border, finalCol, half(innerAlpha));
                }
            }
            
            float finalAlpha = finalCol.a * outerAlpha;
            return half4(finalCol.rgb * half(finalAlpha), half(finalAlpha));
        }
    """
}