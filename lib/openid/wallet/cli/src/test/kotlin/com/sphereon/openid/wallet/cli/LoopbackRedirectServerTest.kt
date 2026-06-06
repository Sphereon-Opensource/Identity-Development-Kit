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

package com.sphereon.openid.wallet.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopbackRedirectServerTest {
    @Test
    fun callbackReturns200WithHtmlBodyAndResolvesCode() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            val testServer = LoopbackRedirectServer(port = 18765)
            testServer.start()

            val client = HttpClient(CIO)
            try {
                // Launch awaitCode concurrently so it can receive the result when the GET arrives
                val codeDeferred =
                    async {
                        testServer.awaitCode("xyz")
                    }

                val response = client.get("http://localhost:18765/callback?code=abc&state=xyz")

                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("Authorization complete"), "Body should contain success message, was: $body")

                val code = codeDeferred.await()
                assertEquals("abc", code)
            } finally {
                client.close()
                testServer.stop()
            }
        }
}
