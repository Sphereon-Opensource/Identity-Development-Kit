package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens

/**
 * Scrollable list of blob items with column headers, sorting, and pagination.
 */
@Composable
fun BlobFileList(
    state: BlobExplorerState,
    config: BlobExplorerConfig,
    showSidebarToggle: Boolean = false,
    onSidebarToggle: (() -> Unit)? = null,
    widthDp: Dp = 1000.dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val emptyFg = parseColor(tokens.emptyForeground)
    val emptyIcon = parseColor(tokens.emptyIconColor)
    val primaryColor = parseColor(tokens.breadcrumbForegroundActive)
    val secondaryFg = parseColor(tokens.itemForegroundSecondary)
    val listState = rememberLazyListState()

    val showModified = widthDp > 800.dp
    val showCreated = widthDp > 900.dp
    val showType = widthDp > 1000.dp
    val showSize = widthDp > 650.dp

    // Auto-load more when reaching the bottom
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= totalItems - 2
        }.collect { nearEnd ->
            if (nearEnd && state.hasMore && !state.isLoading) {
                state.loadMore()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sidebar toggle or icon spacer
            Box(modifier = Modifier.width(36.dp)) {
                if (showSidebarToggle && onSidebarToggle != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = "Show folder sidebar"; role = Role.Button }
                            .clickable { onSidebarToggle() }
                            .padding(6.dp),
                    ) {
                        SidebarIcon(color = secondaryFg, size = 16.dp)
                    }
                }
            }
            SortHeaderButton("Name", SortField.Name, state, Modifier.weight(1f), secondaryFg, primaryColor)
            if (showSize) SortHeaderButton("Size", SortField.Size, state, Modifier.width(80.dp), secondaryFg, primaryColor)
            if (showModified) SortHeaderButton("Modified", SortField.LastModified, state, Modifier.width(100.dp), secondaryFg, primaryColor)
            if (showCreated) Text("Created", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = secondaryFg, modifier = Modifier.width(100.dp), letterSpacing = 0.5.sp)
            if (showType) SortHeaderButton("Type", SortField.ContentType, state, Modifier.width(100.dp), secondaryFg, primaryColor)
            Spacer(Modifier.width(72.dp)) // Actions column
        }
        HorizontalDivider()

        if (state.items.isEmpty() && state.folders.isEmpty() && !state.isLoading) {
            // Empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FolderIcon(emptyIcon, size = 56.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No files found",
                        color = emptyFg,
                        fontSize = 14.sp,
                    )
                }
            }
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(4.dp),
        ) {
            // Folder entries
            items(state.folders, key = { "folder:$it" }) { folder ->
                val folderItem = BlobItem(path = folder, isFolder = true)
                BlobListItem(
                    item = folderItem,
                    isSelected = false,
                    onClick = { state.navigateTo(folder) },
                    showSize = showSize,
                    showModified = showModified,
                    showCreated = showCreated,
                    showType = showType,
                    formatDate = config.formatDate,
                )
            }

            // File entries
            items(state.items, key = { "file:${it.path}" }) { item ->
                BlobListItem(
                    item = item,
                    isSelected = state.selectedBlob == item,
                    onClick = {
                        if (item.isFolder) {
                            state.navigateTo(item.path)
                        } else {
                            state.selectBlob(item)
                        }
                    },
                    showSize = showSize,
                    showModified = showModified,
                    showCreated = showCreated,
                    showType = showType,
                    formatDate = config.formatDate,
                    config = config,
                    state = state,
                )
            }

            // Loading indicator
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Load more button (fallback for when auto-scroll doesn't trigger)
            if (state.hasMore && !state.isLoading) {
                item(key = "load-more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = { state.loadMore() }) {
                            Text("Load more")
                        }
                    }
                }
            }

            // Error state
            if (state.error != null) {
                item(key = "error") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error ?: "",
                            color = androidx.compose.ui.graphics.Color.Red,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { state.dismissError() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortHeaderButton(
    label: String,
    field: SortField,
    state: BlobExplorerState,
    modifier: Modifier,
    inactiveColor: androidx.compose.ui.graphics.Color,
    activeColor: androidx.compose.ui.graphics.Color,
) {
    val isActive = state.sortField == field
    val color = if (isActive) activeColor else inactiveColor

    Row(
        modifier = modifier.clickable { state.setSort(field) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isActive) {
            SortArrow(ascending = state.sortDirection == SortDirection.Ascending, color = color)
        }
    }
}
