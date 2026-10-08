/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.StrictCompactJws
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.AdditionalDidLookupInfo
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsDid
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import com.sphereon.crypto.resolution.managed.ManagedOptsX5c
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningProvider
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * General server-side holder JWT VP signer. The protocol command depends only on the provider
 * contract; this adapter is the composition-root implementation for applications that use the
 * shared managed identifier/JWS graph. Wallets replace it with their WSCA adapter.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HolderJwtVpSigningProvider>())
class JwtServiceHolderJwtVpSigningProvider(
    private val jwtService: JwtService,
) : HolderJwtVpSigningProvider {
    override suspend fun sign(
        request: HolderJwtVpSigningRequest,
    ): IdkResult<HolderJwtVpSigningResult, IdkError> {
        if (request.keyReference.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP signing key reference is missing"))
        }
        if (request.identifier.value.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP signing identifier is missing"))
        }
        val expectedJoseAlgorithm = request.signatureAlgorithm.jose
            ?.takeIf { it.type == com.sphereon.crypto.core.jose.AlgorithmType.SIGNATURE }
            ?.value
            ?: return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Holder JWT VP signing requires a JOSE signature algorithm, got ${request.signatureAlgorithm}",
                ),
            )
        if (request.protectedHeader.keys.any { it == "jwk" || it == "kid" || it == "alg" || it == "x5c" }) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP signing does not accept caller-supplied key material or key identifiers"))
        }
        val typ = (request.protectedHeader["typ"] as? JsonPrimitive)?.contentOrNull
        if (typ.isNullOrBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP protected header typ is missing"))
        }

        val (identifier, mode) = identifierOpts(request)
        val signed =
            jwtService
                .createJwsCompact(
                    CreateJwsArgs(
                        issuer = identifier,
                        payload = request.payload,
                        mode = mode,
                        opts = CreateJwsOpts(noIssPayloadUpdate = true, protectedHeader = request.protectedHeader),
                    ),
                ).getOrElse { return Err(it) }
        val parsed = StrictCompactJws.parse(signed.jwt).getOrElse { return Err(it) }
        val algorithm = (parsed.protectedHeader["alg"] as? JsonPrimitive)?.contentOrNull
        if (algorithm.isNullOrBlank() || algorithm.equals("none", ignoreCase = true)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP signature algorithm must be present and cannot be alg:none"))
        }
        if (algorithm != expectedJoseAlgorithm) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Holder JWT VP signer emitted alg '$algorithm', expected '$expectedJoseAlgorithm'",
                ),
            )
        }
        if (parsed.protectedHeader["jwk"] != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP output must not carry embedded jwk material"))
        }
        validateIdentifierHeader(request.identifier, parsed.protectedHeader).getOrElse { return Err(it) }
        return Ok(HolderJwtVpSigningResult(compactJws = signed.jwt, identifier = request.identifier))
    }

    private fun identifierOpts(
        request: HolderJwtVpSigningRequest,
    ): Pair<ManagedIdentifierOptsOrResult, JwsIdentifierMode> =
        when (val identifier = request.identifier) {
            is HolderJwtVpSigningIdentifier.DidVerificationMethod ->
                ManagedOptsDid(
                    identifier = identifier.value,
                    lookup = AdditionalDidLookupInfo(alias = request.keyReference, kid = identifier.value),
                ) to JwsIdentifierMode.DID

            is HolderJwtVpSigningIdentifier.JwksKid ->
                ManagedOptsKid(
                    identifier = identifier.value,
                    lookup = KeyInfo(alias = request.keyReference, kid = identifier.value, signatureAlgorithm = request.signatureAlgorithm),
                ) to JwsIdentifierMode.KID

            is HolderJwtVpSigningIdentifier.ManagedKid ->
                ManagedOptsKid(
                    identifier = identifier.value,
                    lookup = KeyInfo(alias = request.keyReference, kid = identifier.value, signatureAlgorithm = request.signatureAlgorithm),
                ) to JwsIdentifierMode.KID

            is HolderJwtVpSigningIdentifier.X509 ->
                ManagedOptsX5c(
                    identifier = identifier.certificateChain,
                    lookup = AdditionalIdentifierLookup(alias = request.keyReference),
                ) to JwsIdentifierMode.X5C
        }

    private fun validateIdentifierHeader(
        identifier: HolderJwtVpSigningIdentifier,
        header: kotlinx.serialization.json.JsonObject,
    ): IdkResult<Unit, IdkError> =
        when (identifier) {
            is HolderJwtVpSigningIdentifier.DidVerificationMethod,
            is HolderJwtVpSigningIdentifier.JwksKid,
            is HolderJwtVpSigningIdentifier.ManagedKid,
            -> {
                val expected = identifier.value
                val actual = (header["kid"] as? JsonPrimitive)?.contentOrNull
                if (actual != expected) {
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Holder JWT VP protected header kid must equal the admitted identifier"))
                } else {
                    Ok(Unit)
                }
            }

            is HolderJwtVpSigningIdentifier.X509 -> {
                val x5c = header["x5c"] as? JsonArray
                val actual = x5c?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (header["kid"] != null || actual.isNullOrEmpty() || actual != identifier.certificateChain) {
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "X.509 holder JWT VP signing requires a protected x5c header and no kid"))
                } else {
                    Ok(Unit)
                }
            }
        }
}
