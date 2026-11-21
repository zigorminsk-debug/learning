package com.zigorminsk.textbook

import android.os.Bundle
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val chapters = ChapterRepository.load(this)

        val header = findViewById<TextView>(R.id.headerText)
        header.text = getString(R.string.header_text, chapters.size)

        findViewById<TextView>(R.id.buildText).text =
            getString(R.string.build_label, currentVersionName())

        val list = findViewById<RecyclerView>(R.id.chapterList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ChapterAdapter(chapters) { position ->
            startActivity(ChapterActivity.intent(this, position))
        }
    }

    private fun currentVersionName(): String {
        return try {
            val info: PackageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
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
