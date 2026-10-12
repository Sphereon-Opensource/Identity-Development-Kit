/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.openid.oid4vp.holder.MdocCredentialSigningSignature
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.PreparedMdocCredentialSigning
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.wallet.interaction.WalletApprovalOperation
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A serializable mdoc operation plan. It contains no WSCA prepared handle or signature. */
@Serializable
data class Oid4vpPreparedMdocSigning(
    val signing: PreparedMdocCredentialSigning,
    val operation: WalletApprovalOperation,
    val walletUnitId: String,
    val walletAccountId: String?,
    val operationBinding: String,
    val audience: String,
    val nonce: String,
)

data class Oid4vpMdocHolderSigningRequest(
    val walletUnitId: String,
    val operationBinding: String?,
    val request: ResolvedOid4vpRequest,
    val selectedCredentials: List<SelectedCredential>,
)

internal data class Oid4vpValidatedMdocSigningContext(
    val plan: Oid4vpPreparedMdocSigning,
    val signingRequest: WscaSigningRequest,
    val wscaPrepared: com.sphereon.wallet.wsca.WscaPreparedSigning,
)

/** One-submit in-memory validation result; deliberately not serializable or stored in session state. */
class Oid4vpMdocSigningValidation internal constructor(
    internal val owner: Oid4vpMdocHolderSigningProvider,
    internal val walletUnitId: String,
    internal val operationBinding: String?,
    internal val requestFingerprint: String,
    internal val planFingerprint: String,
    internal val contexts: List<Oid4vpValidatedMdocSigningContext>,
) {
    companion object {
        internal fun issue(
            owner: Oid4vpMdocHolderSigningProvider,
            request: Oid4vpMdocHolderSigningRequest,
            plans: List<Oid4vpPreparedMdocSigning>,
            contexts: List<Oid4vpValidatedMdocSigningContext>,
        ) = Oid4vpMdocSigningValidation(
            owner = owner,
            walletUnitId = request.walletUnitId,
            operationBinding = request.operationBinding,
            requestFingerprint = request.fingerprint(),
            planFingerprint = plans.fingerprint(),
            contexts = contexts,
        )
    }
}

interface Oid4vpMdocHolderSigningProvider {
    suspend fun prepare(request: Oid4vpMdocHolderSigningRequest): IdkResult<List<Oid4vpPreparedMdocSigning>, IdkError>

    /** Re-derives and validates every operation before any presentation signature is made. */
    suspend fun validate(
        request: Oid4vpMdocHolderSigningRequest,
        prepared: List<Oid4vpPreparedMdocSigning>,
    ): IdkResult<Oid4vpMdocSigningValidation, IdkError>

    /** Re-derives the holder snapshot and WSCA digest before making any private-key operation. */
    suspend fun finalize(
        request: Oid4vpMdocHolderSigningRequest,
        prepared: List<Oid4vpPreparedMdocSigning>,
        validation: Oid4vpMdocSigningValidation,
    ): IdkResult<List<MdocCredentialSigningSignature>, IdkError>
}

