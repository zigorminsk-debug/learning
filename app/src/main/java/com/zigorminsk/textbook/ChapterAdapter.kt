package com.zigorminsk.textbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Chapter>,
    private val onClick: (Chapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.Holder>() {

    private var visible: List<Chapter> = chapters
    private var snippets: Map<String, String> = emptyMap()
    private var lastQuery: String = ""

    var bookmarks: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var progress: Map<String, Int> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var selectedId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var restrictToBookmarks: Boolean = false

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val number: TextView = view.findViewById(R.id.chapterNumber)
        val title: TextView = view.findViewById(R.id.chapterTitle)
        val subtitle: TextView = view.findViewById(R.id.chapterSubtitle)
        val star: ImageView = view.findViewById(R.id.chapterStar)
        val progressContainer: LinearLayout = view.findViewById(R.id.progressContainer)
        val progressFill: View = view.findViewById(R.id.progressFill)
        val progressRest: View = view.findViewById(R.id.progressRest)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val chapter = visible[position]
        val ctx = holder.itemView.context

        holder.number.text = (chapters.indexOfFirst { it.id == chapter.id } + 1).toString()
        holder.title.text = chapter.title

        // Подзаголовок: фрагмент из полнотекстового поиска (с подсветкой) или свой
        val snippet = snippets[chapter.id]
        if (snippet != null && lastQuery.isNotBlank()) {
            holder.subtitle.text = SearchIndex.highlight(ctx, snippet, lastQuery)
        } else {
            holder.subtitle.text = chapter.subtitle
        }

        // Закладка
        holder.star.visibility = if (chapter.id in bookmarks) View.VISIBLE else View.GONE

        // Прогресс чтения
        val pct = (progress[chapter.id] ?: 0).coerceIn(0, 100)
        if (pct >= 3) {
            holder.progressContainer.visibility = View.VISIBLE
            holder.progressFill.layoutParams = (holder.progressFill.layoutParams as LinearLayout.LayoutParams)
                .apply { weight = pct.toFloat() }
            holder.progressRest.layoutParams = (holder.progressRest.layoutParams as LinearLayout.LayoutParams)
                .apply { weight = (100 - pct).toFloat() }
            holder.progressContainer.requestLayout()
        } else {
            holder.progressContainer.visibility = View.GONE
        }

        // Выделение текущей главы (двухпанельный режим)
        if (chapter.id == selectedId) {
            holder.card.strokeWidth = 2
            holder.card.strokeColor =
                ContextCompat.getColor(ctx, R.color.selected_stroke)
        } else {
            holder.card.strokeWidth = 1
            holder.card.strokeColor =
                ContextCompat.getColor(ctx, R.color.divider)
        }

        holder.itemView.setOnClickListener { onClick(chapter) }
    }

    override fun getItemCount(): Int = visible.size

    fun setSnippets(map: Map<String, String>) {
        snippets = map
    }

    /** Фильтр: закладки + запрос по заголовку/подзаголовку/полному тексту. */
    fun filter(query: String) {
        lastQuery = query
        val normalized = query.trim()
        visible = chapters.filter { chapter ->
            val inBase = !restrictToBookmarks || chapter.id in bookmarks
            if (!inBase) return@filter false
            if (normalized.isEmpty()) return@filter true
            chapter.title.contains(normalized, true) ||
                chapter.subtitle.contains(normalized, true) ||
                snippets.containsKey(chapter.id)
        }
        notifyDataSetChanged()
    }
}
