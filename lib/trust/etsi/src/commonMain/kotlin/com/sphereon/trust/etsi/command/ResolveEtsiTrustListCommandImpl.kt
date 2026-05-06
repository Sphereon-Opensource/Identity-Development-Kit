/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.etsi.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.cache.CacheRequirements
import com.sphereon.core.api.cache.CacheService
import com.sphereon.core.api.cache.CacheTtlConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import com.sphereon.trust.etsi.resolver.ETSITrustListResolver
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

/**
 * Command for resolving ETSI trust lists, including LoTL (List of Trusted Lists).
 *
 * Fetches the LoTL from configured URL, parses pointer entries to member state
 * trust lists, resolves each member state list, and caches all results.
 */
interface ResolveEtsiTrustListCommand : ServiceCommand<ResolveEtsiTrustListArgs, ResolveEtsiTrustListResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.EXECUTE

    companion object {
        const val COMMAND_ID = "trust.etsi.resolve"
    }
}

@Serializable
data class ResolveEtsiTrustListArgs(
    val trustListUri: String? = null,
    val resolveLotl: Boolean = false,
    val territories: List<String> = emptyList(),
    val verifySignatures: Boolean = true,
)

@Serializable
data class ResolveEtsiTrustListResult(
    val trustListCount: Int,
    val territories: List<String>,
    val totalServices: Int,
    val details: String? = null,
)

@Inject
@SingleIn(SessionScope::class)
class ResolveEtsiTrustListCommandImpl(
    execution: SessionExecution,
    private val trustListResolver: ETSITrustListResolver,
    private val trustListParser: ETSITrustListParser,
    private val cacheService: CacheService,
) : TypedServiceCommandAdapter<ResolveEtsiTrustListArgs, ResolveEtsiTrustListResult, IdkError>(
        commandId = ResolveEtsiTrustListCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveEtsiTrustListArgs>(),
        outputTypeToken = typeToken<ResolveEtsiTrustListResult>(),
    ),
    ResolveEtsiTrustListCommand {
    override val commandId: String get() = ResolveEtsiTrustListCommand.COMMAND_ID

    private val logger = execution.log.logManager.withTagAsync("ResolveEtsiTrustListCommand")

    private val resolvedListCache by lazy {
        cacheService.getCache(
            CacheRequirements(
                namespace = "trust.etsi.resolved",
                ttlConfig = CacheTtlConfig(app = 60.minutes),
            ),
        )
    }

    override suspend fun doExecute(
        args: ResolveEtsiTrustListArgs,
        applyDuring: (ResolveEtsiTrustListArgs) -> ResolveEtsiTrustListArgs,
    ): IdkResult<ResolveEtsiTrustListResult, IdkError> {
        val applied = applyDuring(args)

        return try {
            if (applied.resolveLotl) {
                resolveLotl(applied)
            } else if (applied.trustListUri != null) {
                resolveSingleList(applied.trustListUri)
            } else {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Either trustListUri or resolveLotl must be specified"))
            }
        } catch (expected: Exception) {
            logger.error("Failed to resolve ETSI trust list", exception = expected)
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to resolve ETSI trust list: ${expected.message}"))
        }
    }

    private suspend fun resolveSingleList(uri: String): IdkResult<ResolveEtsiTrustListResult, IdkError> {
        // Check cache
        val cached = resolvedListCache.getApp(uri)
        if (cached != null) {
            return Ok(
                kotlinx.serialization.json.Json
                    .decodeFromString<ResolveEtsiTrustListResult>(cached),
            )
        }

        val trustListData = trustListResolver.resolve(uri)
        val trustList = trustListParser.parseFromBytes(trustListData.data)

        val totalServices = trustList.trustedEntities.sumOf { it.trustedEntityServices.size }
        val result =
            ResolveEtsiTrustListResult(
                trustListCount = 1,
                territories = listOf(trustList.schemeTerritory),
                totalServices = totalServices,
                details = "Resolved trust list: ${trustList.schemeName.firstOrNull()?.value} (seq: ${trustList.sequenceNumber})",
            )

        resolvedListCache.putApp(
            uri,
            kotlinx.serialization.json.Json
                .encodeToString(result),
        )
        return Ok(result)
    }

    private suspend fun resolveLotl(args: ResolveEtsiTrustListArgs): IdkResult<ResolveEtsiTrustListResult, IdkError> {
        val cacheKey = "lotl:${args.territories.sorted().joinToString(",")}"
        val cached = resolvedListCache.getApp(cacheKey)
        if (cached != null) {
            return Ok(
                kotlinx.serialization.json.Json
                    .decodeFromString<ResolveEtsiTrustListResult>(cached),
            )
        }

        // Fetch the EU LoTL
        val lotlData = trustListResolver.resolveEULOTL()
        val lotl = trustListParser.parseFromBytes(lotlData.data)

        // Extract pointers to member state trust lists
        val pointers = lotl.pointersToOtherLoTE
        logger.info("LoTL contains ${pointers.size} pointers to member state trust lists")

        val resolvedTerritories = mutableListOf<String>()
        var totalServices = 0
        var listCount = 0

        for (pointer in pointers) {
            val territory = pointer.schemeTerritory
            val pointerUri = pointer.location

            // Filter by requested territories if specified
            if (args.territories.isNotEmpty() && territory !in args.territories) {
                continue
            }

            try {
                val memberListData = trustListResolver.resolve(pointerUri)
                val memberList = trustListParser.parseFromBytes(memberListData.data)

                val services = memberList.trustedEntities.sumOf { it.trustedEntityServices.size }
                totalServices += services
                listCount++
                resolvedTerritories.add(territory)

                logger.info("Resolved $territory trust list: $services services")
            } catch (expected: Exception) {
                logger.warn("Failed to resolve trust list for territory $territory from $pointerUri: ${expected.message}")
            }
        }

        val result =
            ResolveEtsiTrustListResult(
                trustListCount = listCount,
                territories = resolvedTerritories,
                totalServices = totalServices,
                details = "Resolved $listCount member state trust lists from EU LoTL with $totalServices total services",
            )

        resolvedListCache.putApp(
            cacheKey,
            kotlinx.serialization.json.Json
                .encodeToString(result),
        )
        return Ok(result)
    }
}

@ContributesTo(SessionScope::class)
interface EtsiResolveCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ResolveEtsiTrustListCommand.COMMAND_ID)
    fun resolveEtsiTrustList(impl: ResolveEtsiTrustListCommandImpl): ServiceCommand<*, *, *> = impl
}
