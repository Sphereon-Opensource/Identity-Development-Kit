/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.format.JwtVcJsonFormatHandler
import com.sphereon.openid.oid4vci.issuer.impl.format.VcLdJsonJwtFormatHandler
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Concrete handlers are exposed only for this real issuer conformance fixture. */
@ContributesTo(SessionScope::class)
interface RealIssuerFormatHandlersTestGraph {
    val vcLdJsonJwtFormatHandler: VcLdJsonJwtFormatHandler
    val jwtVcJsonFormatHandler: JwtVcJsonFormatHandler
}

/**
 * Real issuer-format conformance: both credential profiles are signed by the production
 * JwtService through the software KMS and then checked independently with the public key.
 */
class VcLdJsonJwtRealSignatureE2ETest {
    private val ctx = Oid4vciTestContext(this, protocolBasePath = "/oid4vci")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun vcdm2VcLdJsonJwtIsRealJwsAndRejectsIndependentTampering() =
        runTest {
            val alias = "j2-real-vcdm2-signing"
            val publicKey = generateIssuerKey(alias)
            val jwt = issueVcdm2(alias)
            val (header, payload) = decodeCompact(jwt)

            assertEquals("vc+jwt", header["typ"]?.jsonPrimitive?.content)
            assertEquals("vc", header["cty"]?.jsonPrimitive?.content)
            assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
            assertFalse(header["alg"]?.jsonPrimitive?.content == "none")

            val contexts = payload["@context"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("https://www.w3.org/ns/credentials/v2", contexts.first())
            assertNotNull(payload["issuer"])
            assertNotNull(payload["credentialSubject"])
            assertNotNull(payload["validFrom"])
            assertNotNull(payload["validUntil"])
            assertNotNull(payload["credentialSubject"]!!.jsonObject["claims"])
            assertFalse(payload.containsKey("claims"), "credential claims belong inside credentialSubject")
            assertFalse(payload.containsKey("vc"), "VCDM 2.0 payload must not add a vc wrapper")
            assertFalse(payload.containsKey("vp"), "VCDM 2.0 payload must not add a vp wrapper")

            assertTrue(verifyRawJws(jwt, publicKey), "the compact JWS must verify with its public key")
            assertFalse(verifyRawJws(tamperHeader(jwt), publicKey), "header tampering must fail cryptographic verification")
            assertFalse(verifyRawJws(tamperPayload(jwt), publicKey), "payload tampering must fail cryptographic verification")
            assertFalse(verifyRawJws(tamperSignature(jwt), publicKey), "signature tampering must fail cryptographic verification")
        }

    @Test
    fun vcdm11JwtVcJsonRemainsWrappedAndIndependentlySecured() =
        runTest {
            val alias = "j2-real-vcdm11-signing"
            val publicKey = generateIssuerKey(alias)
            val jwt = issueVcdm11(alias)
            val (header, payload) = decodeCompact(jwt)

            assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
            assertNotNull(payload["vc"], "VCDM 1.1 JWT representation retains its vc wrapper")
            assertFalse(payload.containsKey("vp"))
            val vc = payload["vc"]!!.jsonObject
            assertEquals("https://www.w3.org/2018/credentials/v1", vc["@context"]!!.jsonArray.first().jsonPrimitive.content)
            assertNotNull(vc["issuanceDate"], "VCDM 1.1 uses issuanceDate inside vc")
            assertFalse(vc.containsKey("validFrom"), "VCDM 1.1 does not use the VCDM 2.0 validFrom field")
            assertFalse(payload.containsKey("validFrom"))
            assertTrue(verifyRawJws(jwt, publicKey), "the VCDM 1.1 representation must also be signed")
        }

    private suspend fun generateIssuerKey(alias: String): ManagedKeyInfoType<*> {
        val kms = keyManagerService()
        val generated =
            kms.generateKeyResult(
                alias = alias,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
        assertTrue(generated.isOk, "real software KMS key generation must succeed")
        val privateKey = generated.value.keyPair?.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        assertNotNull(privateKey, "KMS must return the generated signing key")
        return privateKey.toManagedPublicKeyInfo()
    }

    private suspend fun issueVcdm2(alias: String): String {
        val config =
            CredentialConfigurationSupported(
                format = CredentialFormat.JWT_VC_JSON_LD.value,
                credentialDefinition =
                    CredentialDefinition(
                        type = listOf("VerifiableCredential", "J2Vcdm2Credential"),
                    ),
            )
        val context =
            IssuanceContext(
                subject = "did:example:j2-holder-v2",
                clientId = "j2-real-client",
                issuerIdentifier = "https://issuer.example.com/j2",
                credentialConfigurationId = "j2-vcdm2",
                credentialConfiguration = config,
                holderBindingKey = null,
                attributes = mapOf("claims" to buildJsonObject { put("level", "gold") }),
                signingKeyAlias = alias,
                signingKeyMode = SigningKeyMode.JwkThumbprint,
                issuanceClockSkewInSeconds = 0,
                expirationInDays = 30,
            )
        val handler: CredentialFormatHandler = handlers().vcLdJsonJwtFormatHandler
        val result = handler.issueCredential(CredentialRequest(format = config.format), context)
        assertTrue(result.isOk, "VCDM 2.0 issuance must succeed: ${if (result.isErr) result.error else ""}")
        return result.value.credential.jsonPrimitive.content
    }

    private suspend fun issueVcdm11(alias: String): String {
        val config =
            CredentialConfigurationSupported(
                format = CredentialFormat.JWT_VC_JSON.value,
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "J2Vcdm11Credential")),
            )
        val context =
            IssuanceContext(
                subject = "did:example:j2-holder-v1",
                clientId = "j2-real-client",
                issuerIdentifier = "https://issuer.example.com/j2",
                credentialConfigurationId = "j2-vcdm11",
                credentialConfiguration = config,
                holderBindingKey = null,
                attributes = mapOf("claims" to JsonPrimitive("gold")),
                signingKeyAlias = alias,
                signingKeyMode = SigningKeyMode.JwkThumbprint,
                issuanceClockSkewInSeconds = 0,
                expirationInDays = 30,
            )
        val handler: CredentialFormatHandler = handlers().jwtVcJsonFormatHandler
        val result = handler.issueCredential(CredentialRequest(format = config.format), context)
        assertTrue(result.isOk, "VCDM 1.1 issuance must succeed: ${if (result.isErr) result.error else ""}")
        return result.value.credential.jsonPrimitive.content
    }

