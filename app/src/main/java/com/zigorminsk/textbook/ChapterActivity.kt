package com.zigorminsk.textbook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ChapterActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_POSITION = "chapter_position"
        private const val STATE_SCROLL_Y = "webview_scroll_y"
        private const val MAX_READING_WIDTH_DP = 780

        fun intent(context: Context, position: Int): Intent =
            Intent(context, ChapterActivity::class.java)
                .putExtra(EXTRA_POSITION, position)
    }

    private lateinit var chapters: List<Chapter>
    private lateinit var webView: WebView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private var position: Int = 0

    private var textZoom: Int = Prefs.TEXT_ZOOM_DEFAULT
    private var pendingScrollY: Int = 0
    private var restoreScrollAfterLoad: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)

        chapters = ChapterRepository.load(this)
        position = savedInstanceState?.getInt(EXTRA_POSITION, 0)
            ?: intent.getIntExtra(EXTRA_POSITION, 0)
        position = position.coerceIn(0, chapters.lastIndex)

        textZoom = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.TEXT_ZOOM, Prefs.TEXT_ZOOM_DEFAULT)
        pendingScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y, 0) ?: 0
        restoreScrollAfterLoad = pendingScrollY > 0

        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = true
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            @Suppress("DEPRECATION")
            setSupportZoom(true)
        }
        webView.settings.textZoom = effectiveTextZoom()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                // Остаёмся только внутри локальных ассетов
                return url.startsWith("file:///android_asset/")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (restoreScrollAfterLoad && pendingScrollY > 0) {
                    val y = pendingScrollY
                    val target = view
                    if (target != null) {
                        target.post { target.scrollTo(0, y) }
                    }
                    restoreScrollAfterLoad = false
                }
            }
        }

        applyReadingWidth()

        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnPrev.setOnClickListener { open(position - 1) }
        btnNext.setOnClickListener { open(position + 1) }

        show(position)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chapter, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_font_down -> {
            changeTextZoom(-Prefs.TEXT_ZOOM_STEP)
            true
        }
        R.id.action_font_up -> {
            changeTextZoom(Prefs.TEXT_ZOOM_STEP)
            true
        }
        R.id.action_toc -> {
            showTocDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Меняет размер шрифта главы (сохраняется; учитывается системный масштаб шрифта). */
    private fun changeTextZoom(delta: Int) {
        textZoom = (textZoom + delta).coerceIn(Prefs.TEXT_ZOOM_MIN, Prefs.TEXT_ZOOM_MAX)
        getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(Prefs.TEXT_ZOOM, textZoom)
            .apply()
        webView.settings.textZoom = effectiveTextZoom()
    }

    private fun effectiveTextZoom(): Int =
        (textZoom * resources.configuration.fontScale).toInt().coerceIn(50, 400)

    /** Переход к главе из кнопок или диалога «Содержание». */
    private fun open(newPosition: Int) {
        if (newPosition !in chapters.indices) return
        if (newPosition == position) {
            webView.scrollTo(0, 0)
            return
        }
        pendingScrollY = 0
        restoreScrollAfterLoad = false
        position = newPosition
        show(position)
        webView.scrollTo(0, 0)
    }

    private fun show(pos: Int) {
        val chapter = chapters[pos]
        toolbar.title = chapter.title
        toolbar.subtitle = getString(R.string.chapter_label, pos + 1, chapters.size)
        webView.loadUrl("file:///android_asset/chapters/${chapter.file}")
        btnPrev.isEnabled = pos > 0
        btnNext.isEnabled = pos < chapters.size - 1
        btnPrev.alpha = if (btnPrev.isEnabled) 1f else 0.4f
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.4f

        // Запоминаем последнюю главу для кнопки «Продолжить чтение»
        getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(Prefs.LAST_CHAPTER, pos)
            .apply()
    }

    private fun showTocDialog() {
        val titles = chapters.mapIndexed { i, c -> "${i + 1}. ${c.title}" }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.toc_title)
            .setSingleChoiceItems(titles, position) { d, which ->
                open(which)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        dialog.limitWidth()
    }

    /**
     * На широких экранах (планшет, landscape) ограничиваем колонку чтения,
     * чтобы строки не были слишком длинными, и центрируем её.
     */
    private fun applyReadingWidth() {
        val widthDp = resources.configuration.screenWidthDp
        if (widthDp > MAX_READING_WIDTH_DP) {
            val px = (MAX_READING_WIDTH_DP * resources.displayMetrics.density + 0.5f).toInt()
            val parent = webView.parent as? android.widget.FrameLayout
            if (parent != null) {
                webView.layoutParams = android.widget.FrameLayout.LayoutParams(
                    px,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER_HORIZONTAL
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_POSITION, position)
        outState.putInt(STATE_SCROLL_Y, webView.scrollY)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
