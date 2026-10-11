/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.domain.search

enum class SearchSort {
    MATCH,

    /**
     * 排名
     */
    RANK,

    /**
     * 收藏人数
     */
    COLLECTION,

    /**
     * 发布日期
     */
    DATE,
}

enum class SubjectType {
    ANIME,
    BOOK,

    /*
    bangumi supports
            条目类型
            - `1` 为 书籍
            - `2` 为 动画
            - `3` 为 音乐
            - `4` 为 游戏
            - `6` 为 三次元

            没有 `5`
     */
}

