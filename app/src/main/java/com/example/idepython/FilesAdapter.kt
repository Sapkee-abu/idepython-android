package com.example.idepython

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FilesAdapter(
    private val onOpen: (File) -> Unit,
    private val onLongPress: (File) -> Unit
) : RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {

    private val files = mutableListOf<File>()

    fun submit(newFiles: List<File>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.label.text = file.name
        holder.itemView.setOnClickListener { onOpen(file) }
        holder.itemView.setOnLongClickListener {
            onLongPress(file)
            true
        }
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.fileName)
    }
}
