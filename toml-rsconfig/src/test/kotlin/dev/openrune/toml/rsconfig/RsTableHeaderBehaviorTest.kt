package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.rsconfig.TypedRsTableRowPostDecode
import dev.openrune.toml.tomlMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RsTableHeaders("row", atMostOneOf = ["a", "b"])
private data class MutualRow(var id: Int = 0, var a: Int = 0, var b: String? = null)

internal object CountingHook : TypedRsTableRowPostDecode<HookedRow>(HookedRow::class) {
    var hits = 0
    var lastContent: Map<String, TomlValue>? = null

    fun reset() {
        hits = 0
        lastContent = null
    }

    override fun applyTyped(mapper: TomlMapper, content: Map<String, TomlValue>, def: HookedRow) {
        hits++
        lastContent = content
    }
}

@RsTableHeaders("hooked", rowPostDecode = CountingHook::class)
internal data class HookedRow(val x: Int = 0)

class RsTableHeaderBehaviorTest {

    @Test
    fun `atMostOneOf accepts zero or one set`() {
        val mapper = tomlMapper { }
        val one =
            mapper.decodeRuneScapeList<MutualRow>(
                """
                [[row]]
                id = 0
                a = 1
                """.trimIndent(),
            )
        assertEquals(1, one.size)
        assertEquals(1, one.single().a)

        val none =
            mapper.decodeRuneScapeList<MutualRow>(
                """
                [[row]]
                id = 0
                """.trimIndent(),
            )
        assertEquals(1, none.size)
    }

    @Test
    fun `atMostOneOf rejects more than one set`() {
        val mapper = tomlMapper { }
        assertFailsWith<IllegalStateException> {
            mapper.decodeRuneScapeList<MutualRow>(
                """
                [[row]]
                id = 0
                a = 1
                b = "x"
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `rowPostDecode receives content map`() {
        CountingHook.reset()
        val mapper = tomlMapper { }
        val rows =
            mapper.decodeRuneScapeList<HookedRow>(
                """
                [[hooked]]
                x = 3
                """.trimIndent(),
            )
        assertEquals(1, rows.size)
        assertEquals(1, CountingHook.hits)
        assertEquals(TomlValue.Integer(3L), CountingHook.lastContent?.get("x"))
    }
}
