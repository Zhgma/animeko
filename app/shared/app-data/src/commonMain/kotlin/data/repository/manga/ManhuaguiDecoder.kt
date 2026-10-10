/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Decodes the site's packed data without evaluating JavaScript or loading remote code. */
internal object ManhuaguiDecoder {
    fun chapter(html: String): JsonObject {
        val match = Regex("""\}\('((?:\\.|[^'\\])*)',(\d+),(\d+),'([^']+)'""").find(html)
            ?: error("漫画柜章节格式有变")
        val (packed, radixText, _, compressed) = match.destructured
        val radix = radixText.toInt().also { require(it in 2..62) }
        val words = decompress(compressed).split('|')
        val unpacked = Regex("[a-zA-Z0-9_]+").replace(packed.replace("\\'", "'").replace("\\\\", "\\")) { token ->
            var index = 0L
            for (c in token.value) {
                val digit = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(c)
                if (digit !in 0 until radix) return@replace token.value
                index = index * radix + digit
                if (index > words.lastIndex) return@replace token.value
            }
            words.getOrNull(index.toInt())?.takeIf { it.isNotEmpty() } ?: token.value
        }
        return Json.parseToJsonElement(unpacked.substringAfter('(').substringBeforeLast(')')
            .substringBefore(").")).jsonObject
    }

    /** LZ-string base64 bit stream, with bounded output and strict truncation checks. */
    fun decompress(input: String): String {
        require(input.length <= 2_000_000) { "漫画柜数据过大" }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
        var position = 32
        var cursor = 0
        var value = alphabet.indexOf(input.getOrNull(cursor) ?: error("空数据"))
        fun readBits(count: Int): Int {
            var result = 0
            repeat(count) { bit ->
                require(cursor <= input.length) { "漫画柜数据不完整" }
                if (value and position != 0) result = result or (1 shl bit)
                position = position shr 1
                if (position == 0) {
                    position = 32
                    cursor++
                    value = input.getOrNull(cursor)?.let(alphabet::indexOf) ?: 0
                }
            }
            return result
        }
        val first = when (readBits(2)) {
            0 -> readBits(8).toChar().toString()
            1 -> readBits(16).toChar().toString()
            2 -> return ""
            else -> error("漫画柜压缩格式有变")
        }
        val dictionary = mutableListOf("", "", "", first)
        var previous = first
        var bits = 3
        var remaining = 4
        val output = StringBuilder(first)
        while (output.length < 4_000_000 && dictionary.size < 1_000_000) {
            var code = readBits(bits)
            if (code == 2) return output.toString()
            if (code == 0 || code == 1) {
                dictionary.add(readBits(if (code == 0) 8 else 16).toChar().toString())
                code = dictionary.lastIndex
                remaining--
            }
            if (remaining == 0) { remaining = 1 shl bits; bits++ }
            val entry = dictionary.getOrNull(code) ?: if (code == dictionary.size) previous + previous[0]
                else error("漫画柜压缩字典无效")
            output.append(entry)
            dictionary.add(previous + entry[0])
            previous = entry
            remaining--
            if (remaining == 0) { remaining = 1 shl bits; bits++ }
        }
        error("漫画柜解压数据过大")
    }
}
