/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.common.impl.validation

import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlin.test.Test
import kotlin.test.assertTrue

class CredentialOfferValidatorTest {
    @Test
    fun validOfferPasses() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = listOf("UniversityDegree_LDP_VC"),
            )
        val result = credentialOfferValidator(offer)
        assertTrue(result is Valid, "A valid credential offer should pass validation")
    }

    @Test
    fun emptyIssuerFails() {
        val offer =
            CredentialOffer(
                credentialIssuer = "",
                credentialConfigurationIds = listOf("UniversityDegree_LDP_VC"),
            )
        val result = credentialOfferValidator(offer)
        assertTrue(result is Invalid, "An empty credentialIssuer should fail validation")
    }

    @Test
    fun emptyConfigIdsFails() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = emptyList(),
            )
        val result = credentialOfferValidator(offer)
        assertTrue(result is Invalid, "An empty credentialConfigurationIds list should fail validation")
    }

    @Test
    fun duplicateConfigIdsFails() {
        val offer =
            CredentialOffer(
                credentialIssuer = "https://issuer.example.com",
                credentialConfigurationIds = listOf("VerifiableCredential", "VerifiableCredential"),
            )
        val result = credentialOfferValidator(offer)
        assertTrue(result is Invalid, "Duplicate credentialConfigurationIds should fail validation")
    }
}
