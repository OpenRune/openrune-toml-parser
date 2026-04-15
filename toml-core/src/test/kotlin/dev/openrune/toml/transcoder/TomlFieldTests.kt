package dev.openrune.toml.transcoder

import dev.openrune.toml.model.TomlException
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.serialization.TomlField
import dev.openrune.toml.serialization.TomlFieldDecodeContext
import dev.openrune.toml.serialization.TomlFieldEncodeContext
import dev.openrune.toml.serialization.TomlFieldSerializer
import dev.openrune.toml.tomlMapper
import dev.openrune.toml.transcoding.TomlDecoder
import dev.openrune.toml.transcoding.TomlEncoder
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private data class Tag(val raw: String)

private object TagSerializer : TomlFieldSerializer<Tag> {
    override fun decodeField(
        decoder: TomlDecoder,
        targetType: KType,
        value: TomlValue?,
        decodeContext: TomlFieldDecodeContext,
    ): Tag {
        val v = requireNotNull(value)
        val s = when (v) {
            is TomlValue.String -> v.value
            else -> error("expected string")
        }
        return Tag(s.uppercase())
    }

    override fun encodeField(
        encoder: TomlEncoder,
        value: Tag,
        encodeContext: TomlFieldEncodeContext,
    ): TomlValue =
        TomlValue.String(value.raw.lowercase())
}

/** Not constructible; used to assert [dev.openrune.toml.model.TomlException.InvalidTomlFieldSerializer]. */
private class BadFieldSerializer private constructor() : TomlFieldSerializer<Tag> {
    override fun decodeField(
        decoder: TomlDecoder,
        targetType: KType,
        value: TomlValue?,
        decodeContext: TomlFieldDecodeContext,
    ): Tag = Tag("x")

    override fun encodeField(
        encoder: TomlEncoder,
        value: Tag,
        encodeContext: TomlFieldEncodeContext,
    ): TomlValue = TomlValue.String("x")
}

class TomlFieldTests {

    @Test
    fun `TomlField serializer decodes and encodes property`() {
        data class Row(
            @param:TomlField(serializer = TagSerializer::class)
            val tag: Tag,
        )

        val mapper = tomlMapper { }
        val decoded = mapper.decode<Row>(TomlValue.Map("tag" to TomlValue.String("hello")))
        assertEquals(Tag("HELLO"), decoded.tag)
        assertEquals(
            TomlValue.Map("tag" to TomlValue.String("hello")),
            mapper.encode(Row(Tag("HELLO"))),
        )
    }

    @Test
    fun `TomlField aliases accept alternate TOML keys`() {
        data class Row(
            @param:TomlField(aliases = ["legacy"])
            val v: Int,
        )

        val mapper = tomlMapper { }
        assertEquals(
            Row(99),
            mapper.decode(TomlValue.Map("legacy" to TomlValue.Integer(99))),
        )
        assertEquals(
            Row(1),
            mapper.decode(TomlValue.Map("v" to TomlValue.Integer(1))),
        )
    }

    @Test
    fun `TomlField aliases combine with mapper mapping`() {
        data class Row(
            @param:TomlField(aliases = ["y"])
            val x: Int,
        )

        val mapper = tomlMapper {
            mapping<Row>("m" to "x")
        }
        assertEquals(Row(7), mapper.decode(TomlValue.Map("m" to TomlValue.Integer(7))))
        assertEquals(Row(8), mapper.decode(TomlValue.Map("y" to TomlValue.Integer(8))))
    }

    @Test
    fun `invalid TomlFieldSerializer class throws`() {
        data class Row(
            @param:TomlField(serializer = BadFieldSerializer::class)
            val tag: Tag,
        )

        assertFailsWith<TomlException.InvalidTomlFieldSerializer> {
            tomlMapper { }.decode<Row>(TomlValue.Map("tag" to TomlValue.String("a")))
        }
    }
}
