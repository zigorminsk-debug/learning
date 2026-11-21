package com.zigorminsk.textbook

/** Общие ключи SharedPreferences приложения. */
object Prefs {
    const val NAME = "textbook_prefs"
    const val LAST_CHAPTER = "last_chapter"
    const val NIGHT_MODE = "night_mode"
    const val TEXT_ZOOM = "text_zoom"

    const val TEXT_ZOOM_MIN = 70
    const val TEXT_ZOOM_MAX = 180
    const val TEXT_ZOOM_STEP = 15
    const val TEXT_ZOOM_DEFAULT = 100
}
