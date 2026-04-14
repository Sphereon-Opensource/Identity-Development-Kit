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

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.compat.JsExportCompat
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("SimpleRestClientOptions", exact = true)
data class SimpleRestClientOptions(
    val engine: HttpClientEngineType,
    val json: Boolean = true,
    val logLevel: LogLevel = LogLevel.INFO,
) {
    companion object {
        @JvmStatic
        val DEFAULT_OPTIONS = SimpleRestClientOptions(HttpClientEngineType.CIO, true, LogLevel.INFO)
    }

    fun applyTo(options: LegacyHttpClientOptions) =
        options.copy(
            engine = engine,
            enableContentNegotiation = json,
            contentNegotiationConfig = {
                json(
                    Json {
                        encodeDefaults = true
                        ignoreUnknownKeys = true
                        prettyPrint = false
                    },
                )
            },
            loggingConfig = {
                level =
                    io.ktor.client.plugins.logging.LogLevel
                        .valueOf(logLevel.name)
            },
        )
}
