package dev.openrune.toml.rsconfig

import dev.openrune.toml.tomlMapper
import dev.openrune.definition.constants.ConstantProvider
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RsConfigTokenTests {
    @Test
    fun `tokenized replacements from global map are applied`() {
        val input = """
            [[config]]
            settings = 90

            [config.params]
            "%global.dragon%" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enableTokenizedReplacement(mapOf("dragon" to "items.dragonlong"))
                enableConstantProvider()
            }
        }

        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "items" to mutableMapOf("items.dragonlong" to 4158)
                )
            )
        )

        val decoded = mapper.decodeRuneScape<Config>(input)
        assertEquals(
            Config(settings = 90, params = mapOf("4158" to 34L)),
            decoded
        )
    }

    @Test
    fun `tokenized replacements can be loaded from file`() {
        val tempFile = createTempFile(suffix = ".toml")
        try {
            tempFile.writeText(
                """
                [[tokenizedReplacement]]
                dragon = "items.dragonlong"
                """.trimIndent()
            )

            ConstantProvider.load(
                TestMappingProvider(
                    mutableMapOf(
                        "items" to mutableMapOf("items.dragonlong" to 4158)
                    )
                )
            )

            val input = """
                [[config]]
                settings = 90

                [config.params]
                "%global.dragon%" = 34
            """.trimIndent()

            val mapper = tomlMapper {
                rsconfig {
                    allowedTableHeaders("config")
                    enableTokenizedReplacement(tempFile)
                    enableConstantProvider()
                }
            }

            val decoded = mapper.decodeRuneScape<Config>(input)
            assertEquals(
                Config(settings = 90, params = mapOf("4158" to 34L)),
                decoded
            )
        } finally {
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun `tokenized replacements from current document are applied`() {
        ConstantProvider.load(
            TestMappingProvider(
                mutableMapOf(
                    "items" to mutableMapOf("items.dragonlong" to 4158)
                )
            )
        )

        val input = """
            [[tokenizedReplacement]]
            dragon = "items.dragonlong"

            [[config]]
            settings = 90

            [config.params]
            "%dragon%" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement()
                enableConstantProvider()
            }
        }

        val decoded = mapper.decodeRuneScape<Config>(input)
        assertEquals(
            Config(settings = 90, params = mapOf("4158" to 34L)),
            decoded
        )
    }

    @Test
    fun `tokenized replacements fail with clear error when unresolved`() {
        val input = """
            [[config]]
            settings = 90

            [config.params]
            "%missing%" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement()
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            mapper.decodeRuneScape<Config>(input)
        }
        assertTrue(error.message?.contains("unresolved tokenized replacement") == true)
        assertTrue(error.message?.contains("missing") == true)
    }

    @Test
    fun `tokenized placeholders must be quoted strings`() {
        val input = """
            [[tokenizedReplacement]]
            value = "123"

            [[config]]
            settings = %value%
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement()
            }
        }

        val error = assertFailsWith<IllegalStateException> {
            mapper.decodeRuneScape<Config>(input)
        }
        assertTrue(error.message?.contains("must be quoted string values") == true)
        assertTrue(error.message?.contains("value") == true)
    }

    @Test
    fun `quoted numeric placeholder is converted to int literal`() {
        val input = """
            [[tokenizedReplacement]]
            first_settings = 90
            param_key_one = "dffd"

            [[config]]
            settings = "%first_settings%"

            [config.params]
            "%param_key_one%" = 34
        """.trimIndent()

        val mapper = tomlMapper {
            rsconfig {
                allowedTableHeaders("config")
                enabledTokenizedReplacement()
            }
        }

        val decoded = mapper.decodeRuneScape<Config>(input)
        assertEquals(Config(settings = 90, params = mapOf("dffd" to 34L)), decoded)
    }
}
