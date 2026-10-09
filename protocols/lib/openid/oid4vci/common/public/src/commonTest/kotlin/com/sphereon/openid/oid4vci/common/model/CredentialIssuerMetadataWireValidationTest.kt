/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.openid.oid4vci.common.model

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.validation.issuerMetadataValidator
import io.konform.validation.Invalid
import io.konform.validation.Valid
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialIssuerMetadataWireValidationTest {
    private val json = Oid4vciJson.lenient

    @Test
    fun completeLiteralIssuerMetadataDecodesThroughOwningSerializer() {
        val decoded = json.decodeFromString<CredentialIssuerMetadata>(validWire())

        assertEquals("https://issuer.example", decoded.credentialIssuer)
        assertEquals("https://issuer.example/credential", decoded.credentialEndpoint)
        val configuration = decoded.credentialConfigurationsSupported.getValue("IdentityCredential")
        assertEquals("dc+sd-jwt", configuration.format)
        assertEquals("https://credentials.example/identity", configuration.vct)
        assertEquals(listOf(JsonPrimitive("ES256")), configuration.credentialSigningAlgValuesSupported)
        assertEquals(listOf("ES256"), configuration.proofTypesSupported?.get("jwt")?.proofSigningAlgValuesSupported)
        assertEquals(JsonPrimitive(true), decoded.additionalMetadata["custom_enabled"])
        assertEquals(JsonPrimitive("retained"), configuration.additionalParameters["format_extension"])
    }

    @Test
    fun credentialIssuerMustBeAJsonString() {
        assertEquals("https://issuer.example", json.decodeFromString<CredentialIssuerMetadata>(validWire()).credentialIssuer)
        for (invalid in listOf("12", "true", "null", "{}", "[]")) {
            assertFailsWith<SerializationException>("credential_issuer=$invalid") {
                json.decodeFromString<CredentialIssuerMetadata>(validWire(issuer = invalid))
            }
        }
    }

    @Test
    fun credentialEndpointMustBeAJsonString() {
        assertEquals("https://issuer.example/credential", json.decodeFromString<CredentialIssuerMetadata>(validWire()).credentialEndpoint)
        for (invalid in listOf("12", "false", "null", "{}", "[]")) {
            assertFailsWith<SerializationException>("credential_endpoint=$invalid") {
                json.decodeFromString<CredentialIssuerMetadata>(validWire(endpoint = invalid))
            }
        }
    }

    @Test
    fun credentialConfigurationFormatMustBeAJsonString() {
        assertEquals("dc+sd-jwt", json.decodeFromString<CredentialIssuerMetadata>(validWire()).credentialConfigurationsSupported.getValue("IdentityCredential").format)
        for (invalid in listOf("12", "true", "null", "{}", "[]")) {
            assertFailsWith<SerializationException>("format=$invalid") {
                json.decodeFromString<CredentialIssuerMetadata>(validWire(format = invalid))
            }
        }
    }

    @Test
    fun absentMandatoryMetadataMembersFailWithControlledDecodeErrors() {
        json.decodeFromString<CredentialIssuerMetadata>(validWire())
        val mandatoryKeys = listOf("credential_issuer", "credential_endpoint", "credential_configurations_supported")
        for (missing in mandatoryKeys) {
            val wire = when (missing) {
                "credential_issuer" -> """{"credential_endpoint":"https://issuer.example/credential","credential_configurations_supported":{"IdentityCredential":{"format":"dc+sd-jwt","vct":"https://credentials.example/identity"}}}"""
                "credential_endpoint" -> """{"credential_issuer":"https://issuer.example","credential_configurations_supported":{"IdentityCredential":{"format":"dc+sd-jwt","vct":"https://credentials.example/identity"}}}"""
                else -> """{"credential_issuer":"https://issuer.example","credential_endpoint":"https://issuer.example/credential"}"""
            }
            assertFailsWith<SerializationException>("missing $missing") {
                json.decodeFromString<CredentialIssuerMetadata>(wire)
            }
        }
    }

    @Test
    fun absentAndSeparateAuthorizationServersPreserveTheirDistinctWireSemantics() {
        val absentLenient = json.decodeFromString<CredentialIssuerMetadata>(validWire())
        val absentStrict = decodeStrict(validWire())
        assertNull(absentLenient.authorizationServers)
        assertNull(absentStrict.authorizationServers)
        assertTrue(issuerMetadataValidator(absentLenient) is Valid)

        val separate = "https://authorization.example:8443/Tenant/CaseSensitive"
        val wire = validWire(authorizationServers = "[\"$separate\"]")
        val decodedLenient = json.decodeFromString<CredentialIssuerMetadata>(wire)
        val decodedStrict = decodeStrict(wire)
        assertEquals(listOf(separate), decodedLenient.authorizationServers)
        assertEquals(listOf(separate), decodedStrict.authorizationServers)
        assertTrue(issuerMetadataValidator(decodedLenient) is Valid)
    }

    @Test
    fun presentEmptyAuthorizationServersDecodeButFailOwningValidation() {
        val wire = validWire(authorizationServers = "[]")
        val lenient = json.decodeFromString<CredentialIssuerMetadata>(wire)
        val strict = decodeStrict(wire)

        assertEquals(emptyList(), lenient.authorizationServers)
        assertEquals(emptyList(), strict.authorizationServers)
        assertTrue(issuerMetadataValidator(lenient) is Invalid)
        assertTrue(issuerMetadataValidator(strict) is Invalid)
    }

    @Test
    fun authorizationServersWrongContainersFailInBothOwningDecodeModes() {
        json.decodeFromString<CredentialIssuerMetadata>(validWire(authorizationServers = "[\"https://authorization.example\"]"))
        for (invalid in listOf("null", "\"https://authorization.example\"", "{}", "12", "true")) {
            val wire = validWire(authorizationServers = invalid)
            assertFailsWith<SerializationException>("lenient authorization_servers=$invalid") {
                json.decodeFromString<CredentialIssuerMetadata>(wire)
            }
            assertFailsWith<SerializationException>("strict authorization_servers=$invalid") {
                decodeStrict(wire)
            }
        }
    }

    @Test
    fun authorizationServersMixedNonStringElementsFailInBothOwningDecodeModes() {
        val validSource = validWire(authorizationServers = "[\"https://authorization.example\"]")
        json.decodeFromString<CredentialIssuerMetadata>(validSource)
        decodeStrict(validSource)
        for (invalid in listOf("12", "false", "null", "{}", "[]")) {
            val wire = validWire(authorizationServers = "[\"https://authorization.example\",$invalid]")
            assertFailsWith<SerializationException>("lenient authorization_servers item=$invalid") {
                json.decodeFromString<CredentialIssuerMetadata>(wire)
            }
            assertFailsWith<SerializationException>("strict authorization_servers item=$invalid") {
                decodeStrict(wire)
            }
        }
    }

    private fun decodeStrict(wire: String): CredentialIssuerMetadata =
        Json.decodeFromJsonElement(CredentialIssuerMetadata.serializer(), Json.parseToJsonElement(wire))

    private fun validWire(
        issuer: String = "\"https://issuer.example\"",
        endpoint: String = "\"https://issuer.example/credential\"",
        format: String = "\"dc+sd-jwt\"",
        authorizationServers: String? = null,
    ): String = """{
      "credential_issuer":$issuer,
      ${authorizationServers?.let { "\"authorization_servers\":$it," } ?: ""}
      "credential_endpoint":$endpoint,
      "credential_configurations_supported":{
        "IdentityCredential":{
          "format":$format,
          "vct":"https://credentials.example/identity",
          "cryptographic_binding_methods_supported":["jwk"],
          "credential_signing_alg_values_supported":["ES256"],
          "proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}},
          "format_extension":"retained"
        }
      },
      "custom_enabled":true
    }"""
}
