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

@file:Suppress("MagicNumber") // Canvas icon drawing uses coordinate fractions and color hex values

package com.sphereon.conf.theme.ui.compose.blobexplorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme-colored folder icon using Canvas drawing.
 */
@Composable
fun FolderIcon(
    color: Color,
    size: Dp = 22.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path =
            Path().apply {
                moveTo(w * 0.08f, h * 0.88f) // bottom-left
                lineTo(w * 0.08f, h * 0.21f) // top-left
                lineTo(w * 0.38f, h * 0.21f) // tab start
                lineTo(w * 0.46f, h * 0.33f) // tab peak
                lineTo(w * 0.83f, h * 0.33f) // tab end to top-right
                quadraticTo(w * 0.92f, h * 0.33f, w * 0.92f, h * 0.42f)
                lineTo(w * 0.92f, h * 0.79f) // right side
                quadraticTo(w * 0.92f, h * 0.88f, w * 0.83f, h * 0.88f)
                close()
            }
        drawPath(path, color.copy(alpha = 0.15f))
        drawPath(path, color, style = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Generic file icon.
 */
@Composable
fun FileIcon(
    color: Color = Color(0xFF49454F),
    size: Dp = 22.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path =
            Path().apply {
                moveTo(w * 0.58f, h * 0.08f)
                lineTo(w * 0.25f, h * 0.08f)
                quadraticTo(w * 0.17f, h * 0.08f, w * 0.17f, h * 0.17f)
                lineTo(w * 0.17f, h * 0.83f)
                quadraticTo(w * 0.17f, h * 0.92f, w * 0.25f, h * 0.92f)
                lineTo(w * 0.75f, h * 0.92f)
                quadraticTo(w * 0.83f, h * 0.92f, w * 0.83f, h * 0.83f)
                lineTo(w * 0.83f, h * 0.33f)
                close()
            }
        drawPath(path, color, style = stroke)
        // Fold corner
        drawLine(color, Offset(w * 0.58f, h * 0.08f), Offset(w * 0.58f, h * 0.33f), strokeWidth = w * 0.065f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.58f, h * 0.33f), Offset(w * 0.83f, h * 0.33f), strokeWidth = w * 0.065f, cap = StrokeCap.Round)
    }
}

/**
 * Image file icon.
 */
@Composable
fun ImageIcon(
    color: Color,
    size: Dp = 22.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.76f, h * 0.76f), cornerRadius = CornerRadius(w * 0.08f), style = stroke)
        drawCircle(color, radius = w * 0.065f, center = Offset(w * 0.35f, h * 0.35f))
        val path =
            Path().apply {
                moveTo(w * 0.88f, h * 0.62f)
                lineTo(w * 0.67f, h * 0.42f)
                lineTo(w * 0.21f, h * 0.88f)
            }
        drawPath(path, color, style = stroke)
    }
}

/**
 * PDF file icon.
 */
@Composable
fun PdfIcon(size: Dp = 22.dp) {
    val color = Color(0xFFE53935)
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path =
            Path().apply {
                moveTo(w * 0.58f, h * 0.08f)
                lineTo(w * 0.25f, h * 0.08f)
                quadraticTo(w * 0.17f, h * 0.08f, w * 0.17f, h * 0.17f)
                lineTo(w * 0.17f, h * 0.83f)
                quadraticTo(w * 0.17f, h * 0.92f, w * 0.25f, h * 0.92f)
                lineTo(w * 0.75f, h * 0.92f)
                quadraticTo(w * 0.83f, h * 0.92f, w * 0.83f, h * 0.83f)
                lineTo(w * 0.83f, h * 0.33f)
                close()
            }
        drawPath(path, color, style = stroke)
        drawLine(color, Offset(w * 0.58f, h * 0.08f), Offset(w * 0.58f, h * 0.33f), strokeWidth = w * 0.065f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.58f, h * 0.33f), Offset(w * 0.83f, h * 0.33f), strokeWidth = w * 0.065f, cap = StrokeCap.Round)
    }
}

/**
 * Sort direction arrow.
 */
@Composable
fun SortArrow(
    ascending: Boolean,
    color: Color,
    size: Dp = 10.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path =
            Path().apply {
                if (ascending) {
                    moveTo(w * 0.5f, h * 0.17f)
                    lineTo(w * 0.83f, h * 0.67f)
                    lineTo(w * 0.17f, h * 0.67f)
                } else {
                    moveTo(w * 0.5f, h * 0.83f)
                    lineTo(w * 0.83f, h * 0.33f)
                    lineTo(w * 0.17f, h * 0.33f)
                }
                close()
            }
        drawPath(path, color)
    }
}

/**
 * Sidebar toggle icon using Canvas drawing, similar to the other icons.
 */
@Composable
fun SidebarIcon(
    color: Color,
    size: Dp = 16.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Outer rectangle
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = stroke,
        )
        // Vertical divider line (sidebar separator)
        drawLine(
            color,
            start = Offset(w * 0.38f, h * 0.12f),
            end = Offset(w * 0.38f, h * 0.88f),
            strokeWidth = w * 0.065f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Returns the appropriate icon composable for a content type.
 */
@Composable
fun ContentTypeIcon(
    contentType: String?,
    primaryColor: Color,
    size: Dp = 22.dp,
) {
    when {
        contentType == null -> FileIcon(size = size)
        contentType.startsWith("image/") -> ImageIcon(primaryColor, size = size)
        contentType == "application/pdf" -> PdfIcon(size = size)
        else -> FileIcon(size = size)
    }
}
