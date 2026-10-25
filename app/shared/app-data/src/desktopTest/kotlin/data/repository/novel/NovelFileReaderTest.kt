/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

class NovelFileReaderTest {
    @Test fun textEncodingsAndChineseChapterBoundaries() {
        val source = "第一章 初见\r\n中文正文。\r\n第二章 重逢\n结束。"
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16, Charset.forName("GB18030"))) {
            val book = readNovelFile("测试.txt", source.toByteArray(charset)) { error("unexpected image") }
            assertEquals(2, book.chapters.size)
            assertTrue(book.contents[0].any { it.text == "中文正文。" })
            assertTrue(book.contents[1].any { it.text == "结束。" })
        }
    }

    @Test fun epubUsesSpineOrderAndPreservesImagesWithoutExecutingScripts() {
        val epub = zip(mapOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
            "OPS/book.opf" to """<package><metadata><dc:title>测试书</dc:title></metadata><manifest><item id="b" href="b.xhtml"/><item id="a" href="a.xhtml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""",
            "OPS/b.xhtml" to "<body><h1>第二章</h1><p>结尾</p></body>",
            "OPS/a.xhtml" to "<body><h1>第一章</h1><script>danger()</script><p>开头</p><img src='../images/p.png'/></body>",
            "images/p.png" to "image-fixture",
        ))
        var images = 0
        val result = readNovelFile("test.epub", epub) { images++; "file:///image.png" }
        assertEquals(listOf("第一章", "第二章"), result.chapters.map { it.title })
        assertEquals(1, images)
        assertFalse(result.contents.flatten().any { it.text.contains("danger") })
        assertTrue(result.contents[0].any { it.image == "file:///image.png" })
    }

    @Test fun rejectsTraversalCorruptionAndEncryptedBooks() {
        assertFails { readNovelFile("bad.epub", zip(mapOf("../../outside" to "bad"))) { "" } }
        assertFails { readNovelFile("bad.mobi", byteArrayOf(1, 2, 3)) { "" } }
        val encrypted = mobi("<p>secret</p>", encrypted = true)
        assertContains(assertFails { readNovelFile("encrypted.azw3", encrypted) { "" } }.message.orEmpty(), "加密")
    }

    @Test fun legacyMobiTextAndPalmDocBackReferences() {
        val book = readNovelFile("example.mobi", mobi("<h1>第一章</h1><p>正文。</p><mbp:pagebreak/><h1>第二章</h1><p>完。</p>")) { "" }
        assertEquals(2, book.chapters.size)
        assertTrue(book.contents.last().any { it.text == "完。" })
        assertEquals("abcabcabc", palmDoc(byteArrayOf(97, 98, 99, 0x80.toByte(), 0x1b)).decodeToString())
        assertFails { palmDoc(byteArrayOf(0x80.toByte(), 0)) }
    }

    @Test fun publicDomainBooksAcrossThreeRealFormats() {
        val directory = System.getenv("NOVEL_FIXTURE_DIR")
        assumeTrue(directory != null, "Set NOVEL_FIXTURE_DIR to the downloaded Gutenberg fixtures")
        for (extension in listOf("epub", "mobi", "azw3")) {
            val file = File(directory!!, "alice.$extension")
            var images = 0
            val result = readNovelFile(file.name, file.readBytes()) { images++; "file:///fixture-$images.png" }
            val text = result.contents.flatten().joinToString("\n") { it.text }
            assertContains(text, "Alice", message = extension)
            assertTrue(text.length > 120_000, "$extension text length ${text.length}")
            assertContains(text.lowercase(), "the rabbit-hole", message = extension)
            assertContains(text.lowercase(), "happy summer days", message = extension)
            assertTrue(result.chapters.size >= 12, "$extension chapters ${result.chapters.size}")
            println("NOVEL_FIXTURE $extension chapters=${result.chapters.size} chars=${text.length} images=$images")
        }
    }

    private fun zip(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> files.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() } }
    }.toByteArray()

    private fun mobi(html: String, encrypted: Boolean = false): ByteArray {
        val body = html.toByteArray(); val header = ByteArray(280)
        fun set16(array: ByteArray, offset: Int, value: Int) { array[offset] = (value ushr 8).toByte(); array[offset + 1] = value.toByte() }
        fun set32(array: ByteArray, offset: Int, value: Int) { repeat(4) { array[offset + it] = (value ushr (24 - it * 8)).toByte() } }
        set16(header, 0, 1); set32(header, 4, body.size); set16(header, 8, 1); set16(header, 12, if (encrypted) 1 else 0)
        "MOBI".toByteArray().copyInto(header, 16); set32(header, 20, 232); set32(header, 28, 65001); set32(header, 36, 6)
        set32(header, 84, 260); set32(header, 88, 4); "test".toByteArray().copyInto(header, 260); set32(header, 108, 2)
        val pdb = ByteArray(94); "BOOKMOBI".toByteArray().copyInto(pdb, 60); set16(pdb, 76, 2)
        set32(pdb, 78, pdb.size); set32(pdb, 86, pdb.size + header.size)
        return pdb + header + body
    }
}
