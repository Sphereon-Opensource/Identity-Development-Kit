package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Root composable for the Blob Explorer component.
 *
 * Responsive breakpoints:
 * - Compact (<600dp): Single-pane list, bottom sheet for actions
 * - Medium (600-840dp): Sidebar tree + content area
 * - Expanded (>840dp): Sidebar tree + content area + detail panel
 *
 * @param state State holder created via [rememberBlobExplorerState]
 * @param config Component configuration (readOnly, callbacks, sidebar)
 * @param onUploadClick Callback when the upload button is clicked
 * @param modifier Modifier for the root layout
 */
@Composable
fun BlobExplorer(
    state: BlobExplorerState,
    config: BlobExplorerConfig = BlobExplorerConfig(),
    onUploadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val bg = parseColor(tokens.background)
    val radius = parseDp(tokens.radius, 12.dp)
    var showActionSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<BlobItem?>(null) }

    val sidebarConfig = config.sidebar
    val sidebarHidden = sidebarConfig.defaultMode == SidebarMode.Hidden
    var sidebarOpen by remember { mutableStateOf(sidebarConfig.defaultMode == SidebarMode.Visible) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(radius))
            .background(bg),
    ) {
        val widthDp = maxWidth
        val isCompact = widthDp < 600.dp
        val isMedium = widthDp in 600.dp..840.dp
        val isExpanded = widthDp > 840.dp

        // Auto-collapse sidebar when detail opens on medium screens
        if (sidebarConfig.autoCollapse && state.selectedBlob != null && !isExpanded) {
            LaunchedEffect(state.selectedBlob) {
                sidebarOpen = false
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar with breadcrumbs
            BlobExplorerToolbar(
                state = state,
                config = config,
                onUploadClick = onUploadClick,
            )

            // Main content area
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar (Medium + Expanded, unless hidden)
                if (!isCompact && !sidebarHidden) {
                    BlobTreeView(
                        state = state,
                        isOpen = sidebarOpen,
                        onToggle = { sidebarOpen = !sidebarOpen },
                        visibleFolders = sidebarConfig.visibleFolders,
                    )
                    if (sidebarOpen) {
                        VerticalDivider()
                    }
                }

                // File list (always shown)
                Box(modifier = Modifier.weight(1f)) {
                    BlobFileList(
                        state = state,
                        config = config,
                        showSidebarToggle = !isCompact && !sidebarHidden && !sidebarOpen,
                        onSidebarToggle = { sidebarOpen = true },
                        widthDp = widthDp,
                    )
                }

                // Detail pane (Expanded only)
                if (isExpanded && state.selectedBlob != null) {
                    VerticalDivider()
                    BlobDetailPane(
                        blob = state.selectedBlob!!,
                        state = state,
                        config = config,
                        onRequestDelete = { showDeleteConfirm = it },
                    )
                }
            }
        }

        // Action sheet for compact layout
        if (isCompact && showActionSheet && state.selectedBlob != null) {
            BlobActionSheet(
                blob = state.selectedBlob!!,
                state = state,
                config = config,
                onDismiss = { showActionSheet = false },
            )
        }

        // Delete confirmation dialog
        showDeleteConfirm?.let { blob ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("Delete file") },
                text = { Text("Delete \"${blob.filename ?: extractFilename(blob.path)}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        state.deleteBlob(blob)
                        showDeleteConfirm = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Preview
@Composable
private fun BlobExplorerPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
        }
    }
}
