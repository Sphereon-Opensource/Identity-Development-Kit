package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs
import com.sphereon.openid.oid4vci.holder.CredentialRequestProofPreparation
import com.sphereon.openid.oid4vci.holder.CredentialRequestProofPreparationRequest
import com.sphereon.openid.oid4vci.holder.CreatedProof
import com.sphereon.openid.oid4vci.holder.PreparedCredentialRequestProof
import com.sphereon.openid.oid4vci.holder.PreparedCredentialRequestProofBatch
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaPreparedSigning
import com.sphereon.wallet.wsca.WscaSigningRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Shared OID4VCI proof preparation/finalization used by immediate and durable flows. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialRequestProofPreparation>())
class CredentialRequestProofPreparationImpl(
    private val wsca: Wsca,
) : CredentialRequestProofPreparation {
    override suspend fun prepare(
        request: CredentialRequestProofPreparationRequest,
    ): IdkResult<PreparedCredentialRequestProofBatch, IdkError> {
        val signatureAlgorithm = signatureAlgorithm(request.signingAlgorithm).getOrElse { return Err(it) }
        val snapshots = mutableListOf<PreparedCredentialRequestProof>()
        for (signingKeyId in request.signingKeyIds) {
            val keyRef =
                wsca
                    .ensureKey(
                        walletUnitId = request.walletUnitId,
                        usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                        algorithm = signatureAlgorithm,
                        keyAlias = signingKeyId,
                    ).getOrElse { return Err(it) }
            snapshots += prepareSingle(request, keyRef).getOrElse { return Err(it) }
        }
        return Ok(PreparedCredentialRequestProofBatch(snapshots))
    }

    override suspend fun finalize(
        prepared: PreparedCredentialRequestProofBatch,
    ): IdkResult<CreatedProof, IdkError> {
        val compactProofs = mutableListOf<String>()
        for (snapshot in prepared.proofs) {
            validateSnapshotIntegrity(snapshot).getOrElse { return Err(it) }
            if (snapshot.protectedHeaderBase64Url.isBlank() || snapshot.payloadBase64Url.isBlank() ||
                snapshot.protectedHeaderBase64Url.contains('.') || snapshot.payloadBase64Url.contains('.')) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot JOSE segments must not be empty"))
            }
            val exactSigningInput = decode(snapshot.exactSigningInputBase64Url, "exact signing input").getOrElse { return Err(it) }
            val protectedHeader = decode(snapshot.protectedHeaderBase64Url, "protected header").getOrElse { return Err(it) }
            val payload = decode(snapshot.payloadBase64Url, "payload").getOrElse { return Err(it) }
            if (exactSigningInput.isEmpty() || protectedHeader.isEmpty() || payload.isEmpty()) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot JOSE segments must not be empty"))
            }
            val expectedSigningInput = "${snapshot.protectedHeaderBase64Url}.${snapshot.payloadBase64Url}".encodeToByteArray()
            if (!exactSigningInput.contentEquals(expectedSigningInput)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot signing input does not match its header and payload"))
            }
            if (snapshot.keyRef.algorithm != snapshot.algorithm || snapshot.audience != snapshot.issuerUrl) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot key or audience context is inconsistent"))
            }
            val headerObject = parseObject(protectedHeader, "protected header").getOrElse { return Err(it) }
            val payloadObject = parseObject(payload, "payload").getOrElse { return Err(it) }
            validateJoseSemantics(snapshot, headerObject, payloadObject).getOrElse { return Err(it) }
            val signingRequest =
                WscaSigningRequest(
                    walletUnitId = snapshot.walletUnitId,
                    keyRef = snapshot.keyRef,
                    signingInput = exactSigningInput,
                    operationBinding = snapshot.operationBinding,
                    walletAccountId = snapshot.walletAccountId,
                    audience = snapshot.audience,
                    nonce = snapshot.nonce,
                )
            val reprepared = wsca.prepareSign(signingRequest).getOrElse { return Err(it) }
            validatePreparedSnapshot(snapshot, reprepared, exactSigningInput).getOrElse { return Err(it) }
            val signature = wsca.sign(reprepared, signingRequest).getOrElse { return Err(it) }
            if (signature.isEmpty()) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA returned an empty credential proof signature"))
            }
            compactProofs += "${snapshot.protectedHeaderBase64Url}.${snapshot.payloadBase64Url}.${signature.encodeToBase64Url()}"
        }
        return Ok(CreatedProof(proofs = CredentialRequestProofs.jwt(compactProofs)))
    }

    private suspend fun prepareSingle(
        request: CredentialRequestProofPreparationRequest,
        keyRef: WalletAttestedKeyRef,
    ): IdkResult<PreparedCredentialRequestProof, IdkError> {
        val keyAttestationJwt = request.keyAttestationJwt?.takeIf { it.isNotBlank() }
        val segments = buildJoseSegments(request, keyRef, keyAttestationJwt).getOrElse { return Err(it) }
        val signingInput = "${segments.protectedHeader}.${segments.payload}".encodeToByteArray()
        val signingRequest =
            WscaSigningRequest(
                walletUnitId = request.walletUnitId,
                keyRef = keyRef,
                signingInput = signingInput,
                operationBinding = request.operationBinding,
                walletAccountId = request.walletAccountId ?: keyRef.walletAccountId,
                audience = request.issuerUrl,
                nonce = request.cNonce,
            )
        val prepared = wsca.prepareSign(signingRequest).getOrElse { return Err(it) }
        val snapshot =
            PreparedCredentialRequestProof(
                exactSigningInputBase64Url = signingInput.encodeToBase64Url(),
                protectedHeaderBase64Url = segments.protectedHeader,
                payloadBase64Url = segments.payload,
                keyRef = keyRef,
                algorithm = request.signingAlgorithm,
                keyInclusionMode = request.keyInclusionMode,
                walletUnitId = prepared.walletUnitId,
                walletAccountId = prepared.walletAccountId,
                operationBinding = prepared.operationBinding,
                operationType = prepared.operationType,
                digestBinding = prepared.digestBinding,
                nonce = prepared.nonce,
                audience = prepared.audience,
                issuerUrl = request.issuerUrl,
                clientId = request.clientId,
                cNonce = request.cNonce,
                iatEpochSeconds = request.iatEpochSeconds,
                keyAttestationJwt = keyAttestationJwt,
                integrityBinding = "pending",
            )
        return Ok(snapshot.copy(integrityBinding = fingerprint(snapshot)))
    }

    private fun validatePreparedSnapshot(
        snapshot: PreparedCredentialRequestProof,
        reprepared: WscaPreparedSigning,
        exactSigningInput: ByteArray,
    ): IdkResult<Unit, IdkError> {
        if (reprepared.walletUnitId != snapshot.walletUnitId ||
            reprepared.keyRef != snapshot.keyRef ||
            reprepared.walletAccountId != snapshot.walletAccountId ||
            reprepared.operationBinding != snapshot.operationBinding ||
            reprepared.operationType != snapshot.operationType ||
            reprepared.digestBinding != snapshot.digestBinding ||
            reprepared.nonce != snapshot.nonce ||
            reprepared.audience != snapshot.audience ||
            !reprepared.signingInput.contentEquals(exactSigningInput)
        ) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Re-prepared WSCA context does not match credential proof snapshot"))
        }
        return Ok(Unit)
    }

    private fun validateSnapshotIntegrity(snapshot: PreparedCredentialRequestProof): IdkResult<Unit, IdkError> =
        if (fingerprint(snapshot.copy(integrityBinding = "pending")) == snapshot.integrityBinding) {
            Ok(Unit)
        } else {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot integrity check failed"))
        }

    private fun validateJoseSemantics(
        snapshot: PreparedCredentialRequestProof,
        header: JsonObject,
        payload: JsonObject,
    ): IdkResult<Unit, IdkError> {
        fun exactStringMember(jsonObject: JsonObject, name: String, expected: String?): Boolean {
            val member = jsonObject[name]
            return if (expected == null) {
                member == null
            } else {
                member is JsonPrimitive && member.isString && member.contentOrNull == expected
            }
        }
        fun exactLongMember(jsonObject: JsonObject, name: String, expected: Long): Boolean {
            val member = jsonObject[name]
            return member is JsonPrimitive && !member.isString && member.longOrNull == expected
        }
        if (!exactStringMember(header, "typ", PROOF_JWT_TYP) || !exactStringMember(header, "alg", snapshot.algorithm)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof JOSE header typ/alg does not match the prepared snapshot"))
        }
        val mode =
            try {
                JwsIdentifierMode.valueOf(snapshot.keyInclusionMode)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot has an unsupported key inclusion mode", throwable = expected))
            }
        when (mode) {
            JwsIdentifierMode.KID, JwsIdentifierMode.DID -> {
                if (!exactStringMember(header, "kid", snapshot.keyRef.keyId) || (mode == JwsIdentifierMode.DID && !snapshot.keyRef.keyId.startsWith("did:"))) {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof JOSE kid does not match the selected key mode"))
                }
            }
            JwsIdentifierMode.JWK, JwsIdentifierMode.AUTO -> {
                val publicJwk = snapshot.keyRef.publicKeyJwk ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof JWK mode requires a public JWK"))
                val headerJwk =
                    try {
                        header["jwk"]?.jsonObject
                    } catch (ignored: Exception) {
                        null
                    } ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof JOSE header is missing its JWK"))
                val expectedJwk =
                    try {
                        json.parseToJsonElement(publicJwk).jsonObject
                    } catch (expected: Exception) {
                        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot public JWK is not a JSON object", throwable = expected))
                    }
                if (headerJwk != expectedJwk) {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof JOSE JWK does not match the selected key"))
                }
            }
            JwsIdentifierMode.X5C -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "X5C credential proof mode is not supported by this WSCA proof seam"))
        }
        if (!exactStringMember(header, "key_attestation", snapshot.keyAttestationJwt)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof key attestation does not match the prepared snapshot"))
        }
        if (!exactStringMember(payload, "aud", snapshot.audience) ||
            !exactLongMember(payload, "iat", snapshot.iatEpochSeconds) ||
            !exactStringMember(payload, "nonce", snapshot.cNonce) ||
            !exactStringMember(payload, "iss", snapshot.clientId)
        ) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof payload claims do not match the prepared snapshot"))
        }
        return Ok(Unit)
    }

    private fun parseObject(bytes: ByteArray, label: String): IdkResult<JsonObject, IdkError> =
        try {
            val element = json.parseToJsonElement(bytes.decodeToString())
            Ok(element.jsonObject)
        } catch (expected: Exception) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot $label must be a JSON object", throwable = expected))
        }

    private fun fingerprint(snapshot: PreparedCredentialRequestProof): String =
        "sha256:${hash(json.encodeToString(PreparedCredentialRequestProof.serializer(), snapshot.copy(integrityBinding = "pending")).encodeToByteArray(), DigestAlg.SHA256).encodeToHex()}"

    private fun buildJoseSegments(
        request: CredentialRequestProofPreparationRequest,
        keyRef: WalletAttestedKeyRef,
        keyAttestationJwt: String?,
    ): IdkResult<JoseSegments, IdkError> {
        val mode =
            try {
                JwsIdentifierMode.valueOf(request.keyInclusionMode)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported credential proof key inclusion mode '${request.keyInclusionMode}'", throwable = expected))
            }
        val protectedHeader =
            buildJsonObject {
                put("typ", JsonPrimitive(PROOF_JWT_TYP))
                put("alg", JsonPrimitive(request.signingAlgorithm))
                when (mode) {
                    JwsIdentifierMode.AUTO, JwsIdentifierMode.JWK -> {
                        val publicJwk = keyRef.publicKeyJwk ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WSCA holder key '${keyRef.keyId}' does not expose its public JWK"))
                        put("jwk", json.parseToJsonElement(publicJwk))
                    }
                    JwsIdentifierMode.KID -> put("kid", JsonPrimitive(keyRef.keyId))
                    JwsIdentifierMode.DID -> {
                        if (!keyRef.keyId.startsWith("did:")) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "DID proof mode requires the WSCA key id to be a DID URL"))
                        put("kid", JsonPrimitive(keyRef.keyId))
                    }
                    JwsIdentifierMode.X5C -> return Err(IdkError.INVALID_STATE(message = "X5C proof mode requires a certificate chain surfaced by the selected WSCD"))
                }
                keyAttestationJwt?.let { put("key_attestation", JsonPrimitive(it)) }
            }
        val payload =
            buildJsonObject {
                put("aud", JsonPrimitive(request.issuerUrl))
                put("iat", JsonPrimitive(request.iatEpochSeconds))
                request.cNonce?.let { put("nonce", JsonPrimitive(it)) }
                request.clientId?.let { put("iss", JsonPrimitive(it)) }
            }
        return Ok(
            JoseSegments(
                protectedHeader = json.encodeToString(protectedHeader).encodeToByteArray().encodeToBase64Url(),
                payload = json.encodeToString(payload).encodeToByteArray().encodeToBase64Url(),
            ),
        )
    }

    private fun signatureAlgorithm(value: String): IdkResult<SignatureAlgorithm, IdkError> =
        try {
            Ok(SignatureAlgorithm.fromJose(JwaAlgorithm.fromValue(value)))
        } catch (expected: Exception) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported OID4VCI proof signing algorithm '$value'", throwable = expected))
        }

    private fun decode(value: String, label: String): IdkResult<ByteArray, IdkError> =
        try {
            val decoded = value.decodeFromBase64Url()
            if (decoded.encodeToBase64Url() != value) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot has non-canonical $label encoding"))
            }
            Ok(decoded)
        } catch (expected: Exception) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential proof snapshot has invalid $label encoding", throwable = expected))
        }

    private data class JoseSegments(val protectedHeader: String, val payload: String)

    private companion object {
        const val PROOF_JWT_TYP = "openid4vci-proof+jwt"
        val json = Json { encodeDefaults = false; explicitNulls = false }
    }
}
