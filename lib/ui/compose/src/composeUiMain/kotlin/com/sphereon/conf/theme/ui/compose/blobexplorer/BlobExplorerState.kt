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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class SortField { Name, Size, LastModified, ContentType }

enum class SortDirection { Ascending, Descending }

/**
 * State holder for BlobExplorer. Create via [rememberBlobExplorerState].
 */
@Suppress("TooGenericExceptionCaught")
@Stable
class BlobExplorerState(
    private val dataSource: BlobExplorerDataSource,
    private val scope: CoroutineScope,
) {
    var currentPrefix: String? by mutableStateOf(null)
        private set
    var selectedBlob: BlobItem? by mutableStateOf(null)
        private set
    val items = mutableStateListOf<BlobItem>()
    val folders = mutableStateListOf<String>()
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var hasMore: Boolean by mutableStateOf(false)
        private set
    var nextPageToken: String? by mutableStateOf(null)
        private set
    var sortField: SortField by mutableStateOf(SortField.Name)
        private set
    var sortDirection: SortDirection by mutableStateOf(SortDirection.Ascending)
        private set
    val expandedTreeNodes = mutableStateListOf<String>()
    var error: String? by mutableStateOf(null)
        private set

    val capabilities: BlobExplorerCapabilities get() = dataSource.capabilities

    fun navigateTo(prefix: String?) {
        currentPrefix = prefix
        selectedBlob = null
        nextPageToken = null
        items.clear()
        folders.clear()
        loadItems()
    }

    fun selectBlob(blob: BlobItem?) {
        selectedBlob = blob
    }

    fun loadItems() {
        if (isLoading) {
            return
        }
        isLoading = true
        error = null
        scope.launch {
            try {
                val result = dataSource.listBlobs(currentPrefix, nextPageToken)
                if (nextPageToken == null) {
                    items.clear()
                    folders.clear()
                }
                items.addAll(result.items)
                folders.addAll(result.commonPrefixes)
                nextPageToken = result.nextPageToken
                hasMore = result.hasMore
                sortItems()
            } catch (expected: Exception) {
                expected.printStackTrace()
                error = expected.message ?: "Failed to load items"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (hasMore && !isLoading) {
            loadItems()
        }
    }

    fun setSort(field: SortField) {
        if (sortField == field) {
            sortDirection =
                if (sortDirection == SortDirection.Ascending) {
                    SortDirection.Descending
                } else {
                    SortDirection.Ascending
                }
        } else {
            sortField = field
            sortDirection = SortDirection.Ascending
        }
        sortItems()
    }

    fun toggleTreeNode(prefix: String) {
        if (prefix in expandedTreeNodes) {
            expandedTreeNodes.remove(prefix)
        } else {
            expandedTreeNodes.add(prefix)
        }
    }

    fun deleteBlob(blob: BlobItem) {
        scope.launch {
            try {
                val success = dataSource.deleteBlob(blob.path)
                if (success) {
                    val idx = items.indexOf(blob)
                    items.remove(blob)
                    if (selectedBlob == blob) {
                        // Focus the next item in the list, or the previous if at the end
                        selectedBlob =
                            when {
                                items.isEmpty() -> null
                                idx < items.size -> items[idx]
                                else -> items.lastOrNull()
                            }
                    }
                }
            } catch (expected: Exception) {
                expected.printStackTrace()
                error = expected.message ?: "Failed to delete"
            }
        }
    }

    fun uploadBlob(
        path: String,
        data: ByteArray,
        contentType: String?,
    ) {
        scope.launch {
            try {
                val created = dataSource.uploadBlob(path, data, contentType)
                items.add(created)
                sortItems()
            } catch (expected: Exception) {
                expected.printStackTrace()
                error = expected.message ?: "Failed to upload"
            }
        }
    }

    fun copyBlob(
        source: String,
        destination: String,
    ) {
        scope.launch {
            try {
                val copied = dataSource.copyBlob(source, destination)
                items.add(copied)
                sortItems()
            } catch (expected: Exception) {
                expected.printStackTrace()
                error = expected.message ?: "Failed to copy"
            }
        }
    }

    fun moveBlob(
        source: String,
        destination: String,
    ) {
        scope.launch {
            try {
                val moved = dataSource.moveBlob(source, destination)
                items.removeAll { it.path == source }
                items.add(moved)
                sortItems()
                if (selectedBlob?.path == source) {
                    selectedBlob = moved
                }
            } catch (expected: Exception) {
                expected.printStackTrace()
                error = expected.message ?: "Failed to move"
            }
        }
    }

    fun dismissError() {
        error = null
    }

    private fun sortItems() {
        val sorted =
            items.sortedWith(
                compareBy<BlobItem> { !it.isFolder }.then(
                    when (sortField) {
                        SortField.Name -> compareBy { extractFilename(it.path).lowercase() }
                        SortField.Size -> compareBy { it.sizeBytes }
                        SortField.LastModified -> compareBy { it.lastModified }
                        SortField.ContentType -> compareBy { it.contentType ?: "" }
                    },
                ),
            )
        val finalSorted =
            if (sortDirection == SortDirection.Descending) {
                sorted.reversed()
            } else {
                sorted
            }
        items.clear()
        items.addAll(finalSorted)
    }
}

/**
 * Creates and remembers a [BlobExplorerState].
 * Triggers initial load automatically.
 */
@Composable
fun rememberBlobExplorerState(
    dataSource: BlobExplorerDataSource,
    scope: CoroutineScope = androidx.compose.runtime.rememberCoroutineScope(),
    initialPrefix: String? = null,
): BlobExplorerState =
    remember(dataSource) {
        BlobExplorerState(dataSource, scope).also {
            it.navigateTo(initialPrefix)
        }
    }
