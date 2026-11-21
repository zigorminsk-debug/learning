package com.zigorminsk.textbook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RANDOM = "open_random"
        private const val TWO_PANE_MIN_SW_DP = 720
    }

    private lateinit var chapters: List<Chapter>
    private lateinit var adapter: ChapterAdapter
    private lateinit var list: RecyclerView
    private lateinit var emptyText: TextView

    private var currentQuery: String = ""
    private var bookmarksOnly: Boolean = false
    private var selectedChapterId: String? = null

    private val searchExecutor = Executors.newSingleThreadExecutor()

    private val isTwoPane: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= TWO_PANE_MIN_SW_DP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        chapters = ChapterRepository.load(this)

        findViewById<TextView>(R.id.headerText).text =
            getString(R.string.header_text, chapters.size)
        findViewById<TextView>(R.id.buildText).text =
            getString(R.string.build_label, currentVersionName())

        emptyText = findViewById(R.id.emptyText)
        list = findViewById(R.id.chapterList)
        adapter = ChapterAdapter(chapters) { chapter -> openChapter(chapter) }
        list.adapter = adapter
        applyListLayout()

        refreshState()

        // Восстановление двухпанельного читателя после поворота
        if (isTwoPane) {
            val existing =
                supportFragmentManager.findFragmentByTag(ChapterFragment.TAG) as? ChapterFragment
            if (existing != null) {
                attachReader(existing)
                showChapterInToolbar(existing.currentPosition)
            } else {
                setReaderClosedUi()
            }
        }

        // Виджет «Случайная глава» / пункт меню (только при первом создании)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_RANDOM, false)) {
            openRandomChapter()
        }

        // Автопроверка обновлений (один раз при запуске)
        checkForUpdates(interactive = false)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    /** Кнопка «Продолжить чтение» + закладки/прогресс в списке. */
    private fun refreshState() {
        adapter.bookmarks = Prefs.loadBookmarks(this)
        adapter.progress = Prefs.loadProgress(this)
        adapter.selectedId = selectedChapterId
        adapter.filter(currentQuery)

        val btn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnContinue)
        val last = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.LAST_CHAPTER, -1)
        if (last in chapters.indices) {
            btn.visibility = View.VISIBLE
            btn.text = getString(R.string.continue_reading_with, last + 1, chapters[last].title)
            btn.setOnClickListener {
                startActivity(ChapterActivity.intent(this, last, resume = true))
            }
        } else {
            btn.visibility = View.GONE
        }
    }

    private fun applyListLayout() {
        val widthDp = resources.configuration.screenWidthDp
        val span = when {
            widthDp >= 840 -> 3
            widthDp >= 600 -> 2
            else -> 1
        }
        list.layoutManager = if (span <= 1) {
            LinearLayoutManager(this)
        } else {
            GridLayoutManager(this, span)
        }
    }

    // --- открытие главы (телефон: Activity, планшет: фрагмент в правой панели) ---

    private fun openChapter(chapter: Chapter) {
        val position = chapters.indexOfFirst { it.id == chapter.id }
        if (position >= 0) openChapterAt(position)
    }

    private fun openChapterAt(position: Int) {
        selectedChapterId = chapters[position].id
        adapter.selectedId = selectedChapterId
        if (isTwoPane) {
            val fragment = ChapterFragment.newInstance(position)
            attachReader(fragment)
            supportFragmentManager.beginTransaction()
                .replace(R.id.chapterContainer, fragment, ChapterFragment.TAG)
                .commit()
            showChapterInToolbar(position)
        } else {
            startActivity(ChapterActivity.intent(this, position))
        }
    }

    private fun openRandomChapter() {
        openChapterAt(Random.nextInt(chapters.size))
    }

    private fun attachReader(fragment: ChapterFragment) {
        fragment.onChapterChanged = { position ->
            selectedChapterId = chapters.getOrNull(position)?.id
            adapter.selectedId = selectedChapterId
            showChapterInToolbar(position)
        }
    }

    private fun showChapterInToolbar(position: Int) {
        if (!isTwoPane) return
        val chapter = chapters.getOrNull(position) ?: return
        val toolbar =
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.navigationIcon = getDrawable(R.drawable.ic_close)
        toolbar.setNavigationOnClickListener { closeReader() }
        toolbar.title = chapter.title
        toolbar.subtitle = getString(R.string.chapter_label, position + 1, chapters.size)
        findViewById<TextView>(R.id.twoPaneEmpty)?.visibility = View.GONE
        findViewById<android.widget.FrameLayout>(R.id.chapterContainer)?.visibility = View.VISIBLE
    }

    private fun setReaderClosedUi() {
        if (!isTwoPane) return
        val toolbar =
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
        toolbar.title = getString(R.string.app_name)
        toolbar.subtitle = null
        findViewById<TextView>(R.id.twoPaneEmpty)?.visibility = View.VISIBLE
        findViewById<android.widget.FrameLayout>(R.id.chapterContainer)?.visibility = View.GONE
    }

    private fun closeReader() {
        if (!isTwoPane) return
        val fragment = supportFragmentManager.findFragmentByTag(ChapterFragment.TAG)
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
        }
        selectedChapterId = null
        adapter.selectedId = null
        setReaderClosedUi()
    }

    // --- меню: поиск, случайная глава, закладки, тема, обновления ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilter(newText.orEmpty())
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                applyFilter("")
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_bookmarks)?.isChecked = bookmarksOnly
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_random -> {
            openRandomChapter()
            true
        }
        R.id.action_bookmarks -> {
            bookmarksOnly = !bookmarksOnly
            item.isChecked = bookmarksOnly
            applyFilter(currentQuery)
            true
        }
        R.id.action_theme -> {
            showThemeDialog()
            true
        }
        R.id.action_update -> {
            checkForUpdates(interactive = true)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // --- полнотекстовый поиск ---

    private fun applyFilter(query: String) {
        currentQuery = query
        adapter.restrictToBookmarks = bookmarksOnly
        adapter.filter(query)
        if (query.isNotBlank()) {
            // асинхронный поиск по полному тексту глав
            searchExecutor.execute {
                val hits = SearchIndex.search(SearchIndex.load(this), query)
                val map = hits.associate { it.id to it.snippet }
                runOnUiThread {
                    if (query == currentQuery) {
                        adapter.setSnippets(map)
                        adapter.filter(query)
                        updateEmptyState()
                    }
                }
            }
        } else {
            adapter.setSnippets(emptyMap())
            adapter.filter("")
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = adapter.itemCount == 0
        emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        emptyText.text = when {
            currentQuery.isNotBlank() -> getString(R.string.no_results)
            bookmarksOnly -> getString(R.string.no_bookmarks)
            else -> getString(R.string.no_results)
        }
    }

    // --- тема ---

    private fun showThemeDialog() {
        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val current = prefs.getInt(Prefs.NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val checked = modes.indexOf(current).coerceAtLeast(0)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, checked) { d, which ->
                prefs.edit().putInt(Prefs.NIGHT_MODE, modes[which]).apply()
                AppCompatDelegate.setDefaultNightMode(modes[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        dialog.limitWidth()
    }

    // --- обновления ---

    private fun checkForUpdates(interactive: Boolean) {
        val localBuild = UpdateChecker.buildNumber(currentVersionName()) ?: 0
        UpdateChecker.check { result ->
            if (!isFinishing && !isDestroyed) {
                when {
                    result.ok && (result.latestBuild ?: 0) > localBuild -> {
                        showUpdateDialog(
                            "1.0.${result.latestBuild}",
                            "1.0.$localBuild"
                        )
                    }
                    result.ok && interactive -> {
                        android.widget.Toast.makeText(
                            this,
                            getString(R.string.update_latest, "1.0.$localBuild"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    interactive -> {
                        android.widget.Toast.makeText(
                            this,
                            R.string.update_check_failed,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(latest: String, local: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(getString(R.string.update_available, latest, local))
            .setPositiveButton(R.string.open) { _, _ ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/zigorminsk-debug/learning/releases/latest")
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.later, null)
            .create()
        dialog.show()
        dialog.limitWidth()
    }

    // --- системная кнопка «назад» (закрывает читалку на планшете) ---

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val reader =
            supportFragmentManager.findFragmentByTag(ChapterFragment.TAG) as? ChapterFragment
        if (isTwoPane && reader != null) {
            closeReader()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun currentVersionName(): String {
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            info.versionName ?: "—"
        } catch (e: Exception) {
            "—"
        }
    }
}
