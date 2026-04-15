package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.model.TomlValue
import kotlin.reflect.KClass

/**
 * Base class for [RsTableRowPostDecode] hooks that operate on a single decoded type [T].
 *
 * [apply] checks [instance] against [kClass], casts to [T], then calls [applyTyped]. Your override uses a typed
 * `def: T` instead of [Any].
 *
 * Kotlin does **not** support `MyHook<ParamType>::class` for annotations (type arguments are erased). Pass the class
 * once as `MyType::class` in the superclass constructor, e.g.:
 * ```
 * object ParamHook : TypedRsTableRowPostDecode<ParamType>(ParamType::class) {
 *     override fun applyTyped(mapper, content, def: ParamType) { … }
 * }
 * ```
 */
abstract class TypedRsTableRowPostDecode<T : Any> protected constructor(
    private val kClass: KClass<T>,
) : RsTableRowPostDecode {

    final override fun apply(mapper: TomlMapper, content: Map<String, TomlValue>, instance: Any) {
        if (!kClass.isInstance(instance)) {
            throw IllegalStateException(
                "RsTable row hook ${this::class.simpleName} expected ${kClass.simpleName}, got ${instance::class.qualifiedName}",
            )
        }
        @Suppress("UNCHECKED_CAST")
        applyTyped(mapper, content, instance as T)
    }

    protected abstract fun applyTyped(mapper: TomlMapper, content: Map<String, TomlValue>, def: T)
}
