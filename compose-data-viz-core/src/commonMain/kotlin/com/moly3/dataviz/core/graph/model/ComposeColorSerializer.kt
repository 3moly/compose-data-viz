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
    private const val HEX_PREFIX = "#"
    private const val HEX_RADIX = 16
    private const val ARGB_STRING_LENGTH = 8

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ComposeColor", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Color,
    ) {
        val argb = value.toArgb()
        val hexString =
            HEX_PREFIX +
                argb
                    .toUInt()
                    .toString(HEX_RADIX)
                    .uppercase()
                    .padStart(ARGB_STRING_LENGTH, '0')
        encoder.encodeString(hexString)
    }

    override fun deserialize(decoder: Decoder): Color {
        val hexString = decoder.decodeString()
        val argb = hexString.removePrefix(HEX_PREFIX).toLong(HEX_RADIX).toInt()
        return Color(argb)
    }
}
