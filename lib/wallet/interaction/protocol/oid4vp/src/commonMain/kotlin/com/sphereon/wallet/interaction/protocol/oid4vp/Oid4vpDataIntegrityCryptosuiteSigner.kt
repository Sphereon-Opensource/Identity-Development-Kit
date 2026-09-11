/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.dataintegrity.ecdsardfc2019.EcdsaRdfc2019Cryptosuite
import com.sphereon.crypto.dataintegrity.eddsajcs2022.EddsaJcs2022Cryptosuite
import com.sphereon.crypto.dataintegrity.eddsardfc2022.EddsaRdfc2022Cryptosuite
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.jsonld.processor.JsonLdProcessor
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import kotlinx.serialization.json.JsonObject

/**
 * Resolves the exact WSCA algorithm for a selected holder key.
 *
 * Ed25519 is intrinsic to both EdDSA suites. ECDSA deliberately has no implicit curve: the
 * wallet store or identifier/key metadata adapter must return ES256/P-256 or ES384/P-384 for the
 * selected opaque key reference. This keeps curve selection out of aliases and URI spelling.
 */
fun interface Oid4vpDataIntegritySigningAlgorithmResolver {
    suspend fun resolve(
        credential: SelectedCredential,
        cryptosuite: String,
    ): IdkResult<SignatureAlgorithm, IdkError>

    companion object {
        /** Production resolver: consume the exact algorithm persisted with the selected WSCA key. */
        val selectedCredentialMetadata: Oid4vpDataIntegritySigningAlgorithmResolver =
            Oid4vpDataIntegritySigningAlgorithmResolver { credential, _ ->
                credential.holderSigningAlgorithm?.let(::Ok)
                    ?: Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Data Integrity holder signing requires explicit WSCA key algorithm metadata",
                        ),
                    )
            }

        val strict: Oid4vpDataIntegritySigningAlgorithmResolver =
            Oid4vpDataIntegritySigningAlgorithmResolver { _, cryptosuite ->
                when (cryptosuite) {
                    EddsaJcs2022Cryptosuite.ID,
                    EddsaRdfc2022Cryptosuite.ID,
                    -> Ok(SignatureAlgorithm.ED25519)

                    EcdsaRdfc2019Cryptosuite.ID ->
                        Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "ecdsa-rdfc-2019 requires explicit P-256/ES256 or P-384/ES384 holder-key metadata",
                            ),
                        )

                    else ->
                        Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Unsupported ldp_vc Data Integrity cryptosuite '$cryptosuite'",
                            ),
                        )
                }
            }
    }
}

internal interface Oid4vpDataIntegrityCryptosuiteSigner {
    val cryptosuiteId: String

    fun supports(algorithm: SignatureAlgorithm): Boolean

    suspend fun hashData(
        unsecuredPresentation: JsonObject,
        proofConfig: DataIntegrityProof,
        algorithm: SignatureAlgorithm,
    ): ByteArray

    fun encodeProofValue(
        rawSignature: ByteArray,
        algorithm: SignatureAlgorithm,
    ): String
}

internal class Oid4vpDataIntegrityCryptosuiteSignerRegistry(
    jsonLdProcessor: JsonLdProcessor,
) {
    private val signers =
        listOf(
            EddsaJcsOid4vpCryptosuiteSigner,
            EddsaRdfcOid4vpCryptosuiteSigner(jsonLdProcessor),
            EcdsaRdfcOid4vpCryptosuiteSigner(jsonLdProcessor),
        ).associateBy { it.cryptosuiteId }

    fun require(cryptosuite: String): IdkResult<Oid4vpDataIntegrityCryptosuiteSigner, IdkError> =
        signers[cryptosuite]?.let(::Ok)
            ?: Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported ldp_vc Data Integrity cryptosuite '$cryptosuite'",
                ),
            )
}

private object EddsaJcsOid4vpCryptosuiteSigner : Oid4vpDataIntegrityCryptosuiteSigner {
    override val cryptosuiteId: String = EddsaJcs2022Cryptosuite.ID

    override fun supports(algorithm: SignatureAlgorithm): Boolean = algorithm == SignatureAlgorithm.ED25519

    override suspend fun hashData(
        unsecuredPresentation: JsonObject,
        proofConfig: DataIntegrityProof,
        algorithm: SignatureAlgorithm,
    ): ByteArray = EddsaJcs2022Cryptosuite.hashData(unsecuredPresentation, proofConfig)

    override fun encodeProofValue(
        rawSignature: ByteArray,
        algorithm: SignatureAlgorithm,
    ): String = EddsaJcs2022Cryptosuite.encodeProofValue(rawSignature)
}

private class EddsaRdfcOid4vpCryptosuiteSigner(
    private val jsonLdProcessor: JsonLdProcessor,
) : Oid4vpDataIntegrityCryptosuiteSigner {
    override val cryptosuiteId: String = EddsaRdfc2022Cryptosuite.ID

    override fun supports(algorithm: SignatureAlgorithm): Boolean = algorithm == SignatureAlgorithm.ED25519

    override suspend fun hashData(
        unsecuredPresentation: JsonObject,
        proofConfig: DataIntegrityProof,
        algorithm: SignatureAlgorithm,
    ): ByteArray = EddsaRdfc2022Cryptosuite.hashData(jsonLdProcessor, unsecuredPresentation, proofConfig)

    override fun encodeProofValue(
        rawSignature: ByteArray,
        algorithm: SignatureAlgorithm,
    ): String = EddsaRdfc2022Cryptosuite.encodeProofValue(rawSignature)
}

private class EcdsaRdfcOid4vpCryptosuiteSigner(
    private val jsonLdProcessor: JsonLdProcessor,
) : Oid4vpDataIntegrityCryptosuiteSigner {
    override val cryptosuiteId: String = EcdsaRdfc2019Cryptosuite.ID

    override fun supports(algorithm: SignatureAlgorithm): Boolean =
        algorithm == SignatureAlgorithm.ECDSA_SHA256 || algorithm == SignatureAlgorithm.ECDSA_SHA384

    override suspend fun hashData(
        unsecuredPresentation: JsonObject,
        proofConfig: DataIntegrityProof,
        algorithm: SignatureAlgorithm,
    ): ByteArray =
        EcdsaRdfc2019Cryptosuite.hashData(
            jsonLdProcessor,
            unsecuredPresentation,
            proofConfig,
            algorithm.requireEcdsaCurve(),
        )

    override fun encodeProofValue(
        rawSignature: ByteArray,
        algorithm: SignatureAlgorithm,
    ): String = EcdsaRdfc2019Cryptosuite.encodeProofValue(rawSignature, algorithm.requireEcdsaCurve())
}

private fun SignatureAlgorithm.requireEcdsaCurve(): JwaCurve =
    when (this) {
        SignatureAlgorithm.ECDSA_SHA256 -> JwaCurve.P_256
        SignatureAlgorithm.ECDSA_SHA384 -> JwaCurve.P_384
        else -> throw IllegalArgumentException("ecdsa-rdfc-2019 supports only P-256/ES256 and P-384/ES384")
    }
