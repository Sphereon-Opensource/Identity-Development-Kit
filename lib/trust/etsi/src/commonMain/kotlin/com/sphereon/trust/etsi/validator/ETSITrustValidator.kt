/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.trust.etsi.validator

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityAddress
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.EntityRole
import com.sphereon.trust.core.model.LocalizedString
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustChainNodeRole
import com.sphereon.trust.core.model.TrustChainPosition
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.validation.AbstractTrustValidationService
import com.sphereon.trust.etsi.extractor.EtsiEntityInfoExtractor
import com.sphereon.trust.etsi.lote.model.forLang
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIServiceInformation
import com.sphereon.trust.etsi.model.ETSIServiceStatus
import com.sphereon.trust.etsi.model.ETSITrustedEntity
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import com.sphereon.trust.etsi.resolver.ETSITrustListResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Trust validation service for ETSI TS 119 612 trust lists.
 *
 * This service validates certificates against ETSI trust service status lists,
 * checking if the certificate is issued by a trusted TSP.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class)
class ETSITrustValidator(
    private val trustListResolver: ETSITrustListResolver,
    private val trustListParser: ETSITrustListParser,
    private val x509VerifyService: X509VerifyService,
    private val trustConfigProvider: com.sphereon.trust.core.config.TrustConfigProvider,
    private val cacheService: CacheService,
    private val execution: SessionExecution,
    private val entityInfoExtractor: EtsiEntityInfoExtractor,
) : AbstractTrustValidationService(
        id = "etsi_tsl",
        supportedContextTypes = setOf(TrustContext.TYPE_ETSI_TSL),
    ) {
    private companion object {
        const val UNKNOWN_LABEL = "Unknown"
    }

    private val logger = execution.log.logManager.withTag("ETSITrustValidator")

    private val trustListCache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.etsi.trustlists",
                ttlConfig = CacheTtlConfig(app = 60.minutes),
            ),
        )
    }

    override suspend fun doValidate(request: TrustValidationRequest): TrustValidationResult {
        try {
            // Load the trust list
            val trustList = loadTrustList(request.context)

            // Extract certificate chain from identifier's KeyInfo
            val keyInfo =
                when (val id = request.identifier) {
                    is ExternalIdentifierResult -> id.keyInfo

                    is ManagedIdentifierResult<*> -> id.keyInfo

                    is ExternalIdentifierOpts, is ManagedIdentifierOpts -> return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "Identifier must be resolved before validation",
                        validatedAt = Clock.System.now(),
                    )

                    else -> return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "Invalid identifier type",
                        validatedAt = Clock.System.now(),
                    )
                }

            val x5c =
                keyInfo.x5c
                    ?: return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "X.509 certificate chain (x5c) is required for validation",
                        validatedAt = Clock.System.now(),
                    )

            if (x5c.isEmpty()) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "X.509 certificate chain (x5c) cannot be empty",
                    validatedAt = Clock.System.now(),
                )
            }

            // Convert x5c (Base64) to DER bytes
            val chainDER =
                x5c
                    .map { certBase64: String ->
                        certBase64.decodeFrom(Encoding.BASE64)
                    }.toTypedArray()

            val certificateDER = chainDER.first()

            val x509Result =
                x509VerifyService.verifyCertificateChain(
                    X509VerificationRequest(
                        chainDER = chainDER,
                    ),
                )

            if (x509Result.error) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Certificate chain validation failed: ${x509Result.message ?: "Unknown error"}",
                    validatedAt = Clock.System.now(),
                )
            }

            // Extract the issuer information from the certificate
            val publicKey =
                x509Result.publicKey
                    ?: return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "Could not extract public key from certificate",
                        validatedAt = Clock.System.now(),
                    )

            // Find matching trust service provider
            val validationTime = request.validationTime ?: Clock.System.now()
            val matchResult =
                findMatchingTrustService(
                    trustList = trustList,
                    certificateDER = certificateDER,
                    validationTime = validationTime,
                )

            return when {
                matchResult == null -> {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "Certificate not found in ETSI trust list",
                        validatedAt = Clock.System.now(),
                    )
                }

                matchResult.serviceStatus == ETSIServiceStatus.GRANTED ||
                    matchResult.serviceStatus == ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL -> {
                    val result =
                        TrustValidationResult(
                            trusted = true,
                            status = TrustStatus.TRUSTED,
                            trustAnchor = matchResult.toTrustAnchor(),
                            validationPath =
                                listOf(
                                    trustList.schemeTerritory,
                                    matchResult.tspName,
                                    matchResult.serviceName,
                                ),
                            details = "Certificate is trusted by ETSI TSL",
                            validatedAt = Clock.System.now(),
                        )
                    enrichWithEtsiEntityInfo(result, request, trustList, matchResult)
                }

                matchResult.serviceStatus == ETSIServiceStatus.REVOKED -> {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.REVOKED,
                        details = "Service has been revoked",
                        validatedAt = Clock.System.now(),
                    )
                }

                matchResult.serviceStatus == ETSIServiceStatus.SUSPENDED -> {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "Service is suspended",
                        validatedAt = Clock.System.now(),
                    )
                }

                matchResult.serviceStatus == ETSIServiceStatus.WITHDRAWN -> {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "Service has been withdrawn",
                        validatedAt = Clock.System.now(),
                    )
                }

                else -> {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNKNOWN,
                        details = "Unknown service status: ${matchResult.serviceStatus}",
                        validatedAt = Clock.System.now(),
                    )
                }
            }
        } catch (expected: Exception) {
            logger.error("Trust validation failed", exception = expected)
            return TrustValidationResult(
                trusted = false,
                status = TrustStatus.VALIDATION_ERROR,
                details = "Validation error: ${expected.message}",
                validatedAt = Clock.System.now(),
            )
        }
    }

    override suspend fun doGetTrustAnchors(): List<TrustAnchor> {
        return try {
            val context = TrustContext(type = TrustContext.TYPE_ETSI_TSL)
            val trustList = loadTrustList(context)

            trustList.trustedEntities.flatMap { entity ->
                entity.trustedEntityServices.mapNotNull { service ->
                    val serviceInfo = service.serviceInformation
                    val certBase64 = serviceInfo.serviceDigitalIdentity.x509Certificates.firstOrNull() ?: return@mapNotNull null
                    val certDER = certBase64.decodeFrom(Encoding.BASE64)

                    // Convert certificate to JWK for KeyInfo
                    val certificate = certificateFromDer(certDER)
                    val x5c = arrayOf(certBase64)
                    val jwk = certificate.getPublicKeyJwk(x5c = x5c)
                    val keyInfo =
                        ResolvedKeyInfo(
                            key = jwk,
                            x5c = x5c,
                        )

                    TrustAnchor(
                        id = "${entity.trustedEntityInformation.identifier ?: entity.trustedEntityInformation.name
                            .first()
                            .value}_${serviceInfo.serviceTypeIdentifier}",
                        type = TrustAnchor.TYPE_ETSI_TSP,
                        name = serviceInfo.serviceName.firstOrNull()?.value ?: "Unknown Service",
                        keyInfo = keyInfo,
                        uri = serviceInfo.serviceSupplyPoints.firstOrNull()?.uri,
                        metadata =
                            mapOf(
                                "tspName" to (
                                    entity.trustedEntityInformation.name
                                        .firstOrNull()
                                        ?.value ?: ""
                                ),
                                "serviceType" to serviceInfo.serviceTypeIdentifier,
                                "serviceStatus" to serviceInfo.serviceStatus,
                                "territory" to trustList.schemeTerritory,
                            ),
                        validFrom = serviceInfo.statusStartingTime,
                        validUntil = null,
                    )
                }
            }
        } catch (expected: Exception) {
            logger.error("Failed to load trust anchors", exception = expected)
            emptyList()
        }
    }

    override suspend fun refresh(): Boolean =
        try {
            // CacheService entries expire via TTL; explicit removal per-key
            // Refresh means the next loadTrustList call will re-fetch
            logger.info("Trust list refresh requested; entries will be re-fetched on next access")
            true
        } catch (expected: Exception) {
            logger.error("Failed to refresh trust list", exception = expected)
            false
        }

    private suspend fun loadTrustList(context: TrustContext): ETSILoTE {
        // Use context.framework if provided, otherwise fall back to configured LoTL URL
        val tslUri =
            context.framework
                ?: trustConfigProvider
                    .getTrustConfig()
                    .anchors.etsi.lotlUrl
                ?: throw IllegalArgumentException(
                    "ETSI TSL URI not specified in context.framework and no trust.anchors.etsi.lotl-url configured",
                )

        // Check cache
        val cached = trustListCache.getApp(tslUri)
        if (cached != null) {
            val cachedData =
                kotlinx.serialization.json.Json
                    .decodeFromString<CachedLoTEData>(cached)
            if (Clock.System.now() < cachedData.nextUpdate) {
                logger.debug("Using cached trust list for $tslUri")
                return trustListParser.parseFromBytes(cachedData.rawData)
            }
        }

        // Resolve and parse trust list
        val trustListData = trustListResolver.resolve(tslUri)
        val trustList = trustListParser.parseFromBytes(trustListData.data)

        // Cache the raw data with nextUpdate metadata
        trustListCache.putApp(
            tslUri,
            kotlinx.serialization.json.Json
                .encodeToString(CachedLoTEData(rawData = trustListData.data, nextUpdate = trustList.nextUpdate)),
        )

        logger.info("Loaded ETSI trust list: ${trustList.schemeName.firstOrNull()?.value} (seq: ${trustList.sequenceNumber})")
        return trustList
    }

    private fun findMatchingTrustService(
        trustList: ETSILoTE,
        certificateDER: ByteArray,
        validationTime: Instant,
    ): MatchedService? {
        for (entity in trustList.trustedEntities) {
            for (service in entity.trustedEntityServices) {
                // Check current service status
                val currentMatch = checkServiceMatch(service.serviceInformation, certificateDER, validationTime)
                if (currentMatch != null) {
                    return MatchedService(
                        tspName =
                            entity.trustedEntityInformation.name
                                .firstOrNull()
                                ?.value ?: UNKNOWN_LABEL,
                        serviceName =
                            service.serviceInformation.serviceName
                                .firstOrNull()
                                ?.value ?: UNKNOWN_LABEL,
                        serviceStatus = service.serviceInformation.serviceStatus,
                        serviceInfo = service.serviceInformation,
                        entity = entity,
                    )
                }

                // Check service history
                for (historicService in service.serviceHistory) {
                    val historicMatch = checkServiceMatch(historicService, certificateDER, validationTime)
                    if (historicMatch != null) {
                        return MatchedService(
                            tspName =
                                entity.trustedEntityInformation.name
                                    .firstOrNull()
                                    ?.value ?: UNKNOWN_LABEL,
                            serviceName = historicService.serviceName.firstOrNull()?.value ?: UNKNOWN_LABEL,
                            serviceStatus = historicService.serviceStatus,
                            serviceInfo = historicService,
                        )
                    }
                }
            }
        }
        return null
    }

    private fun checkServiceMatch(
        serviceInfo: ETSIServiceInformation,
        certificateDER: ByteArray,
        validationTime: Instant,
    ): Boolean {
        // Check if the service was active at validation time
        if (validationTime < serviceInfo.statusStartingTime) {
            return false
        }

        // Match certificate
        val serviceCertBase64 = serviceInfo.serviceDigitalIdentity.x509Certificates.firstOrNull() ?: return false
        val serviceCertDER = serviceCertBase64.decodeFrom(Encoding.BASE64)

        return serviceCertDER.contentEquals(certificateDER)
    }

    /**
     * Builds the full trust chain with entity info at every level:
     *   [0..n] X.509 certificate chain (leaf, intermediates from x5c)
     *   [n+1]  Matched TSP entity in the trust list (marked as trust anchor)
     *   [n+2]  Trust list scheme operator
     *
     * This runs automatically when [EntityDiscoveryOptions.enabled] is true.
     */
    private fun enrichWithEtsiEntityInfo(
        result: TrustValidationResult,
        request: TrustValidationRequest,
        trustList: ETSILoTE,
        matchResult: MatchedService,
    ): TrustValidationResult {
        val options = request.entityDiscovery ?: return result
        if (!options.enabled || options.deferred) {
            return result
        }

        val maxDepth =
            if (options.maxDepth == 0) {
                Int.MAX_VALUE
            } else {
                options.maxDepth
            }
        var depth = 0

        val entities =
            buildList {
                // Extract x5c from request identifier for cert chain info
                val x5c =
                    when (val id = request.identifier) {
                        is ExternalIdentifierResult -> id.keyInfo?.x5c
                        is ManagedIdentifierResult<*> -> id.keyInfo?.x5c
                        else -> null
                    }

                // Cert chain: leaf + intermediates from x5c
                if (x5c != null) {
                    for ((index, certBase64) in x5c.withIndex()) {
                        if (depth >= maxDepth) {
                            break
                        }
                        try {
                            val certDer = certBase64.decodeFrom(Encoding.BASE64)
                            val cert = certificateFromDer(certDer)
                            val dnParts = parseSimpleDN(cert.subjectDN)
                            val nodeRole =
                                if (index == 0) {
                                    TrustChainNodeRole.LEAF
                                } else {
                                    TrustChainNodeRole.INTERMEDIATE
                                }

                            add(
                                DiscoveredEntityInfo(
                                    entityIdentifier = cert.subjectDN,
                                    sourceType = TrustAnchorType.X509_CA_BUNDLE,
                                    chainPosition = TrustChainPosition(depth = depth, role = nodeRole),
                                    names =
                                        buildList {
                                            dnParts["CN"]?.let { add(LocalizedString(lang = "und", value = it)) }
                                            val org = dnParts["O"]
                                            if (org != null && org != dnParts["CN"]) {
                                                add(LocalizedString(lang = "und", value = org))
                                            }
                                        },
                                    addresses =
                                        buildList {
                                            dnParts["C"]?.let { add(EntityAddress(locality = dnParts["L"], stateOrProvince = dnParts["ST"], countryName = it)) }
                                        },
                                    organizationName = dnParts["O"],
                                    jurisdiction = dnParts["C"],
                                    roles = listOf(EntityRole.GENERAL),
                                ),
                            )
                            depth++
                        } catch (expected: Exception) {
                            logger.debug("Failed to parse x5c certificate for entity discovery: ${expected.message}")
                        }
                    }

                    // If leaf cert has an issuer not in x5c, add issuer DN info
                    if (x5c.size == 1 && depth < maxDepth) {
                        try {
                            val leafDer = x5c.first().decodeFrom(Encoding.BASE64)
                            val leafCert = certificateFromDer(leafDer)
                            if (leafCert.issuerDN != leafCert.subjectDN) {
                                val issuerParts = parseSimpleDN(leafCert.issuerDN)
                                add(
                                    DiscoveredEntityInfo(
                                        entityIdentifier = leafCert.issuerDN,
                                        sourceType = TrustAnchorType.X509_CA_BUNDLE,
                                        chainPosition = TrustChainPosition(depth = depth, role = TrustChainNodeRole.INTERMEDIATE),
                                        names =
                                            buildList {
                                                issuerParts["CN"]?.let { add(LocalizedString(lang = "und", value = it)) }
                                                val org = issuerParts["O"]
                                                if (org != null && org != issuerParts["CN"]) {
                                                    add(LocalizedString(lang = "und", value = org))
                                                }
                                            },
                                        addresses =
                                            buildList {
                                                issuerParts["C"]?.let { add(EntityAddress(countryName = it)) }
                                            },
                                        organizationName = issuerParts["O"],
                                        jurisdiction = issuerParts["C"],
                                        roles = listOf(EntityRole.GENERAL),
                                    ),
                                )
                                depth++
                            }
                        } catch (expected: Exception) {
                            logger.debug("Failed to parse leaf cert issuer DN for entity discovery: ${expected.message}")
                        }
                    }
                }

                // Matched TSP entity — this is the trust anchor
                if (depth < maxDepth) {
                    val entity = matchResult.entity
                    if (entity != null) {
                        add(
                            entityInfoExtractor
                                .mapEntity(
                                    entity = entity,
                                    territory = trustList.schemeTerritory,
                                    matchedServiceType = matchResult.serviceInfo.serviceTypeIdentifier,
                                    depth = depth,
                                    nodeRole = TrustChainNodeRole.INTERMEDIATE,
                                ).copy(trustAnchor = true),
                        )
                        depth++
                    }
                }

                // Trust list scheme operator
                if (depth < maxDepth) {
                    add(entityInfoExtractor.mapSchemeOperator(trustList, depth = depth))
                }
            }
        return result.copy(discoveredEntities = entities)
    }

    /** Simple DN parser for extracting O, CN, C, L, ST fields. */
    private fun parseSimpleDN(dn: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val parts = dn.split(Regex("""(?<!\\),\s*"""))
        for (part in parts) {
            val eqIndex = part.indexOf('=')
            if (eqIndex > 0) {
                result[part.substring(0, eqIndex).trim()] = part.substring(eqIndex + 1).trim()
            }
        }
        return result
    }

    @Serializable
    private data class CachedLoTEData(
        val rawData: ByteArray,
        val nextUpdate: Instant,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is CachedLoTEData) {
                return false
            }
            return rawData.contentEquals(other.rawData) && nextUpdate == other.nextUpdate
        }

        override fun hashCode(): Int = rawData.contentHashCode() * 31 + nextUpdate.hashCode()
    }

    private data class MatchedService(
        val tspName: String,
        val serviceName: String,
        val serviceStatus: String,
        val serviceInfo: ETSIServiceInformation,
        val entity: ETSITrustedEntity? = null,
    ) {
        fun toTrustAnchor(): TrustAnchor {
            val certBase64 =
                serviceInfo.serviceDigitalIdentity.x509Certificates.firstOrNull()
                    ?: throw IllegalStateException("Service digital identity must have x509Certificates")
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            // Convert certificate to JWK for KeyInfo
            val certificate = certificateFromDer(certDER)
            val x5c = arrayOf(certBase64)
            val jwk = certificate.getPublicKeyJwk(x5c = x5c)
            val keyInfo =
                ResolvedKeyInfo(
                    key = jwk,
                    x5c = x5c,
                )

            return TrustAnchor(
                id = "${tspName}_${serviceInfo.serviceTypeIdentifier}",
                type = TrustAnchor.TYPE_ETSI_TSP,
                name = serviceName,
                keyInfo = keyInfo,
                uri = serviceInfo.serviceSupplyPoints.firstOrNull()?.uri,
                metadata =
                    mapOf(
                        "tspName" to tspName,
                        "serviceType" to serviceInfo.serviceTypeIdentifier,
                        "serviceStatus" to serviceStatus,
                    ),
                validFrom = serviceInfo.statusStartingTime,
            )
        }
    }
}
