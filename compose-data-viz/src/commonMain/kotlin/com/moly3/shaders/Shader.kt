package com.moly3.shaders

/**
 * Interface to describe shaders supported by the [shaderBackground] Modifier.
 */
interface Shader {
    /** The name for this shader. */
    val name: String

    /** Defaut time modifier for this shader */
    val speedModifier: Float
        get() = 0.5f

    /** Contains the sksl shader*/
    val sksl: String

    /** Applies the uniforms required for this shader to the effect */
    fun applyUniforms(
        runtimeEffect: RuntimeEffect,
        time: Float,
        width: Float,
        height: Float,
    ) {
        runtimeEffect.setFloatUniform("uResolution", width, height, width / height)
        runtimeEffect.setFloatUniform("uTime", time)
    }
}
