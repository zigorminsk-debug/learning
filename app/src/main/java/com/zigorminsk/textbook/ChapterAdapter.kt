package com.zigorminsk.textbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.chapterNumber)
        val title: TextView = view.findViewById(R.id.chapterTitle)
        val subtitle: TextView = view.findViewById(R.id.chapterSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val chapter = chapters[position]
        holder.number.text = (position + 1).toString()
        holder.title.text = chapter.title
        holder.subtitle.text = chapter.subtitle
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = chapters.size
}
