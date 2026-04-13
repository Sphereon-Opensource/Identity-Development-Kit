package com.sphereon.openid.oid4vp.common

import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.ClientRegistration
import com.sphereon.oauth2.common.model.ClientType
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.ResponseType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientMetadataSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
    }

    @Test
    fun `test serialize ClientMetadata with RFC 7591 base and OID4VP extensions`() {
        val baseMetadata = ClientRegistration(
            clientId = "verifier-123",
            clientName = "Test Verifier",
            clientUri = "https://verifier.example.com",
            logoUri = "https://verifier.example.com/logo.png",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            responseTypes = listOf(ResponseType.CODE),
            redirectUris = listOf("https://verifier.example.com/callback"),
            scope = "openid",
            tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST,
            jwksUri = "https://verifier.example.com/jwks",
            contacts = listOf("support@verifier.example.com"),
            tosUri = "https://verifier.example.com/tos",
            policyUri = "https://verifier.example.com/policy"
        )

        val vpFormats = mapOf(
            "jwt_vc_json" to VpFormatInfo(
                algValuesSupported = listOf("ES256", "ES384")
            ),
            "ldp_vc" to VpFormatInfo(
                proofTypesSupported = listOf("Ed25519Signature2018")
            )
        )

        val metadata = ClientMetadata(
            baseMetadata = baseMetadata,
            vpFormats = vpFormats,
            clientPurpose = "To verify your educational credentials",
            authorizationEncryptedResponseAlg = "ECDH-ES",
            authorizationEncryptedResponseEnc = "A256GCM"
        )

        val serialized = json.encodeToString(metadata)
        println("Serialized ClientMetadata:\n$serialized")

        val jsonObject = json.parseToJsonElement(serialized).jsonObject

        // Verify RFC 7591 base fields are at top level (not nested)
        assertEquals("verifier-123", jsonObject["client_id"]?.toString()?.trim('"'))
        assertEquals("Test Verifier", jsonObject["client_name"]?.toString()?.trim('"'))
        assertEquals("https://verifier.example.com", jsonObject["client_uri"]?.toString()?.trim('"'))
        assertEquals("https://verifier.example.com/jwks", jsonObject["jwks_uri"]?.toString()?.trim('"'))

        // Verify OID4VP extensions
        assertNotNull(jsonObject["vp_formats"])
        assertEquals("To verify your educational credentials", jsonObject["client_purpose"]?.toString()?.trim('"'))
        assertEquals("ECDH-ES", jsonObject["authorization_encrypted_response_alg"]?.toString()?.trim('"'))
        assertEquals("A256GCM", jsonObject["authorization_encrypted_response_enc"]?.toString()?.trim('"'))
    }

    @Test
    fun `test deserialize ClientMetadata flattens RFC 7591 and OID4VP fields`() {
        val jsonString = """
            {
                "client_id": "verifier-456",
                "client_name": "Another Verifier",
                "grant_types": ["authorization_code"],
                "redirect_uris": ["https://example.com/cb"],
                "jwks_uri": "https://example.com/jwks",
                "vp_formats": {
                    "jwt_vc_json": {
                        "alg_values": ["ES256"]
                    }
                },
                "client_purpose": "Identity verification"
            }
        """.trimIndent()

        val metadata = json.decodeFromString<ClientMetadata>(jsonString)

        // Verify RFC 7591 base fields
        assertEquals("verifier-456", metadata.clientId)
        assertEquals("Another Verifier", metadata.clientName)
        assertEquals(1, metadata.grantTypes.size)
        assertEquals(GrantType.AUTHORIZATION_CODE, metadata.grantTypes.first())
        assertEquals("https://example.com/jwks", metadata.jwksUri)

        // Verify OID4VP extensions
        assertNotNull(metadata.vpFormats)
        assertEquals(1, metadata.vpFormats?.size)
        assertEquals("Identity verification", metadata.clientPurpose)
    }

    @Test
    fun `test ClientMetadata property delegation from baseMetadata`() {
        val baseMetadata = ClientRegistration(
            clientId = "test-client",
            clientSecret = "test-secret",
            clientName = "Test Client",
            clientUri = "https://test.example.com",
            logoUri = "https://test.example.com/logo.png",
            clientType = ClientType.CONFIDENTIAL,
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            responseTypes = listOf(ResponseType.CODE),
            redirectUris = listOf("https://test.example.com/callback"),
            allowedScopes = listOf("openid", "profile"),
            scope = "openid profile",
            tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            jwksUri = "https://test.example.com/jwks",
            contacts = listOf("admin@test.example.com"),
            tosUri = "https://test.example.com/tos",
            policyUri = "https://test.example.com/policy",
            softwareId = "software-xyz",
            softwareVersion = "2.0.0",
            softwareStatement = "eyJhbGc..."
        )

        val metadata = ClientMetadata(baseMetadata = baseMetadata)

        // Test all delegated properties
        assertEquals("test-client", metadata.clientId)
        assertEquals("test-secret", metadata.clientSecret)
        assertEquals("Test Client", metadata.clientName)
        assertEquals("https://test.example.com", metadata.clientUri)
        assertEquals("https://test.example.com/logo.png", metadata.logoUri)
        assertEquals(ClientType.CONFIDENTIAL, metadata.clientType)
        assertEquals(1, metadata.grantTypes.size)
        assertEquals(1, metadata.responseTypes.size)
        assertEquals(1, metadata.redirectUris.size)
        assertEquals(2, metadata.allowedScopes?.size)
        assertEquals("openid profile", metadata.scope)
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, metadata.tokenEndpointAuthMethod)
        assertEquals("https://test.example.com/jwks", metadata.jwksUri)
        assertEquals(1, metadata.contacts?.size)
        assertEquals("https://test.example.com/tos", metadata.tosUri)
        assertEquals("https://test.example.com/policy", metadata.policyUri)
        assertEquals("software-xyz", metadata.softwareId)
        assertEquals("2.0.0", metadata.softwareVersion)
        assertEquals("eyJhbGc...", metadata.softwareStatement)
    }

    @Test
    fun `test ClientMetadata with minimal OID4VP fields`() {
        val jsonString = """
            {
                "client_id": "minimal-verifier",
                "grant_types": ["authorization_code"],
                "vp_formats": {
                    "jwt_vc_json": {
                        "alg_values": ["ES256"]
                    }
                }
            }
        """.trimIndent()

        val metadata = json.decodeFromString<ClientMetadata>(jsonString)

        assertEquals("minimal-verifier", metadata.clientId)
        assertNotNull(metadata.vpFormats)
        assertNull(metadata.clientPurpose)
        assertNull(metadata.authorizationEncryptedResponseAlg)
        assertNull(metadata.authorizationEncryptedResponseEnc)
    }

    @Test
    fun `test ClientMetadata serialization preserves unknown parameters from base`() {
        val jsonString = """
            {
                "client_id": "test-client",
                "grant_types": ["authorization_code"],
                "custom_oauth_field": "custom_value",
                "vp_formats": {
                    "jwt_vc_json": {
                        "alg_values": ["ES256"]
                    }
                }
            }
        """.trimIndent()

        val metadata = json.decodeFromString<ClientMetadata>(jsonString)
        val reserialized = json.encodeToString(metadata)
        val reserializedObj = json.parseToJsonElement(reserialized).jsonObject

        assertEquals("test-client", reserializedObj["client_id"]?.toString()?.trim('"'))
        assertEquals("custom_value", reserializedObj["custom_oauth_field"]?.toString()?.trim('"'))
        assertNotNull(reserializedObj["vp_formats"])
    }

    @Test
    fun `test VpFormatInfo serialization`() {
        val formatInfo = VpFormatInfo(
            algValuesSupported = listOf("ES256", "ES384", "EDDSA"),
            proofTypesSupported = listOf("Ed25519Signature2018", "JsonWebSignature2020")
        )

        val serialized = json.encodeToString(formatInfo)
        val deserialized = json.decodeFromString<VpFormatInfo>(serialized)

        assertEquals(3, deserialized.algValuesSupported?.size)
        assertEquals(2, deserialized.proofTypesSupported?.size)
        assertTrue(deserialized.algValuesSupported?.contains("ES256") == true)
        assertTrue(deserialized.proofTypesSupported?.contains("Ed25519Signature2018") == true)
    }

    @Test
    fun `test ClientMetadata with multiple VP formats`() {
        val jsonString = """
            {
                "client_id": "multi-format-verifier",
                "grant_types": ["authorization_code"],
                "vp_formats": {
                    "jwt_vc_json": {
                        "alg_values": ["ES256", "ES384"]
                    },
                    "ldp_vc": {
                        "proof_types_supported": ["Ed25519Signature2018"]
                    },
                    "mso_mdoc": {
                        "issuerauth_alg_values": [-7, -35],
                        "deviceauth_alg_values": [-7]
                    }
                }
            }
        """.trimIndent()

        val metadata = json.decodeFromString<ClientMetadata>(jsonString)

        assertEquals(3, metadata.vpFormats?.size)
        assertNotNull(metadata.vpFormats?.get("jwt_vc_json"))
        assertNotNull(metadata.vpFormats?.get("ldp_vc"))
        assertNotNull(metadata.vpFormats?.get("mso_mdoc"))
        assertEquals(2, metadata.vpFormats?.get("jwt_vc_json")?.algValuesSupported?.size)
        assertEquals(2, metadata.vpFormats?.get("mso_mdoc")?.issuerAuthAlgValuesSupported?.size)
        assertEquals(1, metadata.vpFormats?.get("mso_mdoc")?.deviceAuthAlgValuesSupported?.size)
    }
}

