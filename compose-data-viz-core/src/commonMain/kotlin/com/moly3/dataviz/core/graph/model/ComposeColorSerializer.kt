package com.moly3.dataviz.core.graph.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ComposeColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ComposeColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) {
        val argb = value.toArgb()
        val hexString = "#" + argb.toUInt().toString(16).uppercase().padStart(8, '0')
        encoder.encodeString(hexString)
    }

    override fun deserialize(decoder: Decoder): Color {
        val hexString = decoder.decodeString()
        val argb = hexString.removePrefix("#").toLong(16).toInt()
        return Color(argb)
    }
}