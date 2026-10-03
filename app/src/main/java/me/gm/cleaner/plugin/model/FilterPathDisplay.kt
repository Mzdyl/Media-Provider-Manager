package me.gm.cleaner.plugin.model

/** Reveal ambiguous characters without changing valid filesystem names. */
object FilterPathDisplay {
    fun revealed(path: String): String = buildString {
        path.forEachIndexed { index, char ->
            val boundarySpace = char == ' ' &&
                (index == 0 || index == path.lastIndex || path[index - 1] == '/' || path[index + 1] == '/')
            if (boundarySpace || (char != ' ' && char.isWhitespace()) ||
                Character.getType(char) == Character.FORMAT.toInt() || char.isISOControl()
            ) {
                append("[U+%04X]".format(char.code))
            } else {
                append(char)
            }
        }
    }
}
