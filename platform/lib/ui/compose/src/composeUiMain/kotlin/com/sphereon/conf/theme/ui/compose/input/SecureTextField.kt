/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.conf.theme.ui.compose.input

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.sphereon.conf.theme.ui.compose.parseColor
import com.sphereon.conf.theme.ui.compose.parseDp
import com.sphereon.conf.theme.ui.compose.tokens.LocalInputTokens

/** Branded M3 secure entry without a platform widget or immutable String value API. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
    inputTransformation: InputTransformation? = null,
) {
    val tokens = LocalInputTokens.current
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { text -> { Text(text) } },
        isError = isError,
        keyboardOptions = keyboardOptions,
        inputTransformation = inputTransformation,
        textObfuscationMode = TextObfuscationMode.Hidden,
        shape = RoundedCornerShape(parseDp(tokens.radius)),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = parseColor(tokens.background),
                unfocusedContainerColor = parseColor(tokens.background),
                focusedBorderColor = parseColor(tokens.borderFocus),
                unfocusedBorderColor = parseColor(tokens.border),
                focusedTextColor = parseColor(tokens.foreground),
                unfocusedTextColor = parseColor(tokens.foreground),
                focusedPlaceholderColor = parseColor(tokens.placeholder),
                unfocusedPlaceholderColor = parseColor(tokens.placeholder),
            ),
    )
}
