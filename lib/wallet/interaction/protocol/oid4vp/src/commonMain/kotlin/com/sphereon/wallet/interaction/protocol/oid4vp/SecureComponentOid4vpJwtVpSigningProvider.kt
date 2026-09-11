/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningProvider
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningResult
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.WscaSigningRequest
import com.sphereon.di.session.SessionScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Wallet holder JWT VP signer. It assembles the protected JWS input locally, but delegates every
 * private-key operation to [Wsca]. Header identifiers come from the explicit holder signing
 * identifier, never from the opaque secure-component alias.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<HolderJwtVpSigningProvider>(),
    replaces = [com.sphereon.openid.oid4vp.holder.impl.JwtServiceHolderJwtVpSigningProvider::class],
)
class SecureComponentOid4vpJwtVpSigningProvider(
    private val secureComponentCryptoSurface: Wsca,
) : HolderJwtVpSigningProvider {
    constructor(secureComponentCryptoSurfaceProvider: () -> Wsca) : this(secureComponentCryptoSurfaceProvider())

    override suspend fun sign(
        request: HolderJwtVpSigningRequest,
    ): IdkResult<HolderJwtVpSigningResult, IdkError> {
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
                audience = operationBinding,
            )
        val prepared = secureComponentCryptoSurface.prepareSign(signingRequest).getOrElse { return Err(it) }
        val signature = secureComponentCryptoSurface.sign(prepared, signingRequest).getOrElse { return Err(it) }
        if (signature.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Wallet JWT VP WSCA returned an empty signature"))
        }
        return Ok(
            HolderJwtVpSigningResult(
                compactJws = "$protectedSegment.$payloadSegment.${signature.encodeToBase64Url()}",
                identifier = request.identifier,
            ),
        )
    }
}
