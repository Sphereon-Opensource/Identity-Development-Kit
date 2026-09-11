/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.eddsardfc2022

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.cryptosuite.CryptosuiteVerification
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteVerifier
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.processor.JsonLdProcessor
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

/** W3C VC-DI-EDDSA `eddsa-rdfc-2022` Verify Proof implementation. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteVerifier>())
class EddsaRdfc2022Verifier(
    private val verificationMethodResolver: VerificationMethodResolver,
    private val signatureService: SimpleSignatureService,
    private val linkedDataDocumentLoader: LinkedDataDocumentLoader,
) : DataIntegrityCryptosuiteVerifier {
    override val cryptosuiteId: String = EddsaRdfc2022Cryptosuite.ID
    private val jsonLdProcessor: JsonLdProcessor by lazy { JsonLdProcessor(linkedDataDocumentLoader) }

    override suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
    ): IdkResult<CryptosuiteVerification, IdkError> {
        if (proof.cryptosuite != EddsaRdfc2022Cryptosuite.ID) return Ok(failed("$ID: cryptosuite mismatch"))
        if (proof.type != DataIntegrityProof.TYPE_DATA_INTEGRITY) return Ok(failed("$ID: proof type mismatch"))
        val signature = try {
            EddsaRdfc2022Cryptosuite.decodeProofValue(proof.proofValue)
        } catch (expected: IllegalArgumentException) {
            return Ok(failed("$ID: invalid proofValue: ${expected.message}"))
        }
        val resolution = resolveVerificationMethod(
            proof.verificationMethod,
            verificationMethodResolutionPolicy,
        )
            ?: return Ok(failed("$ID: verification method could not be resolved"))
        if (resolution.reference != proof.verificationMethod) {
            return Ok(failed("$ID: resolved verification method does not match proof.verificationMethod"))
        }
        if (resolution.controller == null || proof.proofPurpose !in resolution.authorizedProofPurposes) {
            return Ok(failed("$ID: verification method is not authorized for proofPurpose '${proof.proofPurpose.value}'"))
        }
        val key = resolution.key as? JwkType
            ?: return Ok(failed("$ID: resolved verification method is not a JOSE Ed25519 key"))
        try {
            EddsaRdfc2022Cryptosuite.requireEd25519Key(key)
        } catch (expected: IllegalArgumentException) {
            return Ok(failed("$ID: ${expected.message ?: "invalid Ed25519 verification key"}"))
        }
        val hashData = try {
            EddsaRdfc2022Cryptosuite.hashData(jsonLdProcessor, unsecuredDocument, proof.copy(proofValue = ""))
        } catch (expected: Exception) {
            return Ok(failed("$ID: JSON-LD/RDFC transformation failed: ${expected.message}"))
        }
        val valid = try {
            signatureService.isValidRawSignature(KeyInfo(key = key, kid = proof.verificationMethod), hashData, signature)
        } catch (_: Exception) {
            return Ok(failed("$ID: signature verification raised an unexpected exception"))
        }
        return Ok(
            CryptosuiteVerification(
                verified = valid,
                verifiedDocument = if (valid) unsecuredDocument else null,
                errors = if (valid) emptyList() else listOf("signature mismatch"),
            ),
        )
    }

    private suspend fun resolveVerificationMethod(
        reference: String,
        policy: VerificationMethodResolutionPolicy,
    ) = try {
        verificationMethodResolver.resolve(reference, policy).getOrNull()
    } catch (_: Exception) {
        null
    }

    private fun failed(message: String): CryptosuiteVerification = CryptosuiteVerification(verified = false, errors = listOf(message))

    private companion object { const val ID = EddsaRdfc2022Cryptosuite.ID }
}
