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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The `entity_id` claim that earlier-draft wallets read from an `openid_federation` Request Object. */
class LegacyFederationEntityIdTest {
    @Test
    fun federationRequestObjectCarriesTheBareEntityIdentifier() {
        val binding = VerifierSignerBinding.Federation(entityIdentifier = ENTITY_ID, kid = "request-key")
        val request = request(binding.clientId).withLegacyFederationEntityId(binding)

        assertEquals(JsonPrimitive(ENTITY_ID), request.additionalParameters["entity_id"])
        assertEquals("openid_federation:$ENTITY_ID", request.clientId, "client_id keeps its OID4VP 1.0 prefix")
    }

    @Test
    fun otherClientIdentifierPrefixesAreLeftAlone() {
        val binding = VerifierSignerBinding.X509SanDns(dnsName = "verifier.example", certificateChain = listOf("certificate"))
        val request = request(binding.clientId).withLegacyFederationEntityId(binding)

        assertFalse("entity_id" in request.additionalParameters)
    }

    @Test
    fun anExplicitEntityIdIsNotOverwritten() {
        val binding = VerifierSignerBinding.Federation(entityIdentifier = ENTITY_ID, kid = "request-key")
        val explicit = request(binding.clientId).let { it.copy(additionalParameters = it.additionalParameters + ("entity_id" to JsonPrimitive("https://other.example"))) }

        assertEquals(JsonPrimitive("https://other.example"), explicit.withLegacyFederationEntityId(binding).additionalParameters["entity_id"])
    }

    private fun request(clientId: String) =
        AuthorizationRequest(
            clientId = clientId,
            state = "state-1",
            nonce = "nonce-12345678",
            responseMode = "direct_post",
        )

    private companion object {
        const val ENTITY_ID = "https://verifier.example/oid4vp/main"
    }
}
