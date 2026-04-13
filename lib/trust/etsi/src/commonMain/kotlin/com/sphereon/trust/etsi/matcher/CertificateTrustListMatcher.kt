/*
 * © 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.trust.etsi.matcher

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIServiceInformation
import com.sphereon.trust.etsi.model.ETSITrustedEntity
import com.sphereon.trust.etsi.resolution.TspMatchType
import kotlinx.datetime.Instant

/**
 * Reusable utility for matching a certificate against entities in an ETSI trust list.
 *
 * Extracted from [X509ETSIValidationIdentifierResolutionServiceImpl] so that both
 * the existing X.509 validation flow and the new eIDAS role verification flow
 * can share the same matching logic.
 */
object CertificateTrustListMatcher {

    /**
     * Result of matching a certificate against a trust list entity.
     */
    data class EntityMatchResult(
        val entity: ETSITrustedEntity,
        val serviceInfo: ETSIServiceInformation,
        val matchType: TspMatchType,
        val caChain: List<String>?
    )

    /**
     * Searches a trust list for an entity whose service digital identity matches
     * the given certificate (directly or via CA chain).
     *
     * @param trustList       The parsed ETSI trust list to search
     * @param certDER         DER-encoded bytes of the certificate to match
     * @param chain           Optional CA chain (Base64-encoded DER certs, issuer-first)
     * @param serviceTypeFilter If non-null, only consider services matching these type URIs
     * @param allowCAChainMatch Whether to attempt CA chain matching in addition to direct
     * @param validationTime  Optional time constraint — services not yet active are skipped
     * @return The first matching entity result, or null if no match found
     */
    fun findMatchingEntity(
        trustList: ETSILoTE,
        certDER: ByteArray,
        chain: List<String>?,
        serviceTypeFilter: List<String>?,
        allowCAChainMatch: Boolean,
        validationTime: Instant? = null
    ): EntityMatchResult? {
        for (entity in trustList.trustedEntities) {
            for (service in entity.trustedEntityServices) {
                // Apply service type filter
                if (serviceTypeFilter != null &&
                    !serviceTypeFilter.contains(service.serviceInformation.serviceTypeIdentifier)
                ) {
                    continue
                }

                // Check current service
                val currentMatch = checkServiceMatch(
                    serviceInfo = service.serviceInformation,
                    certDER = certDER,
                    chain = chain,
                    validationTime = validationTime,
                    allowCAChainMatch = allowCAChainMatch
                )
                if (currentMatch != null) {
                    return EntityMatchResult(
                        entity = entity,
                        serviceInfo = service.serviceInformation,
                        matchType = currentMatch.matchType,
                        caChain = currentMatch.caChain
                    )
                }

                // Check service history
                for (historicService in service.serviceHistory) {
                    val historicMatch = checkServiceMatch(
                        serviceInfo = historicService,
                        certDER = certDER,
                        chain = chain,
                        validationTime = validationTime,
                        allowCAChainMatch = allowCAChainMatch
                    )
                    if (historicMatch != null) {
                        return EntityMatchResult(
                            entity = entity,
                            serviceInfo = historicService,
                            matchType = historicMatch.matchType,
                            caChain = historicMatch.caChain
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * Checks whether a single service's digital identity matches the certificate.
     */
    internal fun checkServiceMatch(
        serviceInfo: ETSIServiceInformation,
        certDER: ByteArray,
        chain: List<String>?,
        validationTime: Instant?,
        allowCAChainMatch: Boolean
    ): ServiceMatch? {
        // Check if service was active at validation time
        if (validationTime != null && validationTime < serviceInfo.statusStartingTime) {
            return null
        }

        val serviceCertBase64 = serviceInfo.serviceDigitalIdentity.x509Certificates.firstOrNull() ?: return null
        val serviceCertDER = serviceCertBase64.decodeFrom(Encoding.BASE64)

        // Direct match: certificate exactly matches service certificate
        if (serviceCertDER.contentEquals(certDER)) {
            return ServiceMatch(TspMatchType.DIRECT_MATCH, emptyList())
        }

        // CA chain match: certificate was issued by this service certificate
        if (allowCAChainMatch && chain != null) {
            return checkCAChainMatch(certDER, serviceCertDER, chain)
        }

        return null
    }

    /**
     * Checks if the service certificate appears in the provided CA chain.
     */
    internal fun checkCAChainMatch(
        certDER: ByteArray,
        serviceCertDER: ByteArray,
        chain: List<String>
    ): ServiceMatch? {
        val fullChain = mutableListOf<ByteArray>()
        fullChain.add(certDER)
        chain.forEach { fullChain.add(it.decodeFrom(Encoding.BASE64)) }

        val matchIndex = fullChain.indexOfFirst { it.contentEquals(serviceCertDER) }
        if (matchIndex > 0) { // Must be an issuer, not the cert itself
            val caChain = fullChain.subList(0, matchIndex + 1)
                .map { it.encodeTo(Encoding.BASE64) }
            return ServiceMatch(TspMatchType.CA_CHAIN_MATCH, caChain)
        }

        return null
    }

    /**
     * Internal match result from certificate comparison.
     */
    data class ServiceMatch(
        val matchType: TspMatchType,
        val caChain: List<String>
    )

}
