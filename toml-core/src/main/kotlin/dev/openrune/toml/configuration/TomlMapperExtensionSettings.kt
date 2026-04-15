package dev.openrune.toml.configuration

import dev.openrune.toml.TomlMapper
import java.util.Collections
import java.util.WeakHashMap

private val pendingSettingsByConfigurator =
    Collections.synchronizedMap(WeakHashMap<TomlMapperConfigurator, MutableMap<String, Any>>())
private val settingsByMapper =
    Collections.synchronizedMap(WeakHashMap<TomlMapper, MutableMap<String, Any>>())

/**
 * Stores an extension-specific setting on this mapper configurator.
 * Values are transferred to the built [TomlMapper] when [dev.openrune.toml.tomlMapper] creates it.
 */
fun TomlMapperConfigurator.putExtensionSetting(key: String, value: Any) {
    val settings = pendingSettingsByConfigurator.getOrPut(this) { mutableMapOf() }
    settings[key] = value
}

/**
 * Retrieves an extension-specific setting previously attached to this [TomlMapper].
 */
fun TomlMapper.getExtensionSetting(key: String): Any? =
    settingsByMapper[this]?.get(key)

internal fun attachExtensionSettings(configurator: TomlMapperConfigurator, mapper: TomlMapper) {
    val pending = pendingSettingsByConfigurator.remove(configurator) ?: return
    settingsByMapper[mapper] = pending.toMutableMap()
}
