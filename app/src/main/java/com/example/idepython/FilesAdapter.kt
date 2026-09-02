package com.example.idepython

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FilesAdapter(
    private val onOpenFile: (File) -> Unit,
    private val onOpenFolder: (File) -> Unit,
    private val onNavigateUp: () -> Unit,
    private val onLongPress: (File) -> Unit
) : RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {

    private var showUp = false
    private val entries = mutableListOf<File>()
    var textColor: Int? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submit(canGoUp: Boolean, newEntries: List<File>) {
        showUp = canGoUp
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        textColor?.let { holder.label.setTextColor(it) }
        if (showUp && position == 0) {
            holder.label.text = ".."
            holder.label.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_revert, 0, 0, 0
            )
            holder.itemView.setOnClickListener { onNavigateUp() }
            holder.itemView.setOnLongClickListener { true }
            return
        }
        val file = entries[position - if (showUp) 1 else 0]
        holder.label.text = file.name
        val icon = if (file.isDirectory) {
            android.R.drawable.ic_menu_agenda
        } else {
            android.R.drawable.ic_menu_edit
        }
        holder.label.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        holder.itemView.setOnClickListener {
            if (file.isDirectory) onOpenFolder(file) else onOpenFile(file)
        }
        holder.itemView.setOnLongClickListener {
            onLongPress(file)
            true
        }
    }

    override fun getItemCount(): Int = entries.size + if (showUp) 1 else 0

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.fileName)
    }
}
