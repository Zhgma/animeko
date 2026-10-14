/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 每个 缓存引擎 使用独一无二的 key，存储于 持久化记录 用于区分不同的引擎创建的 缓存.
 */
@Serializable
@JvmInline
value class MediaCacheEngineKey(val key: String) {
    /**
     * 云盘引擎按需从服务商取流, 没有磁力链解析、节点和上传, 展示层据此区分本地 BT.
     */
    val isCloud: Boolean get() = this == PikPak

    companion object {
        val Anitorrent = MediaCacheEngineKey("anitorrent")
        val PikPak = MediaCacheEngineKey("pikpak")
        val WebM3u = MediaCacheEngineKey("web-m3u")
    }
}