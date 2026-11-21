package com.zigorminsk.textbook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ChapterActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_POSITION = "chapter_position"

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)

        chapters = ChapterRepository.load(this)
        position = savedInstanceState?.getInt(EXTRA_POSITION, 0)
            ?: intent.getIntExtra(EXTRA_POSITION, 0)
        position = position.coerceIn(0, chapters.lastIndex)

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
            // Разрешаем WebView подстраиваться под тёмную тему (CSS prefers-color-scheme)
            @Suppress("DEPRECATION")
            setSupportZoom(true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                // Остаёмся только внутри локальных ассетов
                return if (url.startsWith("file:///android_asset/")) {
                    false
                } else {
                    true
                }
            }
        }

        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnPrev.setOnClickListener { open(position - 1) }
        btnNext.setOnClickListener { open(position + 1) }

        show(position)
    }

    private fun open(newPosition: Int) {
        if (newPosition !in chapters.indices) return
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_POSITION, position)
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
