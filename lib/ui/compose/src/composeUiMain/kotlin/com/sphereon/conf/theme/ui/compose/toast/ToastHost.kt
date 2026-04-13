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

package com.sphereon.conf.theme.ui.compose.toast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalToastTokens
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class ToastVariant { Default, Success, Error, Warning, Info }

data class ToastData(
    val id: String,
    val message: String,
    val variant: ToastVariant = ToastVariant.Default,
    val dismissible: Boolean = true,
    val duration: Long = 5000L,
)

@Stable
class ToastState {
    private val _toasts = mutableStateListOf<ToastData>()
    val toasts: List<ToastData> get() = _toasts

    private var counter = 0

    fun show(
        message: String,
        variant: ToastVariant = ToastVariant.Default,
        dismissible: Boolean = true,
        duration: Long = 5000L,
    ): String {
        val id = "toast-${++counter}"
        _toasts.add(ToastData(id, message, variant, dismissible, duration))
        return id
    }

    fun dismiss(id: String) {
        _toasts.removeAll { it.id == id }
    }

    fun dismissAll() {
        _toasts.clear()
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun ToastHost(
    state: ToastState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalToastTokens.current

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        state.toasts.forEach { toast ->
            // Auto-dismiss via LaunchedEffect
            if (toast.duration > 0) {
                LaunchedEffect(toast.id) {
                    delay(toast.duration)
                    state.dismiss(toast.id)
                }
            }

            val (bg, fg) = resolveToastColors(toast.variant, tokens)
            val shape = RoundedCornerShape(parseDp(tokens.radius))

            Row(
                modifier =
                    Modifier
                        .clip(shape)
                        .background(bg)
                        .padding(parseDp(tokens.padding))
                        .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = toast.message,
                    color = fg,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (toast.dismissible) {
                    IconButton(
                        onClick = { state.dismiss(toast.id) },
                        modifier =
                            Modifier
                                .size(36.dp)
                                .semantics { contentDescription = "Dismiss notification" },
                    ) {
                        Text(
                            text = "\u2715",
                            color = fg.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun resolveToastColors(
    variant: ToastVariant,
    tokens: com.sphereon.conf.theme.ui.compose.tokens.ToastTokens,
): Pair<Color, Color> =
    when (variant) {
        ToastVariant.Default -> parseColor(tokens.background) to parseColor(tokens.foreground)
        ToastVariant.Success -> parseColor(tokens.successBackground) to parseColor(tokens.successForeground)
        ToastVariant.Error -> parseColor(tokens.errorBackground) to parseColor(tokens.errorForeground)
        ToastVariant.Warning -> parseColor(tokens.warningBackground) to parseColor(tokens.warningForeground)
        ToastVariant.Info -> parseColor(tokens.infoBackground) to parseColor(tokens.infoForeground)
    }

@Preview
@Composable
private fun ToastPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            val state =
                remember {
                    ToastState().apply {
                        show("Default notification", duration = 0)
                        show("Success!", ToastVariant.Success, duration = 0)
                        show("Error occurred", ToastVariant.Error, duration = 0)
                    }
                }
            ToastHost(state = state)
        }
    }
}
