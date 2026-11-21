package com.zigorminsk.textbook

import android.content.Context
import org.json.JSONArray

data class Chapter(
    val id: String,
    val file: String,
    val title: String,
    val subtitle: String
)

object ChapterRepository {

    @Volatile
    private var cached: List<Chapter>? = null

    fun load(context: Context): List<Chapter> {
        cached?.let { return it }
        val json = context.assets.open("index.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val array = JSONArray(json)
        val chapters = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Chapter(
                id = obj.getString("id"),
                file = obj.getString("file"),
                title = obj.getString("title"),
                subtitle = obj.getString("subtitle")
            )
        }
        cached = chapters
        return chapters
    }
}
