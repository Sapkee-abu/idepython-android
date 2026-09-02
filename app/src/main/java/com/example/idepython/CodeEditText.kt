package com.example.idepython

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/** Plain EditText plus a selection-change hook, needed for bracket-match highlighting. */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    var onSelectionChange: ((start: Int, end: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChange?.invoke(selStart, selEnd)
    }
}
