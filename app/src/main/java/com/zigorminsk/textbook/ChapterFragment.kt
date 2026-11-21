package com.zigorminsk.textbook

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import java.util.Locale

/**
 * Экран чтения главы. Используется:
 *  - в ChapterActivity (телефоны),
 *  - в правой панели MainActivity (планшеты, sw ≥ 720dp).
 */
class ChapterFragment : Fragment(), MenuProvider {

    companion object {
        private const val ARG_POSITION = "position"
        private const val ARG_RESUME = "resume"
        private const val STATE_SCROLL = "webview_scroll_y"
        private const val STATE_RESUME_CONSUMED = "resume_consumed"
        private const val MAX_READING_WIDTH_DP = 780
        const val TAG = "chapter"

        fun newInstance(position: Int, resume: Boolean = false): ChapterFragment =
            ChapterFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POSITION, position)
                    putBoolean(ARG_RESUME, resume)
                }
            }
    }

    /** Хост (Activity) получает уведомление о показе/смене главы. */
    var onChapterChanged: ((Int) -> Unit)? = null

    private var chapters: List<Chapter> = emptyList()
    private lateinit var webView: WebView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    private var position: Int = 0
    private var resumeOnOpen: Boolean = false
    private var resumeConsumed: Boolean = false
    private var pendingScrollY: Int = 0
    private var restoreScrollAfterLoad: Boolean = false
    private var loadSequence: Int = 0          // защита от повторных onPageFinished
    private var progressReady: Boolean = false
    private var savedProgressPct: Int = 0
    private var currentProgressPct: Int = 0

    private var textZoom: Int = Prefs.TEXT_ZOOM_DEFAULT

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var speaking: Boolean = false
    private var lastUtterance: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    // --- жест свайпа ---

    private var downX = 0f
    private var downY = 0f
    private var downScrollX = 0
    private var downTime = 0L
    private var gestureCancelled = false

    val currentPosition: Int get() = position

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_chapter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chapters = ChapterRepository.load(requireContext())
        // Позиция: состояние (поворот) важнее аргументов (навигация кнопками)
        val fromState = savedInstanceState?.getInt(ARG_POSITION, Int.MIN_VALUE) ?: Int.MIN_VALUE
        position = if (fromState != Int.MIN_VALUE) {
            fromState
        } else {
            arguments?.getInt(ARG_POSITION, 0) ?: 0
        }
        position = position.coerceIn(0, chapters.lastIndex)
        resumeOnOpen = arguments?.getBoolean(ARG_RESUME, false) ?: false
        resumeConsumed = savedInstanceState?.getBoolean(STATE_RESUME_CONSUMED, false) ?: false
        pendingScrollY = savedInstanceState?.getInt(STATE_SCROLL, 0) ?: 0
        restoreScrollAfterLoad = pendingScrollY > 0

        textZoom = requireContext()
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.TEXT_ZOOM, Prefs.TEXT_ZOOM_DEFAULT)

        webView = view.findViewById(R.id.webView)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)

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
                return url.startsWith("file:///android_asset/")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageLoaded()
            }
        }

        webView.setOnTouchListener { v, event ->
            handleSwipeGesture(v, event)
            false // не перехватываем: WebView обрабатывает сам
        }

        webView.setOnScrollChangeListener { _, _, y, _, _ ->
            if (progressReady) {
                currentProgressPct = percentForScroll(y)
            }
        }

        btnPrev.setOnClickListener { open(position - 1) }
        btnNext.setOnClickListener { open(position + 1) }

        applyReadingWidth()
        initTts()
        show(position)
    }

    override fun onStart() {
        super.onStart()
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onStop() {
        super.onStop()
        // Сохраняем прогресс чтения
        val chapter = chapters.getOrNull(position) ?: return
        Prefs.saveProgress(requireContext(), chapter.id, currentProgressPct)
        if (speaking) {
            tts?.stop()
            speaking = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        tts = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(ARG_POSITION, position)
        outState.putBoolean(STATE_RESUME_CONSUMED, resumeConsumed)
        if (::webView.isInitialized) {
            outState.putInt(STATE_SCROLL, webView.scrollY)
        }
    }

    // --- показ главы ---

    private fun show(pos: Int) {
        val chapter = chapters[pos]
        loadSequence++
        val seq = loadSequence

        restoreScrollAfterLoad = pendingScrollY > 0
        savedProgressPct = requireContext()
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.progressKey(chapter.id), 0)
        currentProgressPct = savedProgressPct
        progressReady = false

        onChapterChanged?.invoke(pos)

        webView.loadUrl("file:///android_asset/chapters/${chapter.file}")

        btnPrev.isEnabled = pos > 0
        btnNext.isEnabled = pos < chapters.size - 1
        btnPrev.alpha = if (btnPrev.isEnabled) 1f else 0.4f
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.4f

        requireContext()
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(Prefs.LAST_CHAPTER, pos)
            .apply()

        // если страница уже в кэше и onPageFinished не придёт повторно — подстраховка
        mainHandler.postDelayed({
            if (seq == loadSequence && !progressReady) onPageLoaded()
        }, 1500)
    }

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

    private fun onPageLoaded() {
        if (!isAdded || !::webView.isInitialized) return
        val seq = loadSequence
        // первая обработка этой загрузки
        if (restoreScrollAfterLoad && pendingScrollY > 0) {
            val y = pendingScrollY
            webView.post { if (seq == loadSequence) webView.scrollTo(0, y) }
            restoreScrollAfterLoad = false
            pendingScrollY = 0
            progressReady = true
            return
        }
        if (resumeOnOpen && !resumeConsumed && savedProgressPct > 2) {
            resumeConsumed = true
            val y = ((savedProgressPct / 100f) * maxScrollY()).toInt()
            webView.post { if (seq == loadSequence) webView.scrollTo(0, y) }
            progressReady = true
            return
        }
        progressReady = true
    }

    private fun maxScrollY(): Int {
        val contentPx = (webView.contentHeight * webView.scale).toInt()
        return (contentPx - webView.height).coerceAtLeast(0)
    }

    private fun percentForScroll(y: Int): Int {
        val max = maxScrollY()
        if (max <= 0) return 100
        return ((y * 100f) / max).toInt().coerceIn(0, 100)
    }

    /** Свайп влево/вправо — переход между главами (не мешает прокрутке таблиц). */
    private fun handleSwipeGesture(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downScrollX = if (::webView.isInitialized) webView.scrollX else 0
                downTime = SystemClock.uptimeMillis()
                gestureCancelled = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> gestureCancelled = true
            MotionEvent.ACTION_UP -> {
                if (!gestureCancelled && ::webView.isInitialized) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val dt = SystemClock.uptimeMillis() - downTime
                    val threshold = 80f * v.resources.displayMetrics.density
                    if (dt in 1..600 && kotlin.math.abs(dx) > threshold &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2
                    ) {
                        // Контент сдвинулся по горизонтали — значит, страница ещё
                        // прокручивается, свайп отдаём ей. Не сдвинулся — контент
                        // упёрся в край (или не шире экрана) — переключаем главу.
                        val contentScrolled = webView.scrollX != downScrollX
                        if (!contentScrolled && dx < 0) {
                            open(position + 1)
                        } else if (!contentScrolled && dx > 0) {
                            open(position - 1)
                        }
                    }
                }
            }
        }
        return false
    }

    private fun applyReadingWidth() {
        val widthDp = resources.configuration.screenWidthDp
        if (widthDp > MAX_READING_WIDTH_DP) {
            val px = (MAX_READING_WIDTH_DP * resources.displayMetrics.density + 0.5f).toInt()
            val parent = webView.parent as? android.widget.FrameLayout
            if (parent != null) {
                webView.layoutParams = android.widget.FrameLayout.LayoutParams(
                    px,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER_HORIZONTAL
                )
            }
        }
    }

    // --- возврат (якоря внутри главы) ---

    fun handleBack(): Boolean {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    // --- меню ---

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_chapter, menu)
        if (chapters.isEmpty() && isAdded) {
            chapters = ChapterRepository.load(requireContext())
        }
        val chapter = chapters.getOrNull(position) ?: return
        val bookmark = menu.findItem(R.id.action_bookmark_toggle)
        if (bookmark != null) {
            val marked = Prefs.isBookmarked(requireContext(), chapter.id)
            bookmark.setIcon(if (marked) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
        }
        menu.findItem(R.id.action_tts)?.title =
            getString(if (speaking) R.string.tts_stop else R.string.tts_play)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        R.id.action_bookmark_toggle -> {
            val chapter = chapters.getOrNull(position)
            if (chapter != null) {
                Prefs.toggleBookmark(requireContext(), chapter.id)
                invalidateMenu()
            }
            true
        }
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
        R.id.action_tts -> {
            toggleTts()
            true
        }
        R.id.action_pdf -> {
            exportPdf()
            true
        }
        R.id.action_share -> {
            shareChapter()
            true
        }
        else -> false
    }

    private fun invalidateMenu() {
        (requireActivity() as? MenuHost)?.invalidateMenu()
    }

    private fun showTocDialog() {
        val titles = chapters.mapIndexed { i, c -> "${i + 1}. ${c.title}" }.toTypedArray()
        val dialog = AlertDialog.Builder(requireContext())
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

    // --- размер шрифта ---

    private fun changeTextZoom(delta: Int) {
        textZoom = (textZoom + delta).coerceIn(Prefs.TEXT_ZOOM_MIN, Prefs.TEXT_ZOOM_MAX)
        requireContext()
            .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(Prefs.TEXT_ZOOM, textZoom)
            .apply()
        webView.settings.textZoom = effectiveTextZoom()
    }

    private fun effectiveTextZoom(): Int =
        (textZoom * resources.configuration.fontScale).toInt().coerceIn(50, 400)

    // --- озвучка (TTS) ---

    private fun initTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (!isAdded) return@TextToSpeech
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId == lastUtterance) {
                    mainHandler.post {
                        speaking = false
                        if (isAdded) invalidateMenu()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    speaking = false
                    if (isAdded) invalidateMenu()
                }
            }
        })
    }

    private fun toggleTts() {
        if (speaking) {
            tts?.stop()
            speaking = false
            invalidateMenu()
            return
        }
        val engine = tts
        if (!ttsReady || engine == null) {
            toast(R.string.tts_unavailable)
            return
        }
        val chapter = chapters.getOrNull(position) ?: return
        val text = SearchIndex.chapterText(requireContext(), chapter.id)
        if (text.isNullOrBlank()) {
            toast(R.string.text_unavailable)
            return
        }
        speaking = true
        invalidateMenu()
        val chunks = chunkText(text, 3000)
        chunks.forEachIndexed { i, chunk ->
            val id = "chapter_${position}_$i"
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, mode, null, id)
            lastUtterance = id
        }
    }

    private fun chunkText(text: String, size: Int): List<String> {
        if (text.length <= size) return listOf(text)
        val chunks = mutableListOf<String>()
        var rest = text
        while (rest.length > size) {
            var cut = maxOf(rest.lastIndexOf(". ", size), rest.lastIndexOf('\n', size))
            if (cut < size / 2) cut = size
            chunks += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotBlank()) chunks += rest
        return chunks
    }

    // --- PDF и шеринг ---

    private fun exportPdf() {
        val chapter = chapters.getOrNull(position) ?: return
        val printManager =
            requireContext().getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = webView.createPrintDocumentAdapter("Бизнес — ${chapter.title}")
        printManager.print(
            "Учебник_глава_${position + 1}",
            adapter,
            PrintAttributes.Builder().build()
        )
    }

    private fun shareChapter() {
        val chapter = chapters.getOrNull(position) ?: return
        val text = SearchIndex.chapterText(requireContext(), chapter.id)
        if (text.isNullOrBlank()) {
            toast(R.string.text_unavailable)
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, chapter.title)
            putExtra(Intent.EXTRA_TEXT, "${chapter.title}\n\n$text")
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_chapter)))
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }
}
