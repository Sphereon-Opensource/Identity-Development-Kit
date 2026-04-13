package com.sphereon.conf.theme.ui.compose.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalCardTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalCardTokens.current
    val shape = RoundedCornerShape(parseDp(tokens.radius))
    val colors = CardDefaults.cardColors(
        containerColor = parseColor(tokens.background),
        contentColor = parseColor(tokens.foreground),
    )

    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
        ) {
            Box(Modifier.padding(parseDp(tokens.padding))) {
                content()
            }
        }
    } else {
        androidx.compose.material3.Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
        ) {
            Box(Modifier.padding(parseDp(tokens.padding))) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun CardPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            Card {
                Text("Card content")
            }
        }
    }
}
