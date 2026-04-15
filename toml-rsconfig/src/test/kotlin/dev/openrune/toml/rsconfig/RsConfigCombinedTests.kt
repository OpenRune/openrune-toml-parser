package dev.openrune.toml.rsconfig

import dev.openrune.toml.tomlMapper
import dev.openrune.definition.constants.ConstantProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RsConfigCombinedTests {
    @Test
    fun `global token namespace is resolved independently from local tokens`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "obj" to mutableMapOf("obj.shark" to 385)
                )
            )
        )

        val input = """
            [[tokenizedReplacement]]
            first_settings = 90
            second_settings = 345
            param_key_one = "dffd"
            param_key_two = "obj.shark"

            [[config]]
            settings = "%first_settings%"
            [config.params]
            "%param_key_one%" = 34

            [[config]]
            settings = "%second_settings%"
            [config.params]
            "%param_key_two%" = "%global.param_key_one%"
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement(mapOf("param_key_one" to "hey3"))
                enableConstantProvider()
            }
        }

        val decoded = mapper.decodeRuneScapeList<ConfigAny>(input)
        assertEquals(
            listOf(
                ConfigAny(settings = 90, params = mapOf("dffd" to 34L)),
                ConfigAny(settings = 345, params = mapOf("385" to "hey3"))
            ),
            decoded
        )
    }

    @Test
    fun `local tokenizedReplacement cannot define global namespace`() {
        val input = """
            [[tokenizedReplacement]]
            global.param_key_one = "dffd"

            [[config]]
            settings = 90
            [config.params]
            "%global.param_key_one%" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement(mapOf("param_key_one" to "hey3"))
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            mapper.decodeRuneScape<ConfigAny>(input)
        }
        assertTrue(error.message?.contains("cannot define reserved global token key") == true)
    }

    @Test
    fun `unresolved global token error suggests available global tokens`() {
        val input = """
            [[config]]
            settings = 90
            [config.params]
            "obj.shark" = "%global.missing%"
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement(mapOf("param_key_one" to "hey3"))
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            mapper.decodeRuneScape<ConfigAny>(input)
        }
        assertTrue(error.message?.contains("Add missing global token(s) via rsconfig") == true)
        assertTrue(error.message?.contains("currentAvailable=[global.param_key_one]") == true)
        assertTrue(error.message?.contains("global.missing") == true)
    }
}
