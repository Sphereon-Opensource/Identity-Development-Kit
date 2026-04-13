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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens

/**
 * Collapsible sidebar tree showing folder hierarchy.
 */
@Composable
fun BlobTreeView(
    state: BlobExplorerState,
    isOpen: Boolean,
    onToggle: () -> Unit,
    visibleFolders: List<String>? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val bg = parseColor(tokens.sidebarBackground)
    val sidebarWidth = parseDp(tokens.sidebarWidth, 200.dp)
    val secondaryFg = parseColor(tokens.itemForegroundSecondary)

    AnimatedVisibility(
        visible = isOpen,
        enter = expandHorizontally(expandFrom = Alignment.Start),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start),
    ) {
        Column(
            modifier =
                modifier
                    .width(sidebarWidth)
                    .fillMaxHeight()
                    .background(bg),
        ) {
            // Sidebar header with collapse toggle — aligned with file list header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .semantics {
                                contentDescription = "Hide folder sidebar"
                                role = Role.Button
                            }.clickable { onToggle() }
                            .padding(6.dp),
                ) {
                    SidebarIcon(color = secondaryFg, size = 16.dp)
                }
            }
            HorizontalDivider()

            // Tree content
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .semantics { contentDescription = "Folder tree" }
                        .padding(8.dp),
            ) {
                BlobTreeNode(
                    label = "All files",
                    prefix = null,
                    depth = 0,
                    state = state,
                    visibleFolders = visibleFolders,
                )
            }
        }
    }
}

@Composable
private fun BlobTreeNode(
    label: String,
    prefix: String?,
    depth: Int,
    state: BlobExplorerState,
    visibleFolders: List<String>? = null,
) {
    val tokens = LocalBlobExplorerTokens.current
    val isCurrentPrefix = state.currentPrefix == prefix
    val isExpanded = prefix == null || prefix in state.expandedTreeNodes
    val fg =
        if (isCurrentPrefix) {
            parseColor(tokens.breadcrumbForegroundActive)
        } else {
            parseColor(tokens.itemForeground)
        }
    val primaryColor = parseColor(tokens.breadcrumbForegroundActive)
    val itemBg =
        if (isCurrentPrefix) {
            parseColor(tokens.itemBackgroundSelected)
        } else {
            parseColor(tokens.itemBackground)
        }

    val nodeDescription =
        if (isExpanded) {
            "Folder: $label, expanded"
        } else {
            "Folder: $label, collapsed"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(itemBg)
                .clickable {
                    state.navigateTo(prefix)
                    if (prefix != null) {
                        state.toggleTreeNode(prefix)
                    }
                }.semantics { contentDescription = nodeDescription }
                .padding(start = (depth * 16).dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text =
                if (isExpanded) {
                    "\u25BE"
                } else {
                    "\u25B8"
                },
            color = fg,
            fontSize = 10.sp,
        )
        Box(modifier = Modifier.size(16.dp)) {
            FolderIcon(primaryColor, size = 16.dp)
        }
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    // Render children if expanded
    if (isExpanded) {
        var childFolders =
            state.folders.filter { folder ->
                val isChild =
                    if (prefix == null) {
                        !folder.trimEnd('/').contains('/')
                    } else {
                        folder.startsWith(prefix) && folder != prefix &&
                            folder.removePrefix(prefix).trimEnd('/').count { it == '/' } == 0
                    }
                isChild
            }
        // Filter to visible folders if configured
        if (visibleFolders != null) {
            childFolders =
                childFolders.filter { f ->
                    visibleFolders.any { vf -> f.startsWith(vf) || vf.startsWith(f) }
                }
        }
        childFolders.forEach { folder ->
            BlobTreeNode(
                label = extractFilename(folder.trimEnd('/')),
                prefix = folder,
                depth = depth + 1,
                state = state,
                visibleFolders = visibleFolders,
            )
        }
    }
}
