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

import io.ktor.client.HttpClient

/**
 * Creates an [HttpClient] using [options], executes [block] with it, then closes it — even on exception.
 *
 * This eliminates the repetitive try/finally pattern:
 * ```kotlin
 * val client = httpClientFactory.createClient(options)
 * try { ... } finally { client.close() }
 * ```
 *
 * Usage:
 * ```kotlin
 * httpClientFactory.withClient { client ->
 *     client.get("https://example.com")
 * }
 * ```
 */
suspend fun <T> HttpClientFactory.withClient(
    options: HttpClientOptions = HttpClientOptions.createDefault(),
    block: suspend (HttpClient) -> T,
): T {
    val client = createClient(options)
    return try {
        block(client)
    } finally {
        client.close()
    }
}
