/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.dataintegrity.command.AddProofInput
import com.sphereon.crypto.dataintegrity.command.AddProofOutput
import com.sphereon.crypto.dataintegrity.command.VerifyProofInput
import com.sphereon.crypto.dataintegrity.facade.DataIntegrityServiceFacade
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.DataIntegrityVerificationResult
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolution
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import com.sphereon.crypto.dataintegrity.registry.CryptosuiteRegistry
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationArgs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VcdmDataIntegrityVerifierCryptosuiteTest {
    private val issuer = "did:example:issuer"
    private val verificationMethod = "$issuer#assertion-key"

    @Test
    fun acceptsEcdsaRdfc2019ButRejectsUnknownCryptosuite() = runTest {
        val accepted = verifyWithCryptosuite("ecdsa-rdfc-2019")
        assertTrue(accepted.isOk)

        val unknown = verifyWithCryptosuite("unknown-rdfc-suite")
        assertFalse(unknown.isOk)
    }

    private suspend fun verifyWithCryptosuite(cryptosuite: String) =
        verifier(cryptosuite).verify(
            VcdmDataIntegrityVerificationArgs(
                document = credential(cryptosuite),
                expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                expectedController = issuer,
            ),
        )

    private fun verifier(cryptosuite: String): VcdmDataIntegrityVerifierImpl {
        val proof = DataIntegrityProof(
            cryptosuite = cryptosuite,
            proofPurpose = ProofPurpose.ASSERTION_METHOD,
            verificationMethod = verificationMethod,
            proofValue = "zsignature",
        )
        val document = credential(cryptosuite)
        return VcdmDataIntegrityVerifierImpl(
            facade = FakeFacade(
                DataIntegrityVerificationResult(
                    verified = true,
                    verifiedDocument = JsonObject(document - "proof"),
                    proofs = listOf(proof),
                ),
            ),
            verificationMethodResolver = FakeResolver(),
            cryptosuiteRegistry = FakeCryptosuiteRegistry(setOf("ecdsa-rdfc-2019")),
        )
    }

    private fun credential(cryptosuite: String): JsonObject = buildJsonObject {
        putJsonArray("@context") { add(JsonPrimitive("https://www.w3.org/ns/credentials/v2")) }
        putJsonArray("type") { add(JsonPrimitive("VerifiableCredential")) }
        put("issuer", issuer)
        putJsonObject("credentialSubject") {
            put("id", "did:example:subject")
            put("name", "Alice")
        }
        putJsonObject("proof") {
            put("type", "DataIntegrityProof")
            put("cryptosuite", cryptosuite)
            put("proofPurpose", "assertionMethod")
            put("verificationMethod", verificationMethod)
            put("proofValue", "zsignature")
        }
    }
}

private class FakeCryptosuiteRegistry(
    private val verifierIds: Set<String>,
) : CryptosuiteRegistry {
    override fun getCreator(cryptosuiteId: String): DataIntegrityCryptosuiteCreator? = null
    override fun getVerifier(cryptosuiteId: String): DataIntegrityCryptosuiteVerifier? =
        if (cryptosuiteId in verifierIds) object : DataIntegrityCryptosuiteVerifier {
            override val cryptosuiteId: String = cryptosuiteId
            override suspend fun verifyProof(
                unsecuredDocument: JsonObject,
                proof: DataIntegrityProof,
                verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
            ): IdkResult<com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification, IdkError> =
                error("unused")
        } else {
            null
        }

    override fun supportedCreators(): Set<String> = emptySet()
    override fun supportedVerifiers(): Set<String> = verifierIds
}

private class FakeFacade(
    private val result: DataIntegrityVerificationResult,
) : DataIntegrityServiceFacade {
    override suspend fun addProof(input: AddProofInput): IdkResult<AddProofOutput, IdkError> = error("unused")

    override suspend fun verifyProof(input: VerifyProofInput): IdkResult<DataIntegrityVerificationResult, IdkError> = Ok(result)
}

private class FakeResolver : VerificationMethodResolver {
    override suspend fun resolve(reference: String): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> =
        Ok(
            VerificationMethodResolution(
                reference = reference,
                key = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = "AQ", y = "AQ"),
                controller = "did:example:issuer",
                authorizedProofPurposes = setOf(ProofPurpose.ASSERTION_METHOD),
            ),
        )

    override suspend fun resolve(
        reference: String,
        policy: VerificationMethodResolutionPolicy,
    ): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> = resolve(reference)
}
