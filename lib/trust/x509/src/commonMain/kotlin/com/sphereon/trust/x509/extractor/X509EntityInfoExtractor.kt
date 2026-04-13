/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509.extractor

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityAddress
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.EntityRole
import com.sphereon.trust.core.model.LocalizedString
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.TrustChainPosition
import com.sphereon.trust.core.model.TrustContext
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Extracts entity information from X.509 certificate chains.
 *
 * Parses Subject Distinguished Name fields (O, CN, C, L, ST, OU)
 * to populate organization name, addresses, and entity names.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(scope = SessionScope::class, binding = binding<EntityInfoExtractor>())
class X509EntityInfoExtractor : EntityInfoExtractor {
    override val supportedContextTypes: Set<String> =
        setOf(
            TrustContext.TYPE_X509,
            TrustContext.TYPE_CA_BUNDLE,
        )

    override suspend fun extractEntityInfo(
        context: TrustContext,
        validationPath: List<String>,
        options: EntityDiscoveryOptions,
    ): List<DiscoveredEntityInfo> {
        val effectiveDepth =
            if (options.maxDepth == 0) {
                validationPath.size
            } else {
                options.maxDepth
            }

        return validationPath.take(effectiveDepth).mapIndexedNotNull { index, certBase64 ->
            try {
                val nodeRole =
                    when (index) {
                        0 -> TrustChainNodeRole.LEAF
                        validationPath.size - 1 -> TrustChainNodeRole.TRUST_ANCHOR
                        else -> TrustChainNodeRole.INTERMEDIATE
                    }
                extractFromCertificate(certBase64, index, nodeRole)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Extracts entity info from subject and issuer Distinguished Name strings.
     * Useful when you already have the certificate parsed and want to compose
     * X.509 entity info with other trust chain levels (e.g., ETSI TSP, LOTL).
     */
    fun extractFromCertificateInfo(
        subjectDN: String,
        issuerDN: String? = null,
        depth: Int = 0,
        nodeRole: TrustChainNodeRole = TrustChainNodeRole.LEAF,
    ): DiscoveredEntityInfo {
        val dnParts = parseDistinguishedName(subjectDN)

        val organizationName = dnParts["O"]
        val commonName = dnParts["CN"]
        val country = dnParts["C"]
        val locality = dnParts["L"]
        val state = dnParts["ST"]

        return DiscoveredEntityInfo(
            entityIdentifier = subjectDN,
            sourceType = TrustAnchorType.X509_CA_BUNDLE,
            chainPosition = TrustChainPosition(depth = depth, role = nodeRole),
            names =
                buildList {
                    commonName?.let { add(LocalizedString(lang = "und", value = it)) }
                    if (organizationName != null && organizationName != commonName) {
                        add(LocalizedString(lang = "und", value = organizationName))
                    }
                },
            addresses =
                buildList {
                    if (country != null) {
                        add(
                            EntityAddress(
                                locality = locality,
                                stateOrProvince = state,
                                countryName = country,
                            ),
                        )
                    }
                },
            organizationName = organizationName,
            jurisdiction = country,
            roles = listOf(EntityRole.GENERAL),
        )
    }

    private fun extractFromCertificate(
        certBase64: String,
        depth: Int,
        nodeRole: TrustChainNodeRole,
    ): DiscoveredEntityInfo {
        val derBytes = certBase64.decodeFrom(Encoding.BASE64)
        val certificate = certificateFromDer(derBytes)

        val subjectDn = certificate.subjectDN
        val dnParts = parseDistinguishedName(subjectDn)

        val organizationName = dnParts["O"]
        val commonName = dnParts["CN"]
        val country = dnParts["C"]
        val locality = dnParts["L"]
        val state = dnParts["ST"]

        return DiscoveredEntityInfo(
            entityIdentifier = subjectDn,
            sourceType = TrustAnchorType.X509_CA_BUNDLE,
            chainPosition = TrustChainPosition(depth = depth, role = nodeRole),
            names =
                buildList {
                    commonName?.let { add(LocalizedString(lang = "und", value = it)) }
                    if (organizationName != null && organizationName != commonName) {
                        add(LocalizedString(lang = "und", value = organizationName))
                    }
                },
            addresses =
                buildList {
                    if (country != null) {
                        add(
                            EntityAddress(
                                locality = locality,
                                stateOrProvince = state,
                                countryName = country,
                            ),
                        )
                    }
                },
            organizationName = organizationName,
            jurisdiction = country,
            roles = listOf(EntityRole.GENERAL),
        )
    }

    companion object {
        /**
         * Parses a Distinguished Name string into key-value pairs.
         * Handles format like "CN=Example,O=Org,C=US" or "CN=Example, O=Org, C=US".
         */
        internal fun parseDistinguishedName(dn: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            // Split on comma not preceded by backslash
            val parts = dn.split(Regex("""(?<!\\),\s*"""))
            for (part in parts) {
                val eqIndex = part.indexOf('=')
                if (eqIndex > 0) {
                    val key = part.substring(0, eqIndex).trim()
                    val value = part.substring(eqIndex + 1).trim()
                    result[key] = value
                }
            }
            return result
        }
    }
}
