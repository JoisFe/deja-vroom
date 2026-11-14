package ai.dejavroom.app.data

/**
 * Reads the small YAML subset the files under config/ use: `key: value` lines, nested sections by indentation,
 * `#` comments, optional single or double quotes. Nested keys are flattened with dots (`kakao.baseUrl`).
 * Lists, anchors and multi-line values are not supported and are ignored.
 */
object FlatYaml {
    fun parse(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val parents = ArrayDeque<Pair<Int, String>>()
        for (raw in text.lines()) {
            val line = stripComment(raw).trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("-")) continue
            val indent = line.length - line.trimStart().length
            val content = line.trim()
            val colon = content.indexOf(':')
            if (colon <= 0) continue
            val key = content.substring(0, colon).trim()
            val value = content.substring(colon + 1).trim()
            while (parents.isNotEmpty() && parents.last().first >= indent) parents.removeLast()
            val path = (parents.map { it.second } + key).joinToString(".")
            if (value.isEmpty()) parents.addLast(indent to key) else result[path] = unquote(value)
        }
        return result
    }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        line.forEachIndexed { index, c ->
            when {
                quote != null && c == quote -> quote = null
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == null && c == '#' && (index == 0 || line[index - 1].isWhitespace()) -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && (value.first() == '"' || value.first() == '\'') && value.last() == value.first()) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
}
