/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.contract.AssuranceRequirements
import com.sphereon.core.api.service.contract.AttributeKind
import com.sphereon.core.api.service.contract.CommandIntent
import com.sphereon.core.api.service.contract.CommandPlan
import com.sphereon.core.api.service.contract.ContractCapability
import com.sphereon.core.api.service.contract.ExecutionTraits
import com.sphereon.core.api.service.contract.OperationType
import com.sphereon.core.api.service.contract.PlanningCompleteness
import com.sphereon.core.api.service.contract.PlanningContext
import com.sphereon.core.api.service.contract.RegulatoryFramework
import com.sphereon.core.api.service.contract.ServiceCommandContract
import com.sphereon.core.api.service.contract.assurance
import com.sphereon.core.api.service.contract.capabilities
import com.sphereon.core.api.service.contract.complianceProfile
import com.sphereon.core.api.service.contract.executionTraits
import com.sphereon.core.api.service.contract.resourceTarget
import com.sphereon.core.api.service.contract.schemaOverlay
import com.sphereon.core.api.service.contract.usageProfile
import com.sphereon.core.api.session.CommandId
import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustValidationResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey

private const val DEFAULT_USAGE_WEIGHT = 3
private const val IDENTIFIER_CONTEXT_TYPE = "context_type"
private const val KEY_CONTEXT_TYPE = "contextType"
private const val LABEL_CONTEXT_TYPE = "Context Type"
private const val IDENTIFIER_FRAMEWORK = "framework"
private const val IDENTIFIER_ANCHOR_TYPE = "anchor_type"

// ============================================================================
// ValidateTrust Contract
// ============================================================================

@Inject
@SingleIn(AppScope::class)
class ValidateTrustContract : ServiceCommandContract<ValidateTrustArgs, TrustValidationResult> {
    override val commandId = CommandId(ValidateTrustCommand.COMMAND_ID)
    override val actionType = ActionType.EXECUTE
    override val operationType = OperationType.VALIDATE
    override val inputTypeToken: TypeToken<ValidateTrustArgs> = typeToken<ValidateTrustArgs>()
    override val outputTypeToken: TypeToken<TrustValidationResult> = typeToken<TrustValidationResult>()

    override val summary = "Validate trust for an identifier"
    override val description = "Routes to the appropriate trust validator (ETSI, X.509, DID, OIDFED) based on context type and validates trust chain"

    override val resourceTarget =
        resourceTarget("trust.validation") {
            identifier(IDENTIFIER_CONTEXT_TYPE, required = true)
            identifier(IDENTIFIER_FRAMEWORK)
            constraint("check_revocation")
        }

    override val executionTraits =
        executionTraits {
            idempotent()
            mediumDuration()
        }

    override val assuranceRequirements = assurance { aal1() }

    override val usageProfile = usageProfile { weight(DEFAULT_USAGE_WEIGHT) }

    override val declaredIntentTargets =
        setOf(
            CommandId("trust.etsi.validate"),
            CommandId("trust.x509.validate"),
            CommandId("trust.did.validate"),
            CommandId("trust.oidfed.validate"),
        )

    override suspend fun plan(
        args: ValidateTrustArgs,
        context: PlanningContext,
    ): CommandPlan {
        val targetCommandId =
            when (args.contextType) {
                TrustContext.TYPE_ETSI_TSL -> "trust.etsi.validate"
                TrustContext.TYPE_X509, TrustContext.TYPE_CA_BUNDLE -> "trust.x509.validate"
                TrustContext.TYPE_DID -> "trust.did.validate"
                TrustContext.TYPE_OPENID_FEDERATION -> "trust.oidfed.validate"
                else -> return CommandPlan.leaf()
            }
        return CommandPlan(
            intents = listOf(CommandIntent(targetCommandId, reason = "Dispatches to ${args.contextType} validator")),
            completeness = PlanningCompleteness.PARTIAL,
        )
    }

    override val inputSchemaOverlay =
        schemaOverlay {
            KEY_CONTEXT_TYPE {
                label(LABEL_CONTEXT_TYPE)
                description("Trust framework type: etsi_tsl, x509, did, openid_federation")
                identifier(IDENTIFIER_CONTEXT_TYPE)
            }
            IDENTIFIER_FRAMEWORK {
                label("Framework")
                description("Trust list URI or framework identifier")
                identifier(IDENTIFIER_FRAMEWORK)
            }
            "identifierJson" {
                label("Identifier")
                description("Serialized identifier to validate")
            }
            "checkRevocation" {
                label("Check Revocation")
                description("Whether to perform revocation checking")
                constraint("check_revocation")
            }
        }

    override val complianceProfile =
        complianceProfile {
            framework(RegulatoryFramework.EIDAS)
            framework(RegulatoryFramework.EIDAS2)
        }

