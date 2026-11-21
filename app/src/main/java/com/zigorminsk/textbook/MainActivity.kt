package com.zigorminsk.textbook

import android.content.Context
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity() {

    private lateinit var chapters: List<Chapter>
    private lateinit var adapter: ChapterAdapter
    private lateinit var list: RecyclerView
    private lateinit var emptyText: TextView

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

        setupContinueButton()

        emptyText = findViewById(R.id.emptyText)
        list = findViewById(R.id.chapterList)
        adapter = ChapterAdapter(chapters) { chapter ->
            val pos = chapters.indexOfFirst { it.id == chapter.id }
            if (pos >= 0) startActivity(ChapterActivity.intent(this, pos))
        }
        list.adapter = adapter
        applyListLayout()
    }

    /** Кнопка «Продолжить чтение» — возвращает к последней открытой главе. */
    private fun setupContinueButton() {
        val btn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnContinue)
        val last = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.LAST_CHAPTER, -1)
        if (last in chapters.indices) {
            btn.visibility = View.VISIBLE
            btn.text = getString(R.string.continue_reading_with, last + 1, chapters[last].title)
            btn.setOnClickListener { startActivity(ChapterActivity.intent(this, last)) }
        } else {
            btn.visibility = View.GONE
        }
    }

    /**
     * Адаптация под ширину экрана: телефон — 1 колонка, планшет/landscape — 2,
     * широкие планшеты и расширенные окна — 3.
     */
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
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                applyFilter("")
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_theme -> {
            showThemeDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun applyFilter(query: String) {
        adapter.filter(query)
        emptyText.visibility =
            if (query.isNotBlank() && adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

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
