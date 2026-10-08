/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.ecdsardfc2019

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
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

/** W3C VC-DI ECDSA `ecdsa-rdfc-2019` Verify Proof implementation. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteVerifier>())
class EcdsaRdfc2019Verifier(
    private val verificationMethodResolver: VerificationMethodResolver,
    private val signatureService: SimpleSignatureService,
    private val linkedDataDocumentLoader: LinkedDataDocumentLoader,
) : DataIntegrityCryptosuiteVerifier {
    override val cryptosuiteId: String = EcdsaRdfc2019Cryptosuite.ID
    private val jsonLdProcessor: JsonLdProcessor by lazy { JsonLdProcessor(linkedDataDocumentLoader) }

    override suspend fun verifyProof(
        unsecuredDocument: JsonObject,
        proof: DataIntegrityProof,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
    ): IdkResult<CryptosuiteVerification, IdkError> {
        if (proof.cryptosuite != EcdsaRdfc2019Cryptosuite.ID) return Ok(failed("$ID: cryptosuite mismatch"))
        if (proof.type != DataIntegrityProof.TYPE_DATA_INTEGRITY) return Ok(failed("$ID: proof type mismatch"))
        val resolution = try {
            verificationMethodResolver.resolve(proof.verificationMethod, verificationMethodResolutionPolicy).getOrNull()
        } catch (_: Exception) {
            null
        } ?: return Ok(failed("$ID: verification method could not be resolved"))
        if (resolution.reference != proof.verificationMethod) return Ok(failed("$ID: resolved verification method does not match proof.verificationMethod"))
        if (resolution.controller == null || proof.proofPurpose !in resolution.authorizedProofPurposes) {
            return Ok(failed("$ID: verification method is not authorized for proofPurpose '${proof.proofPurpose.value}'"))
        }
        val key = resolution.key as? JwkType ?: return Ok(failed("$ID: resolved verification method is not a JOSE key"))
        val curve = try {
            EcdsaRdfc2019Cryptosuite.curveForKey(key)
        } catch (expected: IllegalArgumentException) {
            return Ok(failed("$ID: invalid ECDSA verification key: ${expected.message}"))
        }
        val signature = try {
            EcdsaRdfc2019Cryptosuite.decodeProofValue(proof.proofValue, curve)
        } catch (expected: IllegalArgumentException) {
            return Ok(failed("$ID: invalid proofValue: ${expected.message}"))
        }
        val hashData = try {
            EcdsaRdfc2019Cryptosuite.hashData(jsonLdProcessor, unsecuredDocument, proof.copy(proofValue = ""), curve)
        } catch (expected: Exception) {
            return Ok(failed("$ID: JSON-LD/RDFC transformation failed: ${expected.message}"))
        }
        val valid = try {
            signatureService.isValidRawSignature(KeyInfo<KeyType>(key = key, kid = proof.verificationMethod), hashData, signature)
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

    private fun failed(message: String): CryptosuiteVerification = CryptosuiteVerification(verified = false, errors = listOf(message))

    private companion object { const val ID = EcdsaRdfc2019Cryptosuite.ID }
}