class ClientMetadataValidationTest {

    @Test
    fun `test validateClientMetadata accepts valid metadata`() {
        val baseMetadata = ClientRegistration(
            clientId = "valid-verifier",
            clientName = "Valid Verifier",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE),
            redirectUris = listOf("https://example.com/callback")
        )

        val metadata = ClientMetadata(
            baseMetadata = baseMetadata,
            vpFormats = mapOf(
                "jwt_vc_json" to VpFormatInfo(algValuesSupported = listOf("ES256"))
            ),
            clientPurpose = "Credential verification"
        )

        val result = validateClientMetadata(metadata)
        assertTrue(result.errors.isEmpty(), "Expected no validation errors but got: ${result.errors}")
    }

    @Test
    fun `test validateVpFormatInfo accepts SD-JWT format`() {
        val formatInfo = VpFormatInfo(
            sdJwtAlgValuesSupported = listOf("ES256", "ES384"),
            kbJwtAlgValuesSupported = listOf("ES256")
        )

        val result = validateVpFormatInfo(formatInfo)
        assertTrue(result.errors.isEmpty(), "Expected no validation errors but got: ${result.errors}")
    }

    @Test
    fun `test validateVpFormatInfo accepts mdoc format`() {
        val formatInfo = VpFormatInfo(
            issuerAuthAlgValuesSupported = listOf(-7, -35, -36),
            deviceAuthAlgValuesSupported = listOf(-7)
        )

        val result = validateVpFormatInfo(formatInfo)
        assertTrue(result.errors.isEmpty(), "Expected no validation errors but got: ${result.errors}")
    }

    @Test
    fun `test validateVpFormatInfo rejects empty VpFormatInfo`() {
        val formatInfo = VpFormatInfo()

        val result = validateVpFormatInfo(formatInfo)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("At least one algorithm specification") })
    }

    @Test
    fun `test validateVpFormatInfo rejects invalid algorithm identifier`() {
        val formatInfo = VpFormatInfo(
            algValuesSupported = listOf("invalid-lowercase")
        )

        val result = validateVpFormatInfo(formatInfo)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("alg_values") })
    }

    @Test
    fun `test validateClientMetadata rejects empty client_purpose`() {
        val baseMetadata = ClientRegistration(
            clientId = "test-verifier",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE)
        )

        val metadata = ClientMetadata(
            baseMetadata = baseMetadata,
            clientPurpose = ""
        )

        val result = validateClientMetadata(metadata)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("client_purpose") })
    }

    @Test
    fun `test validateClientMetadata rejects empty vp_formats map`() {
        val baseMetadata = ClientRegistration(
            clientId = "test-verifier",
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE)
        )

        val metadata = ClientMetadata(
            baseMetadata = baseMetadata,
            vpFormats = emptyMap()
        )

        val result = validateClientMetadata(metadata)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("vp_formats") })
    }

    @Test
    fun `test validateClientMetadata validates base metadata`() {
        val baseMetadata = ClientRegistration(
            clientId = "",  // Invalid: empty client_id
            grantTypes = listOf(GrantType.AUTHORIZATION_CODE)
        )

        val metadata = ClientMetadata(
            baseMetadata = baseMetadata,
            vpFormats = mapOf(
                "jwt_vc_json" to VpFormatInfo(algValuesSupported = listOf("ES256"))
            )
        )

        val result = validateClientMetadata(metadata)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.message.contains("client_id") })
    }
}

