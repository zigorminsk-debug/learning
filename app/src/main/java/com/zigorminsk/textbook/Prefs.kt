package com.zigorminsk.textbook

import android.content.Context

/** Общие ключи SharedPreferences и помощники состояния. */
object Prefs {
    const val NAME = "textbook_prefs"
    const val LAST_CHAPTER = "last_chapter"
    const val NIGHT_MODE = "night_mode"
    const val TEXT_ZOOM = "text_zoom"
    const val BOOKMARKS = "bookmarks"

    const val TEXT_ZOOM_MIN = 70
    const val TEXT_ZOOM_MAX = 180
    const val TEXT_ZOOM_STEP = 15
    const val TEXT_ZOOM_DEFAULT = 100

    fun progressKey(chapterId: String): String = "progress_$chapterId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun loadBookmarks(context: Context): Set<String> =
        prefs(context).getStringSet(BOOKMARKS, emptySet())?.toSet() ?: emptySet()

    fun isBookmarked(context: Context, chapterId: String): Boolean =
        chapterId in loadBookmarks(context)

    fun toggleBookmark(context: Context, chapterId: String): Boolean {
        val current = loadBookmarks(context).toMutableSet()
        val added = if (!current.add(chapterId)) current.remove(chapterId) else true
        prefs(context).edit().putStringSet(BOOKMARKS, current).apply()
        return added
    }

    fun loadProgress(context: Context): Map<String, Int> {
        val p = prefs(context)
        return ChapterRepository.load(context).associate {
            it.id to p.getInt(progressKey(it.id), 0)
        }
    }

    fun saveProgress(context: Context, chapterId: String, percent: Int) {
        prefs(context).edit().putInt(progressKey(chapterId), percent.coerceIn(0, 100)).apply()
    }
}
