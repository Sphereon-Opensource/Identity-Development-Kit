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

package com.sphereon.ktor.http.client

import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.LegacyHttpClientFactory
import com.sphereon.ktor.http.client.provider.LegacyHttpClientOptions
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertTrue

/**
 * Plain-HTTP smoke coverage for [LegacyHttpClientFactory].
 *
 * Legacy SSL / mTLS helpers were removed from http-client-public; mTLS coverage lives in
 * `:lib-data-link-http-client-kms-impl`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientProviderIntegrationTest {
    @Test
    fun plainLegacyClientCanBeCreatedWithoutSslConfig() =
        runTest {
            val client =
                LegacyHttpClientFactory().createClient(
                    LegacyHttpClientOptions(
                        engine = HttpClientEngineType.OKHTTP,
                        enableContentNegotiation = false,
                        enableLogging = false,
                    ),
                )
            client.use {
                // Construction + close is enough; network calls need a live host.
                assertTrue(it.toString().isNotEmpty())
            }
        }
}
