package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens

/**
 * A single row in the blob file list with columns matching the header.
 */
@Composable
fun BlobListItem(
    item: BlobItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    showSize: Boolean = true,
    showModified: Boolean = true,
    showCreated: Boolean = true,
    showType: Boolean = true,
    config: BlobExplorerConfig? = null,
    state: BlobExplorerState? = null,
    formatDate: (String?) -> String = ::formatIsoDate,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val bg = if (isSelected) parseColor(tokens.itemBackgroundSelected) else parseColor(tokens.itemBackground)
    val fg = parseColor(tokens.itemForeground)
    val fgSecondary = parseColor(tokens.itemForegroundSecondary)
    val primaryColor = parseColor(tokens.breadcrumbForegroundActive)
    val itemRadius = parseDp(tokens.itemRadius, 8.dp)

    val displayName = item.filename ?: extractFilename(item.path)
    val itemDescription = if (item.isFolder) "Folder: $displayName" else "File: $displayName, ${formatFileSize(item.sizeBytes)}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(itemRadius))
            .background(bg)
            .clickable { onClick() }
            .semantics { contentDescription = itemDescription }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (item.isFolder) {
                FolderIcon(primaryColor, size = 22.dp)
            } else {
                ContentTypeIcon(item.contentType, primaryColor, size = 22.dp)
            }
        }
        Spacer(Modifier.width(8.dp))

        // Name
        Text(
            text = item.filename ?: extractFilename(item.path),
            color = fg,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Size
        if (showSize && !item.isFolder) {
            Text(
                text = formatFileSize(item.sizeBytes),
                color = fgSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(80.dp).padding(end = 12.dp),
                maxLines = 1,
            )
        } else if (showSize) {
            Spacer(Modifier.width(80.dp))
        }

        // Modified
        if (showModified) {
            Text(
                text = formatDate(item.lastModified),
                color = fgSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(100.dp),
                maxLines = 1,
            )
        }

        // Created
        if (showCreated) {
            Text(
                text = formatDate(item.createdAt),
                color = fgSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(100.dp),
                maxLines = 1,
            )
        }

        // Type
        if (showType) {
            Text(
                text = if (item.isFolder) "Folder" else (item.contentType ?: ""),
                color = fgSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(100.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Actions spacer
        Spacer(Modifier.width(72.dp))
    }
}
