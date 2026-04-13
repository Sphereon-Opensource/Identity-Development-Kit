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

package com.sphereon.mdoc.transport.restapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for RestApiHandle and RestApiError classes.
 */
class RestApiHandleTest {
    // RestApiHandle tests

    @Test
    fun testRestApiHandleCreation() {
        val handle = RestApiHandle("https://example.com/api")
        assertEquals("https://example.com/api", handle.uri)
    }

    @Test
    fun testRestApiHandleEquality() {
        val handle1 = RestApiHandle("https://example.com/api")
        val handle2 = RestApiHandle("https://example.com/api")
        assertEquals(handle1, handle2)
        assertEquals(handle1.hashCode(), handle2.hashCode())
    }

    @Test
    fun testRestApiHandleToString() {
        val handle = RestApiHandle("https://example.com/api")
        val str = handle.toString()
        assertTrue(str.contains("RestApiHandle"))
        assertTrue(str.contains("https://example.com/api"))
    }

    // RestApiError.InsecureUri tests

    @Test
    fun testInsecureUriCreation() {
        val error = RestApiError.InsecureUri("http://example.com")
        assertTrue(error.errorMessage.contains("HTTPS"))
        assertTrue(error.errorMessage.contains("http://example.com"))
    }

    @Test
    fun testInsecureUriToIdkError() {
        val error = RestApiError.InsecureUri("http://example.com")
        val idkError = error.toIdkError()
        assertEquals("REST_API_INSECURE_URI", idkError.code)
    }

    // RestApiError.ConnectionFailed tests

    @Test
    fun testConnectionFailedCreation() {
        val cause = RuntimeException("Connection refused")
        val error = RestApiError.ConnectionFailed("https://example.com", cause)
        assertTrue(error.errorMessage.contains("Failed to connect"))
        assertTrue(error.errorMessage.contains("https://example.com"))
        assertEquals(cause, error.errorCause)
    }

    @Test
    fun testConnectionFailedToIdkError() {
        val cause = RuntimeException("Connection refused")
        val error = RestApiError.ConnectionFailed("https://example.com", cause)
        val idkError = error.toIdkError()
        assertEquals("REST_API_CONNECTION_FAILED", idkError.code)
    }

    // RestApiError.NetworkError tests

    @Test
    fun testNetworkErrorCreation() {
        val cause = RuntimeException("Timeout")
        val error = RestApiError.NetworkError("https://example.com", cause)
        assertTrue(error.errorMessage.contains("Network error"))
        assertTrue(error.errorMessage.contains("https://example.com"))
        assertEquals(cause, error.errorCause)
    }

    @Test
    fun testNetworkErrorToIdkError() {
        val cause = RuntimeException("Timeout")
        val error = RestApiError.NetworkError("https://example.com", cause)
        val idkError = error.toIdkError()
        assertEquals("REST_API_NETWORK_ERROR", idkError.code)
    }

    // RestApiError.ServerError tests

    @Test
    fun testServerErrorCreation() {
        val error =
            RestApiError.ServerError(
                uri = "https://example.com",
                statusCode = 500,
                serverMessage = "Internal Server Error",
            )
        assertEquals("Internal Server Error", error.errorMessage)
        assertEquals(500, error.statusCode)
        assertEquals("https://example.com", error.uri)
    }

    @Test
    fun testServerErrorToIdkError() {
        val error =
            RestApiError.ServerError(
                uri = "https://example.com",
                statusCode = 500,
                serverMessage = "Internal Server Error",
            )
        val idkError = error.toIdkError()
        assertEquals("REST_API_SERVER_ERROR", idkError.code)
    }

    // RestApiError.MissingEngagementData tests

    @Test
    fun testMissingEngagementDataCreation() {
        val error = RestApiError.MissingEngagementData("Missing reader key")
        assertEquals("Missing reader key", error.errorMessage)
    }

    @Test
    fun testMissingEngagementDataToIdkError() {
        val error = RestApiError.MissingEngagementData("Missing reader key")
        val idkError = error.toIdkError()
        assertEquals("REST_API_MISSING_ENGAGEMENT_DATA", idkError.code)
    }
}
