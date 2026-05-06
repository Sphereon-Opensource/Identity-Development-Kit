/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.security

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.security.EncryptedBlob
import com.sphereon.core.api.security.EncryptionKeyMaterial
import com.sphereon.core.api.security.TenantEncryptionKeyProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [TenantEncryptionKeyProvider] sourced from [AppConfigService] with a
 * tenant-prefixed key path. Used by AppScope multi-tenant stores
 * (`TenantIdpRegistry` etc.) whose cross-tenant queries need to decrypt rows from any
 * tenant.
 *
 * **Configuration keys** (per tenant, resolved through the standard SecretProvider chain):
 *
 *  - `tenants.<tenantId>.security.encryption.activeKeyId` — id of the active key.
 *  - `tenants.<tenantId>.security.encryption.keys.<keyId>` — base64-encoded 32-byte
 *    key material. Required for the active key; rotated-out keys stay listed until
 *    every ciphertext written under them has been re-encrypted.
 *
 * Production wiring binds the key value via `${vault.tenants/<tenantId>/encryption/<keyId>}`
 * placeholders that the `InterpolatingPropertySourcesPropertyResolver` expands via
 * Vault / AWS Secrets Manager / Azure Key Vault — the key never appears in a config
 * file or env var on disk in plaintext.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<TenantEncryptionKeyProvider>())
class ConfigBackedTenantEncryptionKeyProvider(
    private val appConfig: AppConfigService,
) : TenantEncryptionKeyProvider {
    override suspend fun activeKey(tenantId: String): EncryptionKeyMaterial? {
        // Look for a per-tenant override first; fall through to app-level default so a
        // deployment can configure a single shared key at app scope and only override
        // for tenants that need their own. Same fall-through shape SecretProvider uses.
        // Stores without per-call tenant context (e.g. federation_pending) call this
        // with [SYSTEM_TENANT] which has no per-tenant override and falls straight
        // through to app-level config.
        val activeKeyId =
            appConfig.getPropertyAsString(activeKeyIdProperty(tenantId), null)
                ?: appConfig.getPropertyAsString(APP_ACTIVE_KEY_ID_PROPERTY, null)
                ?: return null
        return findKey(tenantId, activeKeyId)
    }

    override suspend fun findKey(
        tenantId: String,
        keyId: String
    ): EncryptionKeyMaterial? {
        val keyMaterialB64 =
            appConfig.getPropertyAsString(keyProperty(tenantId, keyId), null)
                ?: appConfig.getPropertyAsString(appKeyProperty(keyId), null)
                ?: return null
        val keyBytes =
            runCatching { keyMaterialB64.decodeFromBase64() }.getOrElse { e ->
                error(
                    "TenantEncryptionKeyProvider: tenant '$tenantId' key '$keyId' is not valid base64 — check the secret " +
                        "backend has the encoded key, not the raw bytes. Underlying decode error: ${e.message}",
                )
            }
        // Init block on EncryptionKeyMaterial enforces the algorithm-specific length.
        return EncryptionKeyMaterial(
            keyId = keyId,
            keyBytes = keyBytes,
            algorithm = EncryptedBlob.ALG_AES_256_GCM,
        )
    }

    companion object {
        const val TENANT_PROPERTY_PREFIX: String = "tenants"
        const val ACTIVE_KEY_ID_SUFFIX: String = "security.encryption.activeKeyId"
        const val KEYS_SUFFIX: String = "security.encryption.keys"
        const val APP_ACTIVE_KEY_ID_PROPERTY: String = "app.security.encryption.activeKeyId"
        const val APP_KEYS_PROPERTY_PREFIX: String = "app.security.encryption.keys"

        /**
         * Sentinel tenant id for stores that have no per-tenant context (federation
         * pending records, app-wide audit-event payloads, etc.). Reads under this
         * tenant id ALWAYS fall through to the app-level config since there's no
         * per-tenant override path for `tenants._app.…` (the `_` prefix is reserved).
         */
        const val SYSTEM_TENANT: String = "_app"

        private fun activeKeyIdProperty(tenantId: String): String = "$TENANT_PROPERTY_PREFIX.$tenantId.$ACTIVE_KEY_ID_SUFFIX"

        private fun keyProperty(
            tenantId: String,
            keyId: String
        ): String = "$TENANT_PROPERTY_PREFIX.$tenantId.$KEYS_SUFFIX.$keyId"

        private fun appKeyProperty(keyId: String): String = "$APP_KEYS_PROPERTY_PREFIX.$keyId"
    }
}
