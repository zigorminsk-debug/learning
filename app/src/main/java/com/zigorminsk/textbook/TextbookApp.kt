package com.zigorminsk.textbook

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/** Применяет сохранённую тему и Material You (динамические цвета, Android 12+). */
class TextbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        val mode = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getInt(Prefs.NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
