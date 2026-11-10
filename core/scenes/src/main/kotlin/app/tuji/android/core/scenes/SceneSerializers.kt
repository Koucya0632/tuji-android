package app.tuji.android.core.scenes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// The JSON writes a phrasing's line and a 回應's clip as loose nullable fields.
// These read them through the flat shape and fold them into one optional value,
// refusing a half-set line rather than letting it reach a screen.

@Serializable
private class PhrasingJson(
    val id: PhrasingId,
    val role: Phrasing.Role,
    val speaker: Phrasing.Speaker,
    val speed: Phrasing.Speed,
    val ambience: Boolean,
    val text: String? = null,
    val voice: String? = null,
    val audio: String? = null,
    val screen: ScreenMock? = null,
)

internal object PhrasingSerializer : KSerializer<Phrasing> {
    override val descriptor: SerialDescriptor = PhrasingJson.serializer().descriptor

    override fun deserialize(decoder: Decoder): Phrasing {
        val raw = decoder.decodeSerializableValue(PhrasingJson.serializer())
        val spoken = when {
            raw.text != null && raw.voice != null && raw.audio != null -> SpokenLine(raw.text, raw.voice, raw.audio)
            raw.text == null && raw.voice == null && raw.audio == null -> null
            else -> throw SerializationException("${raw.id.raw}: text, voice and audio must be all set or all null")
        }
        return Phrasing(raw.id, raw.role, raw.speaker, raw.speed, raw.ambience, spoken, raw.screen)
    }

    override fun serialize(encoder: Encoder, value: Phrasing) {
        encoder.encodeSerializableValue(
            PhrasingJson.serializer(),
            PhrasingJson(
                value.id, value.role, value.speaker, value.speed, value.ambience,
                value.spoken?.text, value.spoken?.voice, value.spoken?.audio, value.screen,
            ),
        )
    }
}

@Serializable
private class SceneResponseJson(val ja: String, val audio: String? = null, val voice: String? = null)

internal object SceneResponseSerializer : KSerializer<SceneResponse> {
    override val descriptor: SerialDescriptor = SceneResponseJson.serializer().descriptor

    override fun deserialize(decoder: Decoder): SceneResponse {
        val raw = decoder.decodeSerializableValue(SceneResponseJson.serializer())
        val clip = when {
            raw.audio != null && raw.voice != null -> SceneResponse.Clip(raw.audio, raw.voice)
            raw.audio == null && raw.voice == null -> null
            else -> throw SerializationException("response audio and voice must be both set or both null")
        }
        return SceneResponse(raw.ja, clip)
    }

    override fun serialize(encoder: Encoder, value: SceneResponse) {
        encoder.encodeSerializableValue(
            SceneResponseJson.serializer(),
            SceneResponseJson(value.ja, value.clip?.audio, value.clip?.voice),
        )
    }
}
