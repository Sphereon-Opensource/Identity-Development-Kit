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

package com.sphereon.conf.theme.core.token

import com.sphereon.conf.theme.core.model.ThemeToken
import com.sphereon.conf.theme.core.model.ThemeTokenType

/**
 * DSL builder for constructing theme token lists.
 */
class TokenBuilder {
    private val tokens = mutableListOf<ThemeToken>()

    fun color(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.COLOR))
    }

    fun dimension(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.DIMENSION))
    }

    fun fontFamily(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.FONT_FAMILY))
    }

    fun fontWeight(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.FONT_WEIGHT))
    }

    fun opacity(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.OPACITY))
    }

    fun duration(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.DURATION))
    }

    fun easing(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.EASING))
    }

    fun shadow(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.SHADOW))
    }

    fun borderWidth(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.BORDER_WIDTH))
    }

    fun spacing(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.SPACING))
    }

    fun string(
        key: String,
        value: String,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = ThemeTokenType.STRING))
    }

    fun token(
        key: String,
        value: String,
        type: ThemeTokenType = ThemeTokenType.STRING,
    ) {
        tokens.add(ThemeToken(key = key, value = value, type = type))
    }

    fun build(): List<ThemeToken> = tokens.toList()
}

/**
 * DSL entry point for building a token list.
 */
fun buildTokens(block: TokenBuilder.() -> Unit): List<ThemeToken> = TokenBuilder().apply(block).build()
