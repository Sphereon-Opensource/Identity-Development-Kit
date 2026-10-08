/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningProvider
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningResult
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Wallet holder JWT VP signer. It assembles the protected JWS input locally, but delegates every
 * private-key operation to [Wsca]. Header identifiers come from the explicit holder signing
 * identifier, never from the opaque secure-component alias.
 */
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpPreparedJwtSigningProvider>())
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<HolderJwtVpSigningProvider>(),
    replaces = [com.sphereon.openid.oid4vp.holder.impl.JwtServiceHolderJwtVpSigningProvider::class],
)
class SecureComponentOid4vpJwtVpSigningProvider(
    private val secureComponentCryptoSurface: Wsca,
) : HolderJwtVpSigningProvider, Oid4vpPreparedJwtSigningProvider {
    constructor(secureComponentCryptoSurfaceProvider: () -> Wsca) : this(secureComponentCryptoSurfaceProvider())

    override suspend fun sign(request: HolderJwtVpSigningRequest): IdkResult<HolderJwtVpSigningResult, IdkError> {
        val prepared = prepare(request, bindVerifier = false).getOrElse { return Err(it) }
        return finalize(prepared)
    }

    override suspend fun prepare(request: HolderJwtVpSigningRequest): IdkResult<Oid4vpPreparedJwtSigning, IdkError> =
        prepare(request, bindVerifier = true)

    private suspend fun prepare(
        request: HolderJwtVpSigningRequest,
        bindVerifier: Boolean,
    ): IdkResult<Oid4vpPreparedJwtSigning, IdkError> {
        val walletUnitId = request.walletUnitId?.takeIf { it.isNotBlank() }
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP signing requires wallet unit id"))
        val operationBinding = request.operationBinding?.takeIf { it.isNotBlank() }
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP signing requires attended operation binding"))
        if (request.keyReference.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP signing key reference is missing"))
        }
        if (request.protectedHeader.keys.any { it == "jwk" || it == "kid" || it == "alg" || it == "x5c" }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP signing does not accept caller-supplied key material or key identifiers"))
        }
        if (request.identifier.value.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP signing identifier is missing"))
        }
        val joseAlgorithm = request.signatureAlgorithm.jose
            ?.takeIf { it.type == com.sphereon.crypto.core.jose.AlgorithmType.SIGNATURE }
            ?.value
            ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Wallet JWT VP signing requires a JOSE signature algorithm, got ${request.signatureAlgorithm}",
                ),
            )
        val typ = (request.protectedHeader["typ"] as? JsonPrimitive)?.contentOrNull
        if (typ.isNullOrBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP protected header typ is missing"))
        }
        val x509Chain = (request.identifier as? HolderJwtVpSigningIdentifier.X509)?.certificateChain
        if (x509Chain != null && x509Chain.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "X.509 holder JWT VP signing requires configured certificate chain"))
        }

        val key =
            secureComponentCryptoSurface
                .ensureKey(
                    walletUnitId = walletUnitId,
                    usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                    algorithm = request.signatureAlgorithm,
                    keyAlias = request.keyReference,
                ).getOrElse { return Err(it) }
        if (!key.algorithm.equals(joseAlgorithm, ignoreCase = true)) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message =
                        "WSCA holder key '${key.keyId}' reports algorithm " +
                            "'${key.algorithm}', expected '$joseAlgorithm'",
                ),
            )
        }
        val header = buildJsonObject {
            put("alg", joseAlgorithm)
            request.protectedHeader.forEach { (name, value) -> put(name, value) }
            when (val identifier = request.identifier) {
                is HolderJwtVpSigningIdentifier.DidVerificationMethod,
                is HolderJwtVpSigningIdentifier.JwksKid,
                is HolderJwtVpSigningIdentifier.ManagedKid,
                -> put("kid", identifier.value)
                is HolderJwtVpSigningIdentifier.X509 -> put("x5c", JsonArray(identifier.certificateChain.map(::JsonPrimitive)))
            }
        }
        val protectedSegment = header.toString().encodeToByteArray().encodeToBase64Url()
        val payloadSegment = request.payload.toString().encodeToByteArray().encodeToBase64Url()
        val signingRequest =
            WscaSigningRequest(
                walletUnitId = walletUnitId,
                keyRef = key,
                signingInput = "$protectedSegment.$payloadSegment".encodeToByteArray(),
                operationBinding = operationBinding,
                audience = if (bindVerifier) (request.payload["aud"] as? JsonPrimitive)?.contentOrNull else operationBinding,
                nonce = if (bindVerifier) (request.payload["nonce"] as? JsonPrimitive)?.contentOrNull else null,
            )
        val prepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
        if (bindVerifier && ((request.payload["aud"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank() ||
            (request.payload["nonce"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank())) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared JWT VP requires verifier audience and nonce"))
        }
        val snapshot = Oid4vpPreparedJwtSigning(
            request = request, key = prepared.keyRef, protectedSegment = protectedSegment, payloadSegment = payloadSegment,
            walletUnitId = prepared.walletUnitId, walletAccountId = prepared.walletAccountId,
            operationBinding = prepared.operationBinding, operationType = prepared.operationType,
            operationHash = prepared.digestBinding, nonce = prepared.nonce, audience = prepared.audience,
        )
        return Ok(snapshot.copy(integrityBinding = integrityBinding(snapshot)))
    }

    override suspend fun finalize(snapshot: Oid4vpPreparedJwtSigning): IdkResult<HolderJwtVpSigningResult, IdkError> {
        if (snapshot.integrityBinding != integrityBinding(snapshot)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared JWT VP snapshot is corrupt"))
        }
        val request = snapshot.request
        val header = runCatching { Json.parseToJsonElement(snapshot.protectedSegment.decodeFromBase64Url().decodeToString()) }.getOrNull()
            as? kotlinx.serialization.json.JsonObject
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared JWT VP header is corrupt"))
        if (snapshot.payloadSegment != request.payload.toString().encodeToByteArray().encodeToBase64Url() ||
            snapshot.walletUnitId != request.walletUnitId || snapshot.operationBinding != request.operationBinding ||
            (header["alg"] as? JsonPrimitive)?.contentOrNull != request.signatureAlgorithm.jose?.value ||
            request.protectedHeader.any { (key, value) -> header[key] != value } || header["jwk"] != null ||
            snapshot.key.walletUnitId?.let { it != snapshot.walletUnitId } == true ||
            snapshot.key.walletAccountId != snapshot.walletAccountId) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared JWT VP signing facts changed"))
        }
        val expectedHeader = buildJsonObject {
            put("alg", request.signatureAlgorithm.jose!!.value)
            request.protectedHeader.forEach { (name, value) -> put(name, value) }
            when (val identifier = request.identifier) {
                is HolderJwtVpSigningIdentifier.X509 -> put("x5c", JsonArray(identifier.certificateChain.map(::JsonPrimitive)))
                else -> put("kid", identifier.value)
            }
        }
        if (snapshot.protectedSegment != expectedHeader.toString().encodeToByteArray().encodeToBase64Url()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared JWT VP key identifier changed"))
        }
        val signingRequest = WscaSigningRequest(
            walletUnitId = snapshot.walletUnitId, keyRef = snapshot.key,
            signingInput = "${snapshot.protectedSegment}.${snapshot.payloadSegment}".encodeToByteArray(),
            operationBinding = snapshot.operationBinding, walletAccountId = snapshot.walletAccountId,
            audience = snapshot.audience, nonce = snapshot.nonce,
        )
        val prepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
        if (prepared.keyRef != snapshot.key || prepared.walletUnitId != snapshot.walletUnitId ||
            prepared.walletAccountId != snapshot.walletAccountId || prepared.operationBinding != snapshot.operationBinding ||
            prepared.operationType != snapshot.operationType || prepared.digestBinding != snapshot.operationHash ||
            prepared.nonce != snapshot.nonce || prepared.audience != snapshot.audience ||
            !prepared.signingInput.contentEquals(signingRequest.signingInput)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Prepared JWT VP WSCA context changed"))
        }
        val signature = secureComponentCryptoSurface.sign(prepared, signingRequest).getOrElse { return Err(it) }
        if (signature.isEmpty()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP WSCA returned an empty signature"))
        return Ok(HolderJwtVpSigningResult(
            "${snapshot.protectedSegment}.${snapshot.payloadSegment}.${signature.encodeToBase64Url()}", request.identifier,
        ))
    }
    private fun integrityBinding(snapshot: Oid4vpPreparedJwtSigning): String =
        hash(Json.encodeToString(snapshot.copy(integrityBinding = "")).encodeToByteArray(), DigestAlg.SHA256).encodeToHex()

}
