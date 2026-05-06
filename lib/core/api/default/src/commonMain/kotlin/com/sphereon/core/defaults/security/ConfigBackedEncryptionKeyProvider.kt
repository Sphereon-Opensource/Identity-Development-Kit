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

import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.security.EncryptedBlob
import com.sphereon.core.api.security.EncryptionKeyMaterial
import com.sphereon.core.api.security.EncryptionKeyProvider
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [EncryptionKeyProvider] sourced from the tenant-scoped configuration
 * hierarchy. Reads from [TenantConfigService], which falls through to
 * [com.sphereon.core.api.conf.AppConfigService] when the tenant has no override —
 * giving each tenant its own encryption key while the deployment can still set a
 * fall-through default at the app level.
 *
 * The configuration abstraction already wraps the deployment's secret backends
 * (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, env vars, YAML / properties
 * files) and resolves `${vault.<key>}` placeholders transparently — this provider
 * just reads the right keys off it.
 *
 * **Configuration keys** (resolved per tenant, falling through to app):
 *
 *  - `security.encryption.activeKeyId` — id of the currently-active key for this
 *    tenant. Required.
 *  - `security.encryption.keys.<keyId>` — base64-encoded 32-byte key material for the
 *    named key id. Required for the active key; previously-rotated keys stay listed
 *    here until every ciphertext written under them has been re-encrypted.
 *
 * **Production wiring**: a deployment writes
 *   `security.encryption.keys.k1 = ${vault.tenants/<tenantId>/encryption/k1}`
 * in the tenant's YAML overlay; the
 * `InterpolatingPropertySourcesPropertyResolver` resolves the `${vault.…}` placeholder
 * via the configured `SecretProvider` (Vault / AWS / Azure). The key never appears in
 * a config file or env var on disk in plaintext.
 *
 * **Per-tenant isolation**: a tenant compromise leaks only that tenant's encryption
 * key. Other tenants' ciphertexts remain protected. The fall-through to app config is
 * convenience for deployments that want a single shared key (typical for single-
 * tenant or low-assurance use cases).
 *
 * **Rotation**: add a new `security.encryption.keys.k2` entry, change `activeKeyId`
 * to `k2`, and leave `k1` listed until cleanup. The decrypt path looks keys up by id;
 * encrypt always uses the active key. Rotation is per-tenant — one tenant rotating
 * does not touch other tenants' keys.
 *
 * **Failure mode**: when no `activeKeyId` is configured (neither tenant nor app
 * level), [activeKey] returns null and
 * [com.sphereon.core.api.security.EncryptionService] surfaces an error to the caller
 * — better to fail loudly at first encrypt than to silently fall back to plaintext.
 */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<EncryptionKeyProvider>())
class ConfigBackedEncryptionKeyProvider(
    private val tenantConfig: TenantConfigService,
) : EncryptionKeyProvider {
    override suspend fun activeKey(): EncryptionKeyMaterial? {
        val activeKeyId = tenantConfig.getPropertyAsString(ACTIVE_KEY_ID_PROPERTY, null) ?: return null
        return findKey(activeKeyId)
    }

    override suspend fun findKey(keyId: String): EncryptionKeyMaterial? {
        val keyMaterialB64 = tenantConfig.getPropertyAsString("$KEYS_PROPERTY_PREFIX.$keyId", null) ?: return null
        val keyBytes =
            runCatching { keyMaterialB64.decodeFromBase64() }.getOrElse { e ->
                error(
                    "EncryptionKeyProvider: key '$keyId' is not valid base64 — check the secret backend has the encoded key, " +
                        "not the raw bytes. Underlying decode error: ${e.message}",
                )
            }
        // The data class init asserts size = 32 for AES-256-GCM; an operator who
        // configured a key of the wrong length gets a load-time error not a runtime
        // surprise on first encrypt.
        return EncryptionKeyMaterial(
            keyId = keyId,
            keyBytes = keyBytes,
            algorithm = EncryptedBlob.ALG_AES_256_GCM,
        )
    }

    companion object {
        const val ACTIVE_KEY_ID_PROPERTY: String = "security.encryption.activeKeyId"
        const val KEYS_PROPERTY_PREFIX: String = "security.encryption.keys"
    }
}
