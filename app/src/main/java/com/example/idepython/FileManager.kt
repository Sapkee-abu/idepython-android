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

    /** Folders first, then .py files, both alphabetical — for the file-drawer browser. */
    fun listDir(dir: File): List<File> =
        dir.listFiles { f -> f.isDirectory || f.name.endsWith(".py") }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

    /** Finds the first .py file anywhere under dir (depth-first) — used for startup fallback. */
    fun findAnyPyFile(dir: File): File? {
        for (f in dir.listFiles()?.sortedBy { it.name } ?: emptyList()) {
            if (f.isFile && f.name.endsWith(".py")) return f
            if (f.isDirectory) findAnyPyFile(f)?.let { return it }
        }
        return null
    }

    fun read(file: File): String = file.readText()

    fun write(file: File, content: String) = file.writeText(content)

    fun create(dir: File, name: String): File {
        val fileName = if (name.endsWith(".py")) name else "$name.py"
        val file = File(dir, fileName)
        if (!file.exists()) file.writeText("# $fileName\n\n")
        return file
    }

    fun createFolder(dir: File, name: String): File {
        val folder = File(dir, name)
        folder.mkdirs()
        return folder
    }

    fun delete(file: File): Boolean = if (file.isDirectory) file.deleteRecursively() else file.delete()

    /** Reserves a non-colliding "name.py" / "name (1).py" / ... target inside dir. */
    fun uniqueTarget(dir: File, desiredName: String): File {
        val base = if (desiredName.endsWith(".py")) desiredName.dropLast(3) else desiredName
        var candidate = File(dir, "$base.py")
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).py")
            n++
        }
        return candidate
    }

    fun rename(file: File, newName: String): File {
        val fileName = if (file.isDirectory || newName.endsWith(".py")) newName else "$newName.py"
        val target = File(file.parentFile, fileName)
        file.renameTo(target)
        return target
    }
}
