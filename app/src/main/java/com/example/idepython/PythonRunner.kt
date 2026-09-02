package com.example.idepython

import com.chaquo.python.PyException
import com.chaquo.python.Python
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PythonRunner(
    private val onOutput: (String, String) -> Unit,
    private val onFinished: () -> Unit,
    private val onInputRequested: () -> Unit
) {
    private var job: Thread? = null
    private val inputQueue = SynchronousQueue<String>()

    private val callback = object : PythonOutputCallback {
        override fun onOutput(text: String, stream: String) = onOutput.invoke(text, stream)
        override fun onFinished() = onFinished.invoke()
        override fun onInputRequested(): String {
            onInputRequested.invoke()
            return inputQueue.take()
        }
    }

    val isRunning: Boolean
        get() = job?.isAlive == true

    fun run(code: String, args: List<String> = emptyList()) {
        stop()
        job = thread(isDaemon = true) {
            try {
                val runnerModule = Python.getInstance().getModule("runner")
                runnerModule.callAttr("run_code", code, callback, args)
            } catch (e: PyException) {
                onOutput(e.message ?: "Python error", "stderr")
                onFinished()
            }
        }
    }

    /** Runs check_syntax(code) synchronously — call from a background thread. */
    fun checkSyntax(code: String): String? {
        val result = Python.getInstance().getModule("runner").callAttr("check_syntax", code)
        return if (result.isNone) null else result.toString()
    }

    /** Supplies the line an in-flight onInputRequested() call is blocked on. */
    fun submitInput(line: String) {
        // Short timeout, not put(): this runs on the UI thread, and the
        // background thread should already be waiting in take() by now.
        inputQueue.offer(line, 2, TimeUnit.SECONDS)
    }

    /**
     * Best effort only: CPython's exec() can't be preempted from another
     * thread, so this just abandons the thread — it may keep running until
     * the code itself returns or hits a blocked input() call.
     */
    fun stop() {
        job?.takeIf { it.isAlive }?.interrupt()
        job = null
    }
}
