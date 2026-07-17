/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.command

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ResolvedPublicJwkSignatureVerifierTest {
    @Test
    fun resolvedX509PublicKeyDoesNotRequireRegisteredKmsProvider() =
        runTest {
            val keyPair = ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).keyID("verifier").generate()
            val jws =
                JWSObject(
                    JWSHeader.Builder(JWSAlgorithm.ES256).keyID("verifier").build(),
                    Payload("""{"client_id":"x509_hash:test"}"""),
                ).also { it.sign(ECDSASigner(keyPair)) }
            val publicKey = keyPair.toPublicJWK()
            val resolvedPublicKey =
                ResolvedKeyInfo<JwkType>(
                    key =
                        Jwk(
                        kty = JwaKeyType.EC,
                        crv = JwaCurve.P_256,
                        x = publicKey.x.toString(),
                        y = publicKey.y.toString(),
                        kid = publicKey.keyID,
                        alg = JwaAlgorithm.ES256,
                    ),
                    providerId = "no-provider-is-registered",
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyVisibility = KeyVisibility.PUBLIC,
                )
            val registry = mockk<KmsProviderRegistry>()
            every { registry.getProvider(any(), any()) } throws
                IllegalStateException("A resolved verifier public key must not be routed through KMS")
            val command =
                VerifyRawSignatureCommandImpl(
                    execution = mockk<SessionExecution>(relaxed = true),
                    providerRegistry = registry,
                )

            val result =
                command.execute(
                    VerifyRawSignatureArgs(
                        keyInfo = resolvedPublicKey,
                        input = jws.signingInput,
                        signature = jws.signature.decode(),
                    ),
                )

            assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
            assertTrue(result.value.isValid)
            verify(exactly = 0) { registry.getProvider(any(), any()) }
        }
}
