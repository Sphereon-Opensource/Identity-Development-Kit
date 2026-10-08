/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.oauth2.common.model.ClientAssertion
import kotlinx.serialization.Serializable

/** Opaque KMS selector for an OAuth client-assertion signing key. */
@JsExportCompat
@Serializable
data class PrivateKeyJwtKeySelector(
    val providerId: String,
    val alias: String,
    val kid: String,
    val algorithm: String,
)

/** Inputs for a KMS-custodied RFC 7523 `private_key_jwt` client assertion. */
@JsExportCompat
@Serializable
data class PrivateKeyJwtClientAssertionArgs(
    val keySelector: PrivateKeyJwtKeySelector,
    val clientId: String,
    val effectiveTokenEndpoint: String,
    val lifetimeSeconds: Long? = null,
)

/** Creates a compact private-key JWT without exposing signing-key material. */
@JsExportCompat
interface PrivateKeyJwtClientAssertionServiceCommand :
    ServiceCommand<PrivateKeyJwtClientAssertionArgs, ClientAssertion, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.client.create-private-key-jwt-assertion"
    }
}


