/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class Oid4vpPreparedJwtSigningTest {
    private fun request() = HolderJwtVpSigningRequest(
        walletUnitId = "wallet-a",
        payload = buildJsonObject { put("iat", 1234); put("aud", "https://verifier.example"); put("nonce", "verifier-nonce") },
        keyReference = "issued-credential-key",
        signatureAlgorithm = SignatureAlgorithm.ED25519,
        identifier = HolderJwtVpSigningIdentifier.DidVerificationMethod("did:example:holder#proof"),
        protectedHeader = buildJsonObject { put("typ", "JWT") },
        operationBinding = "interaction:present:0",
    )

    @Test
    fun preparationDoesNotSignAndRestartUsesSameCredentialKeyAndBytes() = runTest {
        val original = RecordingWsca()
        val prepared = SecureComponentOid4vpJwtVpSigningProvider(original).prepare(request()).value
        assertTrue(original.signCalls.isEmpty())
        assertEquals(listOf("issued-credential-key"), original.ensureAliases)
        assertEquals("verifier-nonce", prepared.nonce)
        assertEquals("https://verifier.example", prepared.audience)
        val restored = Json.decodeFromString<Oid4vpPreparedJwtSigning>(Json.encodeToString(prepared))
        val restarted = RecordingWsca()
        val result = SecureComponentOid4vpJwtVpSigningProvider(restarted).finalize(restored)
        assertTrue(result.isOk)
        assertTrue(restarted.ensureAliases.isEmpty())
        assertEquals(1, restarted.signCalls.size)
        assertEquals(prepared.protectedSegment + "." + prepared.payloadSegment,
            restarted.signCalls.single().signingInput.decodeToString())
        assertEquals(prepared.request.identifier, result.value.identifier)
        assertEquals(prepared.request.payload.toString().encodeToByteArray().encodeToBase64Url(),
            result.value.compactJws.split('.')[1])
    }

    @Test
    fun corruptDigestPayloadAndIdentifierNeverReachSigning() = runTest {
        val wsca = RecordingWsca()
        val signer = SecureComponentOid4vpJwtVpSigningProvider(wsca)
        val prepared = signer.prepare(request()).value
        assertTrue(signer.finalize(prepared.copy(operationHash = "changed")).isErr)
        assertTrue(signer.finalize(prepared.copy(payloadSegment = "changed")).isErr)
        assertTrue(signer.finalize(prepared.copy(request = prepared.request.copy(
            identifier = HolderJwtVpSigningIdentifier.ManagedKid("different-key"),
        ))).isErr)
        assertTrue(wsca.signCalls.isEmpty())
    }
}
