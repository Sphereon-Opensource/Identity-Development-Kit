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

package com.sphereon.trust.etsi.resolution

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.crypto.core.x509.x509DerOrPemToPem
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierServiceAdapter
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
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.extractor.EtsiEntityInfoExtractor
import com.sphereon.trust.etsi.matcher.CertificateTrustListMatcher
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIServiceInformation
import com.sphereon.trust.etsi.model.ETSIServiceStatus
import com.sphereon.trust.etsi.model.ETSITrustedEntity
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Interface for X.509 ETSI validation identifier resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IX509ETSIValidationIdentifierResolutionService", exact = true)
interface IX509ETSIValidationIdentifierResolutionService : ExternalIdentifierService

/**
 * External identifier service for validating X.509 certificates against ETSI trust lists.
 *
 * This is the primary use case for ETSI trust validation:
 * 1. Receive an X.509 certificate to validate
 * 2. Extract country from certificate's C attribute
 * 3. Navigate LOTL to find appropriate member state trust list (with caching)
 * 4. Check if certificate is directly in trust list OR issued by a CA in the trust list
 * 5. Return trust validation result
 *
 * The service integrates with the identifier resolution framework, treating X.509 ETSI
 * validation as an external identifier resolution operation.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IX509ETSIValidationIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class X509ETSIValidationIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val trustListResolvers: Set<TrustListResolver>,
    private val trustListParser: ETSITrustListParser,
    private val x509VerifyService: X509VerifyService,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierX509ETSIValidationResult>(
        supportedIdentifierMethods = listOf(ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    IX509ETSIValidationIdentifierResolutionService {
    private val logger = execution.log.logManager.withTagAsync("X509ETSIValidationIdentifierResolutionService")
    private val loggerSync = execution.log.logManager.withTag("X509ETSIValidationIdentifierResolutionService")

    // Cache for trust lists (keyed by TSL URI)
    private val tslCache = mutableMapOf<String, CachedTrustList>()

    // Cache for LOTL
    private var lotlCache: CachedTrustList? = null

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierX509ETSIValidationResult, IdkErrorType> {
        log.debug("Validating X.509 certificate against ETSI trust lists...")

        if (!supports(args)) {
            return IdkError
                .COMMAND_ARG_NOT_SUPPORTED_ERROR(
                    message = "External identifier opts for X.509 ETSI validation expected. Type ${args.method ?: args.identifier} not supported",
                ).asErrorResult()
        }

        val optsResult = asSupportedOpts(args)
        if (optsResult.isErr) {
            return optsResult.error.asErrorResult()
        }

        val opts = optsResult.value as ExternalIdentifierX509ETSIValidationOpts

        return try {
            validateCertificate(opts)
        } catch (e: Exception) {
            logger.error("X.509 ETSI validation failed", exception = e)
            IdkError
                .UNKNOWN_ERROR(
                    message = "X.509 ETSI validation failed: ${e.message}",
                ).asErrorResult()
        }
    }

    /**
     * Main validation logic.
     */
    private suspend fun validateCertificate(opts: ExternalIdentifierX509ETSIValidationOpts): IdkResult<ExternalIdentifierX509ETSIValidationResult, IdkErrorType> {
        val validationTime = opts.validationTime ?: Clock.System.now()

        // 1. Extract x5c from KeyInfo - this is required for validation
        val x5c = opts.identifier.x5c
        if (x5c == null || x5c.isEmpty()) {
            return IdkError
                .COMMAND_ARG_NOT_SUPPORTED_ERROR(
                    message = "Cannot validate certificate without x5c in KeyInfo",
                ).asErrorResult()
        }

        // 2. Parse the first certificate (the one to validate)
        val certBase64 = x5c[0]
        val certPEM = x509DerOrPemToPem(certBase64)
        val certificate = certificateFromPem(certPEM)

        logger.info("Validating certificate: ${certificate.subjectDN}")

        // 3. Build certificate chain from remaining x5c entries
        val chain = if (x5c.size > 1) x5c.drop(1) else null

        // 4. First validate the certificate chain with X.509
        val chainValidationResult = validateCertificateChain(x5c)
        if (!chainValidationResult.valid) {
            return createUntrustedResult(
                opts = opts,
                certificate = certificate,
                trustStatus = TrustStatus.VALIDATION_ERROR,
                details = "Certificate chain validation failed: ${chainValidationResult.error}",
                validationTime = validationTime,
            ).asOkResult()
        }

        // 3. Determine which trust list to use
        val tslInfo = determineTrustList(opts, certificate)
        if (tslInfo.isErr) {
            return createUntrustedResult(
                opts = opts,
                certificate = certificate,
                trustStatus = TrustStatus.UNKNOWN,
                details = "Failed to determine trust list: ${tslInfo.error}",
                validationTime = validationTime,
            ).asOkResult()
        }

        val determination = tslInfo.value

        logger.info("Using trust list: ${determination.tslUri} (territory: ${determination.trustList.schemeTerritory})")

        // 5. Find matching TSP in trust list
        val certDER = certBase64.decodeFrom(Encoding.BASE64)
        val matchResult =
            findMatchingTSP(
                trustList = determination.trustList,
                certificate = certificate,
                certDER = certDER,
                chain = chain,
                validationTime = validationTime,
                allowCAChainMatch = opts.allowCAChainMatch,
                serviceTypeFilter = opts.serviceTypeFilter,
            )

        // 5. Create result
        return createValidationResult(
            opts = opts,
            certificate = certificate,
            matchResult = matchResult,
            determination = determination,
            validationTime = validationTime,
            x5c = x5c,
        ).asOkResult()
    }

    /**
     * Validates the certificate chain using X509VerifyService.
     */
    private suspend fun validateCertificateChain(x5c: Array<String>): X509ValidationResult =
        try {
            // Convert x5c (Base64) to DER format
            val chainDER = x5c.map { x509DerOrPemToPem(it).encodeToByteArray() }.toTypedArray()

            val result =
                x509VerifyService.verifyCertificateChain(
                    X509VerificationRequest(
                        chainDER = chainDER,
                    ),
                )

            X509ValidationResult(
                valid = !result.error,
                error = if (result.error) result.message else null,
            )
        } catch (e: Exception) {
            logger.error("Certificate chain validation failed", exception = e)
            X509ValidationResult(valid = false, error = e.message)
        }

    /**
     * Determines which trust list to use for validation.
     * Returns: (TrustList, TSL URI, DeterminationMethod, FromCache)
     */
    private suspend fun determineTrustList(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        certificate: Certificate,
    ): IdkResult<TrustListDetermination, IdkErrorType> {
        // If explicit TSL URI is provided, use it directly
        opts.explicitTslUri?.let { tslUri ->
            logger.debug("Using explicit trust list: $tslUri")
            val trustList = resolveTrustList(tslUri, opts.useCache, opts.maxCacheAge)
            if (trustList.isErr) {
                return trustList.error.asErrorResult()
            }
            val (tsl, fromCache) = trustList.value
            return TrustListDetermination(tsl, tslUri, TslDeterminationMethod.EXPLICIT, fromCache).asOkResult()
        }

        // Extract country from certificate
        val country = extractCountryFromCertificate(certificate)
        if (country == null) {
            logger.warn("Could not extract country from certificate")
            return IdkError
                .UNKNOWN_ERROR(
                    message = "Could not extract country (C attribute) from certificate",
                ).asErrorResult()
        }

        logger.debug("Certificate country: $country")

        // Navigate LOTL to find member state trust list
        val lotlNavResult = navigateLOTL(opts.lotlUri, country, opts.useCache, opts.maxCacheAge)
        if (lotlNavResult.isErr) {
            return lotlNavResult.error.asErrorResult()
        }

        val (tslUri, resolvedLotl) = lotlNavResult.value
        logger.debug("Determined trust list for $country: $tslUri")

        // Resolve the member state trust list
        val trustList = resolveTrustList(tslUri, opts.useCache, opts.maxCacheAge)
        if (trustList.isErr) {
            return trustList.error.asErrorResult()
        }

        val (tsl, fromCache) = trustList.value
        return TrustListDetermination(tsl, tslUri, TslDeterminationMethod.LOTL_NAVIGATION, fromCache, lotl = resolvedLotl).asOkResult()
    }

    /**
     * Extracts the country (C attribute) from the certificate.
     */
    private fun extractCountryFromCertificate(certificate: Certificate): String? {
        // Parse subject DN to extract C (Country) attribute
        // Format is typically: C=NL, O=Organization, CN=Common Name
        val subjectDN = certificate.subjectDN
        val countryRegex = Regex("""C\s*=\s*([A-Z]{2})""", RegexOption.IGNORE_CASE)
        val match = countryRegex.find(subjectDN)
        return match?.groupValues?.get(1)?.uppercase()
    }

    /*
     * Navigates LOTL to find the trust list for the given country.
     */

    /**
     * Navigates LOTL to find the trust list for the given country.
     * Returns the TSL URI and the resolved LOTL (for entity discovery).
     */
    private suspend fun navigateLOTL(
        lotlUri: String,
        country: String,
        useCache: Boolean,
        maxCacheAge: Long,
    ): IdkResult<Pair<String, ETSILoTE>, IdkErrorType> {
        logger.debug("Navigating LOTL to find trust list for country: $country")

        val lotlResult = resolveTrustList(lotlUri, useCache, maxCacheAge)
        if (lotlResult.isErr) {
            return lotlResult.error.asErrorResult()
        }

        val (lotl, _) = lotlResult.value

        // Find pointer to member state trust list
        val pointer =
            lotl.pointersToOtherLoTE.firstOrNull { pointer ->
                pointer.schemeTerritory.equals(country, ignoreCase = true)
            }

        if (pointer == null) {
            return IdkError
                .UNKNOWN_ERROR(
                    message = "No trust list found in LOTL for country: $country",
                ).asErrorResult()
        }

        return (pointer.location to lotl).asOkResult()
    }

    /**
     * Resolves a trust list from URI (with caching).
     * Returns: (TrustList, FromCache)
     */
    private suspend fun resolveTrustList(
        tslUri: String,
        useCache: Boolean,
        maxCacheAge: Long,
    ): IdkResult<Pair<ETSILoTE, Boolean>, IdkErrorType> {
        // Check cache
        if (useCache) {
            tslCache[tslUri]?.let { cached ->
                val age = Clock.System.now().toEpochMilliseconds() - cached.timestamp
                if (age < maxCacheAge) {
                    logger.debug("Using cached trust list for $tslUri (age: ${age}ms)")
                    return (cached.trustList to true).asOkResult()
                } else {
                    logger.debug("Cached trust list for $tslUri is too old (age: ${age}ms > $maxCacheAge ms)")
                }
            }
        }

        // Find resolver
        val resolver =
            trustListResolvers.firstOrNull { it.supports(tslUri) }
                ?: return IdkError
                    .UNKNOWN_ERROR(
                        message = "No trust list resolver supports URI: $tslUri",
                    ).asErrorResult()

        // Resolve trust list
        val resolutionOptions =
            ResolutionOptions(
                useCache = useCache,
                maxCacheAgeMs = maxCacheAge,
                verifySignature = true,
            )

        try {
            val trustListData = resolver.resolve(tslUri, resolutionOptions)
            val trustList = trustListParser.parseFromBytes(trustListData.data)

            // Update cache
            if (useCache) {
                tslCache[tslUri] =
                    CachedTrustList(
                        trustList = trustList,
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                    )
            }

            logger.info("Resolved trust list: ${trustList.schemeName.firstOrNull()?.value} (seq: ${trustList.sequenceNumber})")

            return (trustList to false).asOkResult()
        } catch (e: Exception) {
            logger.error("Failed to resolve trust list from $tslUri", exception = e)
            return IdkError
                .UNKNOWN_ERROR(
                    message = "Failed to resolve trust list: ${e.message}",
                ).asErrorResult()
        }
    }

    /**
     * Finds matching TSP in the trust list.
     * Delegates to [CertificateTrustListMatcher] for the actual certificate matching.
     */
    private fun findMatchingTSP(
        trustList: ETSILoTE,
        certificate: Certificate,
        certDER: ByteArray,
        chain: List<String>?,
        validationTime: Instant,
        allowCAChainMatch: Boolean,
        serviceTypeFilter: List<String>?,
    ): TspMatchResult? {
        val matchResult =
            CertificateTrustListMatcher.findMatchingEntity(
                trustList = trustList,
                certDER = certDER,
                chain = chain,
                serviceTypeFilter = serviceTypeFilter,
                allowCAChainMatch = allowCAChainMatch,
                validationTime = validationTime,
            ) ?: return null

        return TspMatchResult(
            tspName =
                matchResult.entity.trustedEntityInformation.name
                    .firstOrNull()
                    ?.value ?: "Unknown",
            tspIdentifier = matchResult.entity.trustedEntityInformation.identifier,
            serviceName =
                matchResult.serviceInfo.serviceName
                    .firstOrNull()
                    ?.value ?: "Unknown",
            serviceTypeIdentifier = matchResult.serviceInfo.serviceTypeIdentifier,
            serviceStatus = matchResult.serviceInfo.serviceStatus,
            statusStartingTime = matchResult.serviceInfo.statusStartingTime,
            matchType = matchResult.matchType,
            caChain = matchResult.caChain,
            serviceInfo = matchResult.serviceInfo,
            entity = matchResult.entity,
        )
    }

    /**
     * Creates validation result with automatic entity discovery chain.
     */
    private suspend fun createValidationResult(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        certificate: Certificate,
        matchResult: TspMatchResult?,
        determination: TrustListDetermination,
        validationTime: Instant,
        x5c: Array<String>,
    ): ExternalIdentifierX509ETSIValidationResult {
        val trustList = determination.trustList
        val tslUri = determination.tslUri

        // Convert KeyInfo to JWK format using CoseJoseKeyMappingService
        val jwkKeyInfo = CoseJoseKeyMappingService.toJwkKeyInfo(opts.identifier)
        val keyInfo =
            if (jwkKeyInfo.key != null) {
                ResolvedKeyInfo.fromKey(jwkKeyInfo.key!!)
            } else {
                ResolvedKeyInfo.fromKey(certificate.getPublicKeyJwk())
            }

        val trustValidation =
            if (matchResult != null) {
                val (trusted, trustStatus) = evaluateTrustStatus(matchResult.serviceStatus)
                buildTrustValidation(
                    opts = opts,
                    trusted = trusted,
                    trustStatus = trustStatus,
                    matchResult = matchResult,
                    determination = determination,
                    validationTime = validationTime,
                    x5c = x5c,
                )
            } else {
                null
            }

        return if (matchResult == null) {
            ExternalIdentifierX509ETSIValidationResult(
                identifierOpts = opts,
                trusted = false,
                trustStatus = TrustStatus.UNTRUSTED,
                trustAnchor = null,
                validationPath = emptyList(),
                details = "Certificate not found in ETSI trust list",
                trustListInfo = createTrustListInfo(trustList, tslUri, determination.determinationMethod, determination.fromCache),
                matchedTSP = null,
                jwks = arrayOf(keyInfo),
                keyInfo = keyInfo,
                validatedAt = validationTime,
                trustValidation = trustValidation,
            )
        } else {
            val (trusted, trustStatus) = evaluateTrustStatus(matchResult.serviceStatus)
            val trustAnchor = createTrustAnchor(matchResult, trustList.schemeTerritory)

            ExternalIdentifierX509ETSIValidationResult(
                identifierOpts = opts,
                trusted = trusted,
                trustStatus = trustStatus,
                trustAnchor = trustAnchor,
                validationPath = buildValidationPath(trustList, matchResult),
                details = if (trusted) "Certificate validated via ${matchResult.matchType}" else "Service status: ${matchResult.serviceStatus}",
                trustListInfo = createTrustListInfo(trustList, tslUri, determination.determinationMethod, determination.fromCache),
                matchedTSP = createMatchedTSPInfo(matchResult),
                jwks = arrayOf(keyInfo),
                keyInfo = keyInfo,
                validatedAt = validationTime,
                trustValidation = trustValidation,
            )
        }
    }

    /**
     * Builds a [TrustValidationResult] with full entity discovery chain:
     * certs → TSP (trust anchor) → TL operator → LOTL operator
     */
    private fun buildTrustValidation(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        trusted: Boolean,
        trustStatus: TrustStatus,
        matchResult: TspMatchResult,
        determination: TrustListDetermination,
        validationTime: Instant,
        x5c: Array<String>,
    ): TrustValidationResult {
        val discoveryOpts = opts.entityDiscovery
        val discoveredEntities =
            if (discoveryOpts?.enabled == true && discoveryOpts.deferred != true) {
                buildDiscoveredEntities(x5c, matchResult, determination, discoveryOpts)
            } else {
                emptyList()
            }

        return TrustValidationResult(
            trusted = trusted,
            status = trustStatus,
            trustAnchor = createTrustAnchor(matchResult, determination.trustList.schemeTerritory),
            validationPath = buildValidationPath(determination.trustList, matchResult),
            details = if (trusted) "Certificate validated via ${matchResult.matchType}" else "Service status: ${matchResult.serviceStatus}",
            validatedAt = validationTime,
            discoveredEntities = discoveredEntities,
        )
    }

    /**
     * Builds the full entity discovery chain automatically.
     */
    private fun buildDiscoveredEntities(
        x5c: Array<String>,
        matchResult: TspMatchResult,
        determination: TrustListDetermination,
        options: EntityDiscoveryOptions,
    ): List<DiscoveredEntityInfo> {
        val maxDepth = if (options.maxDepth == 0) Int.MAX_VALUE else options.maxDepth
        val extractor = EtsiEntityInfoExtractor()
        var depth = 0

        return buildList {
            // Certs from x5c chain
            for ((index, certBase64) in x5c.withIndex()) {
                if (depth >= maxDepth) return@buildList
                try {
                    val certDer = certBase64.decodeFrom(Encoding.BASE64)
                    val cert = certificateFromDer(certDer)
                    val dnParts = parseSimpleDN(cert.subjectDN)
                    val nodeRole = if (index == 0) TrustChainNodeRole.LEAF else TrustChainNodeRole.INTERMEDIATE

                    add(
                        DiscoveredEntityInfo(
                            entityIdentifier = cert.subjectDN,
                            sourceType = TrustAnchorType.X509_CA_BUNDLE,
                            chainPosition = TrustChainPosition(depth = depth, role = nodeRole),
                            names =
                                buildList {
                                    dnParts["CN"]?.let { add(LocalizedString(lang = "und", value = it)) }
                                    val org = dnParts["O"]
                                    if (org != null && org != dnParts["CN"]) add(LocalizedString(lang = "und", value = org))
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
                } catch (e: Exception) {
                    loggerSync.debug("Failed to parse x5c certificate at index for entity discovery: ${e.message}")
                }
            }

            // Issuer DN if only leaf in x5c
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
                                        if (org != null && org != issuerParts["CN"]) add(LocalizedString(lang = "und", value = org))
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
                } catch (e: Exception) {
                    loggerSync.debug("Failed to parse leaf cert issuer DN for entity discovery: ${e.message}")
                }
            }

            // Matched TSP — the trust anchor
            if (depth < maxDepth && matchResult.entity != null) {
                add(
                    extractor
                        .mapEntity(
                            entity = matchResult.entity,
                            territory = determination.trustList.schemeTerritory,
                            matchedServiceType = matchResult.serviceTypeIdentifier,
                            depth = depth,
                            nodeRole = TrustChainNodeRole.INTERMEDIATE,
                        ).copy(trustAnchor = true),
                )
                depth++
            }

            // TL scheme operator
            if (depth < maxDepth) {
                add(extractor.mapSchemeOperator(determination.trustList, depth = depth))
                depth++
            }

            // LOTL scheme operator (if navigated via LOTL)
            if (depth < maxDepth && determination.lotl != null) {
                add(extractor.mapSchemeOperator(determination.lotl, depth = depth))
            }
        }
    }

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

    /**
     * Creates untrusted result.
     */
    private suspend fun createUntrustedResult(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        certificate: Certificate,
        trustStatus: TrustStatus,
        details: String,
        validationTime: Instant,
    ): ExternalIdentifierX509ETSIValidationResult {
        // Convert KeyInfo to JWK format using CoseJoseKeyMappingService
        val jwkKeyInfo = CoseJoseKeyMappingService.toJwkKeyInfo(opts.identifier)
        val keyInfo =
            if (jwkKeyInfo.key != null) {
                ResolvedKeyInfo.fromKey(jwkKeyInfo.key!!)
            } else {
                ResolvedKeyInfo.fromKey(certificate.getPublicKeyJwk())
            }

        return ExternalIdentifierX509ETSIValidationResult(
            identifierOpts = opts,
            trusted = false,
            trustStatus = trustStatus,
            trustAnchor = null,
            validationPath = emptyList(),
            details = details,
            trustListInfo =
                TrustListValidationInfo(
                    tslUri = "unknown",
                    territory = "unknown",
                    schemeName = "unknown",
                    sequenceNumber = 0,
                    listIssueDateTime = validationTime,
                    nextUpdate = validationTime,
                    fromCache = false,
                    determinationMethod = TslDeterminationMethod.OTHER,
                ),
            matchedTSP = null,
            jwks = arrayOf(keyInfo),
            keyInfo = keyInfo,
            validatedAt = validationTime,
        )
    }

    private fun evaluateTrustStatus(serviceStatus: String): Pair<Boolean, TrustStatus> =
        when (serviceStatus) {
            ETSIServiceStatus.GRANTED,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL,
            -> true to TrustStatus.TRUSTED

            ETSIServiceStatus.REVOKED -> false to TrustStatus.REVOKED

            ETSIServiceStatus.WITHDRAWN,
            ETSIServiceStatus.SUSPENDED,
            -> false to TrustStatus.UNTRUSTED

            else -> false to TrustStatus.UNKNOWN
        }

    private fun createTrustAnchor(
        match: TspMatchResult,
        territory: String,
    ): TrustAnchor {
        val certBase64 =
            match.serviceInfo.serviceDigitalIdentity.x509Certificates
                .firstOrNull()
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
            id = "${match.tspIdentifier ?: match.tspName}_${match.serviceTypeIdentifier}",
            type = TrustAnchor.TYPE_ETSI_TSP,
            name = match.serviceName,
            keyInfo = keyInfo,
            uri =
                match.serviceInfo.serviceSupplyPoints
                    .firstOrNull()
                    ?.uri,
            metadata =
                mapOf(
                    "tspName" to match.tspName,
                    "serviceType" to match.serviceTypeIdentifier,
                    "serviceStatus" to match.serviceStatus,
                    "territory" to territory,
                    "matchType" to match.matchType.name,
                ),
            validFrom = match.statusStartingTime,
        )
    }

    private fun buildValidationPath(
        trustList: ETSILoTE,
        match: TspMatchResult,
    ): List<String> {
        val path = mutableListOf<String>()
        path.add(trustList.schemeTerritory)
        path.add(match.tspName)
        path.add(match.serviceName)
        if (match.matchType == TspMatchType.CA_CHAIN_MATCH) {
            path.add("via CA chain")
        }
        return path
    }

    private fun createTrustListInfo(
        trustList: ETSILoTE,
        tslUri: String,
        determinationMethod: TslDeterminationMethod,
        fromCache: Boolean,
    ): TrustListValidationInfo =
        TrustListValidationInfo(
            tslUri = tslUri,
            territory = trustList.schemeTerritory,
            schemeName = trustList.schemeName.firstOrNull()?.value ?: "Unknown",
            sequenceNumber = trustList.sequenceNumber,
            listIssueDateTime = trustList.listIssueDateTime,
            nextUpdate = trustList.nextUpdate,
            fromCache = fromCache,
            determinationMethod = determinationMethod,
        )

    private fun createMatchedTSPInfo(match: TspMatchResult): MatchedTSPInfo =
        MatchedTSPInfo(
            tspName = match.tspName,
            tspIdentifier = match.tspIdentifier,
            serviceName = match.serviceName,
            serviceTypeIdentifier = match.serviceTypeIdentifier,
            serviceStatus = match.serviceStatus,
            statusStartingTime = match.statusStartingTime,
            matchType = match.matchType,
            caChain = match.caChain,
        )

    override suspend fun supports(args: Any): Boolean =
        when (args) {
            is ExternalIdentifierX509ETSIValidationOpts -> {
                true
            }

            is ExternalIdentifierOptsOrResult -> {
                args.method == ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION
            }

            else -> {
                false
            }
        }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // Support KeyInfoType containing x5c
        return identifier is KeyInfoType<*> && identifier.x5c != null
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<out ExternalIdentifierX509ETSIValidationResult, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
        when {
            opts is ExternalIdentifierX509ETSIValidationOpts -> {
                opts.asOkResult()
            }

            opts.method == ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION &&
                opts.identifier is KeyInfoType<*> -> {
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = opts.identifier as KeyInfoType<KeyType>,
                    context = opts.context,
                    lookup = opts.lookup as? AdditionalIdentifierLookup ?: AdditionalIdentifierLookup(),
                ).asOkResult()
            }

            else -> {
                IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
            }
        }

    // Internal data classes
    private data class TrustListDetermination(
        val trustList: ETSILoTE,
        val tslUri: String,
        val determinationMethod: TslDeterminationMethod,
        val fromCache: Boolean,
        val lotl: ETSILoTE? = null,
    )

    private data class TspMatchResult(
        val tspName: String,
        val tspIdentifier: String?,
        val serviceName: String,
        val serviceTypeIdentifier: String,
        val serviceStatus: String,
        val statusStartingTime: Instant,
        val matchType: TspMatchType,
        val caChain: List<String>?,
        val serviceInfo: ETSIServiceInformation,
        val entity: ETSITrustedEntity? = null,
    )

    private data class CachedTrustList(
        val trustList: ETSILoTE,
        val timestamp: Long,
    )

    private data class X509ValidationResult(
        val valid: Boolean,
        val error: String? = null,
    )

    companion object {
        const val COMMAND_ID = "trust.etsi.x509validation"
    }
}
