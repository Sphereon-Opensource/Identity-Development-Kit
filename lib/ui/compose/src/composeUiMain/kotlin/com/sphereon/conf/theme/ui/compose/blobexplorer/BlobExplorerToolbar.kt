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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalBlobExplorerTokens
import com.sphereon.conf.theme.ui.compose.tokens.LocalButtonTokens

/**
 * Toolbar with breadcrumbs and themed Upload button.
 */
@Composable
fun BlobExplorerToolbar(
    state: BlobExplorerState,
    config: BlobExplorerConfig,
    onUploadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalBlobExplorerTokens.current
    val buttonTokens = LocalButtonTokens.current
    val bg = parseColor(tokens.toolbarBackground)
    val primaryBg = parseColor(buttonTokens.primaryBackground)
    val primaryFg = parseColor(buttonTokens.primaryForeground)
    val buttonRadius = parseDp(buttonTokens.primaryRadius, 12.dp)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BlobBreadcrumbs(
            currentPrefix = state.currentPrefix,
            onNavigate = { state.navigateTo(it) },
            modifier = Modifier.weight(1f),
        )

        if (!config.readOnly && state.capabilities.supportsUpload && onUploadClick != null) {
            Button(
                onClick = onUploadClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = primaryBg,
                        contentColor = primaryFg,
                    ),
                shape = RoundedCornerShape(buttonRadius),
            ) {
                Text("Upload", fontSize = 14.sp)
            }
        }
    }
}
