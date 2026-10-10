/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.models.subject.SubjectRelationGraphMainNode
import me.him188.ani.app.data.models.subject.SubjectRelationGraphBranch
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.ui.subject.details.layout.EditRatingButton
import me.him188.ani.app.ui.rating.EditableRatingActions
import me.him188.ani.app.ui.subject.details.components.RelatedSubjectCard
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.SectionHeaderRelationGraphButton
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

@Composable
internal fun MangaRatingEntry(state: MangaDetailsUiState, actions: EditableRatingActions) {
    EditRatingButton(state.rating.selfRatingInfo.score, actions::requestEditRating,
        Modifier.testTag("manga-edit-rating"), enabled = !state.rating.isUpdating)
}

@Composable
internal fun MangaRelatedSection(state: MangaDetailsUiState, onGraph: () -> Unit, onRetry: () -> Unit, onSubject: (Int) -> Unit) {
    if (state.relations.isEmpty() && state.relationsError == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("关联作品") {
            if (state.relations.isNotEmpty()) SectionHeaderRelationGraphButton(onGraph, Modifier.testTag("manga-relation-graph"))
        }
        state.relationsError?.let { MangaInlineError(it, onRetry) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.relations, key = { it.id }) { related ->
                Column(Modifier.width(112.dp)) {
                    RelatedSubjectCard(RelatedSubjectInfo(related.id, null, related.originalTitle, related.title, related.cover),
                        onClick = { onSubject(related.id) })
                    Text(related.relation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Bangumi's direct relations form branches; no transitive storyline is inferred. */
internal fun mangaRelationGraph(manga: MangaCollection, state: MangaDetailsUiState): SubjectRelationGraph {
    fun subject(id: Int, name: String, title: String, image: String) = SubjectRelationGraphSubject(
        id, name, title, image, PackedDate.Invalid, null, 0, UnifiedCollectionType.NOT_COLLECTED)
    val root = subject(manga.subjectId, manga.originalTitle, manga.title, manga.coverUrl)
    return SubjectRelationGraph(manga.subjectId, listOf(SubjectRelationGraphMainNode(root, false,
        state.relations.map { related -> SubjectRelationGraphBranch(
            subject(related.id, related.originalTitle, related.title, related.cover), null, related.relation)
        })), truncated = false)
}
