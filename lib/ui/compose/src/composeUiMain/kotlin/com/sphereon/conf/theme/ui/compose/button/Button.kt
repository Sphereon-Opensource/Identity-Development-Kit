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

package com.sphereon.conf.theme.ui.compose.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalButtonTokens
import androidx.compose.ui.tooling.preview.Preview

enum class ButtonVariant { Primary, Secondary, Ghost, Outline }

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalButtonTokens.current
    val radius =
        when (variant) {
            ButtonVariant.Primary -> parseDp(tokens.primaryRadius, 12.dp)
            ButtonVariant.Secondary -> parseDp(tokens.secondaryRadius, 12.dp)
            ButtonVariant.Ghost, ButtonVariant.Outline -> parseDp(tokens.primaryRadius, 12.dp)
        }
    val shape = RoundedCornerShape(radius)
    val padding =
        PaddingValues(
            horizontal = parseDp(tokens.primaryPaddingX, 24.dp),
            vertical = parseDp(tokens.primaryPaddingY, 12.dp),
        )

    when (variant) {
        ButtonVariant.Primary -> {
            val bg = parseColor(tokens.primaryBackground)
            val fg = parseColor(tokens.primaryForeground)
            // Disabled uses onSurface tints rather than an alpha-faded brand color: a translucent
            // brand fill with translucent white text is illegible on light surfaces.
            val disabledBase = MaterialTheme.colorScheme.onSurface
            androidx.compose.material3.Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = bg,
                        contentColor = fg,
                        disabledContainerColor = disabledBase.copy(alpha = 0.12f),
                        disabledContentColor = disabledBase.copy(alpha = 0.38f),
                    ),
                shape = shape,
                contentPadding = padding,
                content = content,
            )
        }

        ButtonVariant.Secondary -> {
            val bg = parseColor(tokens.secondaryBackground)
            val fg = parseColor(tokens.secondaryForeground)
            val borderColor = parseColor(tokens.secondaryBorder)
            val borderWidth = parseDp(tokens.secondaryBorderWidth, 1.dp)
            val disabledBase = MaterialTheme.colorScheme.onSurface
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = bg,
                        contentColor = fg,
                        disabledContainerColor = disabledBase.copy(alpha = 0.12f),
                        disabledContentColor = disabledBase.copy(alpha = 0.38f),
                    ),
                border = BorderStroke(borderWidth, borderColor),
                shape = shape,
                contentPadding = padding,
                content = content,
            )
        }

        ButtonVariant.Outline -> {
            val fg = parseColor(tokens.primaryBackground)
            val borderColor = parseColor(tokens.primaryBackground)
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = fg,
                    ),
                border = BorderStroke(1.dp, borderColor),
                shape = shape,
                contentPadding = padding,
                content = content,
            )
        }

        ButtonVariant.Ghost -> {
            val fg = parseColor(tokens.ghostForeground)
            TextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = fg,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                shape = shape,
                contentPadding = padding,
                content = content,
            )
        }
    }
}

@Preview
@Composable
private fun ButtonPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            androidx.compose.foundation.layout.Column(
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                Button(onClick = {}, variant = ButtonVariant.Primary) { androidx.compose.material3.Text("Primary") }
                Button(onClick = {}, variant = ButtonVariant.Secondary) { androidx.compose.material3.Text("Secondary") }
                Button(onClick = {}, variant = ButtonVariant.Outline) { androidx.compose.material3.Text("Outline") }
                Button(onClick = {}, variant = ButtonVariant.Ghost) { androidx.compose.material3.Text("Ghost") }
                Button(onClick = {}, enabled = false) { androidx.compose.material3.Text("Disabled") }
            }
        }
    }
}
