/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.core.security

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Multi-tenant variant of [EncryptionService] for callers that operate across tenant
 * boundaries. AppScope stores that process rows for more than one tenant need to decrypt
 * with the tenant id baked into each row, not the calling user's tenant.
 *
 * The single-tenant [EncryptionService] is preferred when the caller already has a
 * resolved user-scope context — this SPI is the explicit-tenant fallback. Same
 * underlying envelope-encryption mechanism; just takes [tenantId] at the method level
 * instead of binding it at construction time.
 *
 * Per-tenant key isolation is preserved: a tenant compromise leaks only that tenant's
 * key. Applies the same `tenants.<tenantId>.security.encryption.*` key path the per-
 * tenant config hierarchy uses, so an operator configuring keys via Vault /
 * SecretProvider doesn't have to know which kind of caller will read them.
 */
interface TenantEncryptionService {
    /**
     * Encrypt [plaintext] under [tenantId]'s active encryption key. [associatedData]
     * is bound into the auth tag (per AEAD); the same value MUST be supplied on
     * decrypt or the auth-tag check fails. The tenantId itself is automatically mixed
     * into the AAD, so a row's ciphertext cannot be lifted into another tenant's
     * namespace even if the caller forgets to supply explicit AAD.
     */
    suspend fun encrypt(
        tenantId: String,
        plaintext: ByteArray,
        associatedData: ByteArray? = null,
    ): IdkResult<EncryptedBlob, IdkError>

    /**
     * Decrypt [blob] using [tenantId]'s historical key identified by
     * [EncryptedBlob.keyId]. [associatedData] MUST match what was supplied at encrypt
     * time. The [tenantId] passed here MUST match what was used at encrypt time
     * (it's mixed into the AAD); otherwise the auth-tag check fails.
     */
    suspend fun decrypt(
        tenantId: String,
        blob: EncryptedBlob,
        associatedData: ByteArray? = null,
    ): IdkResult<ByteArray, IdkError>
}

/**
 * Multi-tenant key provider — same shape as [EncryptionKeyProvider] but takes
 * [tenantId] at the method level so a single AppScope instance can serve every tenant.
 * The default impl reads keys from `tenants.<tenantId>.security.encryption.*` in the
 * app config, which transparently picks up `${vault.…}` placeholders via the standard
 * SecretProvider chain.
 */
interface TenantEncryptionKeyProvider {
    /** Active encryption key for [tenantId], or null when unconfigured. */
    suspend fun activeKey(tenantId: String): EncryptionKeyMaterial?

    /** Look up an historical key for [tenantId] by id, or null when unknown. */
    suspend fun findKey(
        tenantId: String,
        keyId: String
    ): EncryptionKeyMaterial?
}
