/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Resolves a tenant/role pair to a concrete [KeyInfoType] handle suitable
 * for dispatch through `kms.*` commands.
 *
 * Each domain that needs tenant-scoped KMS operations (vault, eIDAS signing,
 * OID4VCI issuer, reconciliation, audit, etc.) names its own roles and
 * resolves them through this registry. Per-tenant provider selection,
 * per-role algorithm selection, and per-tenant key id conventions all live
 * behind this interface.
 *
 * Role identifier convention (non-enforced): `<domain>.<role>` — for
 * example `vault.iak`, `vault.policy`, `vault.writer`,
 * `eidas.signing`, `issuer.signing`, `audit.signing`.
 *
 * Default implementations back themselves with [com.sphereon.core.api.conf.ConfigService],
 * reading `crypto.kms.roles.<role>.provider` / `crypto.kms.roles.<role>.algorithm`
 * with tenant → app fallback. Per-provider / on-prem overrides can be
 * wired by contributing alternative bindings at higher DI scopes or by
 * replacing the default resolver at the session graph level.
 *
 * Multi-provider-per-tenant is the default: the `provider` config value is
 * resolved per-role, so one tenant can route `vault.iak` to the software
 * provider while pinning `vault.writer` to AWS KMS.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsKeyRoleResolver", exact = true)
interface KmsKeyRoleResolver {
    /**
     * Resolve the [KeyInfoType] bound to the given role for the given
     * tenant. Returns null if the role has no binding — callers decide
     * whether to fall back (e.g. to a software default) or surface an error.
     *
     * [hints] carries per-role parameters that influence the resolved
     * handle but not the resolution itself — e.g. `kid` for deterministic
     * key ids keyed by a domain-specific subject. Known hint keys:
     *
     * - `kid` — overrides the resolver's default kid naming.
     * - `alias` — overrides the resolver's default alias naming.
     * - `signatureAlgorithm` — overrides the role's default algorithm
     *   (rare; usually comes from config).
     */
    suspend fun resolveKey(
        tenantId: String,
        role: String,
        hints: Map<String, String> = emptyMap(),
    ): KeyInfoType<KeyType>?

    /**
     * Resolve the default [SignatureAlgorithm] for this role. Useful for
     * callers that want to know the algorithm without materializing a full
     * KeyInfoType (e.g. verification-only paths).
     */
    suspend fun resolveSignatureAlgorithm(
        tenantId: String,
        role: String
    ): SignatureAlgorithm?

    companion object {
        /** Config key prefix for role bindings. */
        const val CONFIG_PREFIX: String = "crypto.kms.roles"
    }
}
