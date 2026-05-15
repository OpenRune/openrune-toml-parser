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
                params = mapOf("4158" to 34L),
            ),
            decoded,
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

    @Test
    fun `inject debugName after id in array table block then replace constants`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "varbit" to mutableMapOf(
                        "varbit.merlin_teleports_selected_category" to 9001
                    ),
                    "varp" to mutableMapOf(
                        "varp.merlin_teleports" to 500
                    )
                )
            )
        )

        val input = """
            [[varbit]]
            id = "varbit.merlin_teleports_selected_category"
            varp = "varp.merlin_teleports"
            startBit = 0
            endBit = 2
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("varbit")
                enableConstantProvider()
            }
        }
        val decoded = mapper.decodeRuneScapeList<VarbitRow>(input)

        assertEquals(
            listOf(
                VarbitRow(
                    id = 9001,
                    debugName = "merlin_teleports_selected_category",
                    varp = 500,
                    startBit = 0,
                    endBit = 2,
                )
            ),
            decoded,
        )
    }

    @Test
    fun `does not inject debugName when already set in same array table block`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "varbit" to mutableMapOf(
                        "varbit.merlin_teleports_selected_category" to 9001
                    )
                )
            )
        )

        val input = """
            [[varbit]]
            id = "varbit.merlin_teleports_selected_category"
            debugName = "custom"
            startBit = 0
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("varbit")
                enableConstantProvider()
            }
        }
        val decoded = mapper.decodeRuneScapeList<VarbitRow>(input)

        assertEquals(
            listOf(
                VarbitRow(
                    id = 9001,
                    debugName = "custom",
                    startBit = 0,
                    varp = 0,
                    endBit = 0,
                )
            ),
            decoded,
        )
    }

    @Test
    fun `debugName suffix is token text after first dot`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "npcs" to mutableMapOf(
                        "npcs.variant.goblin" to 42
                    )
                )
            )
        )

        val input = """
            [[npcrow]]
            id = "npcs.variant.goblin"
            hp = 2
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("npcrow")
                enableConstantProvider()
            }
        }
        val decoded = mapper.decodeRuneScapeList<NpcRow>(input)

        assertEquals(
            listOf(
                NpcRow(
                    id = 42,
                    debugName = "variant.goblin",
                    hp = 2,
                )
            ),
            decoded,
        )
    }
}
