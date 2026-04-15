package dev.openrune.toml.transcoding

import dev.openrune.toml.model.TomlValue
import kotlin.reflect.KClass

object TomlDecoderRegistry {
    val modifiers: MutableMap<KClass<*>, MutableList<(Map<String, TomlValue>, Any) -> Unit>> = mutableMapOf()


    fun applyModifiers(map: Map<String, TomlValue>, instance: Any) {
        modifiers[instance::class]?.forEach { it(map, instance) }
    }
}