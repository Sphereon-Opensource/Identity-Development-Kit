/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.kms.CertificateStoreService
import com.sphereon.crypto.core.kms.HasKeyStoreService
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Lists legacy platform-managed certificate aliases through a tenant-aware platform seam.
 *
 * Externally managed aliases never come from this contract; they are resolved from the tenant
 * certificate-reference store. Enterprise can replace this binding with its permit-bound,
 * namespaced managed-certificate command without changing generic REST behavior.
 */
interface PlatformManagedCertificateAliasLister {
    suspend fun listTrustedCertificateAliases(providerId: String? = null): List<String>

    suspend fun listCertificateChainAliases(providerId: String? = null): List<String>
}

/** Generic IDK compatibility adapter over the existing default certificate store. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<PlatformManagedCertificateAliasLister>())
class DefaultPlatformManagedCertificateAliasLister(
    private val kms: KeyManagerService,
) : PlatformManagedCertificateAliasLister {
    override suspend fun listTrustedCertificateAliases(providerId: String?): List<String> =
        certificateStore(providerId).listCertificateAliases().toList()

    override suspend fun listCertificateChainAliases(providerId: String?): List<String> =
        certificateStore(providerId).listCertificateChainAliases().toList()

    private suspend fun certificateStore(providerId: String?): CertificateStoreService {
        val target: Any = providerId?.let { kms.getProviderById(it) } ?: kms.keyStore
        return target as? CertificateStoreService
            ?: (target as? HasKeyStoreService)?.keyStore as? CertificateStoreService
            ?: throw IllegalArgumentException("The default key store does not support certificate store operations")
    }
}
