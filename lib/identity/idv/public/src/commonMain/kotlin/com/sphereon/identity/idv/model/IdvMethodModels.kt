/*
 * Copyright 2026 Sphereon International B.V.
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
 */

package com.sphereon.identity.idv.model

import com.sphereon.core.api.IdkResult
import com.sphereon.data.store.party.model.IdentifierType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed interface IdvMethodDefinition {
    val id: IdvMethodId
    val type: IdvMethodType
    val enabled: Boolean
    val assurance: IdvAssuranceProfile
    val compliance: IdvComplianceProfile
}

@Serializable
data class IdvAssuranceProfile(
    val maxAssurance: EidasAssuranceLevel,
    val maxAal: AuthAssuranceLevel,
    val amrCapabilities: Set<AuthMethodReference>,
)

@Serializable
data class IdvOutputProfile(
    val producedIdentifierTypes: Set<IdentifierType>,
    val producedAttributes: Set<AttributePath>,
)

@Serializable
data class IdvComplianceProfile(
    val regulatoryFrameworks: Set<TrustFrameworkType> = emptySet(),
    val trustFramework: TrustFrameworkType? = null,
    val evidenceStrength: EvidenceStrength? = null,
    val proofingScenario: ProofingScenario? = null,
    val padLevel: Int? = null,
    val requiresExplicitConsent: Boolean = false,
    val restrictedIdentifiers: Set<IdentifierType> = emptySet(),
    val applicableTerritories: Set<String>? = null,
    val territoryRestrictions: Map<String, TerritoryRestriction> = emptyMap(),
)

@Serializable
data class TerritoryRestriction(
    val prohibited: Boolean = false,
    val requiresLegalBasis: Set<IdentifierType> = emptySet(),
    val additionalRequirements: Set<String> = emptySet(),
)

@Serializable
data class OidcMethodDefinition(
    override val id: IdvMethodId,
    override val type: IdvMethodType = IdvMethodType.OIDC,
    val scope: IdvMethodScope = IdvMethodScope.APP,
    val tenantId: String? = null,
    override val enabled: Boolean = true,
    val display: IdvDisplay,
    override val assurance: IdvAssuranceProfile,
    val output: IdvOutputProfile,
    override val compliance: IdvComplianceProfile = IdvComplianceProfile(),
    val discoveryUrl: String,
    val clientIdRef: ConfigReference,
    val clientSecretRef: SecretReference,
    val scopes: Set<String> = setOf("openid"),
    val attributeMappings: List<AttributeMapping>,
    val subjectBinding: AttributeBinding,
    val requiredAttributes: Set<AttributePath> = emptySet(),
    val userInfoEnabled: Boolean = false,
    val userInfoAttributeMappings: List<AttributeMapping> = emptyList(),
    val tokenEndpointOverride: String? = null,
    val authorizationEndpointOverride: String? = null,
    val additionalParams: Map<String, String> = emptyMap(),
    val opaqueProviderSettings: JsonObject? = null,
) : IdvMethodDefinition

@Serializable
data class WalletMethodDefinition(
    override val id: IdvMethodId,
    override val type: IdvMethodType = IdvMethodType.WALLET,
    val scope: IdvMethodScope = IdvMethodScope.APP,
    val tenantId: String? = null,
    override val enabled: Boolean = true,
    val display: IdvDisplay,
    override val assurance: IdvAssuranceProfile,
    val output: IdvOutputProfile,
    override val compliance: IdvComplianceProfile = IdvComplianceProfile(),
    val credentialType: String,
    val dcqlQuery: String,
    val trustedIssuers: Set<String>,
    val verifierClientId: String,
    val presentationDefinition: String? = null,
    val attributeMappings: List<AttributeMapping>,
    val requiredAttributes: Set<AttributePath> = emptySet(),
    val trustFramework: TrustFrameworkType? = null,
    val acceptedLoAs: Set<EidasAssuranceLevel>? = null,
) : IdvMethodDefinition

