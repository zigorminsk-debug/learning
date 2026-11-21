package com.zigorminsk.textbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onClick: (Chapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.Holder>() {

    private var visible: List<Chapter> = chapters

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
        val chapter = visible[position]
        // Номер — позиция в полном списке глав (не в отфильтрованном)
        holder.number.text = (chapters.indexOfFirst { it.id == chapter.id } + 1).toString()
        holder.title.text = chapter.title
        holder.subtitle.text = chapter.subtitle
        holder.itemView.setOnClickListener { onClick(chapter) }
    }

    override fun getItemCount(): Int = visible.size

    /** Показывает только главы, подходящие под запрос (пустой запрос = все главы). */
    fun filter(query: String) {
        visible = if (query.isBlank()) {
            chapters
        } else {
            chapters.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}
