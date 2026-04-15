package dev.openrune.toml

interface StringTest : RandomTest {
    val escapeCodeSamples: List<Pair<String, String>>
        get() = listOf(
            "\\b" to "\b",
            "\\f" to "\u000C",
            "\\n" to "\n",
            "\\r" to "\r",
            "\\t" to "\t",
            "\\\"" to "\"",
            "\\\\" to "\\",
            "\\u00e5" to "å",
            "\\U0001f63f" to "😿"
        )

    val alphabet: String
        get() = "abcdefghijklmnopqrstuvwxyzåäöABCDEFGHIJKLMNOPQRSTUBWXYZÅÄÖ \t!#¤%&/()=.,[]{};:<>|ひらがなカタカナ漢字火事"

    val asciiControlChars: List<Char>
        get() = listOf(
            '\u0000'..'\u0008',
            '\u000B'..'\u000C',
            '\u000E'..'\u001F',
            listOf('\u007F'),
        ).flatten()
}
