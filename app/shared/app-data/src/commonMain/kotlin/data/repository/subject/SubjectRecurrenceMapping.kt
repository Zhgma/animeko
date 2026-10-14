// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.repository.subject
import me.him188.ani.client.models.AniAnimeRecurrence
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds

fun AniAnimeRecurrence.toSubjectRecurrence(): SubjectRecurrence? {
    return SubjectRecurrence(
        Instant.parse(startTime),
        interval = intervalMillis.milliseconds,
    )
}
