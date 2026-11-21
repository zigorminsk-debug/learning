package com.zigorminsk.textbook

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Дымовые UI-тесты: ключевые экраны создаются без падений,
 * карточка главы рендерится в PNG (скриншоты уходят артефактом CI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiSmokeTest {

    @Before
    fun setUp() {
        // Не ходим в сеть (проверка обновлений) во время тестов
        UpdateChecker.enabled = false
    }

    @Test
    fun mainScreenInflates() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity: Activity = controller.get()
        assertNotNull(activity.findViewById(R.id.toolbar))
        assertNotNull(activity.findViewById(R.id.chapterList))
        assertNotNull(activity.findViewById(R.id.btnContinue))
        assertEquals(22, ChapterRepository.load(activity).size)
        controller.stop().destroy()
    }

    @Test
    fun chapterActivityHostsReaderFragment() {
        val intent = ChapterActivity.intent(
            ApplicationProvider.getApplicationContext(),
            5
        )
        val controller = Robolectric.buildActivity(ChapterActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val activity = controller.get()
        assertNotNull(activity.findViewById(R.id.toolbar))
        assertNotNull(activity.findViewById(R.id.chapterContainer))
        val fragment =
            activity.supportFragmentManager.findFragmentByTag(ChapterFragment.TAG)
        assertNotNull("читалка должна быть встроена", fragment)
        controller.stop().destroy()
    }

    @Test
    fun renderChapterCardScreenshot() {
        val context = android.view.ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_BusinessTextbook
        )
        val view: View = LayoutInflater.from(context)
            .inflate(R.layout.item_chapter, null, false)

        view.findViewById<TextView>(R.id.chapterNumber).text = "6"
        view.findViewById<TextView>(R.id.chapterTitle).text =
            "Стратегический менеджмент"
        view.findViewById<TextView>(R.id.chapterSubtitle).text =
            "Ансофф, Портер, Минцберг: где и как конкурировать"
        view.findViewById<ImageView>(R.id.chapterStar).visibility = View.VISIBLE

        val progress = view.findViewById<LinearLayout>(R.id.progressContainer)
        progress.visibility = View.VISIBLE
        val fill = view.findViewById<View>(R.id.progressFill)
        val rest = view.findViewById<View>(R.id.progressRest)
        (fill.layoutParams as LinearLayout.LayoutParams).apply { weight = 42f }
        (rest.layoutParams as LinearLayout.LayoutParams).apply { weight = 58f }

        val width = 1000
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, width, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val out = saveScreenshot(bitmap, "chapter_card.png")
        assertTrue("скриншот должен быть записан", out.exists() && out.length() > 1000)
    }

    @Test
    fun renderMainScreenScreenshot() {
        val context = android.view.ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_BusinessTextbook
        )
        val view: View = LayoutInflater.from(context)
            .inflate(R.layout.activity_main, null, false)

        view.findViewById<TextView>(R.id.headerText).text = "Содержание (22 главы)"
        view.findViewById<TextView>(R.id.buildText).text = "Сборка приложения: 1.0.0"

        val width = 1080
        val height = 1920
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val out = saveScreenshot(bitmap, "main_screen.png")
        assertTrue("скриншот должен быть записан", out.exists() && out.length() > 1000)
    }

    private fun saveScreenshot(bitmap: Bitmap, name: String): File {
        val dir = File("build/reports/ui-screenshots")
        dir.mkdirs()
        val out = File(dir, name)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }
}
