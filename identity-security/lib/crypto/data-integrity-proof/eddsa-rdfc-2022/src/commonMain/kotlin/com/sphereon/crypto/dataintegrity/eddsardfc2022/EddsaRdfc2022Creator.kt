/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.eddsardfc2022

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
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

/** W3C VC-DI-EDDSA `eddsa-rdfc-2022` Add Proof implementation. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DataIntegrityCryptosuiteCreator>())
class EddsaRdfc2022Creator(
    private val keyManagerService: KeyManagerService,
    private val linkedDataDocumentLoader: LinkedDataDocumentLoader,
) : DataIntegrityCryptosuiteCreator {
    override val cryptosuiteId: String = EddsaRdfc2022Cryptosuite.ID
    private val jsonLdProcessor: JsonLdProcessor by lazy { JsonLdProcessor(linkedDataDocumentLoader) }

    override suspend fun createProof(
        unsecuredDocument: JsonObject,
        options: ProofOptions,
    ): IdkResult<DataIntegrityProof, IdkError> {
        if (options.cryptosuite != EddsaRdfc2022Cryptosuite.ID) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EddsaRdfc2022Creator received options for cryptosuite '${options.cryptosuite}'"))
        }
        val managedKey = try {
            keyManagerService.getKeyResult(
                KeyInfo<KeyType>(
                    alias = options.signingKeyRef,
                    keyVisibility = KeyVisibility.PUBLIC,
                    keyEncoding = KeyEncoding.JOSE,
                ),
            ).getOrElse { return Err(it) }.key
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Ed25519 signing key", message = "Signing key '${options.signingKeyRef}' was not resolved"))
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EddsaRdfc2022Creator: signing key resolution failed: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        val jwk = managedKey.key as? JwkType
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "EddsaRdfc2022Creator requires a JOSE Ed25519 signing key"))
        try {
            EddsaRdfc2022Cryptosuite.requireEd25519Key(jwk)
        } catch (expected: IllegalArgumentException) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = expected.message ?: "Invalid Ed25519 signing key"))
        }
        val proofConfig = options.toEddsaRdfc2022ProofConfig()
        val hashData = try {
            EddsaRdfc2022Cryptosuite.hashData(jsonLdProcessor, unsecuredDocument, proofConfig)
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EddsaRdfc2022Creator: JSON-LD/RDFC transformation failed: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        val signature = try {
            keyManagerService.createRawSignature(
                KeyInfo<KeyType>(alias = options.signingKeyRef, signatureAlgorithm = SignatureAlgorithm.ED25519),
                hashData,
                requireX5Chain = false,
            )
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "EddsaRdfc2022Creator: signing failed for key '${options.signingKeyRef}': ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
        return try {
            Ok(proofConfig.copy(proofValue = EddsaRdfc2022Cryptosuite.encodeProofValue(signature)))
        } catch (expected: IllegalArgumentException) {
            Err(IdkError.fromString(message = "EddsaRdfc2022Creator: invalid Ed25519 signature: ${expected.message}", code = "PROOF_GENERATION_ERROR", exception = expected))
        }
    }
}

internal fun ProofOptions.toEddsaRdfc2022ProofConfig(): DataIntegrityProof =
    DataIntegrityProof(
        type = DataIntegrityProof.TYPE_DATA_INTEGRITY,
        cryptosuite = EddsaRdfc2022Cryptosuite.ID,
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
