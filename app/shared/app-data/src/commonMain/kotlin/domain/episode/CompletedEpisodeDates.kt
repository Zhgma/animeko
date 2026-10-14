// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.domain.episode

import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.minus
import kotlin.time.Duration.Companion.days

fun areEpisodeDatesCompleted(dates: Sequence<PackedDate>, now: PackedDate = PackedDate.now()): Boolean {
    val maxAirDate = dates.filter { it.isValid }.maxOrNull()
    return maxAirDate != null && now - maxAirDate >= 365.days
}
