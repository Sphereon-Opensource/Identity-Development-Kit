/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.compose.LocalThemeTokens
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class BlobExplorerTest {
    private fun testDataSource(
        items: List<BlobItem> = emptyList(),
        folders: List<String> = emptyList(),
    ): BlobExplorerDataSource =
        object : BlobExplorerDataSource {
            override val capabilities = BlobExplorerCapabilities()

            override suspend fun listBlobs(
                prefix: String?,
                pageToken: String?,
            ) = BlobListResult(
                items = items,
                commonPrefixes = folders,
                nextPageToken = null,
            )

            override suspend fun deleteBlob(path: String) = true

            override suspend fun copyBlob(
                source: String,
                destination: String,
            ) = BlobItem(path = destination)

            override suspend fun moveBlob(
                source: String,
                destination: String,
            ) = BlobItem(path = destination)

            override suspend fun uploadBlob(
                path: String,
                data: ByteArray,
                contentType: String?,
            ) = BlobItem(path = path, sizeBytes = data.size.toLong(), contentType = contentType)

            override suspend fun createTempUrl(path: String) = "https://example.com/temp/$path"
        }

    @Test
    fun emptyExplorerShowsEmptyState() =
        runComposeUiTest {
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Box(Modifier.size(1200.dp, 800.dp)) {
                            BlobExplorer(
                                state = rememberBlobExplorerState(dataSource = testDataSource()),
                                config = BlobExplorerConfig(),
                            )
                        }
                    }
                }
            }
            onNodeWithText("No files found").assertIsDisplayed()
            onAllNodesWithText("All files")[0].assertIsDisplayed()
        }

    @Test
    fun explorerRendersBreadcrumb() =
        runComposeUiTest {
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Box(Modifier.size(1200.dp, 800.dp)) {
                            BlobExplorer(
                                state = rememberBlobExplorerState(dataSource = testDataSource()),
                            )
                        }
                    }
                }
            }
            onAllNodesWithText("All files")[0].assertIsDisplayed()
        }

    @Test
    fun explorerRendersWithCustomTokens() =
        runComposeUiTest {
            val customTokens =
                mapOf(
                    "comp.blobExplorer.background" to "#FF0000",
                    "comp.blobExplorer.item.foreground" to "#00FF00",
                )
            setContent {
                CompositionLocalProvider(
                    LocalThemeTokens provides customTokens,
                ) {
                    ComponentTheme {
                        MaterialTheme {
                            Box(Modifier.size(1200.dp, 800.dp)) {
                                BlobExplorer(
                                    state = rememberBlobExplorerState(dataSource = testDataSource()),
                                )
                            }
                        }
                    }
                }
            }
            onAllNodesWithText("All files")[0].assertIsDisplayed()
        }

    @Test
    fun explorerFallsBackToDefaults() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalThemeTokens provides emptyMap(),
                ) {
                    ComponentTheme {
                        MaterialTheme {
                            Box(Modifier.size(1200.dp, 800.dp)) {
                                BlobExplorer(
                                    state = rememberBlobExplorerState(dataSource = testDataSource()),
                                )
                            }
                        }
                    }
                }
            }
            onAllNodesWithText("All files")[0].assertIsDisplayed()
            onNodeWithText("No files found").assertIsDisplayed()
        }

    @Test
    fun formatFileSizeFormatsCorrectly() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", formatFileSize((2.0 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun extractFilenameHandlesPaths() {
        assertEquals("file.txt", extractFilename("folder/file.txt"))
        assertEquals("file.txt", extractFilename("a/b/c/file.txt"))
        assertEquals("folder", extractFilename("folder/"))
        assertEquals("file", extractFilename("file"))
    }

    @Test
    fun buildBreadcrumbsFromPrefix() {
        val crumbs = buildBreadcrumbs("a/b/c/")
        assertEquals(3, crumbs.size)
        assertEquals("a" to "a/", crumbs[0])
        assertEquals("b" to "a/b/", crumbs[1])
        assertEquals("c" to "a/b/c/", crumbs[2])
    }

    @Test
    fun buildBreadcrumbsEmptyForNull() {
        assertEquals(emptyList(), buildBreadcrumbs(null))
        assertEquals(emptyList(), buildBreadcrumbs(""))
    }

    @Test
    fun setSortTogglesSortDirection() {
        val scope = TestScope()
        val state = BlobExplorerState(testDataSource(), scope)

        assertEquals(SortField.Name, state.sortField)
        assertEquals(SortDirection.Ascending, state.sortDirection)

        state.setSort(SortField.Name)
        assertEquals(SortField.Name, state.sortField)
        assertEquals(SortDirection.Descending, state.sortDirection)

        state.setSort(SortField.Name)
        assertEquals(SortDirection.Ascending, state.sortDirection)
    }

    @Test
    fun setSortChangesField() {
        val scope = TestScope()
        val state = BlobExplorerState(testDataSource(), scope)

        state.setSort(SortField.Size)
        assertEquals(SortField.Size, state.sortField)
        assertEquals(SortDirection.Ascending, state.sortDirection)

        state.setSort(SortField.LastModified)
        assertEquals(SortField.LastModified, state.sortField)
        assertEquals(SortDirection.Ascending, state.sortDirection)
    }

    @Test
    fun navigateToChangesPrefix() {
        val scope = TestScope()
        val state = BlobExplorerState(testDataSource(), scope)

        assertNull(state.currentPrefix)

        state.navigateTo("docs/")
        assertEquals("docs/", state.currentPrefix)

        state.navigateTo(null)
        assertNull(state.currentPrefix)
    }

    @Test
    fun navigateToClearsSelection() {
        val scope = TestScope()
        val state = BlobExplorerState(testDataSource(), scope)

        val blob = BlobItem(path = "test.txt", sizeBytes = 100)
        state.selectBlob(blob)
        assertNotNull(state.selectedBlob)

        state.navigateTo("other/")
        assertNull(state.selectedBlob)
    }

    @Test
    fun dismissErrorClearsError() {
        val scope = TestScope()
        val ds =
            object : BlobExplorerDataSource {
                override val capabilities = BlobExplorerCapabilities()

                override suspend fun listBlobs(
                    prefix: String?,
                    pageToken: String?,
                ): BlobListResult = throw RuntimeException("Test error")

                override suspend fun deleteBlob(path: String) = false

                override suspend fun copyBlob(
                    source: String,
                    destination: String,
                ) = BlobItem(path = destination)

                override suspend fun moveBlob(
                    source: String,
                    destination: String,
                ) = BlobItem(path = destination)

                override suspend fun uploadBlob(
                    path: String,
                    data: ByteArray,
                    contentType: String?,
                ) = BlobItem(path = path)

                override suspend fun createTempUrl(path: String) = ""
            }
        val state = BlobExplorerState(ds, scope)
        state.navigateTo(null)
        scope.testScheduler.advanceUntilIdle()

        assertEquals("Test error", state.error)

        state.dismissError()
        assertNull(state.error)
    }

    @Test
    fun formatIsoDateFormatsCorrectly() {
        assertEquals("Mar 17, 2026", formatIsoDate("2026-03-17T10:30:00Z"))
        assertEquals("Jan 1, 2024", formatIsoDate("2024-01-01"))
        assertEquals("Dec 25, 2025", formatIsoDate("2025-12-25T23:59:59+01:00"))
        assertEquals("", formatIsoDate(null))
        assertEquals("", formatIsoDate(""))
    }

    @Test
    fun fileSelectionOnClick() =
        runComposeUiTest {
            val items =
                listOf(
                    BlobItem(path = "readme.md", sizeBytes = 1024, contentType = "text/markdown"),
                    BlobItem(path = "photo.jpg", sizeBytes = 2048),
                )
            val ds = testDataSource(items = items)
            lateinit var state: BlobExplorerState
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Box(Modifier.size(1200.dp, 800.dp)) {
                            state = rememberBlobExplorerState(dataSource = ds)
                            BlobExplorer(state = state, config = BlobExplorerConfig())
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("readme.md").performClick()
            waitForIdle()
            assertNotNull(state.selectedBlob)
            assertEquals("readme.md", state.selectedBlob?.path)
        }

    @Test
    fun folderNavigationOnClick() =
        runComposeUiTest {
            val ds = testDataSource(folders = listOf("docs/"))
            lateinit var state: BlobExplorerState
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Box(Modifier.size(1200.dp, 800.dp)) {
                            state = rememberBlobExplorerState(dataSource = ds)
                            BlobExplorer(state = state, config = BlobExplorerConfig())
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Folder: docs").performClick()
            waitForIdle()
            assertEquals("docs/", state.currentPrefix)
        }

    @Test
    fun sortColumnClick() =
        runComposeUiTest {
            val items =
                listOf(
                    BlobItem(path = "a.txt", sizeBytes = 200),
                    BlobItem(path = "b.txt", sizeBytes = 100),
                )
            val ds = testDataSource(items = items)
            lateinit var state: BlobExplorerState
            setContent {
                ComponentTheme {
                    MaterialTheme {
                        Box(Modifier.size(1200.dp, 800.dp)) {
                            state = rememberBlobExplorerState(dataSource = ds)
                            BlobExplorer(state = state, config = BlobExplorerConfig())
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Size").performClick()
            waitForIdle()
            assertEquals(SortField.Size, state.sortField)
            assertEquals(SortDirection.Ascending, state.sortDirection)
            onNodeWithText("Size").performClick()
            waitForIdle()
            assertEquals(SortField.Size, state.sortField)
            assertEquals(SortDirection.Descending, state.sortDirection)
        }

    @Test
    fun deleteUpdatesState() {
        val scope = TestScope()
        val item = BlobItem(path = "toDelete.txt", sizeBytes = 512)
        val ds = testDataSource(items = listOf(item))
        val state = BlobExplorerState(ds, scope)

        state.navigateTo(null)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, state.items.size)

        state.deleteBlob(item)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(0, state.items.size)
    }

    @Test
    fun uploadAddsItemToState() {
        val scope = TestScope()
        val ds = testDataSource()
        val state = BlobExplorerState(ds, scope)

        state.navigateTo(null)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(0, state.items.size)

        state.uploadBlob("test.txt", byteArrayOf(1, 2, 3), "text/plain")
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, state.items.size)
        assertEquals("test.txt", state.items[0].path)
        assertEquals(3L, state.items[0].sizeBytes)
        assertEquals("text/plain", state.items[0].contentType)
    }

    @Test
    fun errorOnFailedListShowsErrorState() {
        val scope = TestScope()
        val ds =
            object : BlobExplorerDataSource {
                override val capabilities = BlobExplorerCapabilities()

                override suspend fun listBlobs(
                    prefix: String?,
                    pageToken: String?,
                ): BlobListResult = throw RuntimeException("list failed")

                override suspend fun deleteBlob(path: String) = false

                override suspend fun copyBlob(
                    source: String,
                    destination: String,
                ) = BlobItem(path = destination)

                override suspend fun moveBlob(
                    source: String,
                    destination: String,
                ) = BlobItem(path = destination)

                override suspend fun uploadBlob(
                    path: String,
                    data: ByteArray,
                    contentType: String?,
                ) = BlobItem(path = path)

                override suspend fun createTempUrl(path: String) = ""
            }
        val state = BlobExplorerState(ds, scope)

        state.navigateTo(null)
        scope.testScheduler.advanceUntilIdle()
        assertEquals("list failed", state.error)

        state.dismissError()
        assertNull(state.error)
    }
}
