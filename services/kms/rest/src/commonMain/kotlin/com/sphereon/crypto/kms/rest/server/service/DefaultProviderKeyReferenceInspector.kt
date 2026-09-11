/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * Generic provider inspector used until Task 4 contributes the provider-specific replacement.
 * It is session-scoped so provider resolution remains bound to the authenticated tenant session.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ProviderKeyReferenceInspector>())
class DefaultProviderKeyReferenceInspector(
    private val providerRegistry: KmsProviderRegistry,
) : ProviderKeyReferenceInspector {
    override suspend fun inspect(
        providerId: String,
        alias: String,
        kid: String?,
    ): IdkResult<ManagedKeyInfoType<*>, IdkError> {
        if (providerId.isBlank() || alias.isBlank() || kid?.isBlank() == true) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId, alias, and kid must not be blank"))
        }

        val provider =
            try {
                providerRegistry.getProviderById(providerId)
            } catch (_: Exception) {
                return Err(
                    IdkError.fromString(
                        code = "KMS_PROVIDER_NOT_FOUND",
                        message = "The requested KMS provider is not available",
                    ),
                )
            }

        val aliasKey = resolve(provider, KeyInfo<Jwk>(alias = alias))
            ?: return Err(
                IdkError.fromString(
                    code = "KMS_EXTERNAL_KEY_NOT_FOUND",
                    message = "The requested provider key is not available",
                ),
            )

        if (kid != null) {
            // The alias-resolved key is authoritative. A provider that does not index its keys by
            // kid still has to satisfy the identity check: the requested kid must be the canonical
            // kid of the key the alias resolves to.
            val kidKey = resolve(provider, KeyInfo<Jwk>(kid = kid))
            if (kidKey == null) {
                if (canonicalKid(aliasKey) != kid) {
                    return Err(
                        IdkError.fromString(
                            code = "KMS_EXTERNAL_KEY_IDENTITY_MISMATCH",
                            message = "The alias and kid resolve to different provider key identities",
                        ),
                    )
                }
                return Ok(aliasKey.toManagedPublicKeyInfo())
            }

            val aliasCanonicalKid = canonicalKid(aliasKey)
            val kidCanonicalKid = canonicalKid(kidKey)
            val aliasPublicMaterial = publicMaterial(aliasKey)
            val kidPublicMaterial = publicMaterial(kidKey)
            if (aliasCanonicalKid == null ||
                kidCanonicalKid == null ||
                aliasCanonicalKid != kidCanonicalKid ||
                aliasPublicMaterial == null ||
                kidPublicMaterial == null ||
                aliasPublicMaterial != kidPublicMaterial
            ) {
                return Err(
                    IdkError.fromString(
                        code = "KMS_EXTERNAL_KEY_IDENTITY_MISMATCH",
                        message = "The alias and kid resolve to different provider key identities",
                    ),
                )
            }
        }

        return Ok(aliasKey.toManagedPublicKeyInfo())
    }

    private suspend fun resolve(
        provider: com.sphereon.crypto.core.kms.KmsProvider,
        keyInfo: KeyInfo<Jwk>,
    ): ManagedKeyInfoType<*>? =
        try {
            provider.getKey(keyInfo)
        } catch (_: Exception) {
            null
        }

    private fun canonicalKid(key: ManagedKeyInfoType<*>): String? = key.kid ?: key.key.getKeyId(false)

    /** Returns comparable public material without retaining it in an error or log message. */
    private fun publicMaterial(key: ManagedKeyInfoType<*>): String? =
        when (val material = key.key) {
            is Jwk -> {
                if (material.kty == JwaKeyType.oct) {
                    null
                } else {
                    runCatching {
                        Json.encodeToString(Jwk.serializer(), material.toPublicKey().toMinimalJwk())
                    }.getOrNull()
                }
            }
            else -> key.toManagedPublicKeyInfo().key.toString()
        }
}