class VpFormatInfoBuilderTest {

    private val json = Json {
        prettyPrint = true
    }

    @Test
    fun `test buildVpFormatInfo with SD-JWT format`() {
        val formatInfo = buildVpFormatInfo {
            sdJwtAlgValues("ES256", "ES384")
            kbJwtAlgValues("ES256")
        }

        assertEquals(listOf("ES256", "ES384"), formatInfo.sdJwtAlgValuesSupported)
        assertEquals(listOf("ES256"), formatInfo.kbJwtAlgValuesSupported)
        assertNull(formatInfo.algValuesSupported)
    }

    @Test
    fun `test buildVpFormatInfo with mdoc format`() {
        val formatInfo = buildVpFormatInfo {
            issuerAuthAlgValues(-7, -35, -36)
            deviceAuthAlgValues(-7)
        }

        assertEquals(listOf(-7, -35, -36), formatInfo.issuerAuthAlgValuesSupported)
        assertEquals(listOf(-7), formatInfo.deviceAuthAlgValuesSupported)
    }

    @Test
    fun `test sdJwtVpFormatInfo factory`() {
        val formatInfo = sdJwtVpFormatInfo()

        assertEquals(listOf("ES256", "ES384"), formatInfo.sdJwtAlgValuesSupported)
        assertEquals(listOf("ES256", "ES384"), formatInfo.kbJwtAlgValuesSupported)
    }

