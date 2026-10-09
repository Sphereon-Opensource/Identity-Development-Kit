/*
 * (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vci.common.validation

import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class IssuerMetadataUrlBoundaryTest {
    @Test
    fun signedPortIsInvalidForEachExistingUrlField() {
        assertValidBaseline()
        assertInvalidInEachUrlField("https://issuer.example:+443/Tenant")
    }

    @Test
    fun illegalRawHostIsInvalidForEachExistingUrlField() {
        assertValidBaseline()
        assertInvalidInEachUrlField("https://bad|host.example/Tenant")
    }

    @Test
    fun illegalRawPathAndNonAsciiComponentsAreInvalidForEachExistingUrlField() {
        assertValidBaseline()
        for (invalid in listOf("https://issuer.example/Tenant|Case", "https://éxample.test/Tenant", "https://issuer.example/Ténant")) {
            assertInvalidInEachUrlField(invalid)
        }
    }

    @Test
    fun endpointQueryRawGrammarIsValidatedWithoutForbiddingValidQueries() {
        val baseline = validMetadata()
        assertTrue(issuerMetadataValidator(baseline.copy(credentialEndpoint = "HTTPS://Issuer.example:0/Path%2FCase?next=/one?two")) is Valid)
        for (invalid in listOf("https://issuer.example/credential?x=one|two", "https://issuer.example/credential?x={value}", "https://issuer.example/credential?x=%G0")) {
            assertTrue(issuerMetadataValidator(baseline.copy(credentialEndpoint = invalid)) is Invalid, invalid)
        }
    }

    @Test
    fun malformedIpv6AndEmbeddedIpv4AreInvalidForEachExistingUrlField() {
        assertValidBaseline()
        for (invalid in listOf("https://[1::2::3]/Tenant", "https://[::ffff:192.00.2.128]/Tenant", "https://[::ffff:256.0.2.128]/Tenant", "https://[vG.address]/Tenant")) {
            assertInvalidInEachUrlField(invalid)
        }
    }

    @Test
    fun exactZeroEmptyPortIpv6AndUppercaseIpvFutureRemainValidForExistingFields() {
        val baseline = validMetadata()
        for (value in listOf(
            "HTTPS://Issuer.example:0/Tenant%2FCase", "https://issuer.example:/Tenant",
            "https://[2001:db8::1]/Tenant", "https://[::ffff:192.0.2.128]/Tenant", "https://[Vf.Future:Address]/Tenant",
        )) {
            assertTrue(issuerMetadataValidator(baseline.copy(credentialIssuer = value)) is Valid, "credential_issuer=$value")
            assertTrue(issuerMetadataValidator(baseline.copy(credentialEndpoint = value)) is Valid, "credential_endpoint=$value")
            assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = listOf(value))) is Valid, "authorization_servers=$value")
        }
    }

    @Test
    fun malformedPortReturnsInvalidRatherThanThrowingForEachUrlField() {
        assertValidBaseline()
        assertInvalidInEachUrlField("https://issuer.example:not-a-port")
    }

    @Test
    fun trailingWhitespaceIsInvalidForEachUrlField() {
        assertValidBaseline()
        assertInvalidInEachUrlField("https://issuer.example/Path ")
    }

    @Test
    fun internalWhitespaceIsInvalidForEachUrlField() {
        assertValidBaseline()
        assertInvalidInEachUrlField("https://issuer.example/Path With Space")
    }

    @Test
    fun emptyAuthorityIsInvalidForEachUrlField() {
        assertValidBaseline()
        for (invalid in listOf("https://", "https:///issuer")) {
            assertInvalidInEachUrlField(invalid)
        }
    }

    @Test
    fun malformedPercentEscapesAreInvalidWhileEncodedSeparatorsAndQueryRemainValid() {
        val baseline = validMetadata()
        assertTrue(issuerMetadataValidator(baseline) is Valid)
        val encodedIssuer = "https://issuer.example:8443/Tenant/Case%2FSensitive"
        val encodedEndpoint = "https://issuer.example:9443/Credential/Issue?wallet=One%2FTwo"
        assertTrue(issuerMetadataValidator(baseline.copy(credentialIssuer = encodedIssuer)) is Valid)
        assertTrue(issuerMetadataValidator(baseline.copy(credentialEndpoint = encodedEndpoint)) is Valid)
        assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = listOf(encodedIssuer))) is Valid)

        for (invalid in listOf("https://issuer.example/%", "https://issuer.example/%G0")) {
            assertInvalidInEachUrlField(invalid)
        }
    }

    @Test
    fun rawBackslashIsInvalidInAuthorityOrPathForEachUrlField() {
        assertValidBaseline()
        val encodedBackslash = "https://issuer.example/Tenant%5CName"
        val baseline = validMetadata()
        assertTrue(issuerMetadataValidator(baseline.copy(credentialIssuer = encodedBackslash)) is Valid)
        assertTrue(issuerMetadataValidator(baseline.copy(credentialEndpoint = encodedBackslash)) is Valid)
        assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = listOf(encodedBackslash))) is Valid)

        for (invalid in listOf("https://issuer.example\\Path", "https://\\issuer.example/path")) {
            assertInvalidInEachUrlField(invalid)
        }
    }

    private fun assertInvalidInEachUrlField(invalid: String) {
        val baseline = validMetadata()
        assertTrue(issuerMetadataValidator(baseline.copy(credentialIssuer = invalid)) is Invalid, "credential_issuer=$invalid")
        assertTrue(issuerMetadataValidator(baseline.copy(credentialEndpoint = invalid)) is Invalid, "credential_endpoint=$invalid")
        assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = listOf("https://valid.example", invalid))) is Invalid, "authorization_servers=$invalid")
    }

    private fun assertValidBaseline() {
        val baseline = validMetadata()
        assertTrue(issuerMetadataValidator(baseline) is Valid)
        assertTrue(issuerMetadataValidator(baseline.copy(authorizationServers = listOf("https://authorization.example:8443/Tenant/CaseSensitive"))) is Valid)
    }

    private fun validMetadata() = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example",
        credentialEndpoint = "https://issuer.example/credential",
        credentialConfigurationsSupported = mapOf(
            "IdentityCredential" to CredentialConfigurationSupported(
                format = "dc+sd-jwt",
                vct = "https://credentials.example/identity",
                cryptographicBindingMethodsSupported = listOf("jwk"),
                credentialSigningAlgValuesSupported = listOf(JsonPrimitive("ES256")),
                proofTypesSupported = mapOf("jwt" to ProofTypeSupported(listOf("ES256"))),
            ),
        ),
    )
}
