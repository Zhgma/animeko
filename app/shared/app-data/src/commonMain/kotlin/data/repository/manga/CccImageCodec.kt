/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlin.io.encoding.Base64

internal expect fun mangaSha512(input: ByteArray): ByteArray
internal expect fun decryptMangaAesCbc(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray

/** CCC's anonymous free-reading image format. No account token or paid chapter key is used. */
internal fun decodeCccImage(encodedKey: String, payload: ByteArray): ByteArray {
    val seed = mangaSha512("freeforccc2020reading".encodeToByteArray())
    val keys = decryptMangaAesCbc(seed.copyOfRange(0, 32), seed.copyOfRange(15, 31), Base64.decode(encodedKey))
        .decodeToString().split(':')
    require(keys.size == 2 && keys[0].length == 64 && keys[1].length == 32) { "CCC 图片密钥格式有变" }
    val text = decryptMangaAesCbc(keys[0].hexToByteArray(), keys[1].hexToByteArray(), payload).decodeToString()
    return Base64.decode(text.substringAfter(',')).also { require(it.size in 1..25_000_000) { "CCC 图片大小异常" } }
}
