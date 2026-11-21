package com.zigorminsk.textbook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchIndexTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun indexCoversAllChapters() {
        val entries = SearchIndex.load(context)
        assertEquals(22, entries.size)
        assertTrue(entries.all { it.text.length > 500 })
    }

    @Test
    fun findsPorterInStrategyChapter() {
        val entries = SearchIndex.load(context)
        val hits = SearchIndex.search(entries, "Портер")
        assertTrue("ожидались совпадения с «Портер»", hits.isNotEmpty())
        val strategyHit = hits.find { it.id == "06-strategicheskiy-menadzhment" }
        assertNotNull("глава про стратегию должна содержать Портера", strategyHit)
        assertTrue(strategyHit!!.snippet.contains("Портер"))
    }

    @Test
    fun noHitsForUnknownWord() {
        val entries = SearchIndex.load(context)
        val hits = SearchIndex.search(entries, "квазимодотрон")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun highlightMarksAllOccurrences() {
        val span = SearchIndex.highlight(context, "Портер и снова Портер", "Портер")
        val spans =
            span.getSpans(0, span.length, android.text.style.ForegroundColorSpan::class.java)
        assertEquals(2, spans.size)

        val noMatch = SearchIndex.highlight(context, "текст", "яяя")
        assertEquals(
            0,
            noMatch.getSpans(
                0,
                noMatch.length,
                android.text.style.ForegroundColorSpan::class.java
            ).size
        )
    }
}
