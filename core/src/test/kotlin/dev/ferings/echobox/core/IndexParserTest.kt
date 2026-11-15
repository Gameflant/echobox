package com.feringsapp.echobox.core

import com.feringsapp.echobox.core.sync.IndexParser
import com.feringsapp.echobox.core.sync.normalizeQuery
import com.feringsapp.echobox.core.sync.searchBlob
import com.feringsapp.echobox.core.model.AuthorContacts
import com.feringsapp.echobox.core.model.SeriesRef
import com.feringsapp.echobox.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IndexParserTest {
    private val parser = IndexParser()

    @Test
    fun `streams 200k tracks within a small memory budget`() {
        val file = File.createTempFile("tracks", ".json")
        val total = 200_000
        file.bufferedWriter().use { writer ->
            writer.write("[")
            repeat(total) { index ->
                if (index > 0) writer.write(",")
                writer.write(
                    """{"id":"20261114-a$index","title":"track $index","author":"author",""" +
                        """"game":"Undertale","tags":["boss","piano"],"series":{"name":"OST","index":1,"total":12},""" +
                        """"authorization":"self_made","resource_license":"CC BY-NC 4.0","file":"$index.mp3",""" +
                        """"format":"mp3","duration":183,"cover":"$index.webp","hash":"sha256:abc",""" +
                        """"uploaded_at":"2026-11-14T10:00:00Z"}""",
                )
            }
            writer.write("]")
        }

        val before = usedMemory()
        var count = 0
        file.inputStream().use { input ->
            for (track in parser.tracks(input)) {
                check(track.id.isNotBlank())
                count++
            }
        }
        val growth = usedMemory() - before
        file.delete()

        assertEquals(total, count)
        assertTrue("流式解析内存增长过大: $growth bytes", growth < 96L * 1024 * 1024)
    }

    @Test
    fun `keeps unknown fields and defaults`() {
        val json = """[{"id":"x","title":"t","weird_field":1}]"""
        val track = parser.tracks(json.byteInputStream()).first()
        assertEquals("x", track.id)
        assertEquals("Undertale", track.game)
        assertEquals("self_made", track.authorization)
    }

    @Test
    fun `builds search blob and normalizes query`() {
        val track = Track(
            id = "1",
            title = "Megalovania (Piano)",
            author = "Toby Fox",
            tags = listOf("Boss", "Piano"),
            series = SeriesRef("OST 重编曲", 3, 12),
        )
        val blob = track.searchBlob()
        assertTrue(blob.contains("megalovania"))
        assertTrue(blob.contains("boss"))
        assertTrue(blob.contains("ost 重编曲"))

        assertEquals("mega", normalizeQuery("  MeGa\u0000  "))
        assertTrue(normalizeQuery("x".repeat(200)).length <= 64)
    }

    @Test
    fun `author contacts are optional`() {
        assertEquals(false, AuthorContacts().any())
        assertEquals(true, AuthorContacts(bilibili = "https://space.bilibili.com/1").any())
    }

    private fun usedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
