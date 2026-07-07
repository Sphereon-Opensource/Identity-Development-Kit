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

package com.sphereon.openid.oid4vci.issuer.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateCredentialOfferArgsStaticOfferFieldsTest {
    @Test
    fun uriLifecycleDefaultsToSingleUse() {
        val args =
            CreateCredentialOfferArgs(
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
            )
        assertEquals(OfferUriLifecycle.SINGLE_USE, args.uriLifecycle)
    }

    @Test
    fun initialLifecycleFieldsDefaultsToEmptyMap() {
        val args =
            CreateCredentialOfferArgs(
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
            )
        assertTrue(args.initialLifecycleFields.isEmpty())
    }

    @Test
    fun rateLimitDefaultsToNull() {
        val args =
            CreateCredentialOfferArgs(
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
            )
        assertNull(args.rateLimit)
    }

    @Test
    fun explicitReusableWithRateLimitCarriedThrough() {
        val rateLimit = OfferRateLimit(maxPerWindow = 50, windowSeconds = 3600)
        val args =
            CreateCredentialOfferArgs(
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
                uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                rateLimit = rateLimit,
            )
        assertEquals(OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH, args.uriLifecycle)
        assertEquals(rateLimit, args.rateLimit)
    }
}
