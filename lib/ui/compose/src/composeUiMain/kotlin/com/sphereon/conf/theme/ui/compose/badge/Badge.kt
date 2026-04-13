package com.sphereon.conf.theme.ui.compose.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalBadgeTokens
import com.sphereon.conf.theme.compose.LocalThemeTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class BadgeVariant { Default, Error, Success, Warning, Info }

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
) {
    val tokens = LocalBadgeTokens.current
    val (bg, fg) = when (variant) {
        BadgeVariant.Default -> parseColor(tokens.background) to parseColor(tokens.foreground)
        BadgeVariant.Error -> parseColor(tokens.errorBackground) to parseColor(tokens.errorForeground)
        BadgeVariant.Success -> {
            val themeTokens = LocalThemeTokens.current
            parseColor(themeTokens["color.feedback.success"] ?: tokens.background) to
                parseColor(themeTokens["color.feedback.onSuccess"] ?: tokens.foreground)
        }
        BadgeVariant.Warning -> {
            val themeTokens = LocalThemeTokens.current
            parseColor(themeTokens["color.feedback.warning"] ?: tokens.background) to
                parseColor(themeTokens["color.feedback.onWarning"] ?: tokens.foreground)
        }
        BadgeVariant.Info -> {
            val themeTokens = LocalThemeTokens.current
            parseColor(themeTokens["color.feedback.info"] ?: tokens.background) to
                parseColor(themeTokens["color.feedback.onInfo"] ?: tokens.foreground)
        }
    }
    val shape = RoundedCornerShape(parseDp(tokens.radius, 9999.dp))

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .padding(
                horizontal = parseDp(tokens.paddingX),
                vertical = parseDp(tokens.paddingY),
            ),
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun BadgePreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Badge(text = "Default")
                Badge(text = "Error", variant = BadgeVariant.Error)
                Badge(text = "Success", variant = BadgeVariant.Success)
                Badge(text = "Warning", variant = BadgeVariant.Warning)
                Badge(text = "Info", variant = BadgeVariant.Info)
            }
        }
    }
}
