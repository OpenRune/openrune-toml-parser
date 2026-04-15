package dev.openrune.toml

import dev.openrune.toml.configuration.TomlSerializerConfigurator
import dev.openrune.toml.model.TomlDocument
import dev.openrune.toml.serialization.TomlSerializerConfig
import dev.openrune.toml.serialization.TomlSerializerState
import dev.openrune.toml.serialization.writePath
import dev.openrune.toml.util.JacocoIgnore
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class TomlSerializer internal constructor(private val config: TomlSerializerConfig) {
    /**
     * Serializes the given [TomlDocument] into a valid TOML document using the receiver [TomlSerializer]
     * and writes it to the given [Appendable].
     */
    fun write(tomlDocument: TomlDocument, output: Appendable) {
        TomlSerializerState(config, output).writePath(tomlDocument, emptyList())
    }

    /**
     * Serializes the given [TomlDocument] into a valid TOML document using the receiver [TomlSerializer]
     * and writes it to the given [Appendable].
     */
    fun write(tomlDocument: TomlDocument, outputStream: OutputStream) {
        write(tomlDocument, PrintStream(outputStream) as Appendable)
    }

    /**
     * Serializes the given [TomlDocument] into a valid TOML document using the receiver [TomlSerializer]
     * and writes it to the given [Appendable].
     */
    @JacocoIgnore("JaCoCo thinks use isn't being called, even though it also thinks use's argument IS called")
    fun write(tomlDocument: TomlDocument, path: Path) {
        path.outputStream().use { write(tomlDocument, it) }
    }
}

fun tomlSerializer(configuration: TomlSerializerConfigurator.() -> Unit): TomlSerializer {
    val configurator = TomlSerializerConfigurator()
    configurator.configuration()
    return TomlSerializer(configurator.buildConfig())
}
