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

package com.sphereon.trust.etsi.resolution

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.crypto.core.x509.x509DerOrPemToPem
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierServiceAdapter
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.lote.model.forLang
import com.sphereon.trust.etsi.matcher.CertificateTrustListMatcher
import com.sphereon.trust.etsi.model.*
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding

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
    private val x509VerifyService: X509VerifyService
) : ExternalIdentifierServiceAdapter<ExternalIdentifierX509ETSIValidationResult>(
    supportedIdentifierMethods = listOf(ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION),
    execution = execution,
    commandId = COMMAND_ID
), IX509ETSIValidationIdentifierResolutionService {

    private val logger = execution.log.logManager.withTagAsync("X509ETSIValidationIdentifierResolutionService")
    private val loggerSync = execution.log.logManager.withTag("X509ETSIValidationIdentifierResolutionService")

    // Cache for trust lists (keyed by TSL URI)
    private val tslCache = mutableMapOf<String, CachedTrustList>()

    // Cache for LOTL
    private var lotlCache: CachedTrustList? = null

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult
    ): IdkResult<ExternalIdentifierX509ETSIValidationResult, IdkErrorType> {
        log.debug("Validating X.509 certificate against ETSI trust lists...")

        if (!supports(args)) {
            return IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message = "External identifier opts for X.509 ETSI validation expected. Type ${args.method ?: args.identifier} not supported"
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
            IdkError.UNKNOWN_ERROR(
                message = "X.509 ETSI validation failed: ${e.message}"
            ).asErrorResult()
        }
    }

    /**
     * Main validation logic.
     */
    private suspend fun validateCertificate(
        opts: ExternalIdentifierX509ETSIValidationOpts
    ): IdkResult<ExternalIdentifierX509ETSIValidationResult, IdkErrorType> {
        val validationTime = opts.validationTime ?: Clock.System.now()

        // 1. Extract x5c from KeyInfo - this is required for validation
        val x5c = opts.identifier.x5c
        if (x5c == null || x5c.isEmpty()) {
            return IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message = "Cannot validate certificate without x5c in KeyInfo"
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
                validationTime = validationTime
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
                validationTime = validationTime
            ).asOkResult()
        }

        val (trustList, tslUri, determinationMethod, fromCache) = tslInfo.value

        logger.info("Using trust list: $tslUri (territory: ${trustList.schemeTerritory})")

        // 5. Find matching TSP in trust list
        val certDER = certBase64.decodeFrom(Encoding.BASE64)
        val matchResult = findMatchingTSP(
            trustList = trustList,
            certificate = certificate,
            certDER = certDER,
            chain = chain,
            validationTime = validationTime,
            allowCAChainMatch = opts.allowCAChainMatch,
            serviceTypeFilter = opts.serviceTypeFilter
        )

        // 5. Create result
        return createValidationResult(
            opts = opts,
            certificate = certificate,
            matchResult = matchResult,
            trustList = trustList,
            tslUri = tslUri,
            determinationMethod = determinationMethod,
            fromCache = fromCache,
            validationTime = validationTime
        ).asOkResult()
    }

    /**
     * Validates the certificate chain using X509VerifyService.
     */
    private suspend fun validateCertificateChain(
        x5c: Array<String>
    ): X509ValidationResult {
        return try {
            // Convert x5c (Base64) to DER format
            val chainDER = x5c.map { x509DerOrPemToPem(it).encodeToByteArray() }.toTypedArray()

            val result = x509VerifyService.verifyCertificateChain(
                X509VerificationRequest(
                    chainDER = chainDER
                )
            )

            X509ValidationResult(
                valid = !result.error,
                error = if (result.error) result.message else null
            )
        } catch (e: Exception) {
            logger.error("Certificate chain validation failed", exception = e)
            X509ValidationResult(valid = false, error = e.message)
        }
    }

    /**
     * Determines which trust list to use for validation.
     * Returns: (TrustList, TSL URI, DeterminationMethod, FromCache)
     */
    private suspend fun determineTrustList(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        certificate: Certificate
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
            return IdkError.UNKNOWN_ERROR(
                message = "Could not extract country (C attribute) from certificate"
            ).asErrorResult()
        }

        logger.debug("Certificate country: $country")

        // Navigate LOTL to find member state trust list
        val tslUri = navigateLOTL(opts.lotlUri, country, opts.useCache, opts.maxCacheAge)
        if (tslUri.isErr) {
            return tslUri.error.asErrorResult()
        }

        logger.debug("Determined trust list for $country: ${tslUri.value}")

        // Resolve the member state trust list
        val trustList = resolveTrustList(tslUri.value, opts.useCache, opts.maxCacheAge)
        if (trustList.isErr) {
            return trustList.error.asErrorResult()
        }

        val (tsl, fromCache) = trustList.value
        return TrustListDetermination(tsl, tslUri.value, TslDeterminationMethod.LOTL_NAVIGATION, fromCache).asOkResult()
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

    /**
     * Navigates LOTL to find the trust list for the given country.
     */
    private suspend fun navigateLOTL(
        lotlUri: String,
        country: String,
        useCache: Boolean,
        maxCacheAge: Long
    ): IdkResult<String, IdkErrorType> {
        logger.debug("Navigating LOTL to find trust list for country: $country")

        val lotlResult = resolveTrustList(lotlUri, useCache, maxCacheAge)
        if (lotlResult.isErr) {
            return lotlResult.error.asErrorResult()
        }

        val (lotl, _) = lotlResult.value

        // Find pointer to member state trust list
        val pointer = lotl.pointersToOtherLoTE.firstOrNull { pointer ->
            pointer.schemeTerritory.equals(country, ignoreCase = true)
        }

        if (pointer == null) {
            return IdkError.UNKNOWN_ERROR(
                message = "No trust list found in LOTL for country: $country"
            ).asErrorResult()
        }

        return pointer.location.asOkResult()
    }

    /**
     * Resolves a trust list from URI (with caching).
     * Returns: (TrustList, FromCache)
     */
    private suspend fun resolveTrustList(
        tslUri: String,
        useCache: Boolean,
        maxCacheAge: Long
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
        val resolver = trustListResolvers.firstOrNull { it.supports(tslUri) }
            ?: return IdkError.UNKNOWN_ERROR(
                message = "No trust list resolver supports URI: $tslUri"
            ).asErrorResult()

        // Resolve trust list
        val resolutionOptions = ResolutionOptions(
            useCache = useCache,
            maxCacheAgeMs = maxCacheAge,
            verifySignature = true
        )

        try {
            val trustListData = resolver.resolve(tslUri, resolutionOptions)
            val trustList = trustListParser.parseFromBytes(trustListData.data)

            // Update cache
            if (useCache) {
                tslCache[tslUri] = CachedTrustList(
                    trustList = trustList,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            }

            logger.info("Resolved trust list: ${trustList.schemeName.firstOrNull()?.value} (seq: ${trustList.sequenceNumber})")

            return (trustList to false).asOkResult()
        } catch (e: Exception) {
            logger.error("Failed to resolve trust list from $tslUri", exception = e)
            return IdkError.UNKNOWN_ERROR(
                message = "Failed to resolve trust list: ${e.message}"
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
        serviceTypeFilter: List<String>?
    ): TspMatchResult? {
        val matchResult = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certDER,
            chain = chain,
            serviceTypeFilter = serviceTypeFilter,
            allowCAChainMatch = allowCAChainMatch,
            validationTime = validationTime
        ) ?: return null

        return TspMatchResult(
            tspName = matchResult.entity.trustedEntityInformation.name.firstOrNull()?.value ?: "Unknown",
            tspIdentifier = matchResult.entity.trustedEntityInformation.identifier,
            serviceName = matchResult.serviceInfo.serviceName.firstOrNull()?.value ?: "Unknown",
            serviceTypeIdentifier = matchResult.serviceInfo.serviceTypeIdentifier,
            serviceStatus = matchResult.serviceInfo.serviceStatus,
            statusStartingTime = matchResult.serviceInfo.statusStartingTime,
            matchType = matchResult.matchType,
            caChain = matchResult.caChain,
            serviceInfo = matchResult.serviceInfo
        )
    }

    /**
     * Creates validation result.
     */
    private suspend fun createValidationResult(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        certificate: Certificate,
        matchResult: TspMatchResult?,
        trustList: ETSILoTE,
        tslUri: String,
        determinationMethod: TslDeterminationMethod,
        fromCache: Boolean,
        validationTime: Instant
    ): ExternalIdentifierX509ETSIValidationResult {
        // Convert KeyInfo to JWK format using CoseJoseKeyMappingService
        val jwkKeyInfo = CoseJoseKeyMappingService.toJwkKeyInfo(opts.identifier)
        val keyInfo = if (jwkKeyInfo.key != null) {
            ResolvedKeyInfo.fromKey(jwkKeyInfo.key!!)
        } else {
            ResolvedKeyInfo.fromKey(certificate.getPublicKeyJwk())
        }

        return if (matchResult == null) {
            // Not found in trust list
            ExternalIdentifierX509ETSIValidationResult(
                identifierOpts = opts,
                trusted = false,
                trustStatus = TrustStatus.UNTRUSTED,
                trustAnchor = null,
                validationPath = emptyList(),
                details = "Certificate not found in ETSI trust list",
                trustListInfo = createTrustListInfo(trustList, tslUri, determinationMethod, fromCache),
                matchedTSP = null,
                jwks = arrayOf(keyInfo),
                keyInfo = keyInfo,
                validatedAt = validationTime
            )
        } else {
            // Found in trust list - check status
            val (trusted, trustStatus) = evaluateTrustStatus(matchResult.serviceStatus)
            val trustAnchor = createTrustAnchor(matchResult, trustList.schemeTerritory)

            ExternalIdentifierX509ETSIValidationResult(
                identifierOpts = opts,
                trusted = trusted,
                trustStatus = trustStatus,
                trustAnchor = trustAnchor,
                validationPath = buildValidationPath(trustList, matchResult),
                details = if (trusted) "Certificate validated via ${matchResult.matchType}" else "Service status: ${matchResult.serviceStatus}",
                trustListInfo = createTrustListInfo(trustList, tslUri, determinationMethod, fromCache),
                matchedTSP = createMatchedTSPInfo(matchResult),
                jwks = arrayOf(keyInfo),
                keyInfo = keyInfo,
                validatedAt = validationTime
            )
        }
    }

    /**
     * Creates untrusted result.
     */
    private suspend fun createUntrustedResult(
        opts: ExternalIdentifierX509ETSIValidationOpts,
        certificate: Certificate,
        trustStatus: TrustStatus,
        details: String,
        validationTime: Instant
    ): ExternalIdentifierX509ETSIValidationResult {
        // Convert KeyInfo to JWK format using CoseJoseKeyMappingService
        val jwkKeyInfo = CoseJoseKeyMappingService.toJwkKeyInfo(opts.identifier)
        val keyInfo = if (jwkKeyInfo.key != null) {
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
            trustListInfo = TrustListValidationInfo(
                tslUri = "unknown",
                territory = "unknown",
                schemeName = "unknown",
                sequenceNumber = 0,
                listIssueDateTime = validationTime,
                nextUpdate = validationTime,
                fromCache = false,
                determinationMethod = TslDeterminationMethod.OTHER
            ),
            matchedTSP = null,
            jwks = arrayOf(keyInfo),
            keyInfo = keyInfo,
            validatedAt = validationTime
        )
    }

    private fun evaluateTrustStatus(serviceStatus: String): Pair<Boolean, TrustStatus> {
        return when (serviceStatus) {
            ETSIServiceStatus.GRANTED,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.REVOKED -> false to TrustStatus.REVOKED
            ETSIServiceStatus.WITHDRAWN,
            ETSIServiceStatus.SUSPENDED -> false to TrustStatus.UNTRUSTED
            else -> false to TrustStatus.UNKNOWN
        }
    }

    private fun createTrustAnchor(match: TspMatchResult, territory: String): TrustAnchor {
        val certBase64 = match.serviceInfo.serviceDigitalIdentity.x509Certificates.firstOrNull()
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
            id = "${match.tspIdentifier ?: match.tspName}_${match.serviceTypeIdentifier}",
            type = TrustAnchor.TYPE_ETSI_TSP,
            name = match.serviceName,
            keyInfo = keyInfo,
            uri = match.serviceInfo.serviceSupplyPoints.firstOrNull()?.uri,
            metadata = mapOf(
                "tspName" to match.tspName,
                "serviceType" to match.serviceTypeIdentifier,
                "serviceStatus" to match.serviceStatus,
                "territory" to territory,
                "matchType" to match.matchType.name
            ),
            validFrom = match.statusStartingTime
        )
    }

    private fun buildValidationPath(trustList: ETSILoTE, match: TspMatchResult): List<String> {
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
        fromCache: Boolean
    ): TrustListValidationInfo {
        return TrustListValidationInfo(
            tslUri = tslUri,
            territory = trustList.schemeTerritory,
            schemeName = trustList.schemeName.firstOrNull()?.value ?: "Unknown",
            sequenceNumber = trustList.sequenceNumber,
            listIssueDateTime = trustList.listIssueDateTime,
            nextUpdate = trustList.nextUpdate,
            fromCache = fromCache,
            determinationMethod = determinationMethod
        )
    }

    private fun createMatchedTSPInfo(match: TspMatchResult): MatchedTSPInfo {
        return MatchedTSPInfo(
            tspName = match.tspName,
            tspIdentifier = match.tspIdentifier,
            serviceName = match.serviceName,
            serviceTypeIdentifier = match.serviceTypeIdentifier,
            serviceStatus = match.serviceStatus,
            statusStartingTime = match.statusStartingTime,
            matchType = match.matchType,
            caChain = match.caChain
        )
    }

    override suspend fun supports(args: Any): Boolean {
        return when (args) {
            is ExternalIdentifierX509ETSIValidationOpts -> true
            is ExternalIdentifierOptsOrResult -> {
                args.method == ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION
            }
            else -> false
        }
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // Support KeyInfoType containing x5c
        return identifier is KeyInfoType<*> && identifier.x5c != null
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<out ExternalIdentifierX509ETSIValidationResult, IdkErrorType> {
        return execute(opts)
    }

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> {
        return when {
            opts is ExternalIdentifierX509ETSIValidationOpts -> opts.asOkResult()
            opts.method == ETSIValidationIdentifierMethod.X509_ETSI_VALIDATION &&
                opts.identifier is KeyInfoType<*> -> {
                ExternalIdentifierX509ETSIValidationOpts(
                    identifier = opts.identifier as KeyInfoType<KeyType>,
                    context = opts.context,
                    lookup = opts.lookup as? AdditionalIdentifierLookup ?: AdditionalIdentifierLookup()
                ).asOkResult()
            }
            else -> IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }
    }

    // Internal data classes
    private data class TrustListDetermination(
        val trustList: ETSILoTE,
        val tslUri: String,
        val determinationMethod: TslDeterminationMethod,
        val fromCache: Boolean
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
        val serviceInfo: ETSIServiceInformation
    )

    private data class CachedTrustList(
        val trustList: ETSILoTE,
        val timestamp: Long
    )

    private data class X509ValidationResult(
        val valid: Boolean,
        val error: String? = null
    )

    companion object {
        const val COMMAND_ID = "trust.etsi.x509validation"
    }
}
