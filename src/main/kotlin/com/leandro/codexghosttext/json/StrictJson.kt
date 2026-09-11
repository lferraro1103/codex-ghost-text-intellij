package com.leandro.codexghosttext.json

/**
 * The minimal JSON model both providers need: enough to read a field out of a CLI envelope, and
 * strict enough to reject anything ambiguous.
 *
 * Duplicate object keys and trailing content are refused, so an envelope cannot smuggle a second
 * value for a field this plugin acts on.
 */
sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class LiteralValue(val value: String) : JsonValue

    companion object {
        fun parse(text: String): JsonValue? = runCatching { StrictJsonReader(text).read() }.getOrNull()

        fun parseObject(text: String): ObjectValue? = parse(text) as? ObjectValue
    }
}

fun JsonValue.ObjectValue.obj(key: String): JsonValue.ObjectValue? = values[key] as? JsonValue.ObjectValue

fun JsonValue.ObjectValue.array(key: String): JsonValue.ArrayValue? = values[key] as? JsonValue.ArrayValue

fun JsonValue.ObjectValue.string(key: String): String? = (values[key] as? JsonValue.StringValue)?.value

fun JsonValue.ObjectValue.literal(key: String): String? = (values[key] as? JsonValue.LiteralValue)?.value

/** The scalar spelling of an id field, which JSON-RPC allows to be a number or a string. */
fun JsonValue.ObjectValue.scalar(key: String): String? = string(key) ?: literal(key)

internal class StrictJsonReader(private val input: String) {
    private var index = 0

    fun read(): JsonValue {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        require(index == input.length) { "Trailing JSON content" }
        return value
    }

    private fun readValue(): JsonValue = when (peek()) {
        '{' -> readObject()
        '[' -> readArray()
        '"' -> JsonValue.StringValue(readString())
        else -> JsonValue.LiteralValue(readLiteral())
    }

    private fun readObject(): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonValue.ObjectValue(values)
        while (true) {
            skipWhitespace()
            require(peek() == '"') { "Object key required" }
            val key = readString()
            require(!values.containsKey(key)) { "Duplicate object key" }
            skipWhitespace()
            expect(':')
            skipWhitespace()
            values[key] = readValue()
            skipWhitespace()
            if (consume('}')) break
            expect(',')
        }
        return JsonValue.ObjectValue(values)
    }

    private fun readArray(): JsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonValue.ArrayValue(values)
        while (true) {
            skipWhitespace()
            values += readValue()
            skipWhitespace()
            if (consume(']')) break
            expect(',')
        }
        return JsonValue.ArrayValue(values)
    }

    private fun readString(): String {
        expect('"')
        val value = StringBuilder()
        while (index < input.length) {
            val character = input[index++]
            when (character) {
                '"' -> return value.toString()
                '\\' -> value.append(readEscape())
                else -> {
                    require(character.code >= 0x20) { "Control character" }
                    value.append(character)
                }
            }
        }
        error("Unterminated string")
    }

    private fun readEscape(): Char {
        require(index < input.length) { "Unterminated escape" }
        return when (val escaped = input[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(index + 4 <= input.length) { "Short unicode escape" }
                val hex = input.substring(index, index + 4)
                index += 4
                hex.toIntOrNull(16)?.toChar() ?: error("Invalid unicode escape")
            }
            else -> error("Invalid escape $escaped")
        }
    }

    private fun readLiteral(): String {
        val start = index
        while (index < input.length && input[index] !in " \t\r\n,}]") index++
        require(start != index) { "Value required" }
        return input.substring(start, index)
    }

    private fun peek(): Char = input.getOrNull(index) ?: error("Unexpected end of JSON")

    private fun expect(character: Char) {
        require(consume(character)) { "Expected $character" }
    }

    private fun consume(character: Char): Boolean = if (input.getOrNull(index) == character) {
        index++
        true
    } else {
        false
    }

    private fun skipWhitespace() {
        while (input.getOrNull(index)?.isWhitespace() == true) index++
    }
}
