package com.zigorminsk.textbook

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import org.json.JSONArray

data class SearchEntry(val id: String, val title: String, val text: String)

data class SearchHit(val id: String, val snippet: String)

/** Полнотекстовый поиск по главам (индекс генерируется tools/build_html.py). */
object SearchIndex {

    @Volatile
    private var cache: List<SearchEntry>? = null

    fun load(context: Context): List<SearchEntry> {
        cache?.let { return it }
        val json = context.assets.open("search_index.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val array = JSONArray(json)
        val entries = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            SearchEntry(
                id = obj.getString("id"),
                title = obj.getString("title"),
                text = obj.getString("text")
            )
        }
        cache = entries
        return entries
    }

    fun chapterText(context: Context, chapterId: String): String? =
        load(context).find { it.id == chapterId }?.text

    /** Ищет query в каждой главе, возвращает фрагменты текста вокруг совпадений. */
    fun search(entries: List<SearchEntry>, query: String): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        return entries.mapNotNull { entry ->
            val snip = snippet(entry.text, query) ?: return@mapNotNull null
            SearchHit(entry.id, snip)
        }
    }

    fun snippet(text: String, query: String, radius: Int = 70): String? {
        val idx = text.indexOf(query, ignoreCase = true)
        if (idx < 0) return null
        val start = maxOf(0, idx - radius)
        val end = minOf(text.length, idx + query.length + radius)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val body = text.substring(start, end)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
        return prefix + body + suffix
    }

    /** Подсвечивает все вхождения query в тексте. */
    fun highlight(context: Context, text: String, query: String): SpannableString {
        val span = SpannableString(text)
        if (query.isBlank()) return span
        val color = ContextCompat.getColor(context, R.color.primary)
        var i = text.indexOf(query, ignoreCase = true)
        while (i >= 0 && i + query.length <= text.length) {
            span.setSpan(
                ForegroundColorSpan(color),
                i,
                i + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            i = text.indexOf(query, i + query.length, ignoreCase = true)
        }
        return span
    }
}
