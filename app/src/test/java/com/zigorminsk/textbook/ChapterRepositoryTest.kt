package com.zigorminsk.textbook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterRepositoryTest {

    @Test
    fun loadsAllChapters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chapters = ChapterRepository.load(context)
        assertEquals(22, chapters.size)
        assertTrue(chapters.all { it.title.isNotBlank() })
        assertTrue(chapters.all { it.file.endsWith(".html") })
        assertTrue(chapters.all { it.id.isNotBlank() })
    }

    @Test
    fun chapterFilesExistInAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chapters = ChapterRepository.load(context)
        for (chapter in chapters) {
            context.assets.open("chapters/${chapter.file}").close()
        }
    }
}
