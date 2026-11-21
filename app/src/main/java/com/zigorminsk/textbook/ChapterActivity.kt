package com.zigorminsk.textbook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Оболочка экрана чтения для телефонов: тулбар + ChapterFragment.
 * На планшетах (sw ≥ 720dp) тот же фрагмент встраивается в MainActivity.
 */
class ChapterActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_POSITION = "chapter_position"
        const val EXTRA_RESUME = "resume"

        fun intent(context: Context, position: Int, resume: Boolean = false): Intent =
            Intent(context, ChapterActivity::class.java)
                .putExtra(EXTRA_POSITION, position)
                .putExtra(EXTRA_RESUME, resume)
    }

    private var chapterFragment: ChapterFragment? = null
    private lateinit var chapters: List<Chapter>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chapter)

        chapters = ChapterRepository.load(this)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            val position = intent.getIntExtra(EXTRA_POSITION, 0).coerceIn(0, chapters.lastIndex)
            val resume = intent.getBooleanExtra(EXTRA_RESUME, false)
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.chapterContainer,
                    ChapterFragment.newInstance(position, resume),
                    ChapterFragment.TAG
                )
                .commitNow()
        }
        attachFragment()
    }

    private fun attachFragment() {
        val fragment =
            supportFragmentManager.findFragmentByTag(ChapterFragment.TAG) as? ChapterFragment
                ?: return
        chapterFragment = fragment
        fragment.onChapterChanged = { position -> updateToolbar(position) }
        if (fragment.view != null) {
            updateToolbar(fragment.currentPosition)
        }
    }

    private fun updateToolbar(position: Int) {
        val chapter = chapters.getOrNull(position) ?: return
        val toolbar =
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.title = chapter.title
        toolbar.subtitle = getString(R.string.chapter_label, position + 1, chapters.size)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (chapterFragment?.handleBack() == true) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
