package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.configuration.TomlMapperConfigurator
import dev.openrune.toml.configuration.getExtensionSetting
import dev.openrune.toml.configuration.putExtensionSetting
import dev.openrune.toml.decode
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.serialization.from
import dev.openrune.toml.util.InternalAPI
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType

@PublishedApi
internal const val RS_CONFIG_OPTIONS_KEY = "dev.openrune.toml.rsconfig.options"

data class RsConfigOptions internal constructor(
    val allowedTableHeaders: Set<String>,
    val constantProviderEnabled: Boolean,
    val tokenizedReplacementEnabled: Boolean,
    val tokenizedReplacements: Map<String, String>
)

/**
 * One root `[[table]]` block after RuneScape-style normalization: the array-table name and the parsed TOML subtree.
 */
data class RuneScapeTomlBlock(
    val name: String,
    val map: TomlValue.Map,
)

class RsConfigConfigurator internal constructor() {
    private val allowedHeaders = linkedSetOf<String>()
    private var constantProviderEnabled = false
    private var tokenizedReplacementEnabled = false
    private val tokenizedReplacements = mutableMapOf<String, String>()

    fun allowedTableHeaders(vararg headers: String) {
        headers.map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .forEach { allowedHeaders += it }
    }

    fun enableConstantProvider(enabled: Boolean = true) {
        constantProviderEnabled = enabled
    }

    /**
     * Enable tokenized replacement support and register global replacement entries.
     * Placeholders are matched as %global.token% (case-insensitive).
     */
    fun enableTokenizedReplacement(replacements: Map<String, String>) {
        tokenizedReplacementEnabled = true
        tokenizedReplacements.putAll(
            replacements.mapKeys { (key, _) ->
                TokenizedReplacement.toGlobalTokenKey(key.trim().lowercase())
            }
        )
    }

    /**
     * Enable tokenized replacement support by loading entries from a TOML file.
     * Expected file format:
     * [[tokenizedReplacement]]
     * foo = "bar"
     *
     * Loaded tokens are exposed as %global.foo%.
     */
    fun enableTokenizedReplacement(path: Path) {
        tokenizedReplacementEnabled = true
        tokenizedReplacements.putAll(
            TokenizedReplacement.parseEntries(path.readText())
                .mapKeys { (key, _) -> TokenizedReplacement.toGlobalTokenKey(key) }
        )
    }

    /**
     * Enable tokenized replacement support for per-file [[tokenizedReplacement]] blocks.
     */
    fun enableTokenizedReplacement() {
        tokenizedReplacementEnabled = true
    }

    /**
     * Enable global tokenized replacements from an optional inline map and/or optional TOML file.
     * If both [replacements] is non-empty and [path] is non-null, entries are merged (file first, then map overrides
     * on duplicate keys) and a short message is printed to stdout.
     */
    fun enableTokenizedReplacement(replacements: Map<String, String>, path: Path?) {
        enabledTokenizedReplacement(replacements, path)
    }

    fun enabledTokenizedReplacement(replacements: Map<String, String> = emptyMap(), path: Path? = null) {
        val mapActive = replacements.isNotEmpty()
        val fileActive = path != null
        if (mapActive && fileActive) {
            println(
                "rsconfig: merging global tokenized replacements from inline map (${replacements.size} entries) " +
                    "and file '$path' (inline map wins on duplicate keys)",
            )
        }
        when {
            !mapActive && !fileActive -> enableTokenizedReplacement()
            else -> {
                tokenizedReplacementEnabled = true
                if (fileActive) {
                    val filePath = path!!
                    tokenizedReplacements.putAll(
                        TokenizedReplacement.parseEntries(filePath.readText())
                            .mapKeys { (key, _) -> TokenizedReplacement.toGlobalTokenKey(key) },
                    )
                }
                if (mapActive) {
                    tokenizedReplacements.putAll(
                        replacements.mapKeys { (key, _) ->
                            TokenizedReplacement.toGlobalTokenKey(key.trim().lowercase())
                        },
                    )
                }
            }
        }
    }

    fun enabledTokenizedReplacement(replacements: Map<String, String>) =
        enabledTokenizedReplacement(replacements, null)

