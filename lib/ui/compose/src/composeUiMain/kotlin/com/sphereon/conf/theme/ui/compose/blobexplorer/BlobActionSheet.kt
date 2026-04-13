package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet for blob actions in Compact layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlobActionSheet(
    blob: BlobItem,
    state: BlobExplorerState,
    config: BlobExplorerConfig,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = blob.filename ?: extractFilename(blob.path),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "${formatFileSize(blob.sizeBytes)} ${blob.contentType?.let { "- $it" } ?: ""}",
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            HorizontalDivider()

            config.onViewBlob?.let { onView ->
                TextButton(
                    onClick = { onView(blob); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("View") }
            }

            config.onDownloadBlob?.let { onDownload ->
                TextButton(
                    onClick = { onDownload(blob); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Download") }
            }

            if (!config.readOnly) {
                config.onEditBlob?.let { onEdit ->
                    TextButton(
                        onClick = { onEdit(blob); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Edit") }
                }

                if (state.capabilities.supportsDelete) {
                    TextButton(
                        onClick = { state.deleteBlob(blob); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete") }
                }
            }

            if (state.capabilities.supportsTempUrls) {
                TextButton(
                    onClick = { /* Temp URL generation would be handled by host */ onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create Temporary URL") }
            }
        }
    }
}