    @Test
    fun `test mdocVpFormatInfo factory`() {
        val formatInfo = mdocVpFormatInfo()

        assertEquals(listOf(-7, -35, -36), formatInfo.issuerAuthAlgValuesSupported)
        assertEquals(listOf(-7, -35, -36), formatInfo.deviceAuthAlgValuesSupported)
    }

    @Test
    fun `test jwtVpFormatInfo factory`() {
        val formatInfo = jwtVpFormatInfo()

        assertEquals(listOf("ES256", "ES384", "RS256"), formatInfo.algValuesSupported)
    }

    @Test
    fun `test ldpVpFormatInfo factory`() {
        val formatInfo = ldpVpFormatInfo()

        assertEquals(listOf("Ed25519Signature2018", "JsonWebSignature2020"), formatInfo.proofTypesSupported)
    }

    @Test
    fun `test buildVpFormats DSL`() {
        val vpFormats = buildVpFormats {
            sdJwtDc()
            msoMdoc()
            jwtVpJson(listOf("ES256"))
        }

        assertEquals(3, vpFormats.size)
        assertNotNull(vpFormats["dc+sd-jwt"])
        assertNotNull(vpFormats["mso_mdoc"])
        assertNotNull(vpFormats["jwt_vp_json"])
        assertEquals(listOf("ES256"), vpFormats["jwt_vp_json"]?.algValuesSupported)
    }

    @Test
    fun `test buildVpFormats with custom format`() {
        val vpFormats = buildVpFormats {
            format("custom_format") {
                algValues("RS256", "RS384")
            }
        }

        assertEquals(1, vpFormats.size)
        assertNotNull(vpFormats["custom_format"])
        assertEquals(listOf("RS256", "RS384"), vpFormats["custom_format"]?.algValuesSupported)
    }

    @Test
    fun `test VpFormatInfo serialization with new fields`() {
        val formatInfo = sdJwtVpFormatInfo(
            sdJwtAlgValues = listOf("ES256"),
            kbJwtAlgValues = listOf("ES256")
        )

        val serialized = json.encodeToString(formatInfo)
        println("Serialized SD-JWT VpFormatInfo: $serialized")

        assertTrue(serialized.contains("sd-jwt_alg_values"))
        assertTrue(serialized.contains("kb-jwt_alg_values"))

        val deserialized = json.decodeFromString<VpFormatInfo>(serialized)
        assertEquals(formatInfo.sdJwtAlgValuesSupported, deserialized.sdJwtAlgValuesSupported)
        assertEquals(formatInfo.kbJwtAlgValuesSupported, deserialized.kbJwtAlgValuesSupported)
    }

    @Test
    fun `test VpFormatInfo serialization with mdoc COSE algorithms`() {
        val formatInfo = mdocVpFormatInfo(
            issuerAuthAlgValues = listOf(-7, -35),
            deviceAuthAlgValues = listOf(-7)
        )

        val serialized = json.encodeToString(formatInfo)
        println("Serialized mdoc VpFormatInfo: $serialized")

        assertTrue(serialized.contains("issuerauth_alg_values"))
        assertTrue(serialized.contains("deviceauth_alg_values"))
        assertTrue(serialized.contains("-7"))
        assertTrue(serialized.contains("-35"))

        val deserialized = json.decodeFromString<VpFormatInfo>(serialized)
        assertEquals(formatInfo.issuerAuthAlgValuesSupported, deserialized.issuerAuthAlgValuesSupported)
        assertEquals(formatInfo.deviceAuthAlgValuesSupported, deserialized.deviceAuthAlgValuesSupported)
    }
}
