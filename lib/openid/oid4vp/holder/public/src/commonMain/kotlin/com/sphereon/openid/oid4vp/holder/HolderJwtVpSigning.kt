/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.sphereon.openid.oid4vp.holder

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlinx.serialization.json.JsonObject

/**
 * An explicit, caller-admitted identifier for the key that secures a JWT VP.
 *
 * A key reference is deliberately not an identifier. In particular, a secure-component alias is
 * never copied into a JWT `kid`. The signer owns the mapping from [keyReference] to private key and
 * must emit the identifier selected here (or the configured X.509 header) in the protected header.
 */
sealed interface HolderJwtVpSigningIdentifier {
    val value: String

    /** Exact DID URL for the holder verification method (for example `did:example:123#key-1`). */
    data class DidVerificationMethod(override val value: String) : HolderJwtVpSigningIdentifier

    /** Exact `kid` admitted by the holder's published JWKS. */
    data class JwksKid(override val value: String) : HolderJwtVpSigningIdentifier

    /** Exact non-DID key identifier resolved by a managed/server-side key registry. */
    data class ManagedKid(override val value: String) : HolderJwtVpSigningIdentifier

    /** Trusted X.509 material configured by the deployment, never supplied by the VP payload. */
    data class X509(
        override val value: String,
        val certificateChain: List<String>,
    ) : HolderJwtVpSigningIdentifier
}

/** Input to the holder JWT VP signing seam. */
data class HolderJwtVpSigningRequest(
    /** Wallet unit owning the key. Null for non-wallet/server compositions. */
    val walletUnitId: String? = null,
    /** OIDC/OID4VP JWT payload to sign. */
    val payload: JsonObject,
    /** Opaque WSCA/WSCD or server-side key reference; never a JOSE identifier. */
    val keyReference: String,
    /** Exact signing algorithm negotiated for this VP; never inferred by a signer. */
    val signatureAlgorithm: SignatureAlgorithm,
    /** Explicit trusted/discoverable identifier to bind into the protected header. */
    val identifier: HolderJwtVpSigningIdentifier,
    /** VP media type headers (`typ`, and for VCDM 2.0 `cty`). */
    val protectedHeader: JsonObject,
    /** Attended operation binding supplied by a wallet composition root, when applicable. */
    val operationBinding: String? = null,
)

/** Result of holder JWT VP signing. */
data class HolderJwtVpSigningResult(
    /** Compact JWS (`header.payload.signature`), never an unsecured JWT. */
    val compactJws: String,
    /** Identifier that the provider actually bound to the protected header. */
    val identifier: HolderJwtVpSigningIdentifier,
)

/**
 * Holder-side JWT VP signer. Implementations may use a server key registry or a wallet WSCA, but
 * the generic OID4VP protocol command never resolves a private key or invents a `kid` itself.
 */
fun interface HolderJwtVpSigningProvider {
    suspend fun sign(request: HolderJwtVpSigningRequest): IdkResult<HolderJwtVpSigningResult, IdkError>
}