    fun enabledTokenizedReplacement(path: Path) =
        enabledTokenizedReplacement(emptyMap(), path)

    internal fun build(): RsConfigOptions =
        RsConfigOptions(
            allowedTableHeaders = allowedHeaders.toSet(),
            constantProviderEnabled = constantProviderEnabled,
            tokenizedReplacementEnabled = tokenizedReplacementEnabled,
            tokenizedReplacements = tokenizedReplacements.toMap()
        )
}

fun TomlMapperConfigurator.rsconfig(configuration: RsConfigConfigurator.() -> Unit) {
    val options = RsConfigConfigurator().apply(configuration).build()
    putExtensionSetting(RS_CONFIG_OPTIONS_KEY, options)
}

/**
 * Decode TOML with RuneScape-specific relaxed table-array handling.
 *
 * Supports structures such as:
 * [[settings]]
 * ...
 * [settings.user]
 * [settings.user]
 *
 * by normalizing repeated/array-parent child headers to standard array-table syntax.
 */
inline fun <reified T> TomlMapper.decodeRuneScape(string: String): T =
    decodeRuneScapeValue(normalizeRuneScapeTablesByBlock(applyConstantProviderIfNeeded(applyTokenizedReplacementsIfNeeded(string))))

inline fun <reified T> TomlMapper.decodeRuneScape(stream: InputStream): T =
    decodeRuneScape(stream.readBytes().decodeToString())

inline fun <reified T> TomlMapper.decodeRuneScape(path: Path): T =
    decodeRuneScape(path.readText())

inline fun <reified T> TomlMapper.decodeRuneScapeList(string: String): List<T> {
    val rootKeys = resolveRunescapeRootKeys<T>()
    if (rootKeys.isEmpty()) return emptyList()
    val preprocessed = applyConstantProviderIfNeeded(applyTokenizedReplacementsIfNeeded(string))

    val blocks = extractRootArrayBlocks(preprocessed, rootKeys)
    if (blocks.isEmpty()) {
        val normalized = normalizeRuneScapeTableArrays(preprocessed.removePrefix("\uFEFF").replace("\r\n", "\n"))
        val document = TomlValue.from(normalized)
        val rootValue = rootKeys.asSequence().mapNotNull { key -> document.properties[key] }.firstOrNull() ?: return emptyList()
        val decoded = decode<T>(rootValue)
        applyRsTableHeaderBehavior(T::class, decoded as Any, rootValue)
        return listOf(decoded)
    }

    return blocks.map { (rootKey, blockBody) ->
        val wrapped = buildRootWrappedBlock(rootKey, blockBody)
        decodeRuneScapeValue<T>(normalizeRuneScapeTableArrays(wrapped))
    }
}

inline fun <reified T> TomlMapper.decodeRuneScapeList(stream: InputStream): List<T> =
    decodeRuneScapeList(stream.readBytes().decodeToString())

inline fun <reified T> TomlMapper.decodeRuneScapeList(path: Path): List<T> =
    decodeRuneScapeList(path.readText())

/**
 * Parses the file and returns one [RuneScapeTomlBlock] per top-level `[[name]]` array table (`name` must not contain `.`).
 * Blocks can repeat (e.g. several `[[item]]`) and may alternate (`[[item]]`, `[[enum]]`, …). Nested `[[a.b]]` headers are
 * kept inside the current block.
 */
fun TomlMapper.decodeRuneScapeBlocks(string: String): List<RuneScapeTomlBlock> {
    val preprocessed = applyConstantProviderIfNeeded(applyTokenizedReplacementsIfNeeded(string))
    val blocks = extractAllTopLevelArrayBlocks(preprocessed)
    if (blocks.isEmpty()) return emptyList()
    return blocks.map { (rootKey, blockBody) ->
        val wrapped = buildRootWrappedBlock(rootKey, blockBody)
        val document = TomlValue.from(normalizeRuneScapeTableArrays(wrapped))
        val blockMap = document.properties[rootKey] as? TomlValue.Map
            ?: error("rsconfig: expected table [$rootKey] after normalizing block")
        RuneScapeTomlBlock(rootKey, blockMap)
    }
}

