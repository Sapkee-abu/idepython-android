package com.example.idepython

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan

object PythonSyntaxHighlighter {
    private val KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield"
    )

    // Common builtins — colored separately from keywords so it's obvious
    // these are Python's own names, not something the user declared.
    private val BUILTINS = setOf(
        "abs", "all", "any", "bin", "bool", "bytearray", "bytes", "callable",
        "chr", "classmethod", "dict", "dir", "divmod", "enumerate", "eval",
        "exec", "filter", "float", "format", "frozenset", "getattr",
        "hasattr", "hash", "hex", "id", "input", "int", "isinstance",
        "issubclass", "iter", "len", "list", "map", "max", "min", "next",
        "object", "oct", "open", "ord", "pow", "print", "property", "range",
        "repr", "reversed", "round", "set", "setattr", "slice", "sorted",
        "staticmethod", "str", "sum", "super", "tuple", "type", "vars", "zip",
        "self", "cls"
    )

    private val KEYWORD_REGEX = Regex("\\b(${KEYWORDS.joinToString("|")})\\b")
    private val BUILTIN_REGEX = Regex("\\b(${BUILTINS.joinToString("|")})\\b")
    private val FUNCTION_NAME_REGEX = Regex("\\bdef\\s+(\\w+)")
    private val CLASS_NAME_REGEX = Regex("\\bclass\\s+(\\w+)")
    private val STRING_REGEX = Regex(
        "(\"\"\".*?\"\"\"|'''.*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')",
        RegexOption.DOT_MATCHES_ALL
    )
    private val COMMENT_REGEX = Regex("#.*")
    private val NUMBER_REGEX = Regex("\\b\\d+(\\.\\d+)?\\b")

    private const val COLOR_KEYWORD = "#C678DD"
    private const val COLOR_BUILTIN = "#56B6C2"
    private const val COLOR_FUNCTION_NAME = "#61AFEF"
    private const val COLOR_CLASS_NAME = "#E5C07B"
    private const val COLOR_STRING = "#98C379"
    private const val COLOR_COMMENT = "#5C6370"
    private const val COLOR_NUMBER = "#D19A66"

    fun highlight(editable: Editable) {
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }

        applySpans(editable, KEYWORD_REGEX, COLOR_KEYWORD)
        applySpans(editable, BUILTIN_REGEX, COLOR_BUILTIN)
        applySpans(editable, NUMBER_REGEX, COLOR_NUMBER)
        applyGroupSpans(editable, FUNCTION_NAME_REGEX, COLOR_FUNCTION_NAME)
        applyGroupSpans(editable, CLASS_NAME_REGEX, COLOR_CLASS_NAME)
        // Strings and comments are applied last so they win over keyword
        // matches that happen to appear inside them.
        applySpans(editable, STRING_REGEX, COLOR_STRING)
        applySpans(editable, COMMENT_REGEX, COLOR_COMMENT)
    }

    private fun applySpans(editable: Editable, regex: Regex, colorHex: String) {
        val color = Color.parseColor(colorHex)
        for (match in regex.findAll(editable)) {
            editable.setSpan(
                ForegroundColorSpan(color),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** Colors only the first capture group of each match (e.g. the name after "def "). */
    private fun applyGroupSpans(editable: Editable, regex: Regex, colorHex: String) {
        val color = Color.parseColor(colorHex)
        for (match in regex.findAll(editable)) {
            val group = match.groups[1] ?: continue
            editable.setSpan(
                ForegroundColorSpan(color),
                group.range.first,
                group.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
