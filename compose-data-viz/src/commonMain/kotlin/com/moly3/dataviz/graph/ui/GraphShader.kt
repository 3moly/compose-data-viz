package com.moly3.dataviz.graph.ui

import com.moly3.shaders.Shader

class GraphShader(val layerCount: Int) : Shader {

    init {
        require(layerCount >= 0) { "layerCount must be >= 0, got $layerCount" }
    }

    override val name: String get() = "graph_node_l$layerCount"

    override val sksl: String get() = buildSksl()

    private fun buildSksl(): String = buildString {
        for (i in 0 until layerCount) {
            appendLine("uniform shader uAtlas$i;")
            appendLine("uniform float uTileSize$i;")
            appendLine("uniform float uColumns$i;")
            appendLine("uniform float uCircular$i;")   // NEW: 1.0 = mask to circle, 0.0 = pass through
        }
        appendLine("uniform float uUseAtlas;")
        appendLine("uniform float uLayerCount;")
        appendLine("uniform float uQuality;")
        appendLine("uniform float uBorderWidth;")
        appendLine("uniform half4 uBorderColor;")
        appendLine("uniform float uUseBorderColor;")
        appendLine()

        appendLine("half4 main(float2 coord) {")
        appendLine("    bool isBackground = coord.x < -50.0;")
        appendLine()
        appendLine("    if (!isBackground) {")
        appendLine("        if (uUseAtlas < 0.5) return half4(0.0);")
        appendLine()
        appendLine("        float2 atlasCoord = coord;")
        appendLine("        half4 texColor = half4(0.0);")
        appendLine("        float currentTileSize = 64.0;")
        appendLine("        float currentCircular = 1.0;")  // NEW
        appendLine()

        var isFirst = true
        for (i in layerCount - 1 downTo 1) {
            val threshold = (i * STRIDE - HALF_STRIDE).toFloat()
            val countGate = i.toFloat() + 0.5f
            val prefix = if (isFirst) { isFirst = false; "if" } else { "else if" }
            appendLine("        $prefix (coord.x > $threshold && uLayerCount > $countGate) {")
            appendLine("            atlasCoord = coord - float2(${(i * STRIDE).toFloat()}, 0.0);")
            appendLine("            texColor = uAtlas$i.eval(atlasCoord);")
            appendLine("            currentTileSize = uTileSize$i;")
            appendLine("            currentCircular = uCircular$i;")  // NEW
            appendLine("        }")
        }

        val fallbackPrefix = if (layerCount > 1) "else if" else "if"
        if (layerCount > 0) {
            appendLine("        $fallbackPrefix (uLayerCount > 0.5) {")
            appendLine("            texColor = uAtlas0.eval(coord);")
            appendLine("            currentTileSize = uTileSize0;")
            appendLine("            currentCircular = uCircular0;")  // NEW
            appendLine("        }")
        }

        appendLine()
        // Only mask to circle if this layer is marked circular
        appendLine("        if (currentCircular > 0.5) {")
        appendLine("            float2 tileCenter = floor(atlasCoord / currentTileSize) * currentTileSize + currentTileSize * 0.5;")
        appendLine("            float2 localCoord = (atlasCoord - tileCenter) / (currentTileSize * 0.5);")
        appendLine("            float dist = length(localCoord);")
        appendLine("            float aaWidth = mix(0.15, 0.02, uQuality);")
        appendLine("            float alpha = 1.0 - smoothstep(1.0 - aaWidth, 1.0, dist);")
        appendLine("            if (dist > 1.0) return half4(0.0);")
        appendLine("            return texColor * half(alpha);")
        appendLine("        }")
        appendLine("        return texColor;")  // Pass-through for non-circular layers
        appendLine("    }")
        appendLine()
        // Background circle code unchanged...
        appendLine("    float2 localCoordBg = coord + 100.0;")
        appendLine("    float distBg = length(localCoordBg);")
        appendLine("    float aaWidthBg = mix(0.15, 0.02, uQuality);")
        appendLine("    float outerAlphaBg = 1.0 - smoothstep(1.0 - aaWidthBg, 1.0, distBg);")
        appendLine("    if (distBg > 1.0) return half4(0.0);")
        appendLine()
        appendLine("    half4 finalCol = half4(1.0);")
        appendLine("    if (uBorderWidth > 0.0) {")
        appendLine("        float innerRadius = 1.0 - uBorderWidth;")
        appendLine("        float innerAlpha = 1.0 - smoothstep(innerRadius - aaWidthBg, innerRadius, distBg);")
        appendLine("        half4 border = mix(half4(0.6, 0.6, 0.6, 1.0), uBorderColor, half(uUseBorderColor));")
        appendLine("        finalCol = mix(border, finalCol, half(innerAlpha));")
        appendLine("    }")
        appendLine()
        appendLine("    float finalAlpha = finalCol.a * outerAlphaBg;")
        appendLine("    return half4(finalCol.rgb * half(finalAlpha), half(finalAlpha));")
        appendLine("}")
    }

    companion object {
        const val STRIDE = 100_000
        const val HALF_STRIDE = STRIDE / 2
    }
}