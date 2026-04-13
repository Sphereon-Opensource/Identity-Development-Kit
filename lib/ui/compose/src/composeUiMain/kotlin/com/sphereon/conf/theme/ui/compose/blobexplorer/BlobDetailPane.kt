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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalButtonTokens

/**
 * Detail pane showing metadata and themed action buttons for the selected blob.
 */
@Suppress("MagicNumber")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlobDetailPane(
    blob: BlobItem,
    state: BlobExplorerState,
    config: BlobExplorerConfig,
    onRequestDelete: ((BlobItem) -> Unit)? = null,
    formatDate: (String?) -> String = config.formatDate,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val buttonTokens = LocalButtonTokens.current
    val bg = parseColor(tokens.detailBackground)
    val fg = parseColor(tokens.itemForeground)
    val fgSecondary = parseColor(tokens.itemForegroundSecondary)
    val detailWidth = parseDp(tokens.detailWidth, 300.dp)
    val primaryBg = parseColor(buttonTokens.primaryBackground)
    val primaryFg = parseColor(buttonTokens.primaryForeground)
    val secondaryBg = parseColor(buttonTokens.secondaryBackground)
    val secondaryFg = parseColor(buttonTokens.secondaryForeground)
    val buttonRadius = parseDp(buttonTokens.primaryRadius, 12.dp)

    Column(
        modifier =
            modifier
                .width(detailWidth)
                .fillMaxHeight()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header
        Text(
            text = blob.filename ?: extractFilename(blob.path),
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        HorizontalDivider()

        // Metadata rows
        DetailRow("Path", blob.path, fgSecondary, fg)
        DetailRow("Size", formatFileSize(blob.sizeBytes), fgSecondary, fg)
        if (blob.contentType != null) {
            DetailRow("Type", blob.contentType!!, fgSecondary, fg)
        }
        if (blob.etag != null) {
            DetailRow("ETag", blob.etag!!, fgSecondary, fg)
        }
        blob.lastModified?.let { DetailRow("Modified", formatDate(it), fgSecondary, fg) }
        blob.createdAt?.let { DetailRow("Created", formatDate(it), fgSecondary, fg) }

        if (blob.metadata.isNotEmpty()) {
            HorizontalDivider()
            Text("Metadata", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            blob.metadata.forEach { (key, value) -> DetailRow(key, value, fgSecondary, fg) }
        }

        HorizontalDivider()

        // Themed action buttons
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            config.onViewBlob?.let { onView ->
                Button(
                    onClick = { onView(blob) },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBg, contentColor = primaryFg),
                    shape = RoundedCornerShape(buttonRadius),
                ) { Text("View", fontSize = 13.sp) }
            }
            config.onDownloadBlob?.let { onDownload ->
                OutlinedButton(
                    onClick = { onDownload(blob) },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = secondaryBg, contentColor = secondaryFg),
                    shape = RoundedCornerShape(buttonRadius),
                ) { Text("Download", fontSize = 13.sp) }
            }
            if (!config.readOnly) {
                config.onEditBlob?.let { onEdit ->
                    OutlinedButton(
                        onClick = { onEdit(blob) },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = secondaryBg, contentColor = secondaryFg),
                        shape = RoundedCornerShape(buttonRadius),
                    ) { Text("Edit", fontSize = 13.sp) }
                }
                if (state.capabilities.supportsDelete) {
                    OutlinedButton(
                        onClick = {
                            if (onRequestDelete != null) {
                                onRequestDelete(blob)
                            } else {
                                state.deleteBlob(blob)
                            }
                        },
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF9DEDC),
                                contentColor = Color(0xFFB3261E),
                            ),
                        shape = RoundedCornerShape(buttonRadius),
                    ) { Text("Delete", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Column {
        Text(
            text = label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
        )
        Text(text = value, color = valueColor, fontSize = 13.sp)
    }
}
