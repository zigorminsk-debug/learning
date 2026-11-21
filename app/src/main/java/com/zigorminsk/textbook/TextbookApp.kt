package com.zigorminsk.textbook

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Применяет сохранённый режим темы до создания первых Activity. */
class TextbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val mode = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getInt(Prefs.NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
