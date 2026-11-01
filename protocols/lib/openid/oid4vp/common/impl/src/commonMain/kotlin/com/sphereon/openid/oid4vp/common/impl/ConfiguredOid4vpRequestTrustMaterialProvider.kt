/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.Oid4vpRequestTrustMaterial
import com.sphereon.openid.oid4vp.common.Oid4vpRequestTrustMaterialProvider
import com.sphereon.openid.oid4vp.common.Oid4vpX509TrustAnchor
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Configuration-backed X.509 roots and OpenID Federation Trust Anchors for request-object and
 * client identifier validation.
 *
 * X.509 anchors are read from `oid4vp.request.trust-anchors`, either as one value or as indexed
 * sub-keys (`oid4vp.request.trust-anchors.0`, `.1`, ...). A value may hold several concatenated
 * PEM certificates. Federation Trust Anchor Entity Identifiers are read the same way from
 * `oid4vp.request.federation-trust-anchors`. With neither configured the provider fails closed, so
 * an x5c chain can never be accepted against its own leaf.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class ConfiguredOid4vpRequestTrustMaterialProvider private constructor(
    private val resolverProvider: () -> PropertyResolver?,
) : Oid4vpRequestTrustMaterialProvider {
    constructor(propertyResolver: PropertyResolver? = null) : this({ propertyResolver })

    // IDK configuration is owned by the execution context; standalone wallet graphs do not
    // publish an optional PropertyResolver binding. Use the principal's governed configuration.
    @Inject
    constructor(execution: SessionExecution) : this({ execution.conf.conf(ConfigLevel.PRINCIPAL) })

    override suspend fun resolve(): IdkResult<Oid4vpRequestTrustMaterial, IdkError> {
        val resolver = resolverProvider() ?: return Err(unavailable())
        val anchors = configuredValues(resolver, TRUST_ANCHORS_KEY).flatMap(::splitPemCertificates).distinct().map(::Oid4vpX509TrustAnchor)
        val federationTrustAnchors = configuredValues(resolver, FEDERATION_TRUST_ANCHORS_KEY).map(String::trim).filter(String::isNotEmpty).distinct()
        if (anchors.isEmpty() && federationTrustAnchors.isEmpty()) return Err(unavailable())
        return Ok(Oid4vpRequestTrustMaterial(x509 = anchors, federationTrustAnchors = federationTrustAnchors))
    }

    private fun configuredValues(resolver: PropertyResolver, key: String): List<String> =
        buildList {
            resolver.getPropertyAsString(key)?.let(::add)
            addAll(
                // Sorted by key so the anchor order is the same on every run and every platform.
                resolver.getSubPropertiesAsString(setOf(key), stripPrefix = true, redact = false)
                    .entries
                    .sortedBy { it.key }
                    .map { it.value },
            )
        }

    private fun unavailable(): IdkError =
        IdkError.fromString(
            message = "No governed request trust anchors are configured under $TRUST_ANCHORS_KEY or $FEDERATION_TRUST_ANCHORS_KEY",
            code = "X5C_TRUST_MATERIAL_UNAVAILABLE",
            category = ErrorCategory.UNAVAILABLE,
        )

    companion object {
        const val TRUST_ANCHORS_KEY = "oid4vp.request.trust-anchors"
        const val FEDERATION_TRUST_ANCHORS_KEY = "oid4vp.request.federation-trust-anchors"
        private const val PEM_BEGIN = "-----BEGIN CERTIFICATE-----"
        private const val PEM_END = "-----END CERTIFICATE-----"

        /** Splits concatenated PEM material into single certificates; non-PEM values pass through whole. */
        internal fun splitPemCertificates(value: String): List<String> {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (!trimmed.contains(PEM_BEGIN)) return listOf(trimmed)
            val certificates = mutableListOf<String>()
            var cursor = 0
            while (true) {
                val begin = trimmed.indexOf(PEM_BEGIN, cursor)
                if (begin < 0) break
                val end = trimmed.indexOf(PEM_END, begin)
                if (end < 0) break
                certificates += trimmed.substring(begin, end + PEM_END.length)
                cursor = end + PEM_END.length
            }
            return certificates
        }
    }
}