@Serializable
data class AttributeMatchMethodDefinition(
    override val id: IdvMethodId,
    override val type: IdvMethodType = IdvMethodType.CLAIM_MATCH,
    val scope: IdvMethodScope = IdvMethodScope.APP,
    val tenantId: String? = null,
    override val enabled: Boolean = true,
    val display: IdvDisplay,
    override val assurance: IdvAssuranceProfile,
    val output: IdvOutputProfile,
    override val compliance: IdvComplianceProfile = IdvComplianceProfile(),
    val identifierType: IdentifierType,
    val targetAttributeBinding: AttributeBinding,
    val hashBeforeLookup: Boolean = true,
) : IdvMethodDefinition

@Serializable
data class AttributeMapping(
    val sourceAttribute: AttributePath,
    val targetAttribute: AttributePath,
    val identifierType: IdentifierType? = null,
    val required: Boolean = false,
)

interface IdvMethodDriver {
    val methodType: IdvMethodType

    suspend fun dispatch(work: DispatchWork): IdkResult<DispatchOutcome, IdvError>

    suspend fun submit(work: SubmitWork): IdkResult<SubmitOutcome, IdvError>

    suspend fun callback(work: CallbackWork): IdkResult<CallbackOutcome, IdvError>

    suspend fun poll(work: PollWork): IdkResult<PollOutcome, IdvError>

    suspend fun cancel(work: CancelWork): IdkResult<CancelOutcome, IdvError>
}

@Serializable
data class DispatchWork(
    val executionId: IdvExecutionId,
    val nodeId: IdvNodeId,
    val methodDefinition: IdvMethodDefinition,
    val resolvedAttributes: AttributeBag,
    val callbackBaseUrl: String,
)

@Serializable
sealed interface DispatchOutcome

@Serializable
data class PendingDispatch(val action: IdvPendingAction) : DispatchOutcome

@Serializable
data class ImmediateResult(val result: IdvNodeResult) : DispatchOutcome

@Serializable
data class SubmitWork(
    val executionId: IdvExecutionId,
    val nodeId: IdvNodeId,
    val methodDefinition: IdvMethodDefinition,
    val input: Map<InputFieldId, JsonElement>,
    val driverState: JsonObject? = null,
) 

@Serializable
sealed interface SubmitOutcome

@Serializable
data class SubmitComplete(val result: IdvNodeResult) : SubmitOutcome

@Serializable
data class SubmitPending(val action: IdvPendingAction) : SubmitOutcome

@Serializable
data class SubmitFailed(val error: IdvError) : SubmitOutcome

@Serializable
data class CallbackWork(
    val executionId: IdvExecutionId,
    val nodeId: IdvNodeId,
    val methodDefinition: IdvMethodDefinition,
    val callbackData: Map<String, String>,
    val driverState: JsonObject? = null,
)

@Serializable
sealed interface CallbackOutcome

@Serializable
data class CallbackComplete(val result: IdvNodeResult) : CallbackOutcome

@Serializable
data class CallbackPending(val action: IdvPendingAction) : CallbackOutcome

@Serializable
data class CallbackFailed(val error: IdvError) : CallbackOutcome

@Serializable
data class PollWork(
    val executionId: IdvExecutionId,
    val nodeId: IdvNodeId,
    val methodDefinition: IdvMethodDefinition,
    val driverState: JsonObject? = null,
)

@Serializable
sealed interface PollOutcome

@Serializable
data class PollComplete(val result: IdvNodeResult) : PollOutcome

@Serializable
data class PollPending(val action: PollAction) : PollOutcome

@Serializable
data class PollFailed(val error: IdvError) : PollOutcome

@Serializable
data class CancelWork(
    val executionId: IdvExecutionId,
    val nodeId: IdvNodeId,
    val methodDefinition: IdvMethodDefinition,
    val driverState: JsonObject? = null,
)

@Serializable
sealed interface CancelOutcome

@Serializable
data class CancelAcknowledged(val cleanedUp: Boolean) : CancelOutcome
