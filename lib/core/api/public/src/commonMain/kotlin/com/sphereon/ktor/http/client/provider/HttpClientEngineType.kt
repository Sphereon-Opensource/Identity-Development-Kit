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
 *
 */

package com.sphereon.ktor.http.client.provider

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Supported HTTP engine types.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientEngineType", exact = true)
enum class HttpClientEngineType {
    /**
     * CIO is a fully asynchronous coroutine-based engine that can be used on JVM, Android, and Native platforms.
     * It supports only HTTP/1.x for now.
     */
    CIO,

    /**
     * OKHTTP is a fully asynchronous coroutine-based engine that can be used on JVM, Android, and Native platforms.
     * With this client we can bypass ktor-network-tls which does not support EC client certificates.
     */
    OKHTTP,

    /**
     * Apple Darwin engine.
     */
    DARWIN,

    /**
     * Ktor JS engine that delegates to the platform's native HTTP stack
     * (Node.js `http`/`https` modules or browser `fetch`). Handles TLS natively
     * without requiring ktor-network-tls.
     */
    JS,
}
