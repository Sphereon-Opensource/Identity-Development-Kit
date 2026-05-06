/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.kms

import com.sphereon.core.api.conf.getProperty
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KmsKeyRoleResolver
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [KmsKeyRoleResolver] implementation backed by [SessionExecution.conf].
 *
 * Resolution order (for a `(tenantId, role)` lookup):
 * 1. Read `crypto.kms.roles.<role>.provider` from the tenant config.
 * 2. Fall back to the same key in the app config.
 * 3. If neither defines the provider, returns `null` — callers decide the
 *    policy (error, default software provider, etc.).
 *
 * Same pattern for `.algorithm`. The tenantId argument here is a witness
 * — `SessionExecution` already scopes config reads to the current tenant,
 * so the tenantId passed in must match the session tenant (enforced by
 * callers; the resolver itself does not cross-check).
 *
 * Kid naming: domain-specific (vault picks `vault-iak-<tenant>-<identity>`,
 * eIDAS picks something else) and flows through `hints["kid"]`. If no hint
 * is provided, the resolver falls back to `<role>-<tenantId>`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KmsKeyRoleResolver>())
class ConfigBackedKmsKeyRoleResolver(
    private val execution: SessionExecution,
) : KmsKeyRoleResolver {
    override suspend fun resolveKey(
        tenantId: String,
        role: String,
        hints: Map<String, String>,
    ): KeyInfoType<KeyType>? {
        val providerId =
            lookup("${KmsKeyRoleResolver.CONFIG_PREFIX}.$role.provider")
                ?: return null
        val algorithm = algorithmFor(role)
        val kid = hints["kid"] ?: defaultKid(role, tenantId)
        val alias = hints["alias"] ?: kid
        return KeyInfo<KeyType>(
            kid = kid,
            alias = alias,
            providerId = providerId,
            signatureAlgorithm = algorithm,
            keyVisibility = KeyVisibility.PUBLIC,
        )
    }

    override suspend fun resolveSignatureAlgorithm(
        tenantId: String,
        role: String,
    ): SignatureAlgorithm? = algorithmFor(role)

    private fun lookup(key: String): String? {
        val tenantValue = execution.conf.tenant.getProperty<String>(key)
        if (!tenantValue.isNullOrBlank()) return tenantValue
        val appValue = execution.conf.app.getProperty<String>(key)
        return if (appValue.isNullOrBlank()) null else appValue
    }

    private fun algorithmFor(role: String): SignatureAlgorithm? {
        val raw = lookup("${KmsKeyRoleResolver.CONFIG_PREFIX}.$role.algorithm") ?: return null
        return parseAlgorithm(raw)
    }

    private fun parseAlgorithm(raw: String): SignatureAlgorithm =
        when (raw.uppercase()) {
            "ED25519", "EDDSA" -> SignatureAlgorithm.ED25519
            "ES256" -> SignatureAlgorithm.ECDSA_SHA256
            "ES384" -> SignatureAlgorithm.ECDSA_SHA384
            "ES512" -> SignatureAlgorithm.ECDSA_SHA512
            else -> error("unsupported signature algorithm: $raw (for role config)")
        }

    private fun defaultKid(
        role: String,
        tenantId: String
    ): String = "$role-$tenantId".replace('.', '-')
}
