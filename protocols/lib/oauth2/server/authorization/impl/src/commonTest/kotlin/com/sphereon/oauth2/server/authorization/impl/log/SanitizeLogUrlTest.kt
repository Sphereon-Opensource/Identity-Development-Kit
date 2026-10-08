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

package com.sphereon.oauth2.server.authorization.impl.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SanitizeLogUrlTest {
    @Test
    fun keepsHostPathAndSafeQueryParams() {
        val sanitized =
            sanitizeLogUrl(
                "https://kw1ctest.brightspace.com/d2l/lp/auth/login/openIDConnectLogin.d2l" +
                    "?code=lVWOz8YiYWf6RVTm4-LP-d94apOWAtvZWcI2h08E2oo" +
                    "&state=66f94490-2ee1-4285-9a1f-81c8b6c22cd4" +
                    "&iss=https%3A%2F%2Fkw1c-surf.demo.sphereon",
            )
        assertTrue(sanitized.startsWith("https://kw1ctest.brightspace.com/d2l/lp/auth/login/openIDConnectLogin.d2l?"))
        assertTrue(sanitized.contains("state=66f94490-2ee1-4285-9a1f-81c8b6c22cd4"))
        assertTrue(sanitized.contains("iss=https://kw1c-surf.demo.sphereon"))
        assertTrue(sanitized.contains("code=[redacted]"))
        assertFalse(sanitized.contains("lVWOz8YiYWf6RVTm4-LP-d94apOWAtvZWcI2h08E2oo"))
    }

    @Test
    fun decodesAndSanitizesNestedReturnUrl() {
        val sanitized =
            sanitizeLogUrl(
                "https://portal.kw1c-surf.demo.sphereon.com/login" +
                    "?session_id=sess-1" +
                    "&return_url=https%3A%2F%2Fkw1c-surf.demo.sphereon%2Fauthorize%2Fcallback%3Fsession_id%3Dsess-1%26code%3Dsecret-code",
            )
        assertTrue(sanitized.contains("session_id=sess-1"))
        assertTrue(sanitized.contains("return_url=https://kw1c-surf.demo.sphereon/authorize/callback?session_id=sess-1&code=[redacted]"))
        assertFalse(sanitized.contains("secret-code"))
    }

    @Test
    fun sanitizesRelativeAuthorizeCallback() {
        assertEquals(
            "/authorize/callback?session_id=abc&code=[redacted]",
            sanitizeLogUrl("/authorize/callback?session_id=abc&code=raw-code"),
        )
    }

    @Test
    fun redactsFragmentTokens() {
        val sanitized = sanitizeLogUrl("https://rp.example/cb#access_token=tok&state=s1")
        assertEquals("https://rp.example/cb#access_token=[redacted]&state=s1", sanitized)
    }

    @Test
    fun leavesBareUrlsUnchanged() {
        assertEquals("https://kw1c-surf.demo.sphereon/federation/callback", sanitizeLogUrl("https://kw1c-surf.demo.sphereon/federation/callback"))
        assertEquals("", sanitizeLogUrl(""))
    }
}
