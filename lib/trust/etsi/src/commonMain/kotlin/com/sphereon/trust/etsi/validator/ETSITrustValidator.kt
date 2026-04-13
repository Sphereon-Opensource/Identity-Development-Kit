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

package com.sphereon.trust.etsi.validator

import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.trust.core.validation.AbstractTrustValidationService
import com.sphereon.trust.core.model.*
import com.sphereon.trust.etsi.model.*
import com.sphereon.trust.etsi.lote.model.forLang
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import com.sphereon.trust.etsi.resolver.ETSITrustListResolver
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlin.time.Duration.Companion.minutes
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoSet
import com.sphereon.di.session.SessionScope

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
    private val execution: SessionExecution
) : AbstractTrustValidationService(
    id = "etsi_tsl",
    supportedContextTypes = setOf(TrustContext.TYPE_ETSI_TSL)
) {
    private val logger = execution.log.logManager.withTagAsync("ETSITrustValidator")

    private val trustListCache by lazy {
        cacheService.getStringCache<CachedLoTEData>(
            CacheRequirements(
                namespace = "trust.etsi.trustlists",
                ttlConfig = CacheTtlConfig(app = 60.minutes)
            )
        )
    }

    override suspend fun validate(request: TrustValidationRequest): TrustValidationResult {
        try {
            // Load the trust list
            val trustList = loadTrustList(request.context)

            // Extract certificate chain from identifier's KeyInfo
            val keyInfo = when (val id = request.identifier) {
                is ExternalIdentifierResult -> id.keyInfo
                is ManagedIdentifierResult<*> -> id.keyInfo
                is ExternalIdentifierOpts, is ManagedIdentifierOpts -> return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Identifier must be resolved before validation",
                    validatedAt = Clock.System.now()
                )
                else -> return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Invalid identifier type",
                    validatedAt = Clock.System.now()
                )
            }

            val x5c = keyInfo.x5c
                ?: return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "X.509 certificate chain (x5c) is required for validation",
                    validatedAt = Clock.System.now()
                )

            if (x5c.isEmpty()) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "X.509 certificate chain (x5c) cannot be empty",
                    validatedAt = Clock.System.now()
                )
            }

            // Convert x5c (Base64) to DER bytes
            val chainDER = x5c.map { certBase64: String ->
                certBase64.decodeFrom(Encoding.BASE64)
            }.toTypedArray()

            val certificateDER = chainDER.first()

            val x509Result = x509VerifyService.verifyCertificateChain(
                X509VerificationRequest(
                    chainDER = chainDER
                )
            )

            if (x509Result.error) {
                return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Certificate chain validation failed: ${x509Result.message ?: "Unknown error"}",
                    validatedAt = Clock.System.now()
                )
            }

            // Extract the issuer information from the certificate
            val publicKey = x509Result.publicKey
                ?: return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Could not extract public key from certificate",
                    validatedAt = Clock.System.now()
                )

            // Find matching trust service provider
            val validationTime = request.validationTime ?: Clock.System.now()
            val matchResult = findMatchingTrustService(
                trustList = trustList,
                certificateDER = certificateDER,
                validationTime = validationTime
            )

            return when {
                matchResult == null -> TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNTRUSTED,
                    details = "Certificate not found in ETSI trust list",
                    validatedAt = Clock.System.now()
                )

                matchResult.serviceStatus == ETSIServiceStatus.GRANTED ||
                matchResult.serviceStatus == ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL -> {
                    TrustValidationResult(
                        trusted = true,
                        status = TrustStatus.TRUSTED,
                        trustAnchor = matchResult.toTrustAnchor(),
                        validationPath = listOf(
                            trustList.schemeTerritory,
                            matchResult.tspName,
                            matchResult.serviceName
                        ),
                        details = "Certificate is trusted by ETSI TSL",
                        validatedAt = Clock.System.now()
                    )
                }

                matchResult.serviceStatus == ETSIServiceStatus.REVOKED -> TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.REVOKED,
                    details = "Service has been revoked",
                    validatedAt = Clock.System.now()
                )

                matchResult.serviceStatus == ETSIServiceStatus.SUSPENDED -> TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNTRUSTED,
                    details = "Service is suspended",
                    validatedAt = Clock.System.now()
                )

                matchResult.serviceStatus == ETSIServiceStatus.WITHDRAWN -> TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNTRUSTED,
                    details = "Service has been withdrawn",
                    validatedAt = Clock.System.now()
                )

                else -> TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.UNKNOWN,
                    details = "Unknown service status: ${matchResult.serviceStatus}",
                    validatedAt = Clock.System.now()
                )
            }

        } catch (e: Exception) {
            logger.error("Trust validation failed", exception = e)
            return TrustValidationResult(
                trusted = false,
                status = TrustStatus.VALIDATION_ERROR,
                details = "Validation error: ${e.message}",
                validatedAt = Clock.System.now()
            )
        }
    }

    override suspend fun getTrustAnchors(): List<TrustAnchor> {
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
                    val keyInfo = ResolvedKeyInfo(
                        key = jwk,
                        x5c = x5c
                    )

                    TrustAnchor(
                        id = "${entity.trustedEntityInformation.identifier ?: entity.trustedEntityInformation.name.first().value}_${serviceInfo.serviceTypeIdentifier}",
                        type = TrustAnchor.TYPE_ETSI_TSP,
                        name = serviceInfo.serviceName.firstOrNull()?.value ?: "Unknown Service",
                        keyInfo = keyInfo,
                        uri = serviceInfo.serviceSupplyPoints.firstOrNull()?.uri,
                        metadata = mapOf(
                            "tspName" to (entity.trustedEntityInformation.name.firstOrNull()?.value ?: ""),
                            "serviceType" to serviceInfo.serviceTypeIdentifier,
                            "serviceStatus" to serviceInfo.serviceStatus,
                            "territory" to trustList.schemeTerritory
                        ),
                        validFrom = serviceInfo.statusStartingTime,
                        validUntil = null
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load trust anchors", exception = e)
            emptyList()
        }
    }

    override suspend fun refresh(): Boolean {
        return try {
            // CacheService entries expire via TTL; explicit removal per-key
            // Refresh means the next loadTrustList call will re-fetch
            logger.info("Trust list refresh requested; entries will be re-fetched on next access")
            true
        } catch (e: Exception) {
            logger.error("Failed to refresh trust list", exception = e)
            false
        }
    }

    private suspend fun loadTrustList(context: TrustContext): ETSILoTE {
        // Use context.framework if provided, otherwise fall back to configured LoTL URL
        val tslUri = context.framework
            ?: trustConfigProvider.getTrustConfig().anchors.etsi.lotlUrl
            ?: throw IllegalArgumentException(
                "ETSI TSL URI not specified in context.framework and no trust.anchors.etsi.lotl-url configured"
            )

        // Check cache
        val cached = trustListCache.getApp(tslUri)
        if (cached.isOk && cached.value != null) {
            val cachedData = cached.value!!
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
            CachedLoTEData(rawData = trustListData.data, nextUpdate = trustList.nextUpdate)
        )

        logger.info("Loaded ETSI trust list: ${trustList.schemeName.firstOrNull()?.value} (seq: ${trustList.sequenceNumber})")
        return trustList
    }

    private fun findMatchingTrustService(
        trustList: ETSILoTE,
        certificateDER: ByteArray,
        validationTime: Instant
    ): MatchedService? {
        for (entity in trustList.trustedEntities) {
            for (service in entity.trustedEntityServices) {
                // Check current service status
                val currentMatch = checkServiceMatch(service.serviceInformation, certificateDER, validationTime)
                if (currentMatch != null) {
                    return MatchedService(
                        tspName = entity.trustedEntityInformation.name.firstOrNull()?.value ?: "Unknown",
                        serviceName = service.serviceInformation.serviceName.firstOrNull()?.value ?: "Unknown",
                        serviceStatus = service.serviceInformation.serviceStatus,
                        serviceInfo = service.serviceInformation
                    )
                }

                // Check service history
                for (historicService in service.serviceHistory) {
                    val historicMatch = checkServiceMatch(historicService, certificateDER, validationTime)
                    if (historicMatch != null) {
                        return MatchedService(
                            tspName = entity.trustedEntityInformation.name.firstOrNull()?.value ?: "Unknown",
                            serviceName = historicService.serviceName.firstOrNull()?.value ?: "Unknown",
                            serviceStatus = historicService.serviceStatus,
                            serviceInfo = historicService
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
        validationTime: Instant
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

    @Serializable
    private data class CachedLoTEData(
        val rawData: ByteArray,
        val nextUpdate: Instant
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CachedLoTEData) return false
            return rawData.contentEquals(other.rawData) && nextUpdate == other.nextUpdate
        }

        override fun hashCode(): Int {
            return rawData.contentHashCode() * 31 + nextUpdate.hashCode()
        }
    }

    private data class MatchedService(
        val tspName: String,
        val serviceName: String,
        val serviceStatus: String,
        val serviceInfo: ETSIServiceInformation
    ) {
        fun toTrustAnchor(): TrustAnchor {
            val certBase64 = serviceInfo.serviceDigitalIdentity.x509Certificates.firstOrNull()
                ?: throw IllegalStateException("Service digital identity must have x509Certificates")
            val certDER = certBase64.decodeFrom(Encoding.BASE64)

            // Convert certificate to JWK for KeyInfo
            val certificate = certificateFromDer(certDER)
            val x5c = arrayOf(certBase64)
            val jwk = certificate.getPublicKeyJwk(x5c = x5c)
            val keyInfo = ResolvedKeyInfo(
                key = jwk,
                x5c = x5c
            )

            return TrustAnchor(
                id = "${tspName}_${serviceInfo.serviceTypeIdentifier}",
                type = TrustAnchor.TYPE_ETSI_TSP,
                name = serviceName,
                keyInfo = keyInfo,
                uri = serviceInfo.serviceSupplyPoints.firstOrNull()?.uri,
                metadata = mapOf(
                    "tspName" to tspName,
                    "serviceType" to serviceInfo.serviceTypeIdentifier,
                    "serviceStatus" to serviceStatus
                ),
                validFrom = serviceInfo.statusStartingTime
            )
        }
    }
}
