package com.example.idepython

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.idepython.databinding.ActivityMainBinding
import com.example.idepython.databinding.DialogConsoleBinding
import com.example.idepython.databinding.ItemTabBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var filesAdapter: FilesAdapter
    private lateinit var runner: PythonRunner
    private lateinit var editorController: CodeEditorController
    private val prefs by lazy { getSharedPreferences("ide_prefs", MODE_PRIVATE) }

    private val openTabs = mutableListOf<File>()
    private var currentTabIndex = -1
    private var suppressTabListener = false
    private val stderrBuffer = StringBuilder()

    // Console output persists across runs until the user taps Clear. The
    // popup dialog is just a window onto this buffer — closing it doesn't
    // lose anything, and it keeps updating live while the dialog is open.
    private val consoleBuffer = SpannableStringBuilder()
    private var consoleDialog: AlertDialog? = null
    private var consoleOutputView: TextView? = null
    private var consoleScrollView: ScrollView? = null
    private var stdinInputView: EditText? = null
    private var stdinRowView: View? = null
    private var isAwaitingInput = false

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importFile) }
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-python")) { uri ->
            uri?.let(::exportCurrentFile)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        editorController = CodeEditorController(binding.codeEditor, binding.lineNumbers)

        setupRail()
        setupSymbolToolbar()
        setupFindBar()
        setupFileDrawer()
        setupTabLayout()

        runner = PythonRunner(
            onOutput = { text, stream -> appendConsoleOutput(text, stream) },
            onFinished = { runOnUiThread { showErrorJumpIfAny() } },
            onInputRequested = {
                runOnUiThread {
                    isAwaitingInput = true
                    showConsoleDialog()
                    showInputRow()
                }
            }
        )

        restoreSession()
    }

    // ---- Left action rail --------------------------------------------------

    private fun setupRail() {
        binding.menuButton.setOnClickListener { binding.drawerLayout.openDrawer(Gravity.START) }
        binding.railRunButton.setOnClickListener { runCode() }
        binding.railStopButton.setOnClickListener { runner.stop() }
        binding.railConsoleButton.setOnClickListener { showConsoleDialog() }
        binding.railSaveButton.setOnClickListener {
            saveCurrentTab()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        binding.railFindButton.setOnClickListener { toggleFindBar() }
        binding.railMoreButton.setOnClickListener { showMoreMenu(it) }
    }

    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.rail_more_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import -> { importLauncher.launch(arrayOf("*/*")); true }
                R.id.action_export -> { exportLauncher.launch(currentFile()?.name ?: "code.py"); true }
                R.id.action_font_increase -> { editorController.fontSizeSp += 2f; persistSession(); true }
                R.id.action_font_decrease -> { editorController.fontSizeSp -= 2f; persistSession(); true }
                R.id.action_about -> { showAboutDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showAboutDialog() {
        val version = try {
            Python.getInstance().getModule("platform").callAttr("python_version").toString()
        } catch (e: Exception) {
            "?"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage("IDEPython\nPython $version (Chaquopy)")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun currentFile(): File? = openTabs.getOrNull(currentTabIndex)

    // ---- Tabs -------------------------------------------------------------

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabListener || tab.position == currentTabIndex) return
                switchToTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun openFileInTab(file: File) {
        val existingIndex = openTabs.indexOfFirst { it.path == file.path }
        if (existingIndex >= 0) {
            switchToTab(existingIndex)
        } else {
            saveCurrentTab()
            openTabs.add(file)
            addTabView(file)
            switchToTab(openTabs.size - 1)
        }
    }

    private fun addTabView(file: File) {
        val tabBinding = ItemTabBinding.inflate(layoutInflater)
        tabBinding.tabTitle.text = file.name
        tabBinding.tabClose.setOnClickListener { closeTab(openTabs.indexOf(file)) }
        val tab = binding.tabLayout.newTab().setCustomView(tabBinding.root)
        binding.tabLayout.addTab(tab)
    }

    private fun switchToTab(index: Int) {
        if (index !in openTabs.indices) return
        saveCurrentTab()
        currentTabIndex = index
        editorController.setText(FileManager.read(openTabs[index]))
        suppressTabListener = true
        binding.tabLayout.getTabAt(index)?.select()
        suppressTabListener = false
        persistSession()
    }

    private fun closeTab(index: Int) {
        if (index !in openTabs.indices) return
        val file = openTabs[index]
        AlertDialog.Builder(this)
            .setTitle(R.string.close_tab_title)
            .setMessage(getString(R.string.close_tab_message, file.name))
            .setPositiveButton(R.string.close) { _, _ -> removeTab(index) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun removeTab(index: Int) {
        if (index !in openTabs.indices) return
        val wasCurrent = index == currentTabIndex
        if (wasCurrent) saveCurrentTab()

        openTabs.removeAt(index)
        binding.tabLayout.removeTabAt(index)

        when {
            openTabs.isEmpty() -> {
                currentTabIndex = -1
                editorController.setText("")
                ensureAtLeastOneTab()
            }
            wasCurrent -> {
                // Already saved above; clear the index so switchToTab's own
                // save doesn't write this editor's text into whatever tab
                // shifted into the old slot.
                currentTabIndex = -1
                switchToTab(index.coerceAtMost(openTabs.size - 1))
            }
            index < currentTabIndex -> {
                // A tab before the active one closed — active tab's index
                // shifted down, but its content on screen is unchanged.
                currentTabIndex -= 1
                suppressTabListener = true
                binding.tabLayout.getTabAt(currentTabIndex)?.select()
                suppressTabListener = false
                persistSession()
            }
            else -> persistSession()
        }
    }

    private fun removeTabIfOpen(file: File) {
        val index = openTabs.indexOfFirst { it.path == file.path }
        if (index >= 0) removeTab(index)
    }

    private fun refreshTabTitle(file: File) {
        val index = openTabs.indexOfFirst { it.path == file.path }
        if (index < 0) return
        val tabView = binding.tabLayout.getTabAt(index)?.customView ?: return
        ItemTabBinding.bind(tabView).tabTitle.text = file.name
    }

    private fun ensureAtLeastOneTab() {
        if (openTabs.isNotEmpty()) return
        val files = FileManager.listFiles(this)
        val target = files.firstOrNull() ?: FileManager.create(this, "main.py").also { loadFiles() }
        openTabs.add(target)
        addTabView(target)
        switchToTab(0)
    }

    // ---- Session persistence ------------------------------------------------

    private fun persistSession() {
        prefs.edit()
            .putString(KEY_TABS, openTabs.joinToString(",") { it.name })
            .putInt(KEY_ACTIVE, currentTabIndex)
            .putFloat(KEY_FONT_SIZE, editorController.fontSizeSp)
            .apply()
    }

    private fun restoreSession() {
        editorController.fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 14f)
        val dir = FileManager.scriptsDir(this)
        val names = prefs.getString(KEY_TABS, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val restored = names.mapNotNull { name -> File(dir, name).takeIf { it.exists() } }

        setupFileDrawerList()

        if (restored.isEmpty()) {
            ensureAtLeastOneTab()
        } else {
            restored.forEach { openTabs.add(it); addTabView(it) }
            switchToTab(prefs.getInt(KEY_ACTIVE, 0).coerceIn(0, openTabs.size - 1))
        }
    }

    // ---- File drawer -------------------------------------------------

    private fun setupFileDrawer() {
        filesAdapter = FilesAdapter(
            onOpen = { file -> openFileInTab(file); binding.drawerLayout.closeDrawers() },
            onLongPress = { file -> showFileOptions(file) }
        )
        binding.filesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.filesRecyclerView.adapter = filesAdapter
        binding.newFileButton.setOnClickListener { promptNewFile() }
    }

    private fun setupFileDrawerList() = loadFiles()

    private fun loadFiles() {
        filesAdapter.submit(FileManager.listFiles(this))
    }

    private fun promptNewFile() {
        val input = EditText(this)
        input.hint = getString(R.string.file_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_file)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val file = FileManager.create(this, name)
                    loadFiles()
                    openFileInTab(file)
                    binding.drawerLayout.closeDrawers()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFileOptions(file: File) {
        val options = arrayOf(getString(R.string.rename), getString(R.string.delete))
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptRename(file)
                    1 -> confirmDelete(file)
                }
            }
            .show()
    }

    private fun promptRename(file: File) {
        val input = EditText(this)
        input.setText(file.nameWithoutExtension)
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val renamed = FileManager.rename(file, newName)
                    val index = openTabs.indexOfFirst { it.path == file.path }
                    if (index >= 0) openTabs[index] = renamed
                    loadFiles()
                    refreshTabTitle(renamed)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(file.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                FileManager.delete(file)
                removeTabIfOpen(file)
                loadFiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveCurrentTab() {
        val file = currentFile() ?: return
        FileManager.write(file, editorController.getText())
    }

    // ---- Import / export --------------------------------------------------

    private fun importFile(uri: Uri) {
        val name = queryDisplayName(uri) ?: "imported.py"
        val target = FileManager.uniqueTarget(this, name)
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        loadFiles()
        openFileInTab(target)
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }

    private fun exportCurrentFile(uri: Uri) {
        val file = currentFile() ?: return
        saveCurrentTab()
        contentResolver.openOutputStream(uri)?.use { output ->
            output.write(FileManager.read(file).toByteArray())
        }
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    // ---- Find & replace -----------------------------------------------------

    private fun setupFindBar() {
        binding.closeFindButton.setOnClickListener { binding.findBar.visibility = View.GONE }
        binding.findNextButton.setOnClickListener { findNext(forward = true) }
        binding.findPrevButton.setOnClickListener { findNext(forward = false) }
        binding.replaceButton.setOnClickListener { replaceCurrentMatch() }
        binding.replaceAllButton.setOnClickListener { replaceAll() }
    }

    private fun toggleFindBar() {
        val show = binding.findBar.visibility != View.VISIBLE
        binding.findBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) binding.findInput.requestFocus()
    }

    private fun findNext(forward: Boolean) {
        val query = binding.findInput.text.toString()
        if (query.isEmpty()) return
        val text = editorController.getText()
        val cursor = binding.codeEditor.selectionEnd.coerceAtLeast(0)
        val index = if (forward) {
            val from = text.indexOf(query, cursor)
            if (from == -1) text.indexOf(query) else from
        } else {
            val searchFrom = (cursor - query.length - 1).coerceAtLeast(0)
            val before = text.lastIndexOf(query, searchFrom)
            if (before == -1) text.lastIndexOf(query) else before
        }
        if (index == -1) {
            Toast.makeText(this, R.string.no_matches, Toast.LENGTH_SHORT).show()
            return
        }
        binding.codeEditor.setSelection(index, index + query.length)
        scrollEditorToOffset(index)
    }

    private fun replaceCurrentMatch() {
        val query = binding.findInput.text.toString()
        if (query.isEmpty()) return
        val replacement = binding.replaceInput.text.toString()
        val start = binding.codeEditor.selectionStart
        val end = binding.codeEditor.selectionEnd
        if (start in 0..editorController.getText().length && end >= start) {
            val selected = editorController.getText().substring(start, end.coerceAtMost(editorController.getText().length))
            if (selected == query) {
                binding.codeEditor.text.replace(start, end, replacement)
            }
        }
        findNext(forward = true)
    }

    private fun replaceAll() {
        val query = binding.findInput.text.toString()
        if (query.isEmpty()) return
        val replacement = binding.replaceInput.text.toString()
        val text = editorController.getText()
        val count = text.split(query).size - 1
        if (count == 0) {
            Toast.makeText(this, R.string.no_matches, Toast.LENGTH_SHORT).show()
            return
        }
        editorController.setText(text.replace(query, replacement))
        Toast.makeText(this, getString(R.string.replaced_count, count), Toast.LENGTH_SHORT).show()
    }

    // ---- Symbol toolbar -----------------------------------------------------

    private fun setupSymbolToolbar() {
        val buttons = listOf(
            "⇥" to { editorController.insertAtCursor("    ") },
            ":" to { editorController.insertAtCursor(":") },
            "(" to { editorController.insertAtCursor("(") },
            ")" to { editorController.insertAtCursor(")") },
            "[" to { editorController.insertAtCursor("[") },
            "]" to { editorController.insertAtCursor("]") },
            "{" to { editorController.insertAtCursor("{") },
            "}" to { editorController.insertAtCursor("}") },
            "\"" to { editorController.insertAtCursor("\"") },
            "'" to { editorController.insertAtCursor("'") },
            "#" to { editorController.insertAtCursor("#") },
            "_" to { editorController.insertAtCursor("_") },
            "=" to { editorController.insertAtCursor("=") },
            "+" to { editorController.insertAtCursor("+") },
            "-" to { editorController.insertAtCursor("-") },
            "*" to { editorController.insertAtCursor("*") },
            "/" to { editorController.insertAtCursor("/") },
            "." to { editorController.insertAtCursor(".") },
            "," to { editorController.insertAtCursor(",") },
            "↶" to { editorController.undo() },
            "↷" to { editorController.redo() }
        )
        buttons.forEach { (label, action) ->
            val button = Button(this, null, 0, R.style.SymbolButton)
            button.text = label
            button.setOnClickListener { action() }
            binding.symbolToolbar.addView(button)
        }
    }

    // ---- Run / console / stdin ------------------------------------------------

    private fun runCode() {
        saveCurrentTab()
        isAwaitingInput = false
        stdinRowView?.visibility = View.GONE
        showConsoleDialog()
        runner.run(editorController.getText())
    }

    private fun showConsoleDialog() {
        if (consoleDialog?.isShowing == true) return

        val dialogBinding = DialogConsoleBinding.inflate(layoutInflater)
        consoleOutputView = dialogBinding.consoleOutput
        consoleScrollView = dialogBinding.consoleScroll
        stdinInputView = dialogBinding.stdinInput
        stdinRowView = dialogBinding.inputRow

        dialogBinding.consoleOutput.text = consoleBuffer
        dialogBinding.consoleScroll.post { dialogBinding.consoleScroll.fullScroll(View.FOCUS_DOWN) }

        dialogBinding.consoleClearButton.setOnClickListener {
            consoleBuffer.clear()
            stderrBuffer.clear()
            dialogBinding.consoleOutput.text = ""
        }
        dialogBinding.consoleCloseButton.setOnClickListener { consoleDialog?.dismiss() }
        dialogBinding.stdinSendButton.setOnClickListener { submitStdinInput() }
        dialogBinding.stdinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitStdinInput()
                true
            } else {
                false
            }
        }

        if (isAwaitingInput) showInputRow()

        consoleDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setOnDismissListener {
                consoleOutputView = null
                consoleScrollView = null
                stdinInputView = null
                stdinRowView = null
                consoleDialog = null
            }
            .create()
        consoleDialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
        consoleDialog?.show()
    }

    private fun showInputRow() {
        stdinRowView?.visibility = View.VISIBLE
        stdinInputView?.requestFocus()
    }

    private fun submitStdinInput() {
        val text = stdinInputView?.text?.toString() ?: return
        stdinInputView?.setText("")
        stdinRowView?.visibility = View.GONE
        isAwaitingInput = false
        appendConsoleOutput("$text\n", "stdin_echo")
        runner.submitInput(text)
    }

    private fun appendConsoleOutput(text: String, stream: String) {
        runOnUiThread {
            val color = when (stream) {
                "stderr" -> Color.parseColor("#E06C75")
                "stdin_echo" -> Color.parseColor("#61AFEF")
                else -> Color.parseColor("#D1D5DB")
            }
            if (stream == "stderr") stderrBuffer.append(text)

            val bufferStart = consoleBuffer.length
            consoleBuffer.append(text)
            consoleBuffer.setSpan(
                ForegroundColorSpan(color),
                bufferStart,
                consoleBuffer.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            consoleOutputView?.let { view ->
                val start = view.length()
                view.append(text)
                (view.text as? Spannable)?.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    view.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                consoleScrollView?.post { consoleScrollView?.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    private fun showErrorJumpIfAny() {
        val match = ERROR_LINE_REGEX.findAll(stderrBuffer).lastOrNull() ?: return
        val line = match.groupValues[1].toIntOrNull() ?: return
        Snackbar.make(binding.root, getString(R.string.error_at_line, line), Snackbar.LENGTH_LONG)
            .setAction(R.string.go_to_line) {
                consoleDialog?.dismiss()
                val offset = editorController.offsetForLine(line) ?: return@setAction
                editorController.requestEditorFocus()
                editorController.setSelection(offset)
                scrollEditorToOffset(offset)
            }
            .show()
    }

    private fun scrollEditorToOffset(offset: Int) {
        val line = editorController.lineForOffset(offset)
        binding.codeEditor.post {
            editorController.lineTopPx(line)?.let { y ->
                binding.editorScroll.scrollTo(0, (y - 40).coerceAtLeast(0))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentTab()
        persistSession()
    }

    companion object {
        private const val KEY_TABS = "open_tabs"
        private const val KEY_ACTIVE = "active_tab"
        private const val KEY_FONT_SIZE = "font_size"
        private val ERROR_LINE_REGEX = Regex("""File "<idepython>", line (\d+)""")
    }
}
