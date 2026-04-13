package com.sphereon.conf.theme.ui.compose.modal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sphereon.conf.theme.ui.compose.ComponentTheme
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalModalTokens
import com.sphereon.conf.theme.compose.LocalThemeTokens
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tokens = LocalModalTokens.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(parseDp(tokens.radius)),
            color = parseColor(tokens.background),
            contentColor = parseColor(tokens.foreground),
        ) {
            Column(Modifier.padding(24.dp)) {
                if (title != null) {
                    Text(title, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                }
                if (description != null) {
                    Text(description, fontSize = 14.sp, color = parseColor(
                        LocalThemeTokens.current["color.text.secondary"] ?: tokens.foreground
                    ))
                    Spacer(Modifier.height(16.dp))
                }
                content()
            }
        }
    }
}

@Preview
@Composable
private fun DialogPreview() {
    androidx.compose.material3.MaterialTheme {
        ComponentTheme {
            // Preview shows the dialog content without the overlay
            Surface {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("Dialog Title", fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Dialog description text", fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Dialog content goes here")
                }
            }
        }
    }
}
