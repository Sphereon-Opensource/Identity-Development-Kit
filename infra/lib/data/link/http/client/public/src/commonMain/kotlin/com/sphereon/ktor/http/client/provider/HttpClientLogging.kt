/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LoggerConfig
import io.ktor.http.HttpHeaders
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel

fun LoggerConfig.toKtorHttpClientLogLevel(): KtorLogLevel =
    when (minLevel) {
        LogLevel.TRACE -> KtorLogLevel.ALL
        LogLevel.DEBUG, LogLevel.INFO -> KtorLogLevel.INFO
        LogLevel.WARN, LogLevel.ERROR, LogLevel.OFF -> KtorLogLevel.NONE
    }

fun String.isSensitiveHttpClientLogHeader(): Boolean =
    equals(HttpHeaders.Authorization, ignoreCase = true) ||
        equals("Proxy-Authorization", ignoreCase = true) ||
        equals(HttpHeaders.Cookie, ignoreCase = true) ||
        equals(HttpHeaders.SetCookie, ignoreCase = true) ||
        equals("X-Api-Key", ignoreCase = true)
