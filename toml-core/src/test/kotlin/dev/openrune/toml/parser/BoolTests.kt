package dev.openrune.toml.parser

import dev.openrune.toml.UnitTest
import dev.openrune.toml.model.TomlValue
import kotlin.test.Test

class BoolTests : UnitTest {
    @Test
    fun `can parse booleans`() {
        assertParsesTo(TomlValue.Bool(true), "true")
        assertParsesTo(TomlValue.Bool(false), "false")
    }
}
