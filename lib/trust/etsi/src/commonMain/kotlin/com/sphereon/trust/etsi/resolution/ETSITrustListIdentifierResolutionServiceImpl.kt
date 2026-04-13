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
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierServiceAdapter
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.lote.model.forLang
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding

/**
 * Interface for ETSI Trust List identifier resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("IETSITrustListIdentifierResolutionService", exact = true)
interface IETSITrustListIdentifierResolutionService : ExternalIdentifierService

/**
 * External identifier service for resolving ETSI Trust Lists.
 *
 * This service integrates ETSI TS 119 612 trust list resolution into the identifier resolution
 * framework, allowing trust lists to be resolved just like any other external identifier
 * (X.509, DID, OIDC, etc.).
 *
 * Resolution is agnostic to the underlying key material and storage - it works with both
 * external (HTTP) and managed (locally hosted) trust lists.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IETSITrustListIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class ETSITrustListIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val trustListResolvers: Set<TrustListResolver>,
    private val trustListParser: ETSITrustListParser
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult>(
    supportedIdentifierMethods = listOf(
        ETSIIdentifierMethods.ETSI_TSL,
        ETSIIdentifierMethods.ETSI_TSP
    ),
    execution = execution,
    commandId = COMMAND_ID
), IETSITrustListIdentifierResolutionService {

    private val logger = execution.log.logManager.withTagAsync("ETSITrustListIdentifierResolutionService")

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        log.debug("Resolving ETSI identifier: ${args.identifier.toString().take(100)}...")

        if (!supports(args)) {
            return IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message = "External identifier opts for ETSI expected. Type ${args.method ?: args.identifier} not supported"
            ).asErrorResult()
        }

        val optsResult = asSupportedOpts(args)
        if (optsResult.isErr) {
            return optsResult.error.asErrorResult()
        }

        return when (val opts = optsResult.value) {
            is ExternalIdentifierETSITslOpts -> resolveTrustList(opts)
            is ExternalIdentifierETSITspOpts -> resolveTSP(opts)
            else -> IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message = "Unsupported ETSI opts type: ${opts::class.simpleName}"
            ).asErrorResult()
        }
    }

    /**
     * Resolves an ETSI Trust List.
     */
    private suspend fun resolveTrustList(
        opts: ExternalIdentifierETSITslOpts
    ): IdkResult<ExternalIdentifierETSITslResult, IdkErrorType> {
        try {
            logger.info("Resolving ETSI trust list from ${opts.identifier}")

            // Find appropriate resolver
            val resolver = trustListResolvers.firstOrNull { it.supports(opts.identifier) }
                ?: return IdkError.UNKNOWN_ERROR(
                    message = "No trust list resolver supports URI: ${opts.identifier}"
                ).asErrorResult()

            // Resolve trust list
            val resolutionOptions = ResolutionOptions(
                useCache = opts.useCache,
                maxCacheAgeMs = opts.maxCacheAge ?: 3600000,
                verifySignature = opts.verifySignature
            )

            val trustListData = resolver.resolve(opts.identifier, resolutionOptions)

            // Parse trust list
            val trustList = trustListParser.parseFromBytes(trustListData.data)

            // Apply filters if specified
            val filteredTrustList = applyFilters(trustList, opts)

            // Extract trust anchors
            val trustAnchors = extractTrustAnchors(filteredTrustList)

            // Extract KeyInfo from trust anchors (already converted from certificates)
            // Cast to ExternalJwkInfo since we know these are JWK-based (from certificate.getPublicKeyJwk())
            @Suppress("UNCHECKED_CAST")
            val jwks = trustAnchors.map { anchor ->
                anchor.keyInfo as ResolvedKeyInfoType<JwkType>
            }.toTypedArray()

            val primaryKeyInfo = jwks.firstOrNull()
                ?: return IdkError.UNKNOWN_ERROR(
                    message = "No valid certificates found in trust list"
                ).asErrorResult()

            val metadata = ETSITslResolutionMetadata(
                retrievedAt = Clock.System.now(),
                fromCache = trustListData.fromCache,
                sequenceNumber = trustList.sequenceNumber,
                nextUpdate = trustList.nextUpdate,
                territory = trustList.schemeTerritory,
                tspCount = trustList.trustedEntities.size,
                trustAnchorCount = trustAnchors.size
            )

            return ExternalIdentifierETSITslResult(
                identifierOpts = opts,
                trustList = filteredTrustList,
                trustAnchors = trustAnchors,
                jwks = jwks,
                keyInfo = primaryKeyInfo,
                signatureVerified = opts.verifySignature, // TODO: Actually verify signature
                metadata = metadata
            ).asOkResult()

        } catch (e: Exception) {
            logger.error("Failed to resolve ETSI trust list", exception = e)
            return IdkError.UNKNOWN_ERROR(
                message = "Failed to resolve trust list: ${e.message}"
            ).asErrorResult()
        }
    }

    /**
     * Resolves a specific TSP from a trust list.
     */
    private suspend fun resolveTSP(
        opts: ExternalIdentifierETSITspOpts
    ): IdkResult<ExternalIdentifierETSITspResult, IdkErrorType> {
        try {
            // First resolve the trust list
            val tslUri = opts.tslUri ?: return IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message = "tslUri must be specified for TSP resolution"
            ).asErrorResult()

            val tslOpts = ExternalIdentifierETSITslOpts(identifier = tslUri)
            val tslResult = resolveTrustList(tslOpts)

            if (tslResult.isErr) {
                return tslResult.error.asErrorResult()
            }

            val trustList = tslResult.value.trustList

            // Find matching TSP/service
            // TODO: Implement TSP matching logic based on opts.identifier and opts.certificateToMatch

            return IdkError.UNKNOWN_ERROR(
                message = "TSP resolution not yet fully implemented"
            ).asErrorResult()

        } catch (e: Exception) {
            logger.error("Failed to resolve ETSI TSP", exception = e)
            return IdkError.UNKNOWN_ERROR(
                message = "Failed to resolve TSP: ${e.message}"
            ).asErrorResult()
        }
    }

    /**
     * Applies filters to the trust list based on options.
     */
    private fun applyFilters(trustList: ETSILoTE, opts: ExternalIdentifierETSITslOpts): ETSILoTE {
        var filtered = trustList

        // Apply territory filter
        opts.territory?.let { territory ->
            if (trustList.schemeTerritory != territory) {
                // Return empty trust list if territory doesn't match
                filtered = trustList.copy(trustedEntities = emptyList())
            }
        }

        // Apply service type filter
        opts.serviceTypeFilter?.let { serviceTypes ->
            filtered = filtered.copy(
                trustedEntities = filtered.trustedEntities.map { entity ->
                    entity.copy(
                        trustedEntityServices = entity.trustedEntityServices.filter { service ->
                            serviceTypes.contains(service.serviceInformation.serviceTypeIdentifier)
                        }
                    )
                }.filter { it.trustedEntityServices.isNotEmpty() }
            )
        }

        return filtered
    }

    /**
     * Extracts trust anchors from the trust list.
     */
    private fun extractTrustAnchors(trustList: ETSILoTE): List<TrustAnchor> {
        return trustList.trustedEntities.flatMap { entity ->
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
    }

    override suspend fun supports(args: Any): Boolean {
        return when (args) {
            is ExternalIdentifierETSITslOpts -> true
            is ExternalIdentifierETSITspOpts -> true
            is ExternalIdentifierOptsOrResult -> {
                args.method == ETSIIdentifierMethods.ETSI_TSL ||
                args.method == ETSIIdentifierMethods.ETSI_TSP
            }
            else -> false
        }
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // Support URI strings that look like ETSI trust list locations
        if (identifier is String) {
            return identifier.startsWith("http://", ignoreCase = true) ||
                   identifier.startsWith("https://", ignoreCase = true) ||
                   identifier.contains("tsl", ignoreCase = true) ||
                   identifier.contains("lotl", ignoreCase = true)
        }
        return false
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<out ExternalIdentifierResult, IdkErrorType> {
        return execute(opts)
    }

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> {
        return when {
            opts is ExternalIdentifierETSITslOpts -> opts.asOkResult()
            opts is ExternalIdentifierETSITspOpts -> opts.asOkResult()
            opts.method == ETSIIdentifierMethods.ETSI_TSL && opts.identifier is String -> {
                ExternalIdentifierETSITslOpts(
                    identifier = opts.identifier as String,
                    context = opts.context,
                    lookup = opts.lookup as? AdditionalIdentifierLookup ?: AdditionalIdentifierLookup()
                ).asOkResult()
            }
            opts.method == ETSIIdentifierMethods.ETSI_TSP && opts.identifier is String -> {
                ExternalIdentifierETSITspOpts(
                    identifier = opts.identifier as String,
                    context = opts.context,
                    lookup = opts.lookup as? AdditionalIdentifierLookup ?: AdditionalIdentifierLookup()
                ).asOkResult()
            }
            else -> IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }
    }

    companion object {
        const val COMMAND_ID = "trust.etsi.trustlist"
    }
}
