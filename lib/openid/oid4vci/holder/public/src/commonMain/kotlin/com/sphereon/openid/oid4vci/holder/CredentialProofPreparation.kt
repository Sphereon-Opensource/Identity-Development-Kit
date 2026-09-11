package com.sphereon.openid.oid4vci.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import kotlinx.serialization.Serializable

/**
 * The complete, serializable input needed to prepare OID4VCI JWT credential-request proofs.
 * [iatEpochSeconds] is supplied by the caller so every proof in a batch uses a fixed timestamp.
 */
@Serializable
data class CredentialRequestProofPreparationRequest(
    val walletUnitId: String,
    val walletAccountId: String? = null,
    val operationBinding: String,
    val issuerUrl: String,
    val cNonce: String? = null,
    val signingKeyIds: List<String>,
    val signingAlgorithm: String = "ES256",
    val clientId: String? = null,
    /** Serialized [com.sphereon.crypto.jose.jws.JwsIdentifierMode] name. */
    val keyInclusionMode: String = "KID",
    val keyAttestationJwt: String? = null,
    val iatEpochSeconds: Long,
) {
    init {
        require(walletUnitId.isNotBlank()) { "walletUnitId must not be blank" }
        require(operationBinding.isNotBlank()) { "operationBinding must not be blank" }
        require(issuerUrl.isNotBlank()) { "issuerUrl must not be blank" }
        require(signingKeyIds.isNotEmpty() && signingKeyIds.all { it.isNotBlank() }) { "signingKeyIds must be non-empty and non-blank" }
    }
}

/**
 * Durable proof snapshot. It contains the exact compact-JWS signing input and its already-built
 * header/payload segments, plus every WSCA context fact returned during preparation. Finalization
 * re-prepares the exact request and compares all of these values before signing.
 */
@Serializable
data class PreparedCredentialRequestProof(
    val exactSigningInputBase64Url: String,
    val protectedHeaderBase64Url: String,
    val payloadBase64Url: String,
    val keyRef: WalletAttestedKeyRef,
    val algorithm: String,
    val keyInclusionMode: String,
    val walletUnitId: String,
    val walletAccountId: String?,
    val operationBinding: String,
    val operationType: String,
    val digestBinding: String,
    val nonce: String,
    val audience: String,
    val issuerUrl: String,
    val clientId: String?,
    val cNonce: String?,
    val iatEpochSeconds: Long,
    val keyAttestationJwt: String? = null,
    /** Digest over the complete snapshot, used to detect accidental field-level mutation. */
    val integrityBinding: String,
) {
    init {
        require(walletUnitId.isNotBlank() && operationBinding.isNotBlank()) { "wallet proof identity and binding must not be blank" }
        require(operationType.isNotBlank() && digestBinding.isNotBlank() && nonce.isNotBlank() && audience.isNotBlank()) {
            "prepared WSCA context must be complete"
        }
        require(integrityBinding.isNotBlank()) { "integrityBinding must not be blank" }
    }
}

/** Ordered snapshot for a multi-proof request; each entry is finalized independently. */
@Serializable
data class PreparedCredentialRequestProofBatch(
    val proofs: List<PreparedCredentialRequestProof>,
) {
    init {
        require(proofs.isNotEmpty()) { "prepared proof batch must not be empty" }
    }
}

/** Typed seam for durable OID4VCI credential-proof preparation and finalization. */
interface CredentialRequestProofPreparation {
    suspend fun prepare(
        request: CredentialRequestProofPreparationRequest,
    ): IdkResult<PreparedCredentialRequestProofBatch, IdkError>

    suspend fun finalize(
        prepared: PreparedCredentialRequestProofBatch,
    ): IdkResult<CreatedProof, IdkError>
}
