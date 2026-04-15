package dev.openrune.toml.rsconfig

import dev.openrune.toml.tomlMapper
import dev.openrune.definition.constants.ConstantProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RsConfigConstantTests {
    @Test
    fun `enableConstantProvider replaces constants before parse`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "items" to mutableMapOf(
                        "items.dragonlong" to 4158
                    )
                )
            )
        )

        val input = """
            [[config]]
            settings = 90

            [config.params]
            "items.dragonlong" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enableConstantProvider()
            }
        }
        val decoded = mapper.decodeRuneScape<Config>(input)

        assertEquals(
            Config(
                settings = 90,
                params = mapOf("4158" to 34L)
            ),
            decoded
        )
    }

    @Test
    fun `constant-like token requires enableConstantProvider`() {
        val input = """
            [[config]]
            settings = 90

            [config.params]
            "items.dragonlong" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
            }
        }

        assertFailsWith<IllegalStateException> {
            mapper.decodeRuneScape<Config>(input)
        }
    }

    @Test
    fun `enableConstantProvider leaves quoted keys for unknown tables unchanged`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "npcs" to mutableMapOf(
                        "npcs.goblin" to 100
                    )
                )
            )
        )

        val input = """
            [[config]]
            settings = 90

            [config.params]
            "items.dragonlong" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enableConstantProvider()
            }
        }

        val decoded = mapper.decodeRuneScape<Config>(input)
        assertEquals(
            Config(settings = 90, params = mapOf("items.dragonlong" to 34L)),
            decoded,
        )
    }
}
