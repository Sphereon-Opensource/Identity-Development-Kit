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

package com.sphereon.oauth2.oidf.op

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that Group J's branded login renderer is wired into the harness end to end:
 * `/login` returns the form HTML, and the bundled CSS / SVG assets are served with their
 * correct content types.
 */
class OidfOpLoginPageTest {
    private lateinit var fixture: OidfOpServerFixture
    private lateinit var client: HttpClient

    @BeforeTest
    fun setUp() {
        fixture = OidfOpServerFixture()
        client = HttpClient(CIO)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        fixture.stop()
    }

    @Test
    fun loginPageRendersUsernameAndPasswordForm() =
        runTest {
            val response =
                client.get(
                    "${fixture.baseUrl}/login?session_id=test-session&return_url=${fixture.baseUrl}/",
                )
            assertEquals(HttpStatusCode.OK, response.status, "login page must return 200")
            val contentType = response.headers["Content-Type"]
            assertNotNull(contentType, "login page must declare a Content-Type")
            assertTrue(
                contentType.startsWith("text/html"),
                "login page must declare text/html, got: $contentType",
            )

            val body = response.bodyAsText()
            assertTrue(body.contains("<form", ignoreCase = true), "login page must render a form")
            assertTrue(
                body.contains("name=\"username\"", ignoreCase = true),
                "login page must render a username field",
            )
            assertTrue(
                body.contains("name=\"password\"", ignoreCase = true),
                "login page must render a password field",
            )
        }

    @Test
    fun loginPageServesBundledCssAsset() =
        runTest {
            val response = client.get("${fixture.baseUrl}/login/assets/css/login.css")
            assertEquals(HttpStatusCode.OK, response.status, "login CSS must return 200")
            val contentType = response.headers["Content-Type"]
            assertNotNull(contentType, "login CSS must declare a Content-Type")
            assertTrue(
                contentType.startsWith("text/css"),
                "login CSS must declare text/css, got: $contentType",
            )
            assertTrue(response.bodyAsText().isNotEmpty(), "login CSS must have a body")
        }

    @Test
    fun loginPageServesBundledLogoAsset() =
        runTest {
            val response = client.get("${fixture.baseUrl}/login/assets/img/sphereon-logo.svg")
            assertEquals(HttpStatusCode.OK, response.status, "login logo must return 200")
            val contentType = response.headers["Content-Type"]
            assertNotNull(contentType, "login logo must declare a Content-Type")
            assertTrue(
                contentType.startsWith("image/svg+xml"),
                "login logo must declare image/svg+xml, got: $contentType",
            )
        }
}
