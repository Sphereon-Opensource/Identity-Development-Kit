/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.statuslist.impl.sign

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborString
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1CborCodec
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.MdocCwtStatusListSigningArgs
import com.sphereon.statuslist.MdocRevocationCwtClaims
import com.sphereon.statuslist.MdocStatusListProfile
import com.sphereon.statuslist.StatusListContentTypes
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.impl.codec.MdocRevocationCwtClaimsCodecImpl
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Signs the ISO/IEC 18013-5 revocation CWT profile.
 *
 * Unlike the generic status-list signer, this path deliberately requires a non-empty certificate
 * chain and places it in the protected COSE header. It is kept as a separate service so adding
 * mdoc's text-key/binary-list claim representation cannot change generic JWT/CWT behavior.
 */
@Inject
@SingleIn(SessionScope::class)
class MdocCwtStatusListSigner(
    private val coseCryptoService: CoseCryptoService,
    private val coseSign1Codec: CoseSign1CborCodec,
    private val kms: KeyManagerService,
) {
    /** Adapt the shared status-list driver contract to the strict mdoc CWT signer. */
    suspend fun sign(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> {
        val profile = args.mdocProfile
            ?: return Err(validationError("MDOC_PROFILE_REQUIRED", "mdoc CWT signing requires an mdoc profile"))
        val payload = args.mdocPayload
            ?: return Err(validationError("MDOC_PAYLOAD_REQUIRED", "mdoc CWT signing requires an mdoc payload"))
        if (profile == MdocStatusListProfile.STATUS_LIST && payload !is com.sphereon.statuslist.MdocStatusListPayload.Token) {
            return Err(validationError("MDOC_STATUS_LIST_PAYLOAD_REQUIRED", "status_list profile requires a Token Status List payload"))
        }
        if (profile == MdocStatusListProfile.IDENTIFIER_LIST && payload !is com.sphereon.statuslist.MdocStatusListPayload.IdentifierList) {
            return Err(validationError("MDOC_IDENTIFIER_LIST_PAYLOAD_REQUIRED", "identifier_list profile requires an Identifier List payload"))
        }
        val expiresAt = args.expiresAtEpochSeconds
            ?: return Err(validationError("MDOC_EXP_REQUIRED", "mdoc revocation CWT requires exp"))
        return sign(
            com.sphereon.statuslist.MdocCwtStatusListSigningArgs(
                issuer = args.issuer,
                statusListUri = args.statusListUri,
                signingKeyName = args.signingKeyName.orEmpty(),
                expiresAtEpochSeconds = expiresAt,
                payload = payload,
                issuedAtEpochSeconds = args.issuedAtEpochSeconds,
                ttlSeconds = args.ttlSeconds,
            ),
        )
    }

    suspend fun sign(args: MdocCwtStatusListSigningArgs): IdkResult<StatusListToken, IdkError> {
        if (args.signingKeyName.isBlank()) {
            return Err(validationError("MDOC_CWT_SIGNING_KEY_REQUIRED", "mdoc CWT signing key is required"))
        }
        val managed =
            kms
                .getKeyResult(KeyInfo<Nothing>(alias = args.signingKeyName))
                .getOrElse { return Err(it) }
                .key
                ?: return Err(validationError("MDOC_CWT_SIGNING_KEY_NOT_FOUND", "No signing key for alias '${args.signingKeyName}'"))
        val jwk = managed.key as? Jwk
            ?: return Err(validationError("MDOC_CWT_SIGNING_KEY_NOT_JWK", "mdoc CWT signing requires a resolved JOSE key with x5c"))
        val chain = jwk.x5c
        if (chain.isNullOrEmpty()) {
            return Err(validationError("MDOC_CWT_PROTECTED_X5CHAIN_REQUIRED", "mdoc revocation CWT requires a non-empty protected x5chain"))
        }

        val managedCoseInfo: ManagedKeyInfoType<CoseKeyType> =
            ManagedKeyInfo(
                alias = managed.alias,
                providerId = managed.providerId,
                resolvedKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(managed),
            )
        val alg: CoseAlgorithm? =
            try {
                managedCoseInfo.signatureAlgorithm?.cose ?: managedCoseInfo.key.alg?.let { CoseAlgorithm.fromValue(toIntExact(it.value, "COSE alg")) }
            } catch (e: IllegalArgumentException) {
                return Err(validationError("MDOC_CWT_INVALID_COSE_ALG", e.message ?: "COSE alg is out of range"))
            }
        val contentType =
            when (args.payload) {
                is com.sphereon.statuslist.MdocStatusListPayload.IdentifierList -> StatusListContentTypes.IDENTIFIERLIST_CWT
                is com.sphereon.statuslist.MdocStatusListPayload.Token -> StatusListContentTypes.STATUSLIST_CWT
            }
        val header =
            CoseHeaderCbor(
                alg = alg,
                typ = CborString(contentType),
                x5chain = CborArray(chain.map { it.toCborByteString(Encoding.BASE64) }.toMutableList()),
            )
        val claims =
            MdocRevocationCwtClaims(
                issuer = args.issuer,
                subject = args.statusListUri,
                issuedAtEpochSeconds = args.issuedAtEpochSeconds,
                expiresAtEpochSeconds = args.expiresAtEpochSeconds,
                ttlSeconds = args.ttlSeconds,
                payload = args.payload,
            )
        val input =
            CoseSign1Input
                .Builder()
                .withPayload(MdocRevocationCwtClaimsCodecImpl.encode(claims))
                .withEncodePayloadAsDataItem(true)
                .withProtectedHeader(header)
                .build()
        val signed =
            try {
                coseCryptoService.sign1<Any>(
                    input = input,
                    keyInfo = KeyInfo<Nothing>(alias = managed.alias ?: args.signingKeyName, providerId = managed.providerId),
                    requireX5Chain = true,
                )
            } catch (e: Exception) {
                return Err(
                    IdkError.fromString(
                        code = "MDOC_CWT_SIGN_FAILED",
                        message = "mdoc revocation CWT signing failed: ${e.message}",
                        category = ErrorCategory.INTERNAL,
                        exception = e,
                    ),
                )
            }
        val encoded = coseSign1Codec.encode(signed.coseSign1).getOrElse { return Err(it) }
        return Ok(
            StatusListToken(
                token = encoded.encodeToBase64Url(),
                contentType = contentType,
                ttlSeconds = args.ttlSeconds,
                tokenBytes = encoded,
            ),
        )
    }

    private fun validationError(code: String, message: String): IdkError =
        IdkError.fromString(code = code, message = message, category = ErrorCategory.VALIDATION)
}

private fun toIntExact(value: Long, field: String): Int {
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "$field is outside the signed 32-bit range"
    }
    return value.toInt()
}
