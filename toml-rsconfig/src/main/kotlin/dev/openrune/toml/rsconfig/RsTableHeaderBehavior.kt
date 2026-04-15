package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.model.TomlValue
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun TomlMapper.applyRsTableHeaderBehavior(kClass: KClass<*>, instance: Any, rowToml: TomlValue) {
    val ann = kClass.findAnnotation<RsTableHeaders>() ?: return
    if (ann.atMostOneOf.isNotEmpty()) {
        validateAtMostOneOf(kClass, instance, ann.atMostOneOf.toList())
    }
    val hookClass = ann.rowPostDecode
    if (hookClass == RsTableNoRowHook::class) {
        return
    }
    val content = (rowToml as? TomlValue.Map)?.properties ?: emptyMap()
    val hook =
        try {
            hookClass.objectInstance as? RsTableRowPostDecode
                ?: hookClass.java.getDeclaredConstructor().newInstance() as? RsTableRowPostDecode
        } catch (e: Exception) {
            throw IllegalStateException(
                "RsTableHeaders.rowPostDecode=${hookClass.qualifiedName} must be an object or have a public no-arg constructor " +
                    "and implement RsTableRowPostDecode",
                e,
            )
        }
            ?: throw IllegalStateException(
                "RsTableHeaders.rowPostDecode=${hookClass.qualifiedName} must implement RsTableRowPostDecode",
            )
    hook.apply(this, content, instance)
}

private fun validateAtMostOneOf(kClass: KClass<*>, instance: Any, names: List<String>) {
    val setProps = ArrayList<String>()
    for (propName in names) {
        val prop =
            kClass.memberProperties.find { it.name == propName }
                ?: throw IllegalStateException(
                    "RsTableHeaders.atMostOneOf contains unknown property '$propName' on ${kClass.simpleName}",
                )
        if (!prop.isAccessible) {
            prop.isAccessible = true
        }
        val v = prop.getter.call(instance)
        if (isConsideredSet(prop.returnType.classifier, v)) {
            setProps += propName
        }
    }
    if (setProps.size > 1) {
        throw IllegalStateException(
            "${kClass.simpleName}: at most one of ${names.joinToString()} may be set; found: ${setProps.joinToString()}",
        )
    }
}

private fun isConsideredSet(classifier: KClassifier?, value: Any?): Boolean =
    when (value) {
        null -> false
        is Boolean -> value
        is Int -> value != 0
        is Short -> value.toInt() != 0
        is Byte -> value.toInt() != 0
        is Long -> value != 0L
        is String -> value.isNotBlank()
        else -> true
    }
