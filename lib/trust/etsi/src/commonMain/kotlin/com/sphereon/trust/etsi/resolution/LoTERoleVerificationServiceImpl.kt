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
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.matcher.CertificateTrustListMatcher
import com.sphereon.trust.etsi.model.*
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import com.sphereon.di.session.SessionScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Interface for verifying eIDAS roles against EU trust lists.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ILoTERoleVerificationService", exact = true)
interface LoTERoleVerificationService {
    /**
     * Verifies whether a certificate is authorized for a specific eIDAS role.
     */
    suspend fun verifyRole(request: RoleVerificationRequest): IdkResult<RoleVerificationResult, IdkErrorType>

    /**
     * Discovers all eIDAS roles a certificate is authorized for.
     */
    suspend fun discoverRoles(request: RoleDiscoveryRequest): IdkResult<RoleDiscoveryResult, IdkErrorType>
}

/**
 * Verifies whether a party's X.509 certificate is listed in the official EU trust lists
 * for a specific eIDAS role (PID issuer, wallet provider, QEAA provider, relying party, registrar).
 *
 * Supports both ETSI TS 119 602 (LoTE) and 612 (TSL) trust list formats:
 * - **602 path**: Navigate LOTL by LoTEType qualifier to find role-specific LoTEs
 * - **612 path**: Navigate LOTL by territory, then filter services by type
 *
 * The format is auto-detected based on whether LOTL pointer qualifiers contain LoTEType data.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class LoTERoleVerificationServiceImpl(
    private val execution: SessionExecution,
    private val trustListResolvers: Set<TrustListResolver>,
    private val trustListParser: ETSITrustListParser,
) : LoTERoleVerificationService {

    private val logger = execution.log.logManager.withTagAsync("LoTERoleVerificationService")

    // Cache for trust lists (keyed by URI)
    private val tslCache = mutableMapOf<String, CachedTrustList>()

    override suspend fun verifyRole(
        request: RoleVerificationRequest
    ): IdkResult<RoleVerificationResult, IdkErrorType> {
        val verificationTime = Clock.System.now()

        // Extract x5c
        val x5c = request.certificate.x5c
        if (x5c == null || x5c.isEmpty()) {
            return IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message = "Cannot verify role without x5c in KeyInfo"
            ).asErrorResult()
        }

        val certBase64 = x5c[0]
        val certDER = certBase64.decodeFrom(Encoding.BASE64)
        val chain = if (x5c.size > 1) x5c.drop(1) else null

        logger.info("Verifying role ${request.role.name} for certificate")

        // Resolve LOTL
        val lotlResult = resolveTrustList(request.lotlUri, request.useCache, request.maxCacheAge)
        if (lotlResult.isErr) {
            return notVerifiedResult(
                request.role, TrustStatus.UNKNOWN,
                "Failed to resolve LOTL: ${lotlResult.error}", verificationTime
            ).asOkResult()
        }
        val (lotl, _) = lotlResult.value

        // Find relevant trust list pointers for this role
        val pointers = findPointersForRole(lotl, request.role, request.territory)
        if (pointers.isEmpty()) {
            return notVerifiedResult(
                request.role, TrustStatus.UNKNOWN,
                "No trust list pointers found for role ${request.role.name}" +
                    (request.territory?.let { " in territory $it" } ?: ""),
                verificationTime
            ).asOkResult()
        }

        // Check each matching trust list
        for (pointer in pointers) {
            val tlResult = resolveTrustList(pointer.location, request.useCache, request.maxCacheAge)
            if (tlResult.isErr) {
                logger.warn("Failed to resolve trust list at ${pointer.location}: ${tlResult.error}")
                continue
            }

            val (trustList, fromCache) = tlResult.value

            // Build service type filter based on role
            val serviceTypeFilter = buildServiceTypeFilter(request.role)

            // Determine allow flags based on match strategy
            val (allowDirect, allowChain) = when (request.matchStrategy) {
                MatchStrategy.DIRECT_ONLY -> true to false
                MatchStrategy.CA_CHAIN_ONLY -> false to true
                MatchStrategy.DIRECT_THEN_CA_CHAIN -> true to true
            }

            // For DIRECT_ONLY and DIRECT_THEN_CA_CHAIN, try direct match first
            if (allowDirect) {
                val directResult = CertificateTrustListMatcher.findMatchingEntity(
                    trustList = trustList,
                    certDER = certDER,
                    chain = null, // No chain for direct-only pass
                    serviceTypeFilter = serviceTypeFilter,
                    allowCAChainMatch = false
                )
                if (directResult != null) {
                    return buildVerifiedResult(
                        directResult, request.role, trustList, pointer, fromCache, verificationTime
                    ).asOkResult()
                }
            }

            // For CA_CHAIN_ONLY and DIRECT_THEN_CA_CHAIN, try chain match
            if (allowChain && chain != null) {
                val chainResult = CertificateTrustListMatcher.findMatchingEntity(
                    trustList = trustList,
                    certDER = certDER,
                    chain = chain.toList(),
                    serviceTypeFilter = serviceTypeFilter,
                    allowCAChainMatch = true
                )
                if (chainResult != null) {
                    return buildVerifiedResult(
                        chainResult, request.role, trustList, pointer, fromCache, verificationTime
                    ).asOkResult()
                }
            }
        }

        return notVerifiedResult(
            request.role, TrustStatus.UNTRUSTED,
            "Certificate not found in any trust list for role ${request.role.name}",
            verificationTime
        ).asOkResult()
    }

    override suspend fun discoverRoles(
        request: RoleDiscoveryRequest
    ): IdkResult<RoleDiscoveryResult, IdkErrorType> {
        val discoveryTime = Clock.System.now()
        val results = mutableListOf<RoleVerificationResult>()

        for (role in EidasRole.entries) {
            val verifyRequest = RoleVerificationRequest(
                certificate = request.certificate,
                role = role,
                lotlUri = request.lotlUri,
                territory = request.territory,
                matchStrategy = request.matchStrategy,
                useCache = request.useCache,
                maxCacheAge = request.maxCacheAge
            )
            val result = verifyRole(verifyRequest)
            if (result.isOk) {
                results.add(result.value)
            }
        }

        return RoleDiscoveryResult(
            verifiedRoles = results,
            discoveredAt = discoveryTime
        ).asOkResult()
    }

    /**
     * Finds LOTL pointers relevant for the given role.
     *
     * Uses dual-format detection:
     * - If pointers have LoTEType qualifiers (602 format): filter by LoTEType
     * - Otherwise (612 format): filter by territory
     */
    internal fun findPointersForRole(
        lotl: ETSILoTE,
        role: EidasRole,
        territory: String?
    ): List<ETSIOtherLoTEPointer> {
        // First try 602 navigation: look for pointers with matching LoTEType qualifier
        val loteTypePointers = lotl.pointersToOtherLoTE.filter { pointer ->
            val qualifierLoTEType = pointer.additionalInformation?.otherInformation?.firstOrNull()
            qualifierLoTEType == role.loTEType
        }

        if (loteTypePointers.isNotEmpty()) {
            // 602 format — optionally filter by territory
            return if (territory != null) {
                loteTypePointers.filter { it.schemeTerritory.equals(territory, ignoreCase = true) }
                    .ifEmpty { loteTypePointers }
            } else {
                loteTypePointers
            }
        }

        // Fall back to 612 navigation: filter by territory
        return if (territory != null) {
            lotl.pointersToOtherLoTE.filter { pointer ->
                pointer.schemeTerritory.equals(territory, ignoreCase = true)
            }
        } else {
            // Without territory and without 602 qualifiers, return all pointers
            lotl.pointersToOtherLoTE
        }
    }

    /**
     * Builds the service type filter for a role.
     * Includes both 602 issuance types and 612 legacy types.
     */
    internal fun buildServiceTypeFilter(role: EidasRole): List<String> = buildList {
        add(role.issuanceServiceType)
        role.revocationServiceType?.let { add(it) }
        addAll(role.legacyServiceTypes)
    }

    /**
     * Evaluates whether a service status indicates a trusted or untrusted entity.
     * Handles both 602 and 612 status URIs.
     */
    internal fun evaluateServiceStatus(serviceStatus: String): Pair<Boolean, TrustStatus> {
        return when (serviceStatus) {
            // 602 statuses
            ETSIServiceStatus.NOTIFIED -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.WITHDRAWN_602 -> false to TrustStatus.UNTRUSTED

            // 612 statuses
            ETSIServiceStatus.GRANTED,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.REVOKED -> false to TrustStatus.REVOKED
            ETSIServiceStatus.WITHDRAWN,
            ETSIServiceStatus.SUSPENDED -> false to TrustStatus.UNTRUSTED

            else -> false to TrustStatus.UNKNOWN
        }
    }

    private fun buildVerifiedResult(
        match: CertificateTrustListMatcher.EntityMatchResult,
        role: EidasRole,
        trustList: ETSILoTE,
        pointer: ETSIOtherLoTEPointer,
        fromCache: Boolean,
        verificationTime: Instant
    ): RoleVerificationResult {
        val (trusted, trustStatus) = evaluateServiceStatus(match.serviceInfo.serviceStatus)

        val matchedEntity = MatchedEntityInfo(
            entityName = match.entity.trustedEntityInformation.name.firstOrNull()?.value ?: "Unknown",
            entityIdentifier = match.entity.trustedEntityInformation.identifier,
            serviceName = match.serviceInfo.serviceName.firstOrNull()?.value ?: "Unknown",
            serviceType = match.serviceInfo.serviceTypeIdentifier,
            serviceStatus = match.serviceInfo.serviceStatus,
            statusStartingTime = match.serviceInfo.statusStartingTime,
            matchType = match.matchType,
            territory = trustList.schemeTerritory
        )

        val trustListInfo = TrustListValidationInfo(
            tslUri = pointer.location,
            territory = trustList.schemeTerritory,
            schemeName = trustList.schemeName.firstOrNull()?.value ?: "Unknown",
            sequenceNumber = trustList.sequenceNumber,
            listIssueDateTime = trustList.listIssueDateTime,
            nextUpdate = trustList.nextUpdate,
            fromCache = fromCache,
            determinationMethod = TslDeterminationMethod.LOTL_NAVIGATION
        )

        return RoleVerificationResult(
            verified = trusted,
            role = role,
            trustStatus = trustStatus,
            matchedEntity = matchedEntity,
            trustListInfo = trustListInfo,
            details = if (trusted) {
                "Certificate verified for role ${role.description} via ${match.matchType}"
            } else {
                "Certificate found but service status is ${match.serviceInfo.serviceStatus}"
            },
            verifiedAt = verificationTime
        )
    }

    private fun notVerifiedResult(
        role: EidasRole,
        trustStatus: TrustStatus,
        details: String,
        verificationTime: Instant
    ): RoleVerificationResult {
        return RoleVerificationResult(
            verified = false,
            role = role,
            trustStatus = trustStatus,
            matchedEntity = null,
            trustListInfo = null,
            details = details,
            verifiedAt = verificationTime
        )
    }

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
                    return (cached.trustList to true).asOkResult()
                }
            }
        }

        val resolver = trustListResolvers.firstOrNull { it.supports(tslUri) }
            ?: return IdkError.UNKNOWN_ERROR(
                message = "No trust list resolver supports URI: $tslUri"
            ).asErrorResult()

        return try {
            val resolutionOptions = ResolutionOptions(
                useCache = useCache,
                maxCacheAgeMs = maxCacheAge,
                verifySignature = true
            )
            val trustListData = resolver.resolve(tslUri, resolutionOptions)
            val trustList = trustListParser.parseFromBytes(trustListData.data)

            if (useCache) {
                tslCache[tslUri] = CachedTrustList(
                    trustList = trustList,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            }

            (trustList to false).asOkResult()
        } catch (e: Exception) {
            logger.error("Failed to resolve trust list from $tslUri", exception = e)
            IdkError.UNKNOWN_ERROR(
                message = "Failed to resolve trust list: ${e.message}"
            ).asErrorResult()
        }
    }

    private data class CachedTrustList(
        val trustList: ETSILoTE,
        val timestamp: Long
    )
}
