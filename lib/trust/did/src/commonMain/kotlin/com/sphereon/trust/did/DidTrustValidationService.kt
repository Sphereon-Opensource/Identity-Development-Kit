/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.did

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.ResolveDidArgs
import com.sphereon.did.resolver.ResolveDidCommand
import com.sphereon.did.utils.ParsedDid
import com.sphereon.trust.core.TrustValidationService
import com.sphereon.trust.core.config.TrustConfigProvider
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.trust.core.model.TrustValidationResult
import com.sphereon.trust.core.validation.AbstractTrustValidationService
import com.sphereon.trust.did.extractor.DidEntityInfoExtractor
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * DID-based trust validation service.
 *
 * Two-layer validation:
 * 1. Method allow-list gate: The DID's method MUST be in the configured allowedMethods list.
 * 2. Specific DID trust: The DID is trusted if it's in trustedDids or its controller is.
 *
 * Configuration is read from properties under `trust.anchors.did.*` via TrustConfigProvider.
 * Request-level parameters override config (e.g., context.parameters["allowedMethods"]).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(scope = SessionScope::class, binding = binding<TrustValidationService>())
class DidTrustValidationService(
    private val resolveDidCommand: ResolveDidCommand,
    private val trustConfigProvider: TrustConfigProvider,
    private val execution: SessionExecution,
    private val entityInfoExtractor: DidEntityInfoExtractor,
) : AbstractTrustValidationService("did", setOf(TrustContext.TYPE_DID)) {
    private val logger = execution.log.logManager.withTagAsync("DidTrustValidationService")

    override suspend fun doValidate(request: TrustValidationRequest): TrustValidationResult {
        logger.debug("Validating DID trust for context: ${request.context}")

        val did =
            request.context.parameters["did"]
                ?: return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "No DID specified in context parameters",
                    validatedAt = Clock.System.now(),
                )

        val parsed =
            ParsedDid.tryParse(did)
                ?: return TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "Invalid DID: $did",
                    validatedAt = Clock.System.now(),
                )

        // Read config; request parameters override config values
        val didConfig = trustConfigProvider.getTrustConfig().anchors.did
        val allowedMethods =
            request.context.parameters["allowedMethods"]
                ?.split(",")
                ?.map { it.trim() }
                ?: didConfig.allowedMethods
        val trustedDids =
            request.context.parameters["trustedDids"]
                ?.split(",")
                ?.map { it.trim() }
                ?: didConfig.trustedDids

        // Layer 1: Method allow-list gate
        if (allowedMethods.isNotEmpty() && parsed.method !in allowedMethods) {
            return TrustValidationResult(
                trusted = false,
                status = TrustStatus.UNTRUSTED,
                details = "DID method '${parsed.method}' is not in the allowed methods list: $allowedMethods",
                validatedAt = Clock.System.now(),
            )
        }

        // Layer 2: Specific DID trust
        if (trustedDids.isNotEmpty()) {
            // Resolve before applying either direct-DID or controller trust. This keeps
            // resolver metadata authoritative for directly trusted DIDs as well.
            return try {
                val result =
                    resolveDidCommand.execute(
                        ResolveDidArgs(did = did, options = DidResolutionOptions()),
                    )
                if (result.isErr) {
                    return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "Failed to resolve DID: ${result.error}",
                        validatedAt = Clock.System.now(),
                    )
                }
                val resolutionResult = result.value
                if (resolutionResult.didResolutionMetadata.error != null) {
                    return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "DID resolution error: ${resolutionResult.didResolutionMetadata.error}",
                        validatedAt = Clock.System.now(),
                    )
                }
                if (resolutionResult.didDocument == null) {
                    return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "DID resolution returned no DID document",
                        validatedAt = Clock.System.now(),
                    )
                }
                if (resolutionResult.didDocumentMetadata.deactivated == true) {
                    return TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "DID is deactivated",
                        validatedAt = Clock.System.now(),
                    )
                }

                if (did in trustedDids) {
                    val trustedResult =
                        TrustValidationResult(
                            trusted = true,
                            status = TrustStatus.TRUSTED,
                            details = "DID is in the trusted DIDs list",
                            validatedAt = Clock.System.now(),
                        )
                    return enrichWithEntityInfo(trustedResult, request, entityInfoExtractor)
                }

                // Check controller DID
                val controllers = resolutionResult.didDocument?.controller.orEmpty()
                var controllerResolutionError: String? = null
                var deactivatedTrustedController = false
                for (controller in controllers.filter { it in trustedDids }.distinct()) {
                    val parsedController = ParsedDid.tryParse(controller)
                    if (parsedController == null || (allowedMethods.isNotEmpty() && parsedController.method !in allowedMethods)) {
                        continue
                    }
                    val controllerResult =
                        try {
                            resolveDidCommand.execute(
                                ResolveDidArgs(did = controller, options = DidResolutionOptions()),
                            )
                        } catch (expected: Exception) {
                            controllerResolutionError = controllerResolutionError ?: "${expected.message}"
                            continue
                        }
                    if (controllerResult.isErr) {
                        controllerResolutionError = controllerResolutionError ?: controllerResult.error.toString()
                        continue
                    }
                    val controllerResolution = controllerResult.value
                    if (controllerResolution.didResolutionMetadata.error != null) {
                        controllerResolutionError = controllerResolutionError ?: controllerResolution.didResolutionMetadata.error.orEmpty()
                        continue
                    }
                    if (controllerResolution.didDocument == null) {
                        controllerResolutionError = controllerResolutionError ?: "controller resolution returned no DID document"
                        continue
                    }
                    if (controllerResolution.didDocumentMetadata.deactivated == true) {
                        deactivatedTrustedController = true
                        continue
                    }

                    return enrichWithEntityInfo(
                        TrustValidationResult(
                            trusted = true,
                            status = TrustStatus.TRUSTED,
                            details = "DID's controller is in the trusted DIDs list",
                            validatedAt = Clock.System.now(),
                        ),
                        request,
                        entityInfoExtractor,
                    )
                }

                if (controllerResolutionError != null) {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.VALIDATION_ERROR,
                        details = "Failed to resolve trusted controller: $controllerResolutionError",
                        validatedAt = Clock.System.now(),
                    )
                } else if (deactivatedTrustedController) {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "All matching trusted controllers are deactivated",
                        validatedAt = Clock.System.now(),
                    )
                } else {
                    TrustValidationResult(
                        trusted = false,
                        status = TrustStatus.UNTRUSTED,
                        details = "DID and its controllers are not in the trusted DIDs list",
                        validatedAt = Clock.System.now(),
                    )
                }
            } catch (expected: Exception) {
                logger.error("DID resolution failed during trust validation", exception = expected)
                TrustValidationResult(
                    trusted = false,
                    status = TrustStatus.VALIDATION_ERROR,
                    details = "DID resolution failed: ${expected.message}",
                    validatedAt = Clock.System.now(),
                )
            }
        }

        // No explicit trust configuration - DID method is allowed but trust is unknown
        return TrustValidationResult(
            trusted = false,
            status = TrustStatus.UNKNOWN,
            details = "DID method '${parsed.method}' is allowed but no specific trust relationship configured",
            validatedAt = Clock.System.now(),
        )
    }

    override suspend fun doGetTrustAnchors(): List<TrustAnchor> = emptyList()
}
