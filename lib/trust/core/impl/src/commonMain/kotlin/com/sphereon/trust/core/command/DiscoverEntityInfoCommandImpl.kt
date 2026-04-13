/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.model.TrustAnchorType
import com.sphereon.trust.core.model.TrustContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Standalone command for discovering entity info without full trust validation.
 * Delegates to registered [EntityInfoExtractor] implementations based on context type.
 */
@Inject
@SingleIn(SessionScope::class)
class DiscoverEntityInfoCommandImpl(
    execution: SessionExecution,
    private val extractors: Set<EntityInfoExtractor>,
) : TypedServiceCommandAdapter<DiscoverEntityInfoArgs, DiscoverEntityInfoResult>(
        commandId = DiscoverEntityInfoCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DiscoverEntityInfoArgs>(),
        outputTypeToken = typeToken<DiscoverEntityInfoResult>(),
    ),
    DiscoverEntityInfoCommand {
    override val commandId: String get() = DiscoverEntityInfoCommand.COMMAND_ID

    private val logger = execution.log.logManager.withTagAsync("DiscoverEntityInfoCommand")

    override suspend fun doExecute(
        args: DiscoverEntityInfoArgs,
        applyDuring: (DiscoverEntityInfoArgs) -> DiscoverEntityInfoArgs,
    ): IdkResult<DiscoverEntityInfoResult, IdkError> {
        val applied = applyDuring(args)

        val context =
            TrustContext(
                type = applied.contextType,
                parameters = applied.parameters,
            )

        val extractor =
            extractors.firstOrNull { it.supports(context) }
                ?: return Err(
                    IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                        message = "No entity info extractor for context type: ${applied.contextType}",
                    ),
                )

        return try {
            val entities =
                extractor.extractEntityInfo(
                    context = context,
                    validationPath = listOf(applied.entityIdentifier),
                    options = applied.options,
                )

            val sourceType =
                when (applied.contextType) {
                    TrustContext.TYPE_OPENID_FEDERATION -> TrustAnchorType.OPENID_FEDERATION
                    TrustContext.TYPE_ETSI_TSL -> TrustAnchorType.ETSI_TSL
                    TrustContext.TYPE_X509, TrustContext.TYPE_CA_BUNDLE -> TrustAnchorType.X509_CA_BUNDLE
                    TrustContext.TYPE_DID -> TrustAnchorType.DID
                    else -> TrustAnchorType.CUSTOM
                }

            Ok(
                DiscoverEntityInfoResult(
                    entities = entities,
                    sourceType = sourceType,
                    details = "Discovered ${entities.size} entities",
                ),
            )
        } catch (expected: Exception) {
            logger.error("Entity info discovery failed", exception = expected)
            Err(
                IdkError.UNKNOWN_ERROR(
                    message = "Entity info discovery failed: ${expected.message}",
                ),
            )
        }
    }
}
