/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.sdjwt.SdJwt
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wsca.Wsca
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
 * because WHERE [SelectedCredential.holderKeyAlias] resolves to key material differs by
 * composition root:
 * - [passthrough] leaves credentials untouched, so the generic Oid4vpHolderService signs the Key
 *   Binding JWT itself via its own managed-KMS-identifier path (correct for a standalone consumer
 *   that provisions holder keys directly in a KeyManagerService).
 * - [SecureComponentOid4vpSdJwtHolderBindingProvider] pre-signs the Key Binding JWT here via
 *   [Wsca] and clears [SelectedCredential.holderKeyAlias] on the credentials it handled, so the
 *   downstream generic command treats them as already-final (its no-holder-key fast path). This
 *   is the wallet product's path: a Wscd-custodied key (e.g. non-extractable browser WebCrypto
 *   keys on the js target) does not round-trip through the KMS, so the generic path cannot
 *   resolve it.
 *
 * Every composition root MUST wire exactly one of the two.
 */
interface Oid4vpSdJwtHolderBindingProvider {
    suspend fun applyHolderBinding(request: Oid4vpSdJwtHolderBindingRequest): IdkResult<List<SelectedCredential>, IdkError>

    companion object {
        /** Safe default: preserves today's behavior (KB-JWT signing stays inside the generic holder). */
        val passthrough: Oid4vpSdJwtHolderBindingProvider =
            object : Oid4vpSdJwtHolderBindingProvider {
                override suspend fun applyHolderBinding(
                    request: Oid4vpSdJwtHolderBindingRequest,
                ): IdkResult<List<SelectedCredential>, IdkError> = Ok(request.selectedCredentials)
            }
    }
}

/**
 * WSCA/WSCD-backed SD-JWT holder binding. Reuses [SdJwtCodec] (parsing, presentation
 * serialization) exactly as the generic holder's own `PresentSdJwtCommandImpl` does, but signs the
 * Key Binding JWT through [Wsca.sign] instead of a KMS-managed-identifier lookup: the signing key
 * is resolved via [Wsca.ensureKey] using [SelectedCredential.holderKeyAlias] as the stable alias -
 * the same idempotent alias-based resolution [Oid4vciKeyAttestationProvider][com.sphereon.wallet.interaction.protocol.oid4vci.Oid4vciKeyAttestationProvider]
 * implementations already use to re-resolve a previously-minted holder key.
 */
class SecureComponentOid4vpSdJwtHolderBindingProvider(
    private val secureComponentCryptoSurface: Wsca,
) : Oid4vpSdJwtHolderBindingProvider {
    override suspend fun applyHolderBinding(
        request: Oid4vpSdJwtHolderBindingRequest,
    ): IdkResult<List<SelectedCredential>, IdkError> {
        val nonce = request.request.request.nonce
        val audience = request.request.verifierInfo.clientId
        val bound = mutableListOf<SelectedCredential>()
        for (credential in request.selectedCredentials) {
            val holderKeyAlias = credential.holderKeyAlias
            val format = CredentialFormat.fromValueLenient(credential.format)
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
                    operationBinding =
                        request.operationBinding?.takeIf { it.isNotBlank() }
                            ?: return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "OID4VP holder binding requires attended operation binding",
                                ),
                            ),
                    sdJwtPresentation = credential.presentation,
                    audience = audience,
                    nonce = nonce,
                    holderKeyAlias = holderKeyAlias,
                ).getOrElse { return Err(it) }
            bound += credential.copy(presentation = presentation, holderKeyAlias = null)
        }
        return Ok(bound)
    }

    /**
     * Builds and signs the RFC 9901 Section 4.3 Key Binding JWT, mirroring
     * `PresentSdJwtCommandImpl.createKeyBindingJwt`'s header/payload shape exactly (JWK-mode
     * identifier header, aud/nonce/iat/sd_hash payload, no explicit `typ` - matching the generic
     * path's actual output, not just the RFC's `kb+jwt` recommendation, since this codebase's own
     * verifier does not check `typ`) so a verifier sees the identical shape regardless of which
     * provider produced it.
     */
    private suspend fun signKeyBinding(
        walletUnitId: String,
        operationBinding: String,
        sdJwtPresentation: String,
        audience: String,
        nonce: String,
        holderKeyAlias: String,
    ): IdkResult<String, IdkError> {
        val sdJwt = SdJwtCodec.parse(sdJwtPresentation).getOrElse { return Err(it) }
        val presentationWithoutKb = SdJwtCodec.serialize(sdJwt, includeKeyBinding = false, forPresentation = true)
        val digestAlg = digestAlgorithm(sdJwt.payload.undisclosedPayload)
        val sdHash =
            hash(dataInput = presentationWithoutKb.encodeToByteArray(), digestAlgorithm = digestAlg)
                .encodeToBase64Url()

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

        // JWK-mode identifier header, matching PresentSdJwtCommandImpl's
        // CreateJwsArgs(mode = JwsIdentifierMode.JWK, opts = CreateJwsOpts(noIdentifierInHeader = false)).
        val header =
            buildJsonObject {
                put("alg", JsonPrimitive(keyRef.algorithm))
                put("jwk", Json.parseToJsonElement(publicJwk))
            }
        val payload =
            buildJsonObject {
                put("aud", JsonPrimitive(audience))
                put("nonce", JsonPrimitive(nonce))
                put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
                put("sd_hash", JsonPrimitive(sdHash))
            }
        val encodedHeader = json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
        val encodedPayload = json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        val signingInput = "$encodedHeader.$encodedPayload".encodeToByteArray()
        val signature =
            secureComponentCryptoSurface
                .sign(
                    walletUnitId = walletUnitId,
                    keyRef = keyRef,
                    signingInput = signingInput,
                    operationBinding = operationBinding,
                )
                .getOrElse { return Err(it) }
        val kbJwt = "$encodedHeader.$encodedPayload.${signature.encodeToBase64Url()}"
        return Ok("$presentationWithoutKb$kbJwt")
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

    /**
     * Mirrors `PresentSdJwtCommandImpl.getDigestAlgorithm`: reads the SD-JWT's `_sd_alg` claim from
     * the undisclosed payload, falling back to RFC 9901's SHA-256 default.
     */
    private fun digestAlgorithm(undisclosedPayload: JsonObject): DigestAlg {
        val algClaim = undisclosedPayload[SdJwt.SD_ALG_CLAIM]?.jsonPrimitive?.contentOrNull
        return algClaim
            ?.let { claim -> DigestAlg.entries.find { it.httpHeaderId?.equals(claim, ignoreCase = true) == true } }
            ?: SdJwt.DEFAULT_HASH_ALG
    }

    private companion object {
        val json =
            Json {
                encodeDefaults = false
                explicitNulls = false
            }
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