    override val capabilities =
        capabilities {
            discoverable()
            policyReady()
            workflowReady()
        }
}

// ============================================================================
// GetTrustAnchors Contract
// ============================================================================

@Inject
@SingleIn(AppScope::class)
class GetTrustAnchorsContract : ServiceCommandContract<GetTrustAnchorsArgs, TrustAnchorListResult> {
    override val commandId = CommandId(GetTrustAnchorsCommand.COMMAND_ID)
    override val actionType = ActionType.LIST
    override val operationType = OperationType.LIST
    override val inputTypeToken: TypeToken<GetTrustAnchorsArgs> = typeToken<GetTrustAnchorsArgs>()
    override val outputTypeToken: TypeToken<TrustAnchorListResult> = typeToken<TrustAnchorListResult>()

    override val summary = "List trust anchors"
    override val description = "Returns all registered trust anchors, optionally filtered by type and context"

    override val resourceTarget =
        resourceTarget("trust.anchor.collection") {
            identifier(IDENTIFIER_ANCHOR_TYPE)
            identifier(IDENTIFIER_CONTEXT_TYPE)
        }

    override val executionTraits = ExecutionTraits.READ_ONLY

    // UsageProfile.STANDARD is the default — no override needed

    override val inputSchemaOverlay =
        schemaOverlay {
            "anchorType" {
                label("Anchor Type")
                description("Filter by trust anchor type (ETSI_TSL, X509_CA_BUNDLE, DID, etc.)")
                identifier(IDENTIFIER_ANCHOR_TYPE)
            }
            KEY_CONTEXT_TYPE {
                label(LABEL_CONTEXT_TYPE)
                description("Filter by context type")
                identifier(IDENTIFIER_CONTEXT_TYPE)
            }
        }

    override val complianceProfile =
        complianceProfile {
            framework(RegulatoryFramework.EIDAS)
        }

    override val capabilities =
        capabilities {
            discoverable()
            policyReady()
            workflowReady()
        }
}

// ============================================================================
// RefreshTrustAnchors Contract
// ============================================================================

@Inject
@SingleIn(AppScope::class)
class RefreshTrustAnchorsContract : ServiceCommandContract<RefreshTrustArgs, RefreshTrustResult> {
    override val commandId = CommandId(RefreshTrustAnchorsCommand.COMMAND_ID)
    override val actionType = ActionType.EXECUTE
    override val operationType = OperationType.EXECUTE
    override val inputTypeToken: TypeToken<RefreshTrustArgs> = typeToken<RefreshTrustArgs>()
    override val outputTypeToken: TypeToken<RefreshTrustResult> = typeToken<RefreshTrustResult>()

    override val summary = "Refresh trust anchors"
    override val description = "Forces a refresh of cached trust anchors across all or specific validators"

    override val resourceTarget =
        resourceTarget("trust.anchor") {
            identifier(IDENTIFIER_ANCHOR_TYPE)
            constraint("force_refresh")
        }

    override val executionTraits =
        executionTraits {
            notIdempotent()
            longDuration()
        }

    override val assuranceRequirements = assurance { aal2() }

    override val usageProfile = usageProfile { weight(DEFAULT_USAGE_WEIGHT) }

    override val inputSchemaOverlay =
        schemaOverlay {
            "anchorType" {
                label("Anchor Type")
                description("Specific anchor type to refresh, or null for all")
                identifier(IDENTIFIER_ANCHOR_TYPE)
            }
            "forceRefresh" {
                label("Force Refresh")
                description("Force refresh even if cache is still valid")
                constraint("force_refresh")
            }
        }

    override val complianceProfile =
        complianceProfile {
            framework(RegulatoryFramework.EIDAS)
        }

    override val capabilities =
        capabilities {
            discoverable()
            policyReady()
            workflowReady()
        }
}

// ============================================================================
// CheckRevocation Contract
// ============================================================================

@Inject
@SingleIn(AppScope::class)
class CheckRevocationContract : ServiceCommandContract<CheckRevocationArgs, RevocationCheckCommandResult> {
    override val commandId = CommandId(CheckRevocationCommand.COMMAND_ID)
    override val actionType = ActionType.EXECUTE
    override val operationType = OperationType.VERIFY
    override val inputTypeToken: TypeToken<CheckRevocationArgs> = typeToken<CheckRevocationArgs>()
    override val outputTypeToken: TypeToken<RevocationCheckCommandResult> = typeToken<RevocationCheckCommandResult>()

    override val summary = "Check certificate revocation"
    override val description = "Checks the revocation status of an X.509 certificate via OCSP and/or CRL"

