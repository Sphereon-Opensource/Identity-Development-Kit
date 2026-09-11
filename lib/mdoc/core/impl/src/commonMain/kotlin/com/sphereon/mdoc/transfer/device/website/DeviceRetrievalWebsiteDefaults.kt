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

package com.sphereon.mdoc.transfer.device.website

import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.cbor.cbor
import kotlinx.serialization.cbor.Cbor

internal fun defaultDeviceRetrievalWebsiteHttpClientOptions(): HttpClientOptions =
    HttpClientOptions(
        enableHttpCache = false,
        enableLogging = true,
        followRedirects = false,
        urlValidation = UrlValidationPolicy.BLOCK_PRIVATE,
        loggingConfig = LoggerConfig.Default,
        enableContentNegotiation = true,
        contentNegotiationConfig = {
            cbor(
                Cbor {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                },
            )
        },
        defaultRequest = {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Cbor.toString())
            headers.append(HttpHeaders.Accept, ContentType.Application.Cbor.toString())
        },
    )
