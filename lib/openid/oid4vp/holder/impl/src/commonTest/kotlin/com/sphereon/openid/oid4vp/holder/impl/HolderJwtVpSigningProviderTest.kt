package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HolderJwtVpSigningProviderTest {
    @Test
    fun `managed identifier is emitted as exact kid while key reference remains lookup-only`() = runTest {
        val jwt = RecordingJwtService()
        val provider = JwtServiceHolderJwtVpSigningProvider(jwt)

        val result = provider.sign(
            HolderJwtVpSigningRequest(
                payload = buildJsonObject { put("iss", "did:example:holder") },
                keyReference = "opaque-server-alias",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                identifier = HolderJwtVpSigningIdentifier.ManagedKid("https://wallet.example/jwks#holder-1"),
                protectedHeader = buildJsonObject { put("typ", "JWT") },
            ),
        )

        assertIs<Ok<*>>(result)
        assertEquals("https://wallet.example/jwks#holder-1", jwt.lastArgs!!.issuer!!.lookup.kid)
        assertEquals("https://wallet.example/jwks#holder-1", jwt.lastHeader["kid"]!!.toString().trim('"'))
        assertEquals("JWT", jwt.lastHeader["typ"]!!.toString().trim('"'))
    }

    @Test
    fun `DID identifier is emitted as exact verification method kid`() = runTest {
        val jwt = RecordingJwtService()
        val provider = JwtServiceHolderJwtVpSigningProvider(jwt)
        val vm = "did:example:holder#assertion-key-1"

        val result = provider.sign(
            HolderJwtVpSigningRequest(
                payload = buildJsonObject { put("nonce", "nonce-1") },
                keyReference = "opaque-key",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                identifier = HolderJwtVpSigningIdentifier.DidVerificationMethod(vm),
                protectedHeader = buildJsonObject { put("typ", "JWT") },
            ),
        )

        assertIs<Ok<*>>(result)
        assertEquals(vm, jwt.lastHeader["kid"]!!.toString().trim('"'))
    }

    @Test
    fun `X509 identifier emits the exact configured chain without a kid`() = runTest {
        val jwt = RecordingJwtService()
        val provider = JwtServiceHolderJwtVpSigningProvider(jwt)
        val chain = listOf("bGVhZi1jZXJ0", "aW50ZXJtZWRpYXRlLWNlcnQ=")

        val result = provider.sign(
            HolderJwtVpSigningRequest(
                payload = buildJsonObject { put("nonce", "nonce-1") },
                keyReference = "opaque-x509-key",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                identifier = HolderJwtVpSigningIdentifier.X509("holder-certificate", chain),
                protectedHeader = buildJsonObject { put("typ", "vp+jwt") },
            ),
        )

        assertIs<Ok<*>>(result)
        assertEquals(JsonArray(chain.map(::JsonPrimitive)), jwt.lastHeader["x5c"])
        assertTrue("kid" !in jwt.lastHeader)
    }

    @Test
    fun `server signer rejects output whose JOSE algorithm differs from the request`() = runTest {
        val provider = JwtServiceHolderJwtVpSigningProvider(RecordingJwtService())
        val result = provider.sign(
            HolderJwtVpSigningRequest(
                payload = buildJsonObject { put("nonce", "nonce-1") },
                keyReference = "opaque-key",
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA384,
                identifier = HolderJwtVpSigningIdentifier.ManagedKid("holder-key"),
                protectedHeader = buildJsonObject { put("typ", "JWT") },
            ),
        )

        assertTrue(result.isErr)
        assertTrue(result.error.message.defaultMessage.contains("expected 'ES384'"))
    }

    private class RecordingJwtService : JwtService {
        var lastArgs: CreateJwsArgs? = null
        var lastHeader: JsonObject = JsonObject(emptyMap())

        override val commands: JwtService.Commands get() = error("not used")
        override fun assembleJwsGeneral(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonGeneral = error("not used")
        override fun assembleJwsFlattened(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwsJsonFlattened = error("not used")
        override fun assembleJwsCompact(prepared: PreparedJwsObject, signatureBytes: ByteArray): JwtCompactResult = error("not used")
        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = error("not used")
        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = error("not used")
        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = error("not used")
        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> = error("not used")

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            lastArgs = args
            val configured = args.opts.protectedHeader ?: JsonObject(emptyMap())
            lastHeader = buildJsonObject {
                put("alg", "ES256")
                configured.forEach { (key, value) -> put(key, value) }
                if (args.issuer!!.identifier is String) put("kid", args.issuer!!.identifier as String)
                if (args.issuer!!.identifier is List<*>) {
                    put("x5c", JsonArray((args.issuer!!.identifier as List<*>).map { JsonPrimitive(it as String) }))
                }
            }
            val header = lastHeader.toString().encodeToByteArray().encodeToBase64Url()
            val payload = (args.payload as JsonObject).toString().encodeToByteArray().encodeToBase64Url()
            return Ok(JwtCompactResult("$header.$payload.${"signature".encodeToByteArray().encodeToBase64Url()}"))
        }
    }
}
