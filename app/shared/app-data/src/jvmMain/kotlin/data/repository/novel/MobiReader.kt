/* SPDX-License-Identifier: GPL-3.0-or-later
 * MOBI Huff/CDIC and KF8 index reconstruction adapted from calibre.
 * Copyright 2011-2012 Kovid Goyal <kovid@kovidgoyal.net>, darkninja and igorsk.
 * https://github.com/kovidgoyal/calibre/tree/master/src/calibre/ebooks/mobi
 */
package me.him188.ani.app.data.repository.novel

import com.fleeksoft.ksoup.Ksoup
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

private fun ByteArray.u8(p: Int): Int { require(p in indices) { "电子书数据不完整" }; return this[p].toInt() and 255 }
private fun ByteArray.u16(p: Int) = (u8(p) shl 8) or u8(p + 1)
private fun ByteArray.u32(p: Int): Long = (0..3).fold(0L) { a, i -> (a shl 8) or u8(p + i).toLong() }
private fun ByteArray.int(p: Int): Int = u32(p).also { require(it <= Int.MAX_VALUE) { "无效的电子书索引" } }.toInt()
private fun ByteArray.sliceChecked(start: Int, end: Int): ByteArray {
    require(start >= 0 && end >= start && end <= size) { "电子书索引超出文件范围" }
    return copyOfRange(start, end)
}
private fun ByteArray.signature(p: Int, value: String) = p >= 0 && p + value.length <= size &&
    copyOfRange(p, p + value.length).decodeToString() == value

internal fun readMobi(fallback: String, bytes: ByteArray, saveAsset: (ByteArray) -> String): NovelDocument {
    require(bytes.signature(60, "BOOKMOBI") || bytes.signature(60, "TEXtREAd")) { "不是有效的 MOBI / AZW3 文件" }
    val count = bytes.u16(76)
    require(count in 2..20_000 && 78L + count * 8L <= bytes.size)
    val offsets = (0 until count).map { bytes.int(78 + it * 8) } + bytes.size
    require(offsets.first() >= 78 + count * 8 && offsets.zipWithNext().all { it.first <= it.second })
    val records = (0 until count).map { bytes.sliceChecked(offsets[it], offsets[it + 1]) }
    val header = records.first()
    require(header.u16(12) == 0) { "此 MOBI / AZW3 已加密，请导入未加密文件" }
    val mobi = header.signature(16, "MOBI")
    val textCount = header.u16(8)
    require(textCount in 1 until records.size)
    val textLength = header.int(4)
    require(textLength <= MAX_NOVEL_TEXT) { "电子书正文过大" }
    val version = if (mobi) header.int(36) else 0
    val codec = if (mobi && header.u32(28) == 1252L) Charset.forName("windows-1252") else Charsets.UTF_8
    val compression = header.u16(0)
    val huff = if (compression == 17480) {
        val start = header.int(112); val size = header.int(116)
        require(size in 2..records.size && start >= 0 && start + size <= records.size)
        HuffDecoder(records.subList(start, start + size))
    } else null
    val extraFlags = if (mobi && header.int(20) >= 228 && header.size >= 244) header.u16(242) else 0
    val output = ByteArrayOutputStream()
    for (record in records.subList(1, textCount + 1)) {
        val text = trimRecord(record, extraFlags)
        val decoded = when (compression) {
            1 -> text
            2 -> palmDoc(text)
            17480 -> huff!!.decode(text)
            else -> error("不支持的 MOBI 压缩类型：$compression")
        }
        require(output.size() + decoded.size <= MAX_NOVEL_TEXT) { "电子书正文过大" }
        output.write(decoded)
    }
    val all = output.toByteArray()
    require(all.size >= textLength) { "电子书正文不完整" }
    val raw = all.copyOf(textLength)
    val parts = if (version == 8 && header.size >= 264 && header.u32(252) != 0xffffffffL) {
        val skeletons = mobiIndex(records, header.int(252))
        val fragments = if (header.u32(248) == 0xffffffffL) emptyList() else mobiIndex(records, header.int(248))
        var fragment = 0
        skeletons.map { skeleton ->
            val location = skeleton.tags.getValue(6)
            val start = location[0]; val size = location[1]
            var body = raw.sliceChecked(start, start + size)
            var cursor = start + size
            repeat(skeleton.tags.getValue(1)[0]) {
                require(fragment < fragments.size)
                val entry = fragments[fragment++]
                val length = entry.tags.getValue(6)[1]
                val at = entry.name.toInt() - start
                require(at in 0..body.size)
                body = body.sliceChecked(0, at) + raw.sliceChecked(cursor, cursor + length) + body.sliceChecked(at, body.size)
                cursor += length
                require(body.size <= MAX_NOVEL_TEXT)
            }
            body
        }
    } else listOf(raw)
    val title = if (mobi) String(header.sliceChecked(header.int(84), header.int(84) + header.int(88)), codec).ifBlank { fallback } else fallback
    val firstImage = if (mobi && header.u32(108) != 0xffffffffL) header.int(108) else records.size
    val assets = mutableMapOf<Int, String>()
    val chapters = mutableListOf<NovelChapter>()
    val contents = mutableListOf<List<NovelBlock>>()
    parts.forEach { part ->
        val html = String(part, codec)
        // Legacy MOBI page-breaks delimit chapters. KF8 uses the reconstructed spine parts.
        val sections = if (version == 8) listOf(html) else html.split(Regex("<mbp:pagebreak\\b[^>]*>", RegexOption.IGNORE_CASE))
        sections.forEach { section ->
            val doc = Ksoup.parse(section)
            doc.select("img[recindex]").forEach { it.attr("src", "mobi-image:${it.attr("recindex").toIntOrNull() ?: 0}") }
            val blocks = htmlBlocks(doc.body(), "") { src ->
                val index = when {
                    src.startsWith("mobi-image:") -> src.substringAfter(':').toIntOrNull()?.minus(1)
                    src.startsWith("kindle:embed:") -> src.substringAfter("kindle:embed:").substringBefore('?').toIntOrNull(32)?.minus(1)
                    else -> null
                }
                index?.takeIf { firstImage + it in records.indices }?.let {
                    assets.getOrPut(it) { saveAsset(records[firstImage + it]) }
                }
            }
            if (blocks.isNotEmpty()) {
                val label = doc.selectFirst("h1,h2,h3")?.text()?.takeIf { it.isNotBlank() }
                    ?: blocks.firstOrNull { it.text.isNotBlank() }?.text?.take(60) ?: "第 ${chapters.size + 1} 节"
                chapters += NovelChapter("local-${chapters.size}", label)
                contents += blocks
            }
        }
    }
    require(chapters.isNotEmpty()) { "电子书没有可阅读的正文" }
    return NovelDocument(title, chapters, contents)
}

