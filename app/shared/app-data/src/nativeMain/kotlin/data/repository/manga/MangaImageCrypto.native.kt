/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
@file:OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class)
package me.him188.ani.app.data.repository.manga

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar

internal actual fun mangaSha512(input: ByteArray): ByteArray = UByteArray(64).also { digest ->
    digest.usePinned { CC_SHA512(input.refTo(0), input.size.convert(), it.addressOf(0)) }
}.asByteArray()
internal actual fun decryptMangaAesCbc(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray = memScoped {
    require(key.size in listOf(16, 24, 32) && iv.size == 16 && input.isNotEmpty())
    val output = ByteArray(input.size + 16)
    val written = alloc<size_tVar>()
    val status = CCCrypt(kCCDecrypt, kCCAlgorithmAES, kCCOptionPKCS7Padding,
        key.refTo(0), key.size.convert(), iv.refTo(0), input.refTo(0), input.size.convert(),
        output.refTo(0), output.size.convert(), written.ptr)
    require(status == kCCSuccess) { "CCC 图片解码失败" }
    output.copyOf(written.value.toInt())
}
