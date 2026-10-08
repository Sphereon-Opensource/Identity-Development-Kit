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

import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationGrant
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationPolicySnapshot
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalUuidApi::class)
class CreateCredentialOfferArgsStaticOfferFieldsTest {
    private val instanceId = "00000000-0000-4000-8000-000000000051"
    private val snapshot = Oid4vciAuthorizationPolicySnapshot(
        issuerId = Uuid.parse(instanceId),
        authorizationServerId = Uuid.parse("00000000-0000-4000-8000-000000000052"),
        authorizationServerIssuer = "https://as.example.com",
        applicableGrants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
        profile = Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
        profileRevision = 1,
        authorizationServerRevision = 2,
        bindingRevision = 3,
    )

    @Test
    fun uriLifecycleDefaultsToSingleUse() {
        val args =
            CreateCredentialOfferArgs(
                instanceId = instanceId,
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
                authorizationPolicySnapshot = snapshot,
            )
        assertEquals(OfferUriLifecycle.SINGLE_USE, args.uriLifecycle)
        assertEquals(instanceId, args.instanceId)
    }

    @Test
    fun initialLifecycleFieldsDefaultsToEmptyMap() {
        val args =
            CreateCredentialOfferArgs(
                instanceId = instanceId,
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
                authorizationPolicySnapshot = snapshot,
            )
        assertTrue(args.initialLifecycleFields.isEmpty())
    }

    @Test
    fun rateLimitDefaultsToNull() {
        val args =
            CreateCredentialOfferArgs(
                instanceId = instanceId,
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
                authorizationPolicySnapshot = snapshot,
            )
        assertNull(args.rateLimit)
    }

    @Test
    fun explicitReusableWithRateLimitCarriedThrough() {
        val rateLimit = OfferRateLimit(maxPerWindow = 50, windowSeconds = 3600)
        val args =
            CreateCredentialOfferArgs(
                instanceId = instanceId,
                issuerId = "https://issuer.example.com/oid4vci",
                credentialConfigurationIds = listOf("PID"),
                uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                rateLimit = rateLimit,
                authorizationPolicySnapshot = snapshot,
            )
        assertEquals(OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH, args.uriLifecycle)
        assertEquals(rateLimit, args.rateLimit)
    }
}