private fun trimRecord(bytes: ByteArray, flags: Int): ByteArray {
    var end = bytes.size
    var entries = flags ushr 1
    while (entries != 0) {
        if (entries and 1 != 0) {
            var value = 0; var shift = 0; var cursor = end
            do {
                require(cursor > 0 && shift <= 28)
                val b = bytes.u8(--cursor)
                value = value or ((b and 127) shl shift); shift += 7
            } while (b and 128 == 0)
            require(value in 1..end); end -= value
        }
        entries = entries ushr 1
    }
    if (flags and 1 != 0) end -= (bytes.u8(end - 1) and 3) + 1
    return bytes.sliceChecked(0, end)
}

internal fun palmDoc(bytes: ByteArray): ByteArray {
    val out = ArrayList<Byte>(); var p = 0
    while (p < bytes.size) {
        val b = bytes.u8(p++)
        when (b) {
            in 1..8 -> { require(p + b <= bytes.size); repeat(b) { out += bytes[p++] } }
            in 0..127 -> out += b.toByte()
            in 192..255 -> { out += 32.toByte(); out += (b xor 128).toByte() }
            else -> {
                val pair = (b shl 8) or bytes.u8(p++)
                val distance = (pair and 0x3fff) ushr 3
                val length = (pair and 7) + 3
                require(distance in 1..out.size) { "MOBI 压缩数据无效" }
                repeat(length) { out += out[out.size - distance] }
            }
        }
        require(out.size <= 65536) { "MOBI 文本记录过大" }
    }
    return out.toByteArray()
}

