package com.moly3.dataviz.block.model

import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import androidx.compose.ui.graphics.Color as ComposeColor

@Serializable
data class ArcConnection(
    val id: Long,
    val fromBox: Long,
    val toBox: Long,
    var fromSide: BoxSide = BoxSide.TOP,
    var toSide: BoxSide = BoxSide.TOP,
    val arcHeight: Float = 80f, // Height of the arc
    @Serializable(with = ComposeColorSerializer::class)
    val color: ComposeColor?
)

object ComposeColorSerializer : KSerializer<ComposeColor> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ComposeColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ComposeColor) {
        val argb = value.toArgb()
        val hexString = "#" + argb.toUInt().toString(16).uppercase().padStart(8, '0')
        encoder.encodeString(hexString)
    }

    override fun deserialize(decoder: Decoder): ComposeColor {
        val hexString = decoder.decodeString()
        val argb = hexString.removePrefix("#").toLong(16).toInt()
        return ComposeColor(argb)
    }
}