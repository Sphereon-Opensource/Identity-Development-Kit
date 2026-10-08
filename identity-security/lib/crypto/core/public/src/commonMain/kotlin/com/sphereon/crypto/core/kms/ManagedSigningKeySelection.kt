/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.core.kms

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType

/**
 * Verifies a managed signing selector before a provider performs cryptographic work.
 *
 * The alias is the selector: it is unique within the tenant and provider, and it alone decides
 * which key signs. The kid is a wire coordinate, published in JWKS and stamped into the JWS
 * header. This check exists so that a caller who supplies both cannot have a conflicting kid
 * silently ignored: resolve the alias without the caller's kid, and compare. A provider-native
 * alias is also a valid wire coordinate: callers may deliberately publish the alias as `kid`
 * when the KMS provider exposes that stable identifier rather than the JWK thumbprint.
 *
 * A provider that reports no canonical kid for the alias is not a conflict. Key material has no
 * intrinsic kid; it is metadata a store may or may not keep, and a PKCS12 entry keeps none. There
 * is nothing to disagree with, so selection proceeds on the alias. Refusing here instead rejects
 * a correctly provisioned key for the absence of an optional field, which reads as a missing or
 * mismatched key and is a false negative on the signing path.
 *
 * Inline key material remains authoritative and is validated by the provider's normal
 * inline-key policy.
 */
suspend fun KmsProvider.requireManagedSigningKeySelection(keyInfo: KeyInfoType<*>) {
    if (keyInfo.key != null) return

    keyInfo.alias ?: return
    keyInfo.kid ?: return
    resolveManagedSigningKeySelection(keyInfo)
}

/**
 * Resolves a keyless managed selector once and validates its optional wire kid against that same
 * canonical result. Callers that need the public key should reuse the returned value rather than
 * validating and fetching again, which could observe a different key after alias rotation.
 */
suspend fun KmsProvider.resolveManagedSigningKeySelection(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
    require(keyInfo.key == null) { "Managed signing selection cannot replace explicit inline key material" }
    val alias = requireNotNull(keyInfo.alias) { "Managed signing selection requires an alias" }
    val canonical =
        getKey(
            KeyInfo<KeyType>(
                alias = alias,
                providerId = id,
                keyVisibility = KeyVisibility.PUBLIC,
            ),
        ).toManagedPublicKeyInfo()
    val requestedKid = keyInfo.kid ?: return canonical
    val canonicalKid = canonical.kid ?: return canonical

    // A stable provider-native alias may be the protocol kid. It still identifies the already
    // resolved alias, so it is not a contradictory compound selector. This is required for
    // Azure Key Vault and other providers whose protocol key identity is an alias while their
    // resolved public JWK carries a thumbprint-native kid.
    if (requestedKid == alias) return canonical

    require(canonicalKid == requestedKid) {
        "Managed signing key selector mismatch: alias '$alias' resolved to kid '$canonicalKid' " +
            "in provider '$id', not requested kid '$requestedKid'"
    }
    return canonical
}
