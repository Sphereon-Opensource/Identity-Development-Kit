/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.openid.oid4vp.holder

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.AuthorizationResponse
import kotlinx.serialization.Serializable

/** Private preparation result. This is not a wire response and contains no placeholder signatures. */
@Serializable
@JsExportCompat
data class HolderPreparedJwtVpResponse(
    val state: String? = null,
    val presentations: List<HolderPreparedJwtVp>,
)

@Serializable
@JsExportCompat
data class HolderPreparedJwtVp(
    val credentialQueryId: String,
    val credentialId: String,
    val signingRequest: HolderJwtVpSigningRequest,
)

@Serializable
data class PrepareJwtVpResponseArgs(
    val request: ResolvedOid4vpRequest,
    val selectedCredentials: List<HolderJwtVpPreparationCredential>,
)

/** SelectedCredential's runtime-only key metadata is explicit on this durable command input. */
@Serializable
data class HolderJwtVpPreparationCredential(
    val credential: SelectedCredential,
    val signingIdentifier: HolderJwtVpSigningIdentifier,
    val signingAlgorithm: com.sphereon.crypto.core.generic.SignatureAlgorithm,
) {
    fun selectedCredential(): SelectedCredential = credential.copy(
        holderJwtVpSigningIdentifier = signingIdentifier, holderSigningAlgorithm = signingAlgorithm,
    )
}

@Serializable
data class FinalizeJwtVpResponseArgs(
    val prepared: HolderPreparedJwtVpResponse,
    val signatures: List<HolderJwtVpSigningResult>,
)

interface PrepareJwtVpResponseCommand : ServiceCommand<PrepareJwtVpResponseArgs, HolderPreparedJwtVpResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID
    companion object { const val COMMAND_ID = "oid4vp.holder.prepare-jwt-vp-response" }
}

interface FinalizeJwtVpResponseCommand : ServiceCommand<FinalizeJwtVpResponseArgs, AuthorizationResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID
    companion object { const val COMMAND_ID = "oid4vp.holder.finalize-jwt-vp-response" }
}
