package com.example.idepython

/** Called from a Python thread — dispatch to the UI thread before touching views. */
interface PythonOutputCallback {
    fun onOutput(text: String, stream: String)
    fun onFinished()

    /**
     * Called from the Python thread when input() needs a line. Must block
     * until a line is available — safe here because this always runs off
     * the UI thread.
     */
    fun onInputRequested(): String
}
