/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassifier
import com.sphereon.openid.oid4vc.common.vcdm.VcdmDocumentKind
import com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ContributesTo(SessionScope::class)
interface VcdmJwtMatrixGraph {
    val jwtVcJsonFormatHandler: JwtVcJsonFormatHandler
    val vcLdJsonJwtFormatHandler: VcLdJsonJwtFormatHandler
    val verifyJwsCommand: VerifyJwsCommand
}

/**
 * JWT-only VCDM compatibility matrix.  The positive artifacts are signed by the production
 * issuer handlers and software KMS; all shape and algorithm negatives enter the production
 * classifier/JWS verifier, rather than comparing hand-written JSON strings.
 */
class VcdmJwtVersionAndAlgorithmMatrixE2ETest {
    private val ctx = Oid4vciTestContext(this)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun vcdm11AndVcdm20UseDistinctWrappersAndMediaTypeHeaders() = runTest {
        val v11 = issue(CredentialFormat.JWT_VC_JSON, SignatureAlgorithm.ECDSA_SHA256, "matrix-v11-es256")
        val v20 = issue(CredentialFormat.JWT_VC_JSON_LD, SignatureAlgorithm.ECDSA_SHA256, "matrix-v20-es256")

        val c11 = assertNotNull(VcdmClassifier.classifyCompactJws(v11.jwt).getOrNull())
        assertEquals(VcdmVersion.V1_1, c11.document.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, c11.document.kind)
        assertEquals(CredentialFormat.JWT_VC_JSON, c11.credentialFormat)
        assertTrue(c11.rawPayload.containsKey("vc"))
        assertEquals("JWT", c11.protectedHeader["typ"]?.jsonPrimitive?.content)

        val c20 = assertNotNull(VcdmClassifier.classifyCompactJws(v20.jwt).getOrNull())
        assertEquals(VcdmVersion.V2_0, c20.document.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, c20.document.kind)
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, c20.credentialFormat)
        assertFalse(c20.rawPayload.containsKey("vc"))
        assertFalse(c20.rawPayload.containsKey("vp"))
        assertEquals("vc+jwt", c20.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("vc", c20.protectedHeader["cty"]?.jsonPrimitive?.content)
    }

