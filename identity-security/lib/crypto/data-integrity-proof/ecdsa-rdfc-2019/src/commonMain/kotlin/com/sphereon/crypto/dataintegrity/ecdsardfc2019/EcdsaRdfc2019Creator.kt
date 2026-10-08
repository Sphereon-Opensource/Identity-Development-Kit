/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.ecdsardfc2019

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.dataintegrity.cryptosuite.DataIntegrityCryptosuiteCreator
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.processor.JsonLdProcessor
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject

/** W3C VC-DI ECDSA `ecdsa-rdfc-2019` Add Proof implementation. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteCreator>())
class EcdsaRdfc2019Creator(
    private val keyManagerService: KeyManagerService,
    private val linkedDataDocumentLoader: LinkedDataDocumentLoader,
) : DataIntegrityCryptosuiteCreator {
    override val cryptosuiteId: String = EcdsaRdfc2019Cryptosuite.ID
    private val jsonLdProcessor: JsonLdProcessor by lazy { JsonLdProcessor(linkedDataDocumentLoader) }

    override suspend fun createProof(
        unsecuredDocument: JsonObject,
        options: ProofOptions,
    ): IdkResult<DataIntegrityProof, IdkError> {
        if (options.cryptosuite != EcdsaRdfc2019Cryptosuite.ID) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EcdsaRdfc2019Creator received options for cryptosuite '${options.cryptosuite}'"))
        }
        val managedKey = try {
            keyManagerService.getKeyResult(
                KeyInfo<KeyType>(
                    alias = options.signingKeyRef,
                    keyVisibility = KeyVisibility.PUBLIC,
                    keyEncoding = KeyEncoding.JOSE,
                ),
            ).getOrElse { return Err(it) }.key
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "ECDSA signing key", message = "Signing key '${options.signingKeyRef}' was not resolved"))
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EcdsaRdfc2019Creator: signing key resolution failed: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        val jwk = managedKey.key as? JwkType
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EcdsaRdfc2019Creator requires a JOSE EC signing key"))
        val curve = try {
            EcdsaRdfc2019Cryptosuite.curveForKey(jwk)
        } catch (expected: IllegalArgumentException) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = expected.message ?: "Unsupported ECDSA signing key"))
        }
        val proofConfig = options.toEcdsaRdfc2019ProofConfig()
        val hashData = try {
            EcdsaRdfc2019Cryptosuite.hashData(jsonLdProcessor, unsecuredDocument, proofConfig, curve)
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EcdsaRdfc2019Creator: JSON-LD/RDFC transformation failed: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        val signature = try {
            keyManagerService.createRawSignature(
                KeyInfo<KeyType>(
                    alias = options.signingKeyRef,
                    signatureAlgorithm = if (curve == JwaCurve.P_256) SignatureAlgorithm.ECDSA_SHA256 else SignatureAlgorithm.ECDSA_SHA384,
                ),
                hashData,
                requireX5Chain = false,
            )
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EcdsaRdfc2019Creator: signing failed for key '${options.signingKeyRef}': ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        return try {
            Ok(proofConfig.copy(proofValue = EcdsaRdfc2019Cryptosuite.encodeProofValue(signature, curve)))
        } catch (expected: IllegalArgumentException) {
            Err(IdkError.fromString(message = "EcdsaRdfc2019Creator: invalid ECDSA signature: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
    }
}

internal fun ProofOptions.toEcdsaRdfc2019ProofConfig(): DataIntegrityProof = DataIntegrityProof(
    type = DataIntegrityProof.TYPE_DATA_INTEGRITY,
    cryptosuite = EcdsaRdfc2019Cryptosuite.ID,
    proofPurpose = proofPurpose,
    verificationMethod = verificationMethod,
    proofValue = "",
    id = proofId,
    created = created,
    expires = expires,
    domain = domain,
    domainSet = domainSet,
    challenge = challenge,
    nonce = nonce,
    previousProof = previousProof,
    additionalProofProperties = additionalProofProperties ?: JsonObject(emptyMap()),
)
