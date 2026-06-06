/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline.command

import com.sphereon.attribute.flow.AttributePath
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlinx.serialization.Serializable

/**
 * Evaluates, per credential-claims binding, whether the session's accumulated bag holds every
 * attribute the binding's semantic attribute set marks mandatory.
 *
 * It returns a *verdict only*. It does not create deferred-credential entries, mutate session
 * status, or talk to the issuer core.
 */
interface EvaluateAttributeCompletenessCommand : ServiceCommand<EvaluateAttributeCompletenessArgs, EvaluateAttributeCompletenessResult, IdkError> {
    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ

    companion object {
        const val COMMAND_ID: String = "issuance.pipeline.evaluate-completeness"
    }
}

/**
 * Exposes [EvaluateAttributeCompletenessCommand] as an optional graph accessor so consumers
 * declaring `EvaluateAttributeCompletenessCommand? = null` constructor parameters resolve cleanly
 * under the Metro `nullable type key`. Pure-IDK deployments without the EDK pipeline-impl on the
 * classpath fall through to the `null` default body. The EDK supplier adds a second
 * `@ContributesBinding(SessionScope::class, binding = binding<EvaluateAttributeCompletenessCommand?>())`.
 */
@ContributesTo(SessionScope::class)
interface EvaluateAttributeCompletenessCommandOptionalProvider {
    @OptionalBinding
    val optionalEvaluateAttributeCompletenessCommand: EvaluateAttributeCompletenessCommand? get() = null
}

@Serializable
data class EvaluateAttributeCompletenessArgs(
    /** The session whose bag is evaluated. */
    val correlationId: String,
)

@Serializable
data class EvaluateAttributeCompletenessResult(
    /** One verdict per credential-claims binding in the session's pipeline configuration. */
    val verdicts: List<BindingCompletenessVerdict>,
)

/**
 * The completeness verdict for one credential-claims binding.
 */
@Serializable
data class BindingCompletenessVerdict(
    /** The `CredentialClaimsBinding.id` this verdict is for. */
    val bindingId: String,
    /** True when every mandatory attribute path is present in the bag. */
    val complete: Boolean,
    /** Mandatory attribute paths still missing from the bag. Empty when [complete]. */
    val missingRequiredPaths: List<AttributePath> = emptyList(),
    /**
     * True when the binding is incomplete and its `DeferralPolicy.enabled` is set — i.e. the
     * issuer-side wiring should defer rather than fail. False when incomplete but deferral is
     * disabled (the request should fail) or when [complete].
     */
    val deferralRecommended: Boolean = false,
    /**
     * True when the binding's `DeferralPolicy.approvalRequired` is set and the session has not
     * yet been approved. Even a [complete] binding must defer (HTTP 202) while this is true:
     * the credential is not issued until an approver grants it.
     */
    val awaitingApproval: Boolean = false,
)
