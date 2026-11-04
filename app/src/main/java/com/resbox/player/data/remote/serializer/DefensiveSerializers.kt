package com.resbox.player.data.remote.serializer

import android.text.Html
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.intOrNull

/**
 * Global resilient Json instance configured for unstable third-party TVBox configs & CMS endpoints.
 */
val ResilientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Handles TVBox JSON fields that may arbitrarily be a String, Int, Boolean, Array, or Object,
 * normalizing them into a List<String>. For example:
 * - `"categories": "电影#剧集"` -> `["电影", "剧集"]`
 * - `"categories": ["电影", "剧集"]` -> `["电影", "剧集"]`
 * - `"categories": 1` -> `["1"]`
 */
object FlexibleStringListSerializer : JsonTransformingSerializer<List<String>>(
    ListSerializer(String.serializer())
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return when (element) {
            is JsonArray -> JsonArray(
                element.mapNotNull { item ->
                    when (item) {
                        is JsonPrimitive -> JsonPrimitive(item.content.trim())
                        else -> null
                    }
                }
            )
            is JsonPrimitive -> {
                val raw = element.content.trim()
                if (raw.isEmpty()) {
                    JsonArray(emptyList())
                } else {
                    val splitItems = raw.split(",", "#", "|")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { JsonPrimitive(it) }
                    JsonArray(splitItems)
                }
            }
            else -> JsonArray(emptyList())
        }
    }
}

/**
 * Handles TVBox fields (`searchable`, `quickSearch`, `filterable`, `type`, `playerType`)
 * that can appear as `1`, `"1"`, `true`, `"true"`, or `null`.
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        return try {
            val element = decoder.decodeSerializableValue(JsonElement.serializer())
            when (element) {
                is JsonPrimitive -> {
                    element.intOrNull
                        ?: when (element.content.trim().lowercase()) {
                            "true", "yes" -> 1
                            "false", "no", "" -> 0
                            else -> element.content.trim().toDoubleOrNull()?.toInt() ?: 0
                        }
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * Handles TVBox `ext` field which is notoriously polymorphic:
 * it can be a plain URL/token String, a Base64 string, a JSON Object, or a JSON Array.
 * Always normalizes into a raw String so downstream Spiders receive the exact string representation.
 */
object FlexibleExtSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleExt", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return try {
            val element = decoder.decodeSerializableValue(JsonElement.serializer())
            when (element) {
                is JsonNull -> ""
                is JsonPrimitive -> element.content
                is JsonObject, is JsonArray -> element.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/**
 * Handles HTTP header fields (`header` / `headers`) that may be a JSON Object or a String.
 */
object FlexibleStringMapSerializer : JsonTransformingSerializer<Map<String, String>>(
    MapSerializer(String.serializer(), String.serializer())
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> {
                val mapped = element.entries.associate { (k, v) ->
                    val strVal = (v as? JsonPrimitive)?.content ?: v.toString()
                    k to JsonPrimitive(strVal)
                }
                JsonObject(mapped)
            }
            is JsonPrimitive -> {
                val content = element.content.trim()
                if (content.startsWith("{") && content.endsWith("}")) {
                    try {
                        ResilientJson.parseToJsonElement(content)
                    } catch (_: Exception) {
                        JsonObject(emptyMap())
                    }
                } else {
                    JsonObject(emptyMap())
                }
            }
            else -> JsonObject(emptyMap())
        }
    }
}

/**
 * Automatically strips HTML tags and unescapes HTML entities (`&amp;`, `&nbsp;`, `<p>`)
 * commonly found in MacCMS `vod_content` and `vod_name` fields.
 */
object HtmlCleanStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("HtmlCleanString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val raw = try {
            val element = decoder.decodeSerializableValue(JsonElement.serializer())
            (element as? JsonPrimitive)?.content ?: ""
        } catch (_: Exception) {
            ""
        }
        return cleanHtmlText(raw)
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    fun cleanHtmlText(input: String): String {
        if (input.isBlank()) return ""
        return try {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace("\u00A0", " ")
                .trim()
        } catch (_: Throwable) {
            input.replace(Regex("<[^>]*>"), "").trim()
        }
    }
}
