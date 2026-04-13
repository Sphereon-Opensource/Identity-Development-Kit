package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens

/**
 * Breadcrumb navigation showing the current path as clickable segments.
 */
@Composable
fun BlobBreadcrumbs(
    currentPrefix: String?,
    onNavigate: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val breadcrumbs = buildBreadcrumbs(currentPrefix)
    val fg = parseColor(tokens.breadcrumbForeground)
    val fgActive = parseColor(tokens.breadcrumbForegroundActive)
    val separatorColor = parseColor(tokens.breadcrumbSeparator)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "All files",
            color = if (breadcrumbs.isEmpty()) fgActive else fg,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable { onNavigate(null) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        breadcrumbs.forEachIndexed { index, (label, prefix) ->
            Text(
                text = "/",
                color = separatorColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            val isLast = index == breadcrumbs.lastIndex
            Text(
                text = label,
                color = if (isLast) fgActive else fg,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { onNavigate(prefix) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