private class HuffDecoder(records: List<ByteArray>) {
    private data class Code(val length: Int, val terminal: Boolean, val max: Long)
    private data class Phrase(var data: ByteArray, var decoded: Boolean, var busy: Boolean = false)
    private val fast: List<Code>
    private val minimum = LongArray(33)
    private val maximum = LongArray(33)
    private val phrases = mutableListOf<Phrase>()
    init {
        val h = records.first(); require(h.signature(0, "HUFF"))
        val a = h.int(8); val b = h.int(12)
        fast = (0..255).map { i ->
            val v = h.u32(a + i * 4); val length = (v and 31).toInt()
            require(length in 1..32)
            Code(length, v and 128L != 0L, (((v ushr 8) + 1) shl (32 - length)) - 1)
        }
        for (i in 1..32) {
            minimum[i] = h.u32(b + (i - 1) * 8) shl (32 - i)
            maximum[i] = ((h.u32(b + (i - 1) * 8 + 4) + 1) shl (32 - i)) - 1
        }
        for (record in records.drop(1)) {
            require(record.signature(0, "CDIC"))
            val count = record.int(8); val bits = record.int(12)
            require(bits in 0..20 && count <= 1_000_000)
            repeat(minOf(1 shl bits, count - phrases.size)) { i ->
                val p = 16 + record.u16(16 + i * 2); val size = record.u16(p)
                phrases += Phrase(record.sliceChecked(p + 2, p + 2 + (size and 0x7fff)), size and 0x8000 != 0)
            }
        }
    }
    fun decode(bytes: ByteArray, depth: Int = 0): ByteArray {
        require(depth < 64) { "MOBI 字典循环" }
        var bit = 0; val output = ByteArrayOutputStream()
        while (bit < bytes.size * 8) {
            var code = 0L
            repeat(32) { i ->
                val at = bit + i
                code = (code shl 1) or (if (at < bytes.size * 8) ((bytes.u8(at / 8) ushr (7 - at % 8)) and 1).toLong() else 0)
            }
            val entry = fast[(code ushr 24).toInt()]
            var length = entry.length; var max = entry.max
            if (!entry.terminal) {
                while (length <= 32 && code < minimum[length]) length++
                require(length <= 32); max = maximum[length]
            }
            if (bit + length > bytes.size * 8) break
            bit += length
            val index = ((max - code) ushr (32 - length)).toInt()
            require(index in phrases.indices)
            val phrase = phrases[index]
            require(!phrase.busy) { "MOBI 字典循环" }
            if (!phrase.decoded) {
                phrase.busy = true; phrase.data = decode(phrase.data, depth + 1); phrase.decoded = true; phrase.busy = false
            }
            require(output.size() + phrase.data.size <= MAX_NOVEL_TEXT)
            output.write(phrase.data)
        }
        return output.toByteArray()
    }
}

private data class IndexEntry(val name: String, val tags: Map<Int, List<Int>>)
private fun mobiIndex(records: List<ByteArray>, index: Int): List<IndexEntry> {
    val header = records[index]; require(header.signature(0, "INDX"))
    val tagStart = header.int(180).takeIf { it > 0 } ?: header.int(4)
    require(header.signature(tagStart, "TAGX"))
    val controlCount = header.int(tagStart + 8)
    val descriptors = (tagStart + 12 until tagStart + header.int(tagStart + 4) step 4).map { pos ->
        (0..3).map { header.u8(pos + it) }
    }
    return buildList {
        repeat(header.int(24)) { n ->
            val record = records[index + 1 + n]; require(record.signature(0, "INDX"))
            val table = record.int(20); require(record.signature(table, "IDXT"))
            repeat(record.int(24)) { j ->
                val start = record.u16(table + 4 + j * 2)
                val size = record.u8(start)
                val name = record.sliceChecked(start + 1, start + 1 + size).decodeToString()
                val controls = start + 1 + size
                var cursor = controls + controlCount
                fun integer(): Int {
                    var value = 0; var count = 0
                    do {
                        require(count++ < 5)
                        val b = record.u8(cursor++)
                        require(value <= Int.MAX_VALUE ushr 7)
                        value = (value shl 7) or (b and 127)
                    } while (b and 128 == 0)
                    return value
                }
                var control = 0
                val pending = mutableListOf<Triple<Int, Int, Int>>()
                for ((tag, values, mask, end) in descriptors) {
                    if (end == 1) { control++; continue }
                    val value = record.u8(controls + control) and mask
                    if (value == 0) continue
                    if (value == mask && Integer.bitCount(mask) > 1) pending += Triple(tag, -1, integer())
                    else pending += Triple(tag, (if (value == mask) 1 else value ushr Integer.numberOfTrailingZeros(mask)) * values, 0)
                }
                val tags = mutableMapOf<Int, List<Int>>()
                for ((tag, count, length) in pending) {
                    val end = cursor + length
                    tags[tag] = if (count >= 0) List(count) { integer() } else buildList { while (cursor < end) add(integer()); require(cursor == end) }
                }
                add(IndexEntry(name, tags))
            }
        }
    }
}