    @Test
    fun crossVersionQueriesAndWrapperContextConfusionFailClosed() = runTest {
        val v11 = issue(CredentialFormat.JWT_VC_JSON, SignatureAlgorithm.ECDSA_SHA256, "matrix-cross-v11")
        val v20 = issue(CredentialFormat.JWT_VC_JSON_LD, SignatureAlgorithm.ECDSA_SHA256, "matrix-cross-v20")
        assertFormatMismatch(v11.jwt, CredentialFormat.JWT_VC_JSON_LD)
        assertFormatMismatch(v20.jwt, CredentialFormat.JWT_VC_JSON)

        val v20MissingContext = rewritePayload(v20.jwt) { JsonObject(it.toMutableMap().apply { remove("@context") }) }
        val v20WrongContext = rewritePayload(v20.jwt) { withJsonField(it, "@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1")))) }
        val v20MixedContext = rewritePayload(v20.jwt) {
            withJsonField(it, "@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/credentials/v2"), JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
        }
        val v20VpWrapper = rewritePayload(v20.jwt) { withJsonField(it, "vp", JsonObject(mapOf("@context" to it["@context"]!!))) }
        val v11MixedWrapper = rewritePayload(v11.jwt) { withJsonField(it, "vp", JsonObject(emptyMap<String, JsonElement>())) }
        listOf(v20MissingContext, v20WrongContext, v20MixedContext, v20VpWrapper, v11MixedWrapper).forEach {
            assertTrue(VcdmClassifier.classifyCompactJws(it).isErr, "confused VCDM JWT must be rejected")
        }
    }

    @Test
    fun realKmsAcceptsEs256AndRs256AndRejectsProtectedOrPersistedAlgorithmMismatch() = runTest {
        val algorithms = listOf(SignatureAlgorithm.ECDSA_SHA256, SignatureAlgorithm.RSA_SHA256)
        for (algorithm in algorithms) {
            val joseAlgorithm = assertNotNull(algorithm.jose)
            val artifact = issue(CredentialFormat.JWT_VC_JSON_LD, algorithm, "matrix-${joseAlgorithm.value.lowercase()}")
            assertEquals(joseAlgorithm.value, jwtHeader(artifact.jwt)["alg"]?.jsonPrimitive?.content)
            assertTrue(verifyRaw(artifact.jwt, artifact.publicKey), "${joseAlgorithm.value} issuer signature must verify")
        }

        val valid = issue(CredentialFormat.JWT_VC_JSON, SignatureAlgorithm.ECDSA_SHA256, "matrix-alg-mismatch")
        val persistedMetadata = CredentialConfigurationSupported(
            format = CredentialFormat.JWT_VC_JSON.value,
            credentialSigningAlgValuesSupported = listOf(JsonPrimitive("RS256")),
            credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "MatrixCredential")),
        )
        val protectedAlg = jwtHeader(valid.jwt)["alg"]!!.jsonPrimitive.content
        assertFalse(
            persistedMetadata.credentialSigningAlgValuesSupported.orEmpty().any { it.jsonPrimitive.content == protectedAlg },
            "persisted metadata must not advertise a different protected-header alg",
        )
        val mismatchedMetadataJwk = assertNotNull(valid.publicKey.toManagedPublicKeyInfo().key as? Jwk).copy(alg = JwaAlgorithm.RS256)
        assertFalse(verifyWithMetadataKeyAlg(valid.jwt, mismatchedMetadataJwk.toJsonObject() as JsonObject), "JWK alg metadata mismatch must fail closed")
        assertFalse(verifyWithProtectedAlg(valid.jwt, valid.publicKey, "RS256"), "protected-header alg substitution must not verify with the original issuer key")
    }

    private suspend fun issue(format: CredentialFormat, algorithm: SignatureAlgorithm, alias: String): Artifact {
        val generated = keyManager().generateKeyResult(alias = alias, use = JwkUse.sig, alg = algorithm)
        assertTrue(generated.isOk, "real software KMS must support ${algorithm.jose!!.value}: ${if (generated.isErr) generated.error else ""}")
        val privateKey = assertNotNull(generated.value.keyPair?.joseToManagedKeyInfo(KeyVisibility.PRIVATE))
        val config = CredentialConfigurationSupported(
            format = format.value,
            credentialSigningAlgValuesSupported = listOf(JsonPrimitive(algorithm.jose!!.value)),
            credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "MatrixCredential")),
        )
        val context = IssuanceContext(
            subject = "https://matrix-holder.example/subject",
            clientId = "https://matrix-client.example",
            issuerIdentifier = "https://matrix-issuer.example",
            credentialConfigurationId = "matrix-${format.value}-${assertNotNull(algorithm.jose).value}",
            credentialConfiguration = config,
            holderBindingKey = null,
            attributes = mapOf("level" to JsonPrimitive("gold")),
            signingKeyAlias = alias,
            signingKeyMode = SigningKeyMode.JwkThumbprint,
            issuanceClockSkewInSeconds = 0,
            expirationInDays = 30,
        )
        val handler: CredentialFormatHandler = if (format == CredentialFormat.JWT_VC_JSON) graph().jwtVcJsonFormatHandler else graph().vcLdJsonJwtFormatHandler
        val result = handler.issueCredential(CredentialRequest(format = format.value), context)
        assertTrue(result.isOk, "production ${format.value} issuance must succeed: ${if (result.isErr) result.error else ""}")
        return Artifact(result.value.credential.jsonPrimitive.content, privateKey.toManagedPublicKeyInfo())
    }

    private fun assertFormatMismatch(jwt: String, declared: CredentialFormat) {
        val result = VcdmClassifier.classifyCompactJws(jwt)
        assertTrue(result.isOk)
        assertFalse(result.value.credentialFormat == declared, "${result.value.credentialFormat?.value} must not satisfy ${declared.value}")
    }

    private fun rewritePayload(jwt: String, update: (JsonObject) -> JsonObject): String {
        val parts = jwt.split('.')
        val payload = json.parseToJsonElement(parts[1].decodeFromBase64Url().decodeToString()).jsonObject
        return "${parts[0]}.${update(payload).toString().encodeToByteArray().encodeToBase64Url()}.${parts[2]}"
    }

    private fun jwtHeader(jwt: String): JsonObject = json.parseToJsonElement(jwt.substringBefore('.').decodeFromBase64Url().decodeToString()).jsonObject

    private suspend fun verifyRaw(jwt: String, key: ManagedKeyInfoType<*>): Boolean {
        val parts = jwt.split('.')
        return keyManager().isValidRawSignature(key.toManagedPublicKeyInfo(), "${parts[0]}.${parts[1]}".encodeToByteArray(), parts[2].decodeFromBase64Url())
    }

    private suspend fun verifyWithMetadataKeyAlg(jwt: String, key: JsonObject): Boolean {
        val result = graph().verifyJwsCommand.execute(
            VerifyJwsArgs(
                jws = JwsCompact(jwt),
                trustedJwks = JsonObject(mapOf("keys" to JsonArray(listOf(key)))),
            ),
        )
        return result.isOk && result.value.isValid
    }

    private suspend fun verifyWithProtectedAlg(jwt: String, key: ManagedKeyInfoType<*>, algorithm: String): Boolean {
        val parts = jwt.split('.')
        val header = buildJsonObject { put("alg", algorithm); put("typ", "JWT") }
        val substituted = "${header.toString().encodeToByteArray().encodeToBase64Url()}.${parts[1]}.${parts[2]}"
        return verifyRaw(substituted, key)
    }

    private fun withJsonField(source: JsonObject, name: String, value: JsonElement): JsonObject =
        JsonObject(source.toMutableMap().apply { put(name, value) })

    private fun keyManager(): KeyManagerService = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
    private fun graph(): VcdmJwtMatrixGraph = ctx.session.graph as VcdmJwtMatrixGraph
    private data class Artifact(val jwt: String, val publicKey: ManagedKeyInfoType<*>)
}
