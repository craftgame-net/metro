package net.craftgame.metro.data

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.reader.UnicodeReader
import org.yaml.snakeyaml.representer.Representer
import java.io.File
import java.io.FileInputStream
import java.io.StringWriter

/**
 * A YAML mapping read the way Bukkit's `YamlConfiguration` reads one, so a file means the same to this plugin and to
 * older versions of it. Keys are text, a dot in a key nests the value one level deeper, and null values are left out.
 * Each getter returns what its Bukkit counterpart (`getString` for [string] and so on) would.
 */
class YamlSection private constructor() {
    private val values = LinkedHashMap<String, Any?>()

    val keys: Set<String> get() = values.keys

    /** Returns the value, a nested [YamlSection] for a mapping, or null when the key is absent. */
    operator fun get(key: String): Any? = values[key]

    operator fun contains(key: String): Boolean = key in values

    /** Returns the nested mapping, or null when the value is absent or not a mapping. */
    fun section(key: String): YamlSection? = values[key] as? YamlSection

    /** Any value as text, so `display: 123` reads as "123" and `display: yes` as "true". */
    fun string(key: String): String? = values[key]?.toString()

    /** Returns [default] unless the value is a number; a fraction is cut off. */
    fun int(
        key: String,
        default: Int,
    ): Int = (values[key] as? Number)?.toInt() ?: default

    /** Returns 0 unless the value is a number, so the text "2" reads as 0. */
    fun double(key: String): Double = (values[key] as? Number)?.toDouble() ?: 0.0

    /** Returns [default] unless the value is a boolean, so the text "false" reads as [default]. */
    fun boolean(
        key: String,
        default: Boolean,
    ): Boolean = values[key] as? Boolean ?: default

    fun isList(key: String): Boolean = values[key] is List<*>

    /** Returns the text and simple values of a list as text and skips the rest. Empty when it is not a list. */
    fun stringList(key: String): List<String> =
        (values[key] as? List<*>).orEmpty().filter { it is String || isSimple(it) }.map { it.toString() }

    /** Returns the numbers and numeric texts of a list as whole numbers and skips the rest. Empty when not a list. */
    fun intList(key: String): List<Int> =
        (values[key] as? List<*>).orEmpty().mapNotNull { item ->
            when (item) {
                is Int -> item
                is String -> item.toIntOrNull()
                is Char -> item.code
                is Number -> item.toInt()
                else -> null
            }
        }

    /** Returns the mappings of a list, with their keys as read. Empty when it is not a list. */
    fun mapList(key: String): List<Map<*, *>> = (values[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

    /** Returns the values as plain maps and lists, to write back unchanged. */
    fun toMap(): Map<String, Any?> = values.mapValues { (_, value) -> (value as? YamlSection)?.toMap() ?: value }

    override fun toString(): String = toMap().toString()

    /** Stores [value] at [path]. Each dot opens a nested section, replacing a value that is not one. */
    private fun set(
        path: String,
        value: Any?,
    ) {
        val parts = path.split(PATH_SEPARATOR)
        var target = this
        for (part in parts.dropLast(1)) {
            // An empty part names the section itself, so "a..b" is the same as "a.b".
            if (part.isEmpty()) continue
            val child = target.section(part)
            target =
                when {
                    child != null -> child
                    value == null -> return
                    else -> YamlSection().also { target.values[part] = it }
                }
        }
        val key = parts.last()
        when (value) {
            null -> target.values.remove(key)
            is Map<*, *> -> target.values[key] = of(value)
            else -> target.values[key] = value
        }
    }

    companion object {
        private const val PATH_SEPARATOR = '.'

        /** The nesting depth Bukkit allows. */
        private const val MAX_NESTING = 100

        /** Bukkit wraps long text at 80 columns; it reads back the same either way. */
        private const val LINE_WIDTH = 80

        private const val INDENT = 2

        /**
         * Reads [file] as UTF-8 (a byte order mark is skipped). An empty file gives an empty section.
         * Throws [YAMLException] when the text is not YAML or its top level is not a mapping, and
         * [java.io.IOException] when the file cannot be read.
         */
        fun read(file: File): YamlSection =
            UnicodeReader(FileInputStream(file)).use { reader ->
                when (val root = yaml().load<Any?>(reader)) {
                    null -> YamlSection()
                    is Map<*, *> -> of(root)
                    else -> throw YAMLException("Top level is not a mapping")
                }
            }

        /**
         * Returns [root] as YAML text in block style with a 2-space indent, or empty text for an empty map.
         * A map directly under a map is written as a nested section; anything else, lists included, as one value.
         */
        fun write(root: Map<String, Any?>): String {
            if (root.isEmpty()) return ""
            val options = dumperOptions()
            val text = StringWriter()
            yaml(options).serialize(mappingNode(root, Representer(options)), text)
            return text.toString()
        }

        /**
         * Represents each value on its own, so a list or map that appears under two keys is written out twice
         * instead of as an anchor and an alias.
         */
        private fun mappingNode(
            map: Map<*, *>,
            representer: Representer,
        ): MappingNode {
            val tuples =
                map.map { (key, value) ->
                    val valueNode =
                        if (value is Map<*, *>) mappingNode(value, representer) else representer.represent(value)
                    NodeTuple(representer.represent(key.toString()), valueNode)
                }
            return MappingNode(Tag.MAP, tuples, DumperOptions.FlowStyle.BLOCK)
        }

        private fun of(map: Map<*, *>): YamlSection {
            val section = YamlSection()
            // The key as text: an unquoted `yes` key is the boolean true and reads as "true".
            for ((key, value) in map) section.set(key.toString(), value)
            return section
        }

        /** The types that Bukkit's string list turns into text besides strings. */
        private fun isSimple(value: Any?): Boolean =
            value is Int ||
                value is Long ||
                value is Short ||
                value is Byte ||
                value is Double ||
                value is Float ||
                value is Boolean ||
                value is Char

        private fun dumperOptions(): DumperOptions =
            DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                indent = INDENT
                width = LINE_WIDTH
            }

        /** Not shared: a [Yaml] instance must not be used by two threads at once. */
        private fun yaml(dumper: DumperOptions = dumperOptions()): Yaml {
            val loader =
                LoaderOptions().apply {
                    maxAliasesForCollections = Int.MAX_VALUE
                    codePointLimit = Int.MAX_VALUE
                    nestingDepthLimit = MAX_NESTING
                }
            return Yaml(SafeConstructor(loader), Representer(dumper), dumper, loader)
        }
    }
}
