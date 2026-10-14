/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.collection.components.createTestEditableSubjectCollectionTypeState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.onNodeWithText

@OptIn(TestOnly::class)
class SubjectCollectionItemTest {
    @Test
    fun `edit collection menu stays open when action state is replaced`() = runAniComposeUiTest {
        val recompositionTrigger = mutableIntStateOf(0)

        setContent {
            ProvideCompositionLocalsForPreview {
                val collection = TestSubjectCollections.first()
                val collectionType = remember { MutableStateFlow(collection.collectionType) }
                val backgroundScope = rememberCoroutineScope()
                recompositionTrigger.intValue
                val editableState = createTestEditableSubjectCollectionTypeState(
                    collectionType,
                    backgroundScope,
                )

                SubjectCollectionItem(
                    item = collection,
                    editableSubjectCollectionTypeState = editableState,
                    onClick = {},
                    onShowEpisodeList = {},
                    playButton = {},
                )
            }
        }

        onNodeWithTag(SubjectCollectionItemTestTags.MoreButton).performClick()
        onNodeWithTag(SubjectCollectionItemTestTags.EditCollectionTypeMenu).assertIsDisplayed()

        runOnIdle { recompositionTrigger.intValue++ }

        onNodeWithTag(SubjectCollectionItemTestTags.EditCollectionTypeMenu).assertIsDisplayed()
    }
    @Test
    fun `shared card episode and play buttons do not open subject details`() = runAniComposeUiTest {
        var details=0
        var episodes=0
        var plays=0
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectCollectionCard({details++},cover={},content={
                    SubjectCollectionItemLayout("Long collection title",airingLabel={Text("On air")},
                        onShowEpisodeList={episodes++},playButton={Button({plays++}) {Text("Play test episode")}},
                        menu={},onShowMenu={})
                })
            }
        }
        onNodeWithText("选集").performClick()
        onNodeWithText("Play test episode").performClick()
        runOnIdle {
            assertEquals(1,episodes)
            assertEquals(1,plays)
            assertEquals(0,details)
        }
    }
}