fun TomlMapper.decodeRuneScapeBlocks(stream: InputStream): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocks(stream.readBytes().decodeToString())

fun TomlMapper.decodeRuneScapeBlocks(path: Path): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocks(path.readText())

/**
 * Same as [decodeRuneScapeBlocks] but only keeps segments that start with `[[key]]` for one of [rootKeys].
 * Useful when you want a subset without post-filtering.
 */
fun TomlMapper.decodeRuneScapeBlocks(string: String, rootKeys: Collection<String>): List<RuneScapeTomlBlock> {
    val normalizedKeys = rootKeys.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
    if (normalizedKeys.isEmpty()) return emptyList()

    val preprocessed = applyConstantProviderIfNeeded(applyTokenizedReplacementsIfNeeded(string))
    val blocks = extractRootArrayBlocks(preprocessed, normalizedKeys)

    if (blocks.isEmpty()) {
        val normalized = normalizeRuneScapeTableArrays(preprocessed.removePrefix("\uFEFF").replace("\r\n", "\n"))
        val document = TomlValue.from(normalized)
        val matching = normalizedKeys.asSequence()
            .mapNotNull { key ->
                (document.properties[key] as? TomlValue.Map)?.let { RuneScapeTomlBlock(key, it) }
            }
            .firstOrNull()
            ?: return emptyList()
        return listOf(matching)
    }

    return blocks.map { (rootKey, blockBody) ->
        val wrapped = buildRootWrappedBlock(rootKey, blockBody)
        val document = TomlValue.from(normalizeRuneScapeTableArrays(wrapped))
        val blockMap = document.properties[rootKey] as? TomlValue.Map
            ?: error("rsconfig: expected table [$rootKey] after normalizing block")
        RuneScapeTomlBlock(rootKey, blockMap)
    }
}

fun TomlMapper.decodeRuneScapeBlocks(stream: InputStream, rootKeys: Collection<String>): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocks(stream.readBytes().decodeToString(), rootKeys)

fun TomlMapper.decodeRuneScapeBlocks(path: Path, rootKeys: Collection<String>): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocks(path.readText(), rootKeys)

/**
 * Like [decodeRuneScapeBlocks]`(string, rootKeys)` with [rootKeys] from [T] ([RsTableHeaders] / simpleName and
 * [RsConfigConfigurator.allowedTableHeaders]).
 */
inline fun <reified T> TomlMapper.decodeRuneScapeBlocksForType(string: String): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocks(string, resolveRunescapeRootKeys<T>())

inline fun <reified T> TomlMapper.decodeRuneScapeBlocksForType(stream: InputStream): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocksForType<T>(stream.readBytes().decodeToString())

inline fun <reified T> TomlMapper.decodeRuneScapeBlocksForType(path: Path): List<RuneScapeTomlBlock> =
    decodeRuneScapeBlocksForType<T>(path.readText())

inline fun <reified T> TomlMapper.decodeRuneScapeValue(normalizedToml: String): T {
    val document = TomlValue.from(normalizedToml)
    val rootKeys = resolveRunescapeRootKeys<T>()
    val unwrapped = rootKeys.asSequence()
        .mapNotNull { key -> document.properties[key] }
        .firstOrNull()
        ?: document
    val decoded = decode<T>(unwrapped)
    applyRsTableHeaderBehavior(T::class, decoded as Any, unwrapped)
    return decoded
}

/**
 * Decodes an already-parsed TOML value using a runtime [KType], including [RsTableHeaders] when the effective
 * classifier is a [KClass].
 *
 * If [targetKType] is `List<…>` / `MutableList<…>` but [tomlValue] is a [TomlValue.Map] (one `[[block]]` row),
 * the list type argument is used as the decode target so a `typeOf<List<MyRow>>()`-style [KType] can be passed directly.
 */
