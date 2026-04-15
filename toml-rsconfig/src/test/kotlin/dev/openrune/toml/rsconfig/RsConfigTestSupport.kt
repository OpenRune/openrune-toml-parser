package dev.openrune.toml.rsconfig

import dev.openrune.definition.constants.MappingProvider
import java.io.File

@RsTableHeaders("config")
internal data class Config(
    val settings: Int = 0,
    val params: Map<String, Long>? = null
)

@RsTableHeaders("config1", "config2")
internal data class MultiConfig(
    val settings: Int = 0,
    val params: Map<String, Long>? = null
)

@RsTableHeaders("config")
internal data class ConfigAny(
    val settings: Int = 0,
    val params: Map<String, Any>? = null
)

internal class TestMappingProvider(
    override val mappings: MutableMap<String, MutableMap<String, Int>>
) : MappingProvider {
    override fun load(vararg mappings: File) = Unit
    override fun getSupportedExtensions(): List<String> = emptyList()
}