    override val resourceTarget =
        resourceTarget("trust.revocation") {
            constraint("check_ocsp")
            constraint("check_crl")
            constraint("prefer_ocsp")
            constraint("timeout_ms")
        }

    override val executionTraits =
        executionTraits {
            idempotent()
            mediumDuration()
        }

    override val usageProfile = usageProfile { weight(DEFAULT_USAGE_WEIGHT) }

    override val inputSchemaOverlay =
        schemaOverlay {
            "certificateDer" {
                label("Certificate")
                description("X.509 certificate in DER encoding")
                restricted()
            }
            "issuerCertificateDer" {
                label("Issuer Certificate")
                description("Issuer certificate in DER encoding (for OCSP)")
                restricted()
            }
            "checkOcsp" {
                label("Check OCSP")
                description("Enable OCSP revocation checking")
                constraint("check_ocsp")
            }
            "checkCrl" {
                label("Check CRL")
                description("Enable CRL revocation checking")
                constraint("check_crl")
            }
            "preferOcsp" {
                label("Prefer OCSP")
                description("Prefer OCSP over CRL when both available")
                constraint("prefer_ocsp")
            }
            "timeoutMs" {
                label("Timeout (ms)")
                description("Timeout for revocation check network calls")
                constraint("timeout_ms")
            }
        }

    override val complianceProfile =
        complianceProfile {
            framework(RegulatoryFramework.EIDAS)
            framework(RegulatoryFramework.ISO27001)
        }

    override val capabilities =
        capabilities {
            discoverable()
            policyReady()
            workflowReady()
        }
}

// ============================================================================
// DiscoverEntityInfo Contract
// ============================================================================

@Inject
@SingleIn(AppScope::class)
class DiscoverEntityInfoContract : ServiceCommandContract<DiscoverEntityInfoArgs, DiscoverEntityInfoResult> {
    override val commandId = CommandId(DiscoverEntityInfoCommand.COMMAND_ID)
    override val actionType = ActionType.EXECUTE
    override val operationType = OperationType.RESOLVE
    override val inputTypeToken: TypeToken<DiscoverEntityInfoArgs> = typeToken<DiscoverEntityInfoArgs>()
    override val outputTypeToken: TypeToken<DiscoverEntityInfoResult> = typeToken<DiscoverEntityInfoResult>()

    override val summary = "Discover entity information"
    override val description = "Resolves metadata about a trust entity (names, contacts, logos, roles) from its identifier"

    override val resourceTarget =
        resourceTarget("trust.entity") {
            identifier(IDENTIFIER_CONTEXT_TYPE, required = true)
            identifier("entity_identifier", required = true)
        }

    override val executionTraits =
        executionTraits {
            idempotent()
            mediumDuration()
        }

    override val usageProfile = usageProfile { weight(DEFAULT_USAGE_WEIGHT) }

    override val inputSchemaOverlay =
        schemaOverlay {
            KEY_CONTEXT_TYPE {
                label(LABEL_CONTEXT_TYPE)
                description("Trust framework type for entity resolution")
                identifier(IDENTIFIER_CONTEXT_TYPE)
            }
            "entityIdentifier" {
                label("Entity Identifier")
                description("URI, DN, DID, or entity ID to discover")
                identifier("entity_identifier")
            }
        }

    override val complianceProfile =
        complianceProfile {
            framework(RegulatoryFramework.EIDAS)
            framework(RegulatoryFramework.EIDAS2)
        }

    override val capabilities =
        capabilities {
            discoverable()
            policyReady()
            workflowReady()
        }
}

// ============================================================================
// App-Scope Registration
// ============================================================================

@ContributesTo(AppScope::class)
interface TrustCoreContractDescriptors {
    @Provides @IntoMap
    @StringKey(ValidateTrustCommand.COMMAND_ID)
    fun validateTrust(c: ValidateTrustContract): ServiceCommandContract<*, *> = c

    @Provides @IntoMap
    @StringKey(GetTrustAnchorsCommand.COMMAND_ID)
    fun getTrustAnchors(c: GetTrustAnchorsContract): ServiceCommandContract<*, *> = c

    @Provides @IntoMap
    @StringKey(RefreshTrustAnchorsCommand.COMMAND_ID)
    fun refreshTrustAnchors(c: RefreshTrustAnchorsContract): ServiceCommandContract<*, *> = c

    @Provides @IntoMap
    @StringKey(CheckRevocationCommand.COMMAND_ID)
    fun checkRevocation(c: CheckRevocationContract): ServiceCommandContract<*, *> = c

    @Provides @IntoMap
    @StringKey(DiscoverEntityInfoCommand.COMMAND_ID)
    fun discoverEntityInfo(c: DiscoverEntityInfoContract): ServiceCommandContract<*, *> = c
}
