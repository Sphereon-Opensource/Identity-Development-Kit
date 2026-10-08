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

package com.sphereon.oauth2.client.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlUtilsTest {
    @Test
    fun queryValuesAreFullyPercentEncodedAndRoundTrip() {
        val authorizationDetails =
            """[{"type":"openid_credential","locations":["https://issuer.example/credential"]}]"""

        val encoded = encodeQueryParameters(mapOf("authorization_details" to authorizationDetails))

        assertTrue(encoded.startsWith("authorization_details=%5B%7B%22type%22%3A%22openid_credential%22"))
        assertFalse('[' in encoded)
        assertFalse(']' in encoded)
        assertEquals(authorizationDetails, decodeQueryParameters(encoded)["authorization_details"])
    }
}