    private fun handlers(): RealIssuerFormatHandlersTestGraph = ctx.session.graph as RealIssuerFormatHandlersTestGraph

    private fun keyManagerService(): KeyManagerService = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService

    private fun decodeCompact(jwt: String): Pair<JsonObject, JsonObject> {
        val parts = jwt.split('.')
        assertEquals(3, parts.size, "credential must use compact JWS serialization")
        return json.parseToJsonElement(parts[0].decodeFromBase64Url().decodeToString()).jsonObject to
            json.parseToJsonElement(parts[1].decodeFromBase64Url().decodeToString()).jsonObject
    }

    private suspend fun verifyRawJws(jwt: String, publicKey: ManagedKeyInfoType<*>): Boolean {
        val parts = jwt.split('.')
        if (parts.size != 3) return false
        return keyManagerService().isValidRawSignature(
            publicKey,
            "${parts[0]}.${parts[1]}".encodeToByteArray(),
            parts[2].decodeFromBase64Url(),
        )
    }

    private fun tamperHeader(jwt: String): String {
        val parts = jwt.split('.')
        val tampered = buildJsonObject { put("alg", "none"); put("typ", "vc+jwt"); put("cty", "vc") }
        return "${tampered.toString().encodeToByteArray().encodeToBase64Url()}.${parts[1]}.${parts[2]}"
    }

    private fun tamperPayload(jwt: String): String {
        val parts = jwt.split('.')
        val payload = json.parseToJsonElement(parts[1].decodeFromBase64Url().decodeToString()).jsonObject.toMutableMap()
        payload["issuer"] = JsonPrimitive("https://attacker.example")
        return "${parts[0]}.${JsonObject(payload).toString().encodeToByteArray().encodeToBase64Url()}.${parts[2]}"
    }

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split('.')
        val signature = parts[2].toCharArray()
        signature[0] = if (signature[0] == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.${signature.concatToString()}"
    }
}
