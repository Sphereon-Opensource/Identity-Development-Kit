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
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.openid.oid4vp.holder.credentialDisclosurePathOptions
import com.sphereon.sdjwt.SdJwtPresentation
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

data class Oid4vpSdJwtHolderBindingRequest(
    val walletUnitId: String,
    val operationBinding: String?,
    val request: ResolvedOid4vpRequest,
    val selectedCredentials: List<SelectedCredential>,
)

/**
 * Applies SD-JWT holder binding (RFC 9901 Key Binding JWT) to
 * [Oid4vpSdJwtHolderBindingRequest.selectedCredentials] BEFORE they reach the generic
 * [com.sphereon.openid.oid4vp.holder.Oid4vpHolderService]. Kept as an injectable seam - mirroring
 * the OID4VCI credential-request-proof seam
 * ([com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciCredentialRequestProofProvider]) -
 * because where [SelectedCredential.holderKeyRef] is resolved differs by
 * composition root:
 * - [passthrough] leaves credentials untouched, so the generic Oid4vpHolderService signs the Key
 *   Binding JWT through its configured holder-key resolver.
 * - [SecureComponentOid4vpSdJwtHolderBindingProvider] delegates Key Binding JWT signing to [Wsca].
 *   The selected WSCD resolves the alias and uses its configured KMS for the key operation. Which
 *   KMS implementation and route the WSCD uses remains a WSCD/deployment concern. After signing,
 *   the provider clears [SelectedCredential.holderKeyRef] and marks the prepared presentation so
 *   the downstream generic command validates it without signing it a second time.
 *
 * Every wallet composition root MUST wire the WSCA-backed implementation explicitly.
 */
fun interface Oid4vpSdJwtHolderBindingProvider {
    suspend fun applyHolderBinding(request: Oid4vpSdJwtHolderBindingRequest): IdkResult<List<SelectedCredential>, IdkError>
}

/**
 * WSCA/WSCD-backed SD-JWT holder binding. Reuses [SdJwtCodec] for parsing and presentation
 * serialization, then delegates the signature to [Wsca]. The selected WSCD resolves
 * [SelectedCredential.holderKeyRef] and performs the key operation with its configured KMS.
 * The KMS implementation and local or remote routing remain hidden behind the WSCA/WSCD boundary.
 */
