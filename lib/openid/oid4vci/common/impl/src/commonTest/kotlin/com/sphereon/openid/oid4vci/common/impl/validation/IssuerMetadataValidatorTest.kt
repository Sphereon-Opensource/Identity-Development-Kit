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

import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlin.test.Test
import kotlin.test.assertTrue

class IssuerMetadataValidatorTest {
    private fun validMetadata() =
        CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported =
                mapOf(
                    "UniversityDegree" to CredentialConfigurationSupported(format = "vc+sd-jwt"),
                ),
        )

    @Test
    fun validMetadataPasses() {
        val result = issuerMetadataValidator(validMetadata())
        assertTrue(result is Valid, "Valid issuer metadata should pass validation")
    }

    @Test
    fun emptyIssuerFails() {
        val metadata = validMetadata().copy(credentialIssuer = "")
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "An empty credentialIssuer should fail validation")
    }

    @Test
    fun emptyEndpointFails() {
        val metadata = validMetadata().copy(credentialEndpoint = "")
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "An empty credentialEndpoint should fail validation")
    }

    @Test
    fun emptyConfigsFails() {
        val metadata = validMetadata().copy(credentialConfigurationsSupported = emptyMap())
        val result = issuerMetadataValidator(metadata)
        assertTrue(result is Invalid, "An empty credentialConfigurationsSupported map should fail validation")
    }
}
