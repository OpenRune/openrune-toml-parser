package dev.openrune.toml.model

/**
 * Kotlin representation of a TOML value.
 * A full TOML document is always represented as a [TomlValue.Map].
 *
 * You can either traverse this representation manually, access individual properties using [TomlValue.get], or
 * decode the whole thing into a data class of your choice using [TomlValue.decode].
 *
 * [TomlValue.from] can be used to obtain a `TomlValue` from an input TOML document in the form of a [String],
 * [java.nio.file.Path], or [java.io.InputStream].
 */
sealed class TomlValue {
    sealed class Primitive : TomlValue()

    data class String(val value: kotlin.String) : Primitive() {
        fun setValue(newValue: kotlin.String): String {
            return this.copy(value = newValue)
        }
    }

    data class Integer(val value: Long) : Primitive() {
        fun setValue(newValue: Long): Integer {
            return this.copy(value = newValue)
        }
    }

    data class Double(val value: kotlin.Double) : Primitive() {
        fun setValue(newValue: kotlin.Double): Double {
            return this.copy(value = newValue)
        }
    }

    data class Bool(val value: Boolean) : Primitive() {
        fun setValue(newValue: Boolean): Bool {
            return this.copy(value = newValue)
        }
    }

    data class OffsetDateTime(val value: java.time.OffsetDateTime) : Primitive() {
        fun setValue(newValue: java.time.OffsetDateTime): OffsetDateTime {
            return this.copy(value = newValue)
        }
    }

    data class LocalDateTime(val value: java.time.LocalDateTime) : Primitive() {
        fun setValue(newValue: java.time.LocalDateTime): LocalDateTime {
            return this.copy(value = newValue)
        }
    }

    data class LocalDate(val value: java.time.LocalDate) : Primitive() {
        fun setValue(newValue: java.time.LocalDate): LocalDate {
            return this.copy(value = newValue)
        }
    }

    data class LocalTime(val value: java.time.LocalTime) : Primitive() {
        fun setValue(newValue: java.time.LocalTime): LocalTime {
            return this.copy(value = newValue)
        }
    }

    data class Map(val properties: kotlin.collections.Map<kotlin.String, TomlValue>) : TomlValue() {
        constructor(vararg entries: Pair<kotlin.String, TomlValue>) : this(entries.toMap())

        fun setValue(key: kotlin.String, newValue: TomlValue): Map {
            val updatedProperties = properties.toMutableMap().apply { this[key] = newValue }
            return this.copy(properties = updatedProperties)
        }
    }

    data class List(val elements: kotlin.collections.List<TomlValue>) : TomlValue() {
        constructor(vararg values: TomlValue) : this(values.toList())

        fun setValue(index: Int, newValue: TomlValue): List {
            val updatedElements = elements.toMutableList().apply { this[index] = newValue }
            return this.copy(elements = updatedElements)
        }
    }

    // For serialization extension methods
    companion object
}

/**
 * A TOML document is a map of zero or more keys.
 */
typealias TomlDocument = TomlValue.Map

/**
 * Recursively merge two TOML values.
 * Whenever there is a conflict (i.e. two non-[TomlValue.Map] values are merged), the [other] value is preserved.
 * Note that lists are treated as atomic (i.e. not recursively merged) due to the difficulties of establishing an
 * unambiguous diff between two lists of elements without identity.
 */
internal fun TomlValue.merge(other: TomlValue): TomlValue = when {
    this is TomlValue.Map && other is TomlValue.Map -> TomlValue.Map(properties.merge(other.properties))
    else -> other
}

private fun Map<String, TomlValue>.merge(other: Map<String, TomlValue>): Map<String, TomlValue> {
    val keys = this.keys + other.keys
    return keys.associateWith { key ->
        val thisValue = this[key]
        val otherValue = other[key]
        when {
            thisValue != null && otherValue != null -> thisValue.merge(otherValue)
            otherValue != null -> otherValue
            else -> thisValue!!
        }
    }
}
