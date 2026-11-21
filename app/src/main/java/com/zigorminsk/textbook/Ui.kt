package com.zigorminsk.textbook

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog

/** Ограничивает ширину диалога на больших экранах (планшеты, landscape). */
fun AlertDialog.limitWidth(maxDp: Int = 640): AlertDialog {
    window?.let { w ->
        val dm = context.resources.displayMetrics
        val maxPx = (maxDp * dm.density + 0.5f).toInt()
        val width = minOf(maxPx, dm.widthPixels)
        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    return this
}
