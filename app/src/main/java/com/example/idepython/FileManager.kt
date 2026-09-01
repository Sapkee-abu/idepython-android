package com.example.idepython

import android.content.Context
import java.io.File

object FileManager {
    private const val SCRIPTS_DIR = "scripts"

    fun scriptsDir(context: Context): File {
        val dir = File(context.filesDir, SCRIPTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listFiles(context: Context): List<File> =
        scriptsDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".py") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun read(file: File): String = file.readText()

    fun write(file: File, content: String) = file.writeText(content)

    fun create(context: Context, name: String): File {
        val fileName = if (name.endsWith(".py")) name else "$name.py"
        val file = File(scriptsDir(context), fileName)
        if (!file.exists()) file.writeText("# $fileName\n\n")
        return file
    }

    fun delete(file: File): Boolean = file.delete()

    /** Reserves a non-colliding "name.py" / "name (1).py" / ... target in the scripts dir. */
    fun uniqueTarget(context: Context, desiredName: String): File {
        val base = if (desiredName.endsWith(".py")) desiredName.dropLast(3) else desiredName
        var candidate = File(scriptsDir(context), "$base.py")
        var n = 1
        while (candidate.exists()) {
            candidate = File(scriptsDir(context), "$base ($n).py")
            n++
        }
        return candidate
    }

    fun rename(file: File, newName: String): File {
        val fileName = if (newName.endsWith(".py")) newName else "$newName.py"
        val target = File(file.parentFile, fileName)
        file.renameTo(target)
        return target
    }
}