@OptIn(InternalAPI::class)
fun TomlMapper.decodeRuneScape(targetKType: KType, tomlValue: TomlValue): Any {
    val effectiveType = targetKType.unwrapListElementWhenDecodingTableMap(tomlValue)
    val decoded = decode(effectiveType, tomlValue) as Any
    val kClass = effectiveType.classifier as? KClass<*>
    if (kClass != null) {
        applyRsTableHeaderBehavior(kClass, decoded, tomlValue)
    }
    return decoded
}

fun TomlMapper.decodeRuneScape(targetKType: KType, properties: Map<String, TomlValue>): Any =
    decodeRuneScape(targetKType, TomlValue.Map(properties))

/**
 * Decodes an already-parsed TOML table into [T], including [RsTableHeaders] row hooks / validation
 * (same as [decodeRuneScapeValue] for a subtree). Use with [RuneScapeTomlBlock.map] or [block.map.properties].
 */
fun <T : Any> TomlMapper.decodeRuneScape(clazz: KClass<T>, tomlValue: TomlValue): T =
    decodeRuneScape(clazz.starProjectedType, tomlValue) as T

fun <T : Any> TomlMapper.decodeRuneScape(clazz: KClass<T>, properties: Map<String, TomlValue>): T =
    decodeRuneScape(clazz, TomlValue.Map(properties))

inline fun <reified T : Any> TomlMapper.decodeRuneScape(tomlValue: TomlValue): T =
    decodeRuneScape(T::class, tomlValue)

inline fun <reified T : Any> TomlMapper.decodeRuneScape(properties: Map<String, TomlValue>): T =
    decodeRuneScape(T::class, properties)

inline fun <reified T> runescapeRootKeys(): List<String> {
    val annotation = T::class.java.getAnnotation(RsTableHeaders::class.java)
    val annotated = annotation?.value
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
        .distinct()
    if (annotated.isNotEmpty()) return annotated

    val fallback = T::class.simpleName?.trim()?.lowercase()
    return if (fallback.isNullOrBlank()) emptyList() else listOf(fallback)
}

inline fun <reified T> TomlMapper.resolveRunescapeRootKeys(): List<String> {
    val base = runescapeRootKeys<T>()
    val options = getExtensionSetting(RS_CONFIG_OPTIONS_KEY) as? RsConfigOptions ?: return base
    if (options.allowedTableHeaders.isEmpty()) return base

    val allowed = options.allowedTableHeaders
    val accepted = base.filter { it in allowed }
    if (accepted.isEmpty()) {
        println("rsconfig: skipping '${base.joinToString(",")}' because it is not allowed. try one of [${allowed.joinToString(", ")}]")
    }
    return accepted
}

fun TomlMapper.applyConstantProviderIfNeeded(input: String): String {
    return ConstantReplacement.apply(this, input)
}

fun TomlMapper.applyTokenizedReplacementsIfNeeded(input: String): String {
    return TokenizedReplacement.apply(this, input)
}

@PublishedApi
internal fun normalizeRuneScapeTableArrays(input: String): String {
    return BlockParser.normalizeRuneScapeTableArrays(input)
}

@PublishedApi
internal fun normalizeRuneScapeTablesByBlock(input: String): String {
    return BlockParser.normalizeRuneScapeTablesByBlock(input)
}

@PublishedApi
internal fun extractRootArrayBlocks(input: String, rootKeys: List<String>): List<Pair<String, String>> {
    return BlockParser.extractRootArrayBlocks(input, rootKeys)
}

@PublishedApi
internal fun extractAllTopLevelArrayBlocks(input: String): List<Pair<String, String>> =
    BlockParser.extractAllTopLevelArrayBlocks(input)

@PublishedApi
internal fun buildRootWrappedBlock(rootKey: String, blockBody: String): String =
    BlockParser.buildRootWrappedBlock(rootKey, blockBody)

@PublishedApi
internal fun parseTokenizedReplacementEntries(input: String): Map<String, String> {
    return TokenizedReplacement.parseEntries(input)
}

private fun KType.unwrapListElementWhenDecodingTableMap(tomlValue: TomlValue): KType {
    if (tomlValue !is TomlValue.Map) return this
    val c = classifier as? KClass<*> ?: return this
    if (c == List::class || c == MutableList::class) {
        return arguments.firstOrNull()?.type ?: this
    }
    return this
}