class SecureComponentOid4vpSdJwtHolderBindingProvider(
    private val secureComponentCryptoSurfaceProvider: () -> Wsca,
) : Oid4vpSdJwtHolderBindingProvider {
    constructor(secureComponentCryptoSurface: Wsca) : this({ secureComponentCryptoSurface })

    private val secureComponentCryptoSurface: Wsca by lazy { secureComponentCryptoSurfaceProvider() }

    override suspend fun applyHolderBinding(
        request: Oid4vpSdJwtHolderBindingRequest,
    ): IdkResult<List<SelectedCredential>, IdkError> {
        val nonce = request.request.request.nonce
        val audience = request.request.verifierInfo.clientId
        val bound = mutableListOf<SelectedCredential>()
        for (credential in request.selectedCredentials) {
            val holderKeyAlias = credential.holderKeyRef
            val format = credential.credentialFormat
            if (format?.isSdJwt != true || holderKeyAlias.isNullOrBlank() || nonce.isNullOrBlank()) {
                // Nothing for this seam to add (no holder key, not an SD-JWT format), or the
                // downstream generic command will produce its own typed error (e.g. missing
                // nonce) - leave the credential untouched either way.
                bound += credential
                continue
            }
            val presentation =
                signKeyBinding(
                    walletUnitId = request.walletUnitId,
                    resolvedRequest = request.request,
                    credentialQueryId = credential.credentialQueryId,
                    operationBinding =
                        request.operationBinding?.takeIf { it.isNotBlank() }
                            ?: return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "OID4VP holder binding requires attended operation binding",
                                ),
                            ),
                    sdJwtPresentation =
                        (credential.presentation as? JsonPrimitive)
                            ?.takeIf { it.isString }
                            ?.contentOrNull
                            ?: return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "Selected SD-JWT credential '${credential.credentialId}' must be a JSON string",
                                ),
                            ),
                    audience = audience,
                    nonce = nonce,
                    holderKeyAlias = holderKeyAlias,
                ).getOrElse { return Err(it) }
            bound +=
                credential.copy(
                    presentation = JsonPrimitive(presentation),
                    holderKeyRef = null,
                    sdJwtKeyBindingApplied = true,
                )
        }
        return Ok(bound)
    }

    /**
     * Signs the RFC 9901 Section 4.3 input prepared by the shared SD-JWT module. This adapter only
     * delegates key resolution and signing to WSCA/WSCD; the selected WSCD uses its configured KMS.
     * Disclosure selection, `typ=kb+jwt`, payload construction, and `sd_hash` remain standards logic
     * in `lib/sdjwt`.
     */
    private suspend fun signKeyBinding(
        walletUnitId: String,
        resolvedRequest: ResolvedOid4vpRequest,
        credentialQueryId: String,
        operationBinding: String,
        sdJwtPresentation: String,
        audience: String,
        nonce: String,
        holderKeyAlias: String,
    ): IdkResult<String, IdkError> {
        val sdJwt = SdJwtCodec.parse(sdJwtPresentation).getOrElse { return Err(it) }
        val selection =
            runCatching {
                SdJwtPresentation.select(
                    compact = sdJwtPresentation,
                    disclosurePaths =
                        SdJwtPresentation.firstSatisfiableDisclosurePaths(
                            compact = sdJwtPresentation,
                            options = resolvedRequest.credentialDisclosurePathOptions(credentialQueryId),
                        ),
                )
            }.getOrElse {
                return Err(IdkError.fromString(code = "oid4vp.disclosure_selection_failed", message = it.message ?: "Disclosure selection failed"))
            }

        val keyRef =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = walletUnitId,
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyAlias = holderKeyAlias,
                ).getOrElse { return Err(it) }
        val publicJwk =
            keyRef.publicKeyJwk
                ?: return Err(
                    IdkError.fromString(
                        code = "oid4vp.key_binding_key_missing_jwk",
                        message = "Secure-component holder key '${keyRef.keyId}' does not carry a public JWK",
                    ),
                )
        verifyCredentialHolderKey(sdJwt.payload.undisclosedPayload, publicJwk).getOrElse { return Err(it) }

        val keyBindingInput =
            SdJwtPresentation.keyBindingInput(
                selection = selection,
                audience = audience,
                nonce = nonce,
                algorithm = keyRef.algorithm,
                issuedAtEpochSeconds = Clock.System.now().epochSeconds,
            )
        val signingRequest =
            WscaSigningRequest(
                walletUnitId = walletUnitId,
                keyRef = keyRef,
                signingInput = keyBindingInput.signingInput,
                operationBinding = operationBinding,
                audience = audience,
                nonce = nonce,
            )
        val prepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
        val signature = secureComponentCryptoSurface.sign(prepared, signingRequest).getOrElse { return Err(it) }
        return Ok(keyBindingInput.complete(signature))
    }

    /**
     * The KB-JWT MUST be signed by the key identified by the issued SD-JWT's `cnf.jwk`.
     * Comparing only the alias would allow a lost or incorrectly re-provisioned alias to produce
     * a structurally valid presentation with a different key. Compare the RFC 7638 public key
     * members and fail before signing or submitting anything.
     */
    private fun verifyCredentialHolderKey(
        undisclosedPayload: JsonObject,
        resolvedPublicJwkJson: String,
    ): IdkResult<Unit, IdkError> {
        val credentialJwk =
            (undisclosedPayload["cnf"] as? JsonObject)?.get("jwk") as? JsonObject
                ?: return Err(
                    IdkError.fromString(
                        code = "oid4vp.key_binding_cnf_jwk_missing",
                        message = "SD-JWT credential does not contain the required cnf.jwk holder binding",
                    ),
                )
        val resolvedJwk =
            runCatching { Json.parseToJsonElement(resolvedPublicJwkJson) as? JsonObject }
                .getOrNull()
                ?: return Err(
                    IdkError.fromString(
                        code = "oid4vp.key_binding_key_invalid_jwk",
                        message = "Secure-component holder key does not carry a valid public JWK",
                    ),
                )
        if (!samePublicKey(credentialJwk, resolvedJwk)) {
            return Err(
                IdkError.fromString(
                    code = "oid4vp.key_binding_key_mismatch",
                    message = "Secure-component holder key does not match the SD-JWT credential cnf.jwk",
                ),
            )
        }
        return Ok(Unit)
    }

}

internal fun samePublicKey(
    first: JsonObject,
    second: JsonObject,
): Boolean {
    val members =
        when (first["kty"]?.jsonPrimitive?.contentOrNull) {
            "EC" -> listOf("kty", "crv", "x", "y")
            "RSA" -> listOf("kty", "n", "e")
            "OKP" -> listOf("kty", "crv", "x")
            else -> return false
        }
    return members.all { first[it] != null && first[it] == second[it] }
}
