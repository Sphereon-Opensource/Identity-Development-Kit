/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.lifecycle

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyConfig
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyResolver
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuanceLifecycleHook
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuancePhase
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciOfferLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciPhaseLifecycleArgs
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

@Inject
@SingleIn(SessionScope::class)
class OfferLifecycleInitializer(
    private val policyResolver: CredentialIssuancePolicyResolver? = null,
    private val lifecycleHook: Oid4vciIssuanceLifecycleHook? = null,
) {
    suspend fun validateGrants(args: CreateCredentialOfferArgs): IdkResult<Unit, IdkError> {
        val resolver = policyResolver ?: return Ok(Unit)
        for (configId in args.credentialConfigurationIds) {
            val violation = findGrantPolicyViolation(args, configId, resolver.resolve(configId))
            if (violation != null) return Err(violation)
        }
        return Ok(Unit)
    }

    suspend fun initializeLifecycle(args: CreateCredentialOfferArgs): String? {
        val hook = lifecycleHook ?: return null
        val correlationId =
            hook
                .initializeOffer(
                    Oid4vciOfferLifecycleArgs(
                        issuerId = args.issuerId,
                        credentialConfigurationIds = args.credentialConfigurationIds,
                        preAuthorizedCodeGrant = args.preAuthorizedCodeGrant,
                        authorizationCodeGrant = args.authorizationCodeGrant,
                        txCodeRequired = args.txCodeRequired,
                        initialFields = args.combinedInitialLifecycleFields(args.preSeededAttributes.orEmpty()),
                        boundUsageToken = args.boundUsageToken,
                    ),
                ).getOrNull()
                ?.correlationId
                ?: return null

        recordOfferPhase(args, correlationId, Oid4vciIssuancePhase.START)
        if (args.preAuthorizedCodeGrant) {
            recordOfferPhase(args, correlationId, Oid4vciIssuancePhase.PRE_AUTHORIZED)
        }
        if (args.authorizationCodeGrant) {
            recordOfferPhase(args, correlationId, Oid4vciIssuancePhase.AUTHORIZATION)
        }
        return correlationId
    }

    private fun findGrantPolicyViolation(
        args: CreateCredentialOfferArgs,
        configId: String,
        policy: CredentialIssuancePolicyConfig,
    ): IdkError? {
        if (args.preAuthorizedCodeGrant && !policy.preAuthorizedCodeAllowed) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Pre-authorized code grant is not allowed for credential configuration '$configId'",
            )
        }
        if (args.authorizationCodeGrant && !policy.authorizationCodeAllowed) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Authorization code grant is not allowed for credential configuration '$configId'",
            )
        }
        return null
    }

    private suspend fun recordOfferPhase(
        args: CreateCredentialOfferArgs,
        correlationId: String,
        phase: Oid4vciIssuancePhase,
    ) {
        lifecycleHook
            ?.recordPhase(
                Oid4vciPhaseLifecycleArgs(
                    correlationId = correlationId,
                    phase = phase,
                    fields = args.toProtocolFields(),
                ),
            )?.getOrElse { error(it.toString()) }
    }

    private fun CreateCredentialOfferArgs.toProtocolFields() =
        buildMap {
            put("oid4vci.issuerId", JsonPrimitive(issuerId))
            put(
                "oid4vci.credentialConfigurationIds",
                buildJsonArray { credentialConfigurationIds.forEach { add(it) } },
            )
            put("oid4vci.preAuthorizedCodeGrant", JsonPrimitive(preAuthorizedCodeGrant))
            put("oid4vci.authorizationCodeGrant", JsonPrimitive(authorizationCodeGrant))
            put("oid4vci.txCodeRequired", JsonPrimitive(txCodeRequired))
            boundUsageToken?.let { put("oid4vci.boundUsageToken", JsonPrimitive(it)) }
        }

    private fun CreateCredentialOfferArgs.combinedInitialLifecycleFields(subjectFields: Map<String, JsonElement>) =
        buildMap {
            putAll(subjectFields)
            putAll(initialLifecycleFields)
        }
}
