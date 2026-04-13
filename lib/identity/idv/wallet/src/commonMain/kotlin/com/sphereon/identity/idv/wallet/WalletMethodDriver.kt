package com.sphereon.identity.idv.wallet

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.identity.idv.model.AuthMethodReference
import com.sphereon.identity.idv.model.CallbackComplete
import com.sphereon.identity.idv.model.CallbackFailed
import com.sphereon.identity.idv.model.CallbackOutcome
import com.sphereon.identity.idv.model.CallbackWork
import com.sphereon.identity.idv.model.CancelAcknowledged
import com.sphereon.identity.idv.model.CancelOutcome
import com.sphereon.identity.idv.model.CancelWork
import com.sphereon.identity.idv.model.AttributeMapping
import com.sphereon.identity.idv.model.AttributePath
import com.sphereon.identity.idv.model.DispatchOutcome
import com.sphereon.identity.idv.model.DispatchWork
import com.sphereon.identity.idv.model.DriverError
import com.sphereon.identity.idv.model.IdvEvidence
import com.sphereon.identity.idv.model.IdvEvidenceType
import com.sphereon.identity.idv.model.IdvMethodDefinition
import com.sphereon.identity.idv.model.IdvMethodDriver
import com.sphereon.identity.idv.model.IdvMethodType
import com.sphereon.identity.idv.model.IdvNodeId
import com.sphereon.identity.idv.model.IdvNodeResult
import com.sphereon.identity.idv.model.PendingDispatch
import com.sphereon.identity.idv.model.PollAction
import com.sphereon.identity.idv.model.PollFailed
import com.sphereon.identity.idv.model.PollOutcome
import com.sphereon.identity.idv.model.PollPending
import com.sphereon.identity.idv.model.PollWork
import com.sphereon.identity.idv.model.RedirectAction
import com.sphereon.identity.idv.model.ResolvedIdentifier
import com.sphereon.identity.idv.model.SubmitFailed
import com.sphereon.identity.idv.model.SubmitOutcome
import com.sphereon.identity.idv.model.SubmitWork
import com.sphereon.identity.idv.model.WalletMethodDefinition
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<IdvMethodDriver>())
class WalletMethodDriver(
    private val createAuthorizationRequestCommand: CreateAuthorizationRequestCommand,
    private val buildAuthorizationRequestUriCommand: BuildAuthorizationRequestUriCommand,
    private val parseAuthorizationResponseCommand: ParseAuthorizationResponseCommand,
    private val validateAuthorizationResponseCommand: ValidateAuthorizationResponseCommand,
    private val stateStore: WalletDriverStateStore,
) : IdvMethodDriver {

    override val methodType: IdvMethodType = IdvMethodType.WALLET

    override suspend fun dispatch(work: DispatchWork): IdkResult<DispatchOutcome, com.sphereon.identity.idv.model.IdvError> {
        val definition = work.methodDefinition as? WalletMethodDefinition
            ?: return Err(driverError(work.nodeId, work.methodDefinition, "Wallet driver received unsupported method definition"))

        val dcqlQuery = try {
            Json.decodeFromString<DcqlQuery>(definition.dcqlQuery)
        } catch (e: Exception) {
            return Err(driverError(work.nodeId, definition, "Invalid DCQL query: ${e.message ?: "unknown parse error"}"))
        }

        val nonce = randomId()
        val createdRequest = createAuthorizationRequestCommand.execute(
            CreateAuthorizationRequestArgs(
                dcqlQuery = dcqlQuery,
                clientId = definition.verifierClientId,
                responseUri = work.callbackBaseUrl,
                responseMode = ResponseMode.DIRECT_POST,
                nonce = nonce,
                state = randomId(),
                clientIdScheme = ClientIdScheme.REDIRECT_URI
            )
        ).getOrElse { error ->
            return Err(driverError(work.nodeId, definition, "Failed to create wallet authorization request: ${error.message.defaultMessage}"))
        }

        val requestUri = buildAuthorizationRequestUriCommand.execute(
            BuildAuthorizationRequestUriArgs(request = createdRequest.request)
        ).getOrElse { error ->
            return Err(driverError(work.nodeId, definition, "Failed to build wallet authorization URI: ${error.message.defaultMessage}"))
        }

        stateStore.put(
            WalletDriverState(
                executionId = work.executionId.value,
                nodeId = work.nodeId.value,
                methodId = definition.id.value,
                callbackRef = work.callbackBaseUrl,
                state = createdRequest.request.state,
                nonce = nonce,
                request = createdRequest.request,
                dcqlQuery = dcqlQuery,
                issuedAt = Clock.System.now(),
            )
        )

        return Ok(
            PendingDispatch(
                RedirectAction(
                    url = requestUri.value,
                    callbackRef = work.callbackBaseUrl,
                    expiresAt = Clock.System.now() + 10.minutes
                )
            )
        )
    }

    override suspend fun submit(work: SubmitWork): IdkResult<SubmitOutcome, com.sphereon.identity.idv.model.IdvError> =
        Ok(SubmitFailed(driverError(work.nodeId, work.methodDefinition, "Wallet verification expects a wallet callback, not a submit operation")))

    override suspend fun callback(work: CallbackWork): IdkResult<CallbackOutcome, com.sphereon.identity.idv.model.IdvError> {
        val definition = work.methodDefinition as? WalletMethodDefinition
            ?: return Ok(CallbackFailed(driverError(work.nodeId, work.methodDefinition, "Wallet driver received unsupported method definition")))
        val state = stateStore.get(work.executionId.value, work.nodeId.value)
            ?: return Ok(CallbackFailed(driverError(work.nodeId, definition, "No wallet request found for callback")))

        if (!state.state.isNullOrBlank()) {
            val callbackState = work.callbackData["state"]
            if (!callbackState.isNullOrBlank() && callbackState != state.state) {
                return Ok(CallbackFailed(driverError(work.nodeId, definition, "Wallet callback state mismatch")))
            }
        }

        val parsed = parseAuthorizationResponseCommand.execute(
            ParseAuthorizationResponseArgs(
                responseParams = work.callbackData,
                originalRequest = state.request
            )
        ).getOrElse { error ->
            return Ok(CallbackFailed(driverError(work.nodeId, definition, "Failed to parse wallet response: ${error.message.defaultMessage}")))
        }

        val validation = validateAuthorizationResponseCommand.execute(
            ValidateAuthorizationResponseArgs(
                parsedResponse = parsed,
                originalRequest = state.request,
                dcqlQuery = state.dcqlQuery,
                expectedNonce = state.nonce
            )
        ).getOrElse { error ->
            return Ok(CallbackFailed(driverError(work.nodeId, definition, "Wallet response validation failed: ${error.message.defaultMessage}")))
        }

        if (!validation.valid) {
            return Ok(CallbackFailed(driverError(work.nodeId, definition, "Wallet response is invalid: ${validation.errors.joinToString("; ")}")))
        }

        val rawClaims = validation.matchedCredentials.flatMap { credential ->
            credential.disclosedClaims.entries.map { (key, value) -> key to value.toJsonElement() }
        }.toMap()

        val mappedAttributes = buildMappedAttributes(definition.attributeMappings, rawClaims)
        val identifiers = buildIdentifiers(definition.attributeMappings, mappedAttributes, definition.assurance.maxAssurance)

        val result = IdvNodeResult(
            nodeId = work.nodeId,
            methodId = definition.id,
            identifiers = identifiers,
            attributes = mappedAttributes,
            assurance = definition.assurance.maxAssurance,
            aal = definition.assurance.maxAal,
            amr = definition.assurance.amrCapabilities + AuthMethodReference.HWK,
            evidence = IdvEvidence(
                provider = definition.verifierClientId,
                method = "wallet",
                timestamp = Clock.System.now(),
                metadata = buildMap {
                    put("credential_type", JsonPrimitive(definition.credentialType))
                    put("matched_credentials", JsonPrimitive(validation.matchedCredentials.size))
                    put("trusted_issuers", JsonArray(definition.trustedIssuers.map(::JsonPrimitive)))
                },
                evidenceType = IdvEvidenceType.ELECTRONIC_RECORD
            ),
            trustFramework = definition.trustFramework ?: definition.compliance.trustFramework,
            evidenceStrength = definition.compliance.evidenceStrength,
            proofingScenario = definition.compliance.proofingScenario,
        )

        stateStore.remove(work.executionId.value, work.nodeId.value)
        return Ok(CallbackComplete(result))
    }

    override suspend fun poll(work: PollWork): IdkResult<PollOutcome, com.sphereon.identity.idv.model.IdvError> {
        val definition = work.methodDefinition as? WalletMethodDefinition
            ?: return Ok(PollFailed(driverError(work.nodeId, work.methodDefinition, "Wallet driver received unsupported method definition")))
        val existing = stateStore.get(work.executionId.value, work.nodeId.value)
            ?: return Ok(PollFailed(driverError(work.nodeId, definition, "No wallet request found to poll")))
        return Ok(PollPending(PollAction(nextCheckAt = existing.issuedAt + 15.minutes, pollIntervalMs = 5_000)))
    }

    override suspend fun cancel(work: CancelWork): IdkResult<CancelOutcome, com.sphereon.identity.idv.model.IdvError> {
        stateStore.remove(work.executionId.value, work.nodeId.value)
        return Ok(CancelAcknowledged(cleanedUp = true))
    }

    private fun buildMappedAttributes(mappings: List<AttributeMapping>, claims: Map<String, JsonElement>): Map<AttributePath, JsonElement> =
        buildMap {
            mappings.forEach { mapping ->
                extractValue(mapping.sourceAttribute, claims)?.let { put(mapping.targetAttribute, it) }
            }
        }

    private fun buildIdentifiers(
        mappings: List<AttributeMapping>,
        attributes: Map<AttributePath, JsonElement>,
        assurance: com.sphereon.identity.idv.model.EidasAssuranceLevel,
    ): List<ResolvedIdentifier> =
        mappings.mapNotNull { mapping ->
            val identifierType = mapping.identifierType ?: return@mapNotNull null
            val value = attributes[mapping.targetAttribute]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ResolvedIdentifier(type = identifierType, value = value, verified = true, assurance = assurance)
        }

    private fun driverError(
        nodeId: IdvNodeId,
        definition: IdvMethodDefinition,
        message: String,
    ): DriverError = DriverError(message = message, nodeId = nodeId, methodId = definition.id)

    private fun extractValue(attributePath: AttributePath, claims: Map<String, JsonElement>): JsonElement? {
        val direct = claims[attributePath.value]
        if (direct != null) return direct
        val segments = attributePath.value.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        var current: JsonElement? = claims[segments.first()]
        for (segment in segments.drop(1)) {
            current = when (current) {
                is kotlinx.serialization.json.JsonObject -> current[segment]
                else -> return null
            }
        }
        return current
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> kotlinx.serialization.json.JsonObject(buildMap {
            entries.forEach { (key, value) ->
                key?.toString()?.let { put(it, value.toJsonElement()) }
            }
        })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun randomId(): String = Uuid.random().toString()
}
