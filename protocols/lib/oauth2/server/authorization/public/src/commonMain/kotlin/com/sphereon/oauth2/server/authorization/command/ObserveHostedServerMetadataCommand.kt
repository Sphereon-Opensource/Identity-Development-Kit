/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import kotlinx.serialization.Serializable

/** Trusted server-side selection. Callers must bind both values to the same active endpoint. */
@Serializable
data class ObserveHostedServerMetadataArgs(
    val serverKey: String,
    val effectiveIssuer: String,
)

/** Observed public descriptor only; this is not key material or a key-currentness proof. */
@Serializable
data class ObservedAsSigningDescriptor(
    val kid: String,
    val algorithm: String,
)

/** One unsigned discovery assembly; fingerprints are observations, not publication authority. */
@Serializable
data class ObservedHostedServerMetadata(
    val serverKey: String,
    val effectiveIssuer: String,
    val tokenFormat: TokenFormat,
    val oidcEnabled: Boolean,
    val metadata: AuthorizationServerMetadata,
    val signingDescriptor: ObservedAsSigningDescriptor?,
    val metadataFingerprint: String,
    val contextFingerprint: String,
    val signingDescriptorFingerprint: String?,
)

interface ObserveHostedServerMetadataCommand :
    ServiceCommand<ObserveHostedServerMetadataArgs, ObservedHostedServerMetadata, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.discovery.observe-hosted-metadata"
    }
}
