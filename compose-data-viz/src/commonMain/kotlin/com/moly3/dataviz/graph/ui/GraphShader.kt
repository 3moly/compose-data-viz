package com.moly3.dataviz.graph.ui

import com.moly3.shaders.RuntimeEffect
import com.moly3.shaders.Shader

object GraphShader : Shader {
    override val name: String
        get() = "haha"

    override val sksl: String
        get() = """
    uniform float u_aaEdge;
    
    half4 main(float2 uv) {
        float d = length(uv);
        float mask = smoothstep(1.0, 1.0 - u_aaEdge, d);
        return half4(mask);
    }
"""

    fun setAAEdge(runtimeEffect: RuntimeEffect, value: Float) {
        runtimeEffect.setFloatUniform("u_aaEdge", value)
    }
}