package com.example.idepython

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView

/**
 * Wraps the code EditText with the behaviors a plain EditText doesn't give
 * you: a synced line-number gutter, auto-indent, auto-closing brackets and
 * quotes (with type-through), bracket-match highlighting, debounced syntax
 * highlighting, a simple snapshot-based undo/redo stack, and a "settled
 * text" hook for a caller-driven syntax check.
 */
class CodeEditorController(
    private val editor: CodeEditText,
    private val lineNumbers: TextView
) {
    private var isInternalEdit = false
    private val handler = Handler(Looper.getMainLooper())

    private var highlightRunnable: Runnable? = null
    private var snapshotRunnable: Runnable? = null
    private var settleRunnable: Runnable? = null

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastSnapshot: String = ""
    private var lastLineCount = -1
    private var errorLine: Int? = null

    // AppCompatEditText.getText() is nullable in Kotlin's eyes, but an
    // EditText always has a live Editable buffer once constructed — centralize
    // the assumption here instead of scattering !!/?. through the class.
    // Named "editable" (not "text") since several methods below already have
    // a parameter or local named "text".
    private val editable: Editable
        get() = editor.text!!

    private val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val closeToOpen = mapOf(')' to '(', ']' to '[', '}' to '{')
    private val closers = setOf(')', ']', '}')
    private val quotes = setOf('"', '\'')

    /** Fired ~500ms after typing settles, with the current text — e.g. for a syntax check. */
    var onTextSettled: ((String) -> Unit)? = null

    var fontSizeSp: Float = 14f
        set(value) {
            val clamped = value.coerceIn(10f, 28f)
            field = clamped
            editor.textSize = clamped
            lineNumbers.textSize = clamped
        }

    // Only genuine single-character typing (not backspace/paste/autocorrect)
    // should trigger auto-pair/auto-indent — otherwise deleting text next to
    // a bracket would spuriously "auto-close" it again.
    private var pendingBefore = 0
    private var pendingCount = 0

    init {
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pendingBefore = before
                pendingCount = count
            }
            override fun afterTextChanged(editable: Editable?) {
                if (isInternalEdit || editable == null) return
                if (pendingBefore == 0 && pendingCount == 1) {
                    handleAutoPairAndIndent(editable)
                }
                updateLineNumbers(editable)
                scheduleHighlight(editable)
                scheduleSnapshot()
                scheduleSettle(editable.toString())
            }
        })
        editor.onSelectionChange = { start, end -> if (start == end) updateBracketMatch(start) }
        lastSnapshot = editable.toString()
        updateLineNumbers(editable)
    }

    // ---- Auto-indent / auto-pair ---------------------------------------

    private fun handleAutoPairAndIndent(editable: Editable) {
        val cursor = editor.selectionStart
        if (cursor <= 0 || cursor > editable.length) return
        when (val inserted = editable[cursor - 1]) {
            '\n' -> handleAutoIndent(editable, cursor)
            '(', '[', '{' -> handleAutoPairOpen(editable, cursor, inserted)
            in quotes, in closers -> handleSkipOrPair(editable, cursor, inserted)
        }
    }

    private fun handleAutoIndent(editable: Editable, cursor: Int) {
        val text = editable.toString()
        val newlineAt = cursor - 1
        val lineStart = text.lastIndexOf('\n', newlineAt - 1).let { if (it == -1) 0 else it + 1 }
        val prevLine = text.substring(lineStart, newlineAt)
        val indent = prevLine.takeWhile { it == ' ' }
        val extra = if (prevLine.trimEnd().endsWith(":")) "    " else ""
        val insertion = indent + extra
        if (insertion.isEmpty()) return
        internalEdit { editable.insert(cursor, insertion) }
        editor.setSelection(cursor + insertion.length)
    }

    private fun handleAutoPairOpen(editable: Editable, cursor: Int, open: Char) {
        val close = openToClose[open] ?: return
        internalEdit { editable.insert(cursor, close.toString()) }
        editor.setSelection(cursor)
    }

    /** For quotes/closers: skip over an existing matching char instead of duplicating it. */
    private fun handleSkipOrPair(editable: Editable, cursor: Int, typed: Char) {
        if (cursor < editable.length && editable[cursor] == typed) {
            internalEdit { editable.delete(cursor - 1, cursor) }
            editor.setSelection(cursor)
            return
        }
        if (typed in quotes) {
            internalEdit { editable.insert(cursor, typed.toString()) }
            editor.setSelection(cursor)
        }
    }

    private inline fun internalEdit(action: () -> Unit) {
        isInternalEdit = true
        action()
        isInternalEdit = false
    }

    // ---- Bracket matching ---------------------------------------------------

    private fun updateBracketMatch(cursor: Int) {
        val editable = editor.text ?: return
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }

        // Check the character right after the cursor, then right before it.
        val candidate = when {
            cursor < editable.length && isBracket(editable[cursor]) -> cursor
            cursor - 1 >= 0 && isBracket(editable[cursor - 1]) -> cursor - 1
            else -> return
        }
        val match = findMatchingBracket(editable, candidate) ?: return
        highlightBracket(editable, candidate)
        highlightBracket(editable, match)
    }

    private fun isBracket(c: Char) = c in openToClose || c in closeToOpen

    private fun findMatchingBracket(text: CharSequence, pos: Int): Int? {
        val bracket = text[pos]
        val isOpen = bracket in openToClose
        val partner = if (isOpen) openToClose.getValue(bracket) else closeToOpen.getValue(bracket)
        var depth = 0
        return if (isOpen) {
            for (i in pos until text.length) {
                when (text[i]) {
                    bracket -> depth++
                    partner -> { depth--; if (depth == 0) return i }
                }
            }
            null
        } else {
            for (i in pos downTo 0) {
                when (text[i]) {
                    bracket -> depth++
                    partner -> { depth--; if (depth == 0) return i }
                }
            }
            null
        }
    }

    private fun highlightBracket(editable: Editable, pos: Int) {
        editable.setSpan(
            BackgroundColorSpan(Color.parseColor("#3E4451")),
            pos,
            pos + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    // ---- Live error-line marker (gutter) -------------------------------------

    fun markErrorLine(line: Int) {
        errorLine = line
        applyErrorLineMarker()
    }

    fun clearErrorLineMarker() {
        if (errorLine == null) return
        errorLine = null
        applyErrorLineMarker()
    }

    private fun applyErrorLineMarker() {
        val spannable = lineNumbers.text as? Spannable ?: return
        spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
            .forEach { spannable.removeSpan(it) }
        val line = errorLine ?: return
        var start = 0
        for (i in 1 until line) {
            val next = spannable.indexOf("\n", start)
            if (next == -1) return
            start = next + 1
        }
        val end = spannable.indexOf("\n", start).let { if (it == -1) spannable.length else it }
        if (start >= end) return
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#E06C75")),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    // ---- Line numbers ----------------------------------------------------

    private fun updateLineNumbers(editable: CharSequence) {
        val count = editable.count { it == '\n' } + 1
        if (count == lastLineCount) {
            applyErrorLineMarker()
            return
        }
        lastLineCount = count
        lineNumbers.setText((1..count).joinToString("\n"), TextView.BufferType.SPANNABLE)
        applyErrorLineMarker()
    }

    // ---- Settle hook (debounced) ---------------------------------------------

    private fun scheduleSettle(text: String) {
        settleRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { onTextSettled?.invoke(text) }
        settleRunnable = runnable
        handler.postDelayed(runnable, 500)
    }

    // ---- Syntax highlight (debounced) -------------------------------------

    private fun scheduleHighlight(editable: Editable) {
        highlightRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { PythonSyntaxHighlighter.highlight(editable) }
        highlightRunnable = runnable
        handler.postDelayed(runnable, 150)
    }

    // ---- Undo / redo (debounced snapshots) --------------------------------

    private fun scheduleSnapshot() {
        snapshotRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { pushSnapshotIfChanged() }
        snapshotRunnable = runnable
        handler.postDelayed(runnable, 600)
    }

    private fun pushSnapshotIfChanged() {
        val current = editor.text.toString()
        if (current == lastSnapshot) return
        undoStack.addLast(lastSnapshot)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        lastSnapshot = current
    }

    fun undo() {
        pushSnapshotIfChanged()
        if (undoStack.isEmpty()) return
        redoStack.addLast(editor.text.toString())
        applySnapshot(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(editor.text.toString())
        applySnapshot(redoStack.removeLast())
    }

    private fun applySnapshot(text: String) {
        internalEdit {
            editor.setText(text)
            editor.setSelection(text.length)
        }
        lastSnapshot = text
        updateLineNumbers(text)
        PythonSyntaxHighlighter.highlight(editable)
        onTextSettled?.invoke(text)
    }

    // ---- Content management ------------------------------------------------

    fun setText(text: String) {
        internalEdit {
            editor.setText(text)
            editor.setSelection(0)
        }
        lastSnapshot = text
        lastLineCount = -1
        undoStack.clear()
        redoStack.clear()
        errorLine = null
        updateLineNumbers(text)
        PythonSyntaxHighlighter.highlight(editable)
        onTextSettled?.invoke(text)
    }

    fun getText(): String = editable.toString()

    fun insertAtCursor(text: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        editable.replace(minOf(start, end), maxOf(start, end), text)
    }

    fun requestEditorFocus() {
        editor.requestFocus()
    }

    fun setSelection(offset: Int) {
        editor.setSelection(offset.coerceIn(0, editable.length))
    }

    /** Character offset of the first column of a 1-based line number. */
    fun offsetForLine(lineNumber: Int): Int? {
        val lines = editor.text.toString().split("\n")
        if (lineNumber < 1 || lineNumber > lines.size) return null
        var offset = 0
        for (i in 0 until lineNumber - 1) offset += lines[i].length + 1
        return offset
    }

    /** 1-based line number containing a given character offset. */
    fun lineForOffset(offset: Int): Int {
        val text = editor.text.toString()
        val clamped = offset.coerceIn(0, text.length)
        return text.substring(0, clamped).count { it == '\n' } + 1
    }

    /** Pixel Y (within the editor) of the top of a 1-based line, for scrolling it into view. */
    fun lineTopPx(lineNumber: Int): Int? {
        val layout = editor.layout ?: return null
        val offset = offsetForLine(lineNumber) ?: return null
        val visualLine = layout.getLineForOffset(offset)
        return layout.getLineTop(visualLine) + editor.paddingTop
    }
}