/** WSCA-backed signer for the exact COSE DeviceAuthentication Sig_structure prepared by the holder. */
class SecureComponentOid4vpMdocHolderSigningProvider(
    private val holder: Oid4vpHolderService,
    private val secureComponentCryptoSurface: Wsca,
    private val coseKeyCodec: CoseKeyCborCodec,
) : Oid4vpMdocHolderSigningProvider {
    override suspend fun prepare(
        request: Oid4vpMdocHolderSigningRequest,
    ): IdkResult<List<Oid4vpPreparedMdocSigning>, IdkError> {
        val binding = request.operationBinding?.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing requires attended operation binding"))
        val nonce = request.request.request.nonce?.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing requires verifier nonce"))
        val audience = request.request.verifierInfo.clientId.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing requires verifier audience"))
        val records = holder.prepareMdocDeviceAuthenticationSignatures(request.request, request.selectedCredentials).getOrElse { return Err(it) }
        val mdocCredentialIds = request.selectedCredentials.filter { it.credentialFormat?.isMdoc == true }.map { it.credentialId }
        if (mdocCredentialIds.isNotEmpty() && records.map { it.credentialId }.toSet() != mdocCredentialIds.toSet()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signer did not prepare every selected mdoc credential"))
        }
        if (records.map { it.credentialId }.distinct().size != records.size ||
            records.any { it.keyReference.isBlank() || it.keyReference != it.signing.keyReference }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing records are incomplete, duplicated, or inconsistent"))
        }
        val plans = mutableListOf<Oid4vpPreparedMdocSigning>()
        for (record in records) {
            val key = resolveKey(request.walletUnitId, record).getOrElse { return Err(it) }
            val signingRequest = WscaSigningRequest(
                walletUnitId = request.walletUnitId,
                keyRef = key,
                signingInput = record.signing.sigStructure,
                operationBinding = binding,
                walletAccountId = key.walletAccountId,
                audience = audience,
                nonce = nonce,
            )
            val prepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
            val operationKey = prepared.keyRef.keyRef?.takeIf(String::isNotBlank) ?: prepared.keyRef.keyId
            if (operationKey.isBlank() || prepared.digestBinding.isBlank() ||
                prepared.keyRef != key || prepared.walletAccountId != key.walletAccountId ||
                prepared.walletUnitId != request.walletUnitId || prepared.operationBinding != binding ||
                prepared.audience != audience || prepared.nonce != nonce ||
                !prepared.signingInput.contentEquals(record.signing.sigStructure)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc WSCA preparation changed signing context"))
            }
            plans += Oid4vpPreparedMdocSigning(
                signing = record,
                operation = WalletApprovalOperation(operationKey, prepared.digestBinding),
                walletUnitId = prepared.walletUnitId,
                walletAccountId = prepared.walletAccountId,
                operationBinding = prepared.operationBinding,
                audience = prepared.audience,
                nonce = prepared.nonce,
            )
        }
        return Ok(plans)
    }

    override suspend fun finalize(
        request: Oid4vpMdocHolderSigningRequest,
        prepared: List<Oid4vpPreparedMdocSigning>,
        validation: Oid4vpMdocSigningValidation,
    ): IdkResult<List<MdocCredentialSigningSignature>, IdkError> {
        if (validation.owner !== this || validation.walletUnitId != request.walletUnitId ||
            validation.operationBinding != request.operationBinding || validation.requestFingerprint != request.fingerprint() ||
            validation.planFingerprint != prepared.fingerprint() || validation.contexts.size != prepared.size ||
            validation.contexts.indices.any { index -> Json.encodeToString(validation.contexts[index].plan) != Json.encodeToString(prepared[index]) }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc validated plan changed before signing"))
        }
        val signatures = mutableListOf<MdocCredentialSigningSignature>()
        for (context in validation.contexts) {
            val signature = secureComponentCryptoSurface.sign(context.wscaPrepared, context.signingRequest).getOrElse { return Err(it) }
            if (signature.isEmpty()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc WSCA returned an empty signature"))
            signatures += MdocCredentialSigningSignature(context.plan.signing.credentialId, signature)
        }
        return Ok(signatures)
    }

    override suspend fun validate(
        request: Oid4vpMdocHolderSigningRequest,
        prepared: List<Oid4vpPreparedMdocSigning>,
    ): IdkResult<Oid4vpMdocSigningValidation, IdkError> {
        val current = holder.prepareMdocDeviceAuthenticationSignatures(request.request, request.selectedCredentials).getOrElse { return Err(it) }
        if (current.isEmpty() || current.size != prepared.size || current.map { it.credentialId to it.credentialQueryId } != prepared.map { it.signing.credentialId to it.signing.credentialQueryId } ||
            current.indices.any { index -> Json.encodeToString(current[index]) != Json.encodeToString(prepared[index].signing) }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing facts changed after approval"))
        }
        val binding = request.operationBinding?.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing requires attended operation binding"))
        val nonce = request.request.request.nonce?.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing requires verifier nonce"))
        val audience = request.request.verifierInfo.clientId.takeIf(String::isNotBlank)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc signing requires verifier audience"))
        val pending = mutableListOf<Oid4vpValidatedMdocSigningContext>()
        for ((index, plan) in prepared.withIndex()) {
            if (plan.walletUnitId != request.walletUnitId || plan.operationBinding != binding || plan.audience != audience || plan.nonce != nonce) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc approved signing context changed"))
            }
            val record = current[index]
            val key = resolveKey(request.walletUnitId, record).getOrElse { return Err(it) }
            val signingRequest = WscaSigningRequest(
                walletUnitId = request.walletUnitId,
                keyRef = key,
                signingInput = record.signing.sigStructure,
                operationBinding = binding,
                walletAccountId = key.walletAccountId,
                audience = audience,
                nonce = nonce,
            )
            val wscaPrepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
            val operationKey = wscaPrepared.keyRef.keyRef?.takeIf(String::isNotBlank) ?: wscaPrepared.keyRef.keyId
            if (operationKey != plan.operation.keyRef || wscaPrepared.digestBinding != plan.operation.operationHash ||
                wscaPrepared.keyRef != key || wscaPrepared.walletAccountId != key.walletAccountId ||
                wscaPrepared.walletUnitId != plan.walletUnitId || wscaPrepared.walletAccountId != plan.walletAccountId ||
                wscaPrepared.operationBinding != plan.operationBinding || wscaPrepared.audience != plan.audience ||
                wscaPrepared.nonce != plan.nonce || !wscaPrepared.signingInput.contentEquals(record.signing.sigStructure)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OID4VP mdoc approved WSCA operation changed"))
            }
            pending += Oid4vpValidatedMdocSigningContext(plan, signingRequest, wscaPrepared)
        }
        return Ok(Oid4vpMdocSigningValidation.issue(this, request, prepared, pending))
    }

    private suspend fun resolveKey(
        walletUnitId: String,
        record: PreparedMdocCredentialSigning,
    ): IdkResult<WalletAttestedKeyRef, IdkError> {
        val keyBytes = record.signing.publicDeviceKeyCbor
        val publicKey = coseKeyCodec.decode(keyBytes).getOrElse { return Err(it) }.value
        if (publicKey.d != null || publicKey.rsaP != null || publicKey.rsaQ != null ||
            publicKey.rsaDP != null || publicKey.rsaDQ != null || publicKey.rsaQInv != null || publicKey.additional != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared mdoc device key must be public only"))
        }
        // Decoders retain source bytes on CoseKey.original; clear them to force a fresh canonical encoding.
        val canonicalKeyBytes = coseKeyCodec.encode(publicKey.copy(original = null)).getOrElse { return Err(it) }
        if (!canonicalKeyBytes.contentEquals(keyBytes)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared mdoc device key is not canonically encoded"))
        }
        val algorithm = SignatureAlgorithm.tryFromCoseForKey(record.signing.algorithm, KeyInfo(key = publicKey)).getOrElse { return Err(it) }
        val key = secureComponentCryptoSurface.ensureKey(
            walletUnitId = walletUnitId,
            usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
            algorithm = algorithm,
            keyAlias = record.keyReference,
        ).getOrElse { return Err(it) }
        val actualJwk = key.publicKeyJwk?.let { raw -> runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() }
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA mdoc holder key does not have a public JWK"))
        val expectedJwk = CoseJoseKeyMappingService.tryToJoseJwk(publicKey).getOrElse { return Err(it) }.toJsonObject() as? JsonObject
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared mdoc device key cannot be represented as a public JWK"))
        if (!samePublicKey(expectedJwk, actualJwk)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA mdoc holder key does not match selected credential device key"))
        }
        if (record.signing.providerId != null && key.keystore?.providerId != record.signing.providerId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA mdoc holder key provider does not match selected credential"))
        }
        return Ok(key)
    }
}

private fun Oid4vpMdocHolderSigningRequest.fingerprint(): String =
    hash(
        (Json.encodeToString(request) + Json.encodeToString(selectedCredentials) + walletUnitId + operationBinding.orEmpty()).encodeToByteArray(),
        DigestAlg.SHA256,
    ).encodeToHex()

private fun List<Oid4vpPreparedMdocSigning>.fingerprint(): String =
    hash(Json.encodeToString(this).encodeToByteArray(), DigestAlg.SHA256).encodeToHex()
