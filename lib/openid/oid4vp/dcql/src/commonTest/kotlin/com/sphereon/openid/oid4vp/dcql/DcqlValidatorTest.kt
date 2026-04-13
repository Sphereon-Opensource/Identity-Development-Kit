/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.dcql

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlin.test.Test
import kotlin.test.assertTrue

class DcqlValidatorTest {

    @Test
    fun validateValidQueryWithCredentials() {
        val query = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(
                    id = "test_credential",
                    format = "dc+sd-jwt",
                    claims = listOf(
                        DcqlClaimQuery(path = listOf("name"))
                    )
                )
            )
        )

        val result = validateDcqlQuery(query)
        assertTrue(result is Valid)
    }

    @Test
    fun validateValidQueryWithCredentialSets() {
        val query = DcqlQuery(
            credential_sets = listOf(
                DcqlCredentialSetQuery(
                    required = true,
                    options = listOf(
                        DcqlCredentialSetOption(credential_ids = listOf("passport")),
                        DcqlCredentialSetOption(credential_ids = listOf("drivers_license"))
                    )
                )
            )
        )

        val result = validateDcqlQuery(query)
        assertTrue(result is Valid)
    }

    @Test
    fun failValidationWithEmptyCredentialsArray() {
        val query = DcqlQuery(credentials = emptyList())

        val result = validateDcqlQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("at least one credential") })
    }

    @Test
    fun failValidationWithEmptyCredentialSetsArray() {
        val query = DcqlQuery(credential_sets = emptyList())

        val result = validateDcqlQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("at least one credential set") })
    }

    @Test
    fun validateCredentialQueryWithEmptyId() {
        val query = DcqlCredentialQuery(
            id = "",
            claims = listOf(DcqlClaimQuery(path = listOf("name")))
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("cannot be empty") })
    }

    @Test
    fun validateCredentialQueryWithUnknownFormat() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "unknown_format"
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("known credential format") })
    }

    @Test
    fun validateCredentialQueryWithKnownFormats() {
        val formats = listOf("dc+sd-jwt", "mso_mdoc", "jwt_vc_json", "ldp_vc", "jwt_vp", "ldp_vp")

        formats.forEach { format ->
            val query = DcqlCredentialQuery(
                id = "test",
                format = format
            )

            val result = validateDcqlCredentialQuery(query)
            assertTrue(result is Valid, "Format $format should be valid")
        }
    }

    @Test
    fun validateClaimQueryWithEmptyPath() {
        val query = DcqlClaimQuery(path = emptyList())

        val result = validateDcqlClaimQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("path cannot be empty") })
    }

    @Test
    fun validateClaimQueryWithEmptyPathElement() {
        val query = DcqlClaimQuery(path = listOf("address", "", "street"))

        val result = validateDcqlClaimQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("empty strings") })
    }

    @Test
    fun validateClaimQueryWithEmptyValuesArray() {
        val query = DcqlClaimQuery(
            path = listOf("age"),
            values = emptyList()
        )

        val result = validateDcqlClaimQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("values array cannot be empty") })
    }

    @Test
    fun validateClaimSetWithEmptyId() {
        val claimSet = DcqlClaimSet(
            id = "",
            claims = listOf("claim1")
        )

        val result = validateDcqlClaimSet(claimSet)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("cannot be empty") })
    }

    @Test
    fun validateClaimSetWithEmptyClaimsArray() {
        val claimSet = DcqlClaimSet(
            id = "test_set",
            claims = emptyList()
        )

        val result = validateDcqlClaimSet(claimSet)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("at least one claim") })
    }

    @Test
    fun validateCredentialSetWithEmptyOptions() {
        val credentialSet = DcqlCredentialSetQuery(
            required = true,
            options = emptyList()
        )

        val result = validateDcqlCredentialSetQuery(credentialSet)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("at least one option") })
    }

    @Test
    fun validateCredentialSetOptionWithEmptyCredentialIds() {
        val option = DcqlCredentialSetOption(credential_ids = emptyList())

        val result = validateDcqlCredentialSetOption(option)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("at least one credential") })
    }

    @Test
    fun validateResponseWithCredentialMatches() {
        val response = DcqlResponse(
            credential_matches = listOf(
                DcqlCredentialMatch(
                    credential_id = "test_credential",
                    claims_satisfied = listOf("name", "email")
                )
            )
        )

        val result = validateDcqlResponse(response)
        assertTrue(result is Valid)
    }

    @Test
    fun validateResponseWithCredentialSetMatches() {
        val response = DcqlResponse(
            credential_set_matches = listOf(
                DcqlCredentialSetMatch(
                    credential_set_id = "0",
                    credential_id = "passport"
                )
            )
        )

        val result = validateDcqlResponse(response)
        assertTrue(result is Valid)
    }

    @Test
    fun validateCredentialMatchWithEmptyCredentialId() {
        val match = DcqlCredentialMatch(
            credential_id = "",
            claims_satisfied = listOf("name")
        )

        val result = validateDcqlCredentialMatch(match)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("cannot be empty") })
    }

    @Test
    fun validateCredentialSetMatchWithEmptyIds() {
        val match = DcqlCredentialSetMatch(
            credential_set_id = "",
            credential_id = "test"
        )

        val result = validateDcqlCredentialSetMatch(match)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("cannot be empty") })
    }

    @Test
    fun validateSdJwtVcMetaWithValidValues() {
        val meta = SdJwtVcMeta(
            vct_values = listOf("https://example.com/credential"),
            sd_jwt_alg_values = listOf("ES256", "ES384"),
            kb_jwt_alg_values = listOf("ES256")
        )

        val result = validateSdJwtVcMeta(meta)
        assertTrue(result is Valid)
    }

    @Test
    fun validateSdJwtVcMetaWithEmptyArrays() {
        val meta = SdJwtVcMeta(
            vct_values = emptyList(),
            sd_jwt_alg_values = emptyList()
        )

        val result = validateSdJwtVcMeta(meta)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("cannot be empty if present") })
    }

    @Test
    fun validateMdocMetaWithValidValues() {
        val meta = MdocMeta(
            doctype_value = "org.iso.18013.5.1.mDL",
            namespace_values = listOf("org.iso.18013.5.1")
        )

        val result = validateMdocMeta(meta)
        assertTrue(result is Valid)
    }

    @Test
    fun validateMdocMetaWithEmptyDoctype() {
        val meta = MdocMeta(doctype_value = "")

        val result = validateMdocMeta(meta)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("cannot be empty") })
    }

    @Test
    fun toIdkResultConvertsValidToOk() {
        val query = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(id = "test")
            )
        )

        val result = validateDcqlQuery(query).toIdkResult()
        assertTrue(result is Ok)
    }

    @Test
    fun toIdkResultConvertsInvalidToErr() {
        val query = DcqlQuery(credentials = emptyList())

        val result = validateDcqlQuery(query).toIdkResult()
        assertTrue(result is Err)
    }

    @Test
    fun validateHelperFunction() {
        val query = DcqlQuery(
            credentials = listOf(
                DcqlCredentialQuery(id = "test")
            )
        )

        val result = validate(validateDcqlQuery, query)
        assertTrue(result is Ok)
    }

    @Test
    fun validateCredentialQueryWithTrustedAuthorities() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "dc+sd-jwt",
            trusted_authorities = listOf(
                DcqlTrustedAuthority(type = "openid_federation", values = listOf("https://federation.example.com")),
                DcqlTrustedAuthority(type = "etsi_trusted_list", values = listOf("https://eidas.europa.eu/TL/EN_TL.xml"))
            )
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Valid)
    }

    @Test
    fun failValidationWithEmptyTrustedAuthoritiesArray() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "dc+sd-jwt",
            trusted_authorities = emptyList()
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Invalid)
        assertTrue(result.errors.any { it.message.contains("at least one trusted authority") })
    }

    @Test
    fun validateCredentialQueryWithDefaultRequireCryptographicHolderBinding() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "dc+sd-jwt"
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Valid)
        assertTrue(query.require_cryptographic_holder_binding) // Default is true
    }

    @Test
    fun validateCredentialQueryWithExplicitRequireCryptographicHolderBindingFalse() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "dc+sd-jwt",
            require_cryptographic_holder_binding = false
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Valid)
        assertTrue(!query.require_cryptographic_holder_binding)
    }

    @Test
    fun validateCredentialQueryWithDefaultMultiple() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "dc+sd-jwt"
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Valid)
        assertTrue(!query.multiple) // Default is false
    }

    @Test
    fun validateCredentialQueryWithExplicitMultipleTrue() {
        val query = DcqlCredentialQuery(
            id = "test",
            format = "dc+sd-jwt",
            multiple = true
        )

        val result = validateDcqlCredentialQuery(query)
        assertTrue(result is Valid)
        assertTrue(query.multiple)
    }
}