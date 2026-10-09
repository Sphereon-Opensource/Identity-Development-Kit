/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.json.jsonSerializer
import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.ObserveHostedServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.ObserveHostedServerMetadataCommand
import com.sphereon.oauth2.server.authorization.command.ObservedAsSigningDescriptor
import com.sphereon.oauth2.server.authorization.command.ObservedHostedServerMetadata
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** Observes one selected hosted AS using the ordinary discovery builder's captured assembly. */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ObserveHostedServerMetadataCommandImpl", exact = true)
class ObserveHostedServerMetadataCommandImpl(
    execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider,
    private val assembler: CapturedServerMetadataAssembler,
) : TypedServiceCommandAdapter<ObserveHostedServerMetadataArgs, ObservedHostedServerMetadata, IdkError>(
        commandId = ObserveHostedServerMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ObserveHostedServerMetadataArgs>(),
        outputTypeToken = typeToken<ObservedHostedServerMetadata>(),
    ), ObserveHostedServerMetadataCommand {
    override val commandId: String get() = ObserveHostedServerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ObserveHostedServerMetadataArgs

    override suspend fun doExecute(
        args: ObserveHostedServerMetadataArgs,
        applyDuring: (ObserveHostedServerMetadataArgs) -> ObserveHostedServerMetadataArgs,
    ): IdkResult<ObservedHostedServerMetadata, IdkError> {
        val applied = applyDuring(args)
        if (applied.serverKey.isBlank() || applied.effectiveIssuer.isBlank()) {
            return Err(IdkError.fromDTO(AuthorizationServerError.InvalidRequest(details = "An exact hosted server key and trusted effective issuer are required")))
        }
        val previous = asInstanceIdProvider.currentAsInstanceId()
        try {
            // Temporarily override the existing SessionScope holder and restore it in finally;
            // this does not isolate concurrent commands in the same session.
            asInstanceIdProvider.setCurrentAsInstanceId(applied.serverKey)
            val root = configProvider.getConfig()
            val captured =
                try {
                    CapturedAsServerConfig.select(root, applied.serverKey)
                } catch (expected: IllegalArgumentException) {
                    return Err(IdkError.fromDTO(AuthorizationServerError.InvalidRequest(details = expected.message ?: "Invalid hosted server")))
                } catch (expected: IllegalStateException) {
                    return Err(IdkError.fromDTO(AuthorizationServerError.InvalidRequest(details = expected.message ?: "Hosted server not found")))
                }
            val configuredIssuer = captured.server?.issuer
            if (configuredIssuer != null && configuredIssuer != applied.effectiveIssuer) {
                return Err(IdkError.fromDTO(AuthorizationServerError.InvalidRequest(details = "Trusted effective issuer does not match selected hosted server")))
            }
            val selectedServer = requireNotNull(captured.server)
            val assembled = assembler.assembleCaptured(captured, applied.effectiveIssuer, includeSignedMetadata = false, strictCapabilities = true)
            if (assembled.isErr) return Err(IdkError.fromDTO(assembled.error))
            val metadata = assembled.value.metadata
            if (metadata.signedMetadata != null || metadata.issuer != applied.effectiveIssuer) {
                return Err(IdkError.fromDTO(AuthorizationServerError.ServerError(details = "Captured unsigned metadata is inconsistent with selected issuer")))
            }
            val descriptor =
                assembled.value.signing.identifier?.identifier?.let { key ->
                    val kid = key.kid?.takeIf { it.isNotBlank() }
                        ?: return Err(IdkError.fromDTO(AuthorizationServerError.ServerError(details = "Selected signing descriptor has no public kid")))
                    val algorithm = key.signatureAlgorithm?.let(::keyAlgorithmToJwsAlg)
                        ?: return Err(IdkError.fromDTO(AuthorizationServerError.ServerError(details = "Selected signing descriptor has no algorithm")))
                    ObservedAsSigningDescriptor(kid, algorithm)
                }
            val publicContext = JsonObject(
                mapOf(
                    "serverKey" to JsonPrimitive(applied.serverKey),
                    "effectiveIssuer" to JsonPrimitive(applied.effectiveIssuer),
                    "tokenFormat" to JsonPrimitive(selectedServer.tokenFormat.name),
                    "oidcEnabled" to JsonPrimitive(selectedServer.oidc.isEnabled),
                ),
            )
            fun fingerprint(value: JsonObject) = hash(Jcs.canonicalize(value), DigestAlg.SHA256).encodeToHex()
            val metadataJson = jsonSerializer.encodeToJsonElement(metadata).jsonObject
            val descriptorJson = descriptor?.let { jsonSerializer.encodeToJsonElement(it).jsonObject }
            return Ok(
                ObservedHostedServerMetadata(
                    serverKey = applied.serverKey,
                    effectiveIssuer = applied.effectiveIssuer,
                    tokenFormat = selectedServer.tokenFormat,
                    oidcEnabled = selectedServer.oidc.isEnabled,
                    metadata = metadata,
                    signingDescriptor = descriptor,
                    metadataFingerprint = fingerprint(metadataJson),
                    contextFingerprint = fingerprint(publicContext),
                    signingDescriptorFingerprint = descriptorJson?.let(::fingerprint),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            return Err(IdkError.fromDTO(AuthorizationServerError.ServerError(details = "Hosted metadata observation failed", exception = expected)))
        } finally {
            if (previous == null) asInstanceIdProvider.clearCurrentAsInstanceId()
            else asInstanceIdProvider.setCurrentAsInstanceId(previous)
        }
    }
}
