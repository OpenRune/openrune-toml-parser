package dev.openrune.toml.rsconfig

import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.tomlMapper
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class RsConfigBasicTests {
    @Test
    fun `decodeRuneScape supports single root block style`() {
        val input = """
            [[config]]
            settings = 90

            [config.params]
            dffd = 34
        """.trimIndent()

        val mapper = tomlMapper { }
        val decoded = mapper.decodeRuneScape<Config>(input)

        assertEquals(
            Config(
                settings = 90,
                params = mapOf("dffd" to 34L)
            ),
            decoded
        )
    }

    @Test
    fun `decodeRuneScapeList decodes one object per root block`() {
        val input = """
            [[config]]
            settings = 90

            [config.params]
            dffd = 34

            [[config]]
            settings = 345

            [config.params]
            dffd = 34
        """.trimIndent()

        val mapper = tomlMapper { }
        val decoded = mapper.decodeRuneScapeList<Config>(input)

        assertEquals(
            listOf(
                Config(settings = 90, params = mapOf("dffd" to 34L)),
                Config(settings = 345, params = mapOf("dffd" to 34L))
            ),
            decoded
        )
    }

    @Test
    fun `decodeRuneScapeBlocks returns name and TomlValue map per root block`() {
        val input = """
            [[config]]
            settings = 90

            [config.params]
            dffd = 34

            [[config]]
            settings = 345

            [config.params]
            dffd = 34
        """.trimIndent()

        val mapper = tomlMapper { }
        val blocks = mapper.decodeRuneScapeBlocks(input)

        assertEquals(2, blocks.size)
        assertEquals("config", blocks[0].name)
        assertEquals("config", blocks[1].name)
        assertEquals(90L, (blocks[0].map.properties["settings"] as TomlValue.Integer).value)
        assertEquals(345L, (blocks[1].map.properties["settings"] as TomlValue.Integer).value)
    }

    @Test
    fun `decodeRuneScapeBlocks interleaves different top level array tables`() {
        val input = """
            [[item]]
            id = 1

            [[enum]]
            name = "a"

            [[item]]
            id = 2
        """.trimIndent()

        val mapper = tomlMapper { }
        val blocks = mapper.decodeRuneScapeBlocks(input)

        assertEquals(3, blocks.size)
        assertEquals("item", blocks[0].name)
        assertEquals("enum", blocks[1].name)
        assertEquals("item", blocks[2].name)
        assertEquals(1L, (blocks[0].map.properties["id"] as TomlValue.Integer).value)
        assertEquals("a", (blocks[1].map.properties["name"] as TomlValue.String).value)
        assertEquals(2L, (blocks[2].map.properties["id"] as TomlValue.Integer).value)
    }

    @Test
    fun `decodeRuneScape with KClass decodes TomlValue map properties`() {
        val mapper = tomlMapper { }
        val properties = mapOf(
            "settings" to TomlValue.Integer(90),
            "params" to TomlValue.Map("dffd" to TomlValue.Integer(34)),
        )
        val decoded = mapper.decodeRuneScape(Config::class, properties)
        assertEquals(
            Config(settings = 90, params = mapOf("dffd" to 34L)),
            decoded,
        )
    }

    @Test
    fun `decodeRuneScape with List KType unwraps when value is a single table map`() {
        val mapper = tomlMapper { }
        val properties = mapOf(
            "settings" to TomlValue.Integer(90),
            "params" to TomlValue.Map("dffd" to TomlValue.Integer(34)),
        )
        @Suppress("UNCHECKED_CAST")
        val decoded = mapper.decodeRuneScape(typeOf<List<Config>>(), properties) as Config
        assertEquals(
            Config(settings = 90, params = mapOf("dffd" to 34L)),
            decoded,
        )
    }

    @Test
    fun `rsconfig allowedTableHeaders filters processing`() {
        val input = """
            [[config1]]
            settings = 90

            [config1.params]
            dffd = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config1", "config2")
            }
        }
        val decoded = mapper.decodeRuneScapeList<MultiConfig>(input)

        assertEquals(
            listOf(
                MultiConfig(settings = 90, params = mapOf("dffd" to 34L))
            ),
            decoded
        )
    }
}
