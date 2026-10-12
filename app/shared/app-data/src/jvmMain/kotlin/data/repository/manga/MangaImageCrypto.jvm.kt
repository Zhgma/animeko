/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun mangaSha512(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(input)
internal actual fun decryptMangaAesCbc(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray =
    Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        doFinal(input)
    }
