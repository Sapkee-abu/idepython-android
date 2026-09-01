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

    private val KEYWORD_REGEX = Regex("\\b(${KEYWORDS.joinToString("|")})\\b")
    private val STRING_REGEX = Regex(
        "(\"\"\".*?\"\"\"|'''.*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')",
        RegexOption.DOT_MATCHES_ALL
    )
    private val COMMENT_REGEX = Regex("#.*")
    private val NUMBER_REGEX = Regex("\\b\\d+(\\.\\d+)?\\b")

    private const val COLOR_KEYWORD = "#C678DD"
    private const val COLOR_STRING = "#98C379"
    private const val COLOR_COMMENT = "#5C6370"
    private const val COLOR_NUMBER = "#D19A66"

    fun highlight(editable: Editable) {
        editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }

        applySpans(editable, KEYWORD_REGEX, COLOR_KEYWORD)
        applySpans(editable, NUMBER_REGEX, COLOR_NUMBER)
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
}
