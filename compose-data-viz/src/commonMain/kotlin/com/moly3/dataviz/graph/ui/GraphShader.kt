package com.moly3.dataviz.graph.ui

import com.moly3.shaders.Shader

object GraphShader : Shader {
    override val name: String get() = "graph_node"

    override val sksl: String get() = """
        uniform shader uAtlas0;
        uniform shader uAtlas1;
        uniform shader uAtlas2;
        uniform float uUseAtlas;       // 0 = none, 1 = at least one layer present
        uniform float uLayerCount;     // 1, 2, or 3
        uniform float uTileSize0;
        uniform float uTileSize1;
        uniform float uTileSize2;
        uniform float uColumns0;
        uniform float uColumns1;
        uniform float uColumns2;
        uniform float uQuality;
        uniform float uBorderWidth;
        uniform half4 uBorderColor;
        uniform float uUseBorderColor;

        half4 main(float2 coord) {
            // Sentinel ranges encode which layer to sample on coord.x:
            //   coord.x < -50                                  -> background circle
            //   coord.x in [0, 100000)                         -> icon, layer 0 (uAtlas0)
            //   coord.x in [100000, 200000)                    -> icon, layer 1 (uAtlas1)
            //   coord.x in [200000, +inf)                      -> icon, layer 2 (uAtlas2)
            //
            // The +N*100000 shift on U is added on the CPU side per node
            // based on which atlas layer owns its icon.

            bool isBackground = coord.x < -50.0;

            if (!isBackground) {
                if (uUseAtlas < 0.5) return half4(0.0);

                // Layer 2: coord shifted by +200000 on x
                if (coord.x > 150000.0 && uLayerCount > 2.5) {
                    float2 atlasCoord = coord - float2(200000.0, 0.0);
                    return uAtlas2.eval(atlasCoord);
                }
                // Layer 1: coord shifted by +100000 on x
                if (coord.x > 50000.0 && uLayerCount > 1.5) {
                    float2 atlasCoord = coord - float2(100000.0, 0.0);
                    return uAtlas1.eval(atlasCoord);
                }
                // Layer 0 (default)
                return uAtlas0.eval(coord);
            }

            // BACKGROUND CIRCLE (unchanged)
            float2 localCoord = coord + 100.0;
            float dist = length(localCoord);
            float aaWidth = mix(0.15, 0.02, uQuality);
            float outerAlpha = 1.0 - smoothstep(1.0 - aaWidth, 1.0, dist);

            if (dist > 1.0) return half4(0.0);

            half4 finalCol = half4(1.0);
            if (uBorderWidth > 0.0) {
                float innerRadius = 1.0 - uBorderWidth;
                float innerAlpha = 1.0 - smoothstep(innerRadius - aaWidth, innerRadius, dist);
                half4 border = mix(half4(0.6, 0.6, 0.6, 1.0), uBorderColor, half(uUseBorderColor));
                finalCol = mix(border, finalCol, half(innerAlpha));
            }

            float finalAlpha = finalCol.a * outerAlpha;
            return half4(finalCol.rgb * half(finalAlpha), half(finalAlpha));
        }
    """
}