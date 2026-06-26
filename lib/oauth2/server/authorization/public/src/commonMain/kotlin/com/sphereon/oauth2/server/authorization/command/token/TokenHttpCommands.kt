/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.command.token

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/*
 * HTTP endpoint contracts for the token-endpoint family. Each command is a thin HTTP shell over
 * its underlying ServiceCommand: parse the form/body, dispatch, render the response. The
 * register-pre-authorized-code endpoint additionally lifts Basic-auth credentials from the
 * Authorization header in the HTTP layer; credential validation lives in
 * [RegisterPreAuthorizedCodeCommand].
 */

@JsExportCompat
interface TokenHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.token.token-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/token",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "token",
                commandId = COMMAND_ID,
                tags = setOf("token"),
                summary = "RFC 6749 OAuth 2.0 Token Endpoint",
            )
    }
}

@JsExportCompat
interface RegisterPreAuthorizedCodeHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.token.register-pre-authorized-code-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/internal/preauth/register",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "registerPreAuthCode",
                commandId = COMMAND_ID,
                tags = setOf("token", "internal"),
                summary = "Internal endpoint for cross-service pre-authorized code registration",
            )
    }
}

/**
 * Internal cross-service endpoint that PROVISIONS this tenant's AS signing key INTO the KMS the
 * authorization server uses (in single-port deployments, the per-tenant `tenant-kms` over gRPC).
 *
 * Tenant registration runs in the PLATFORM process, whose KMS is NOT the tenant AS's KMS, so the
 * platform cannot generate the per-tenant signing key where the AS will read it. Instead the
 * platform makes an EXPLICIT east-west call to THIS endpoint on the tenant's own AS host
 * (`{tenant}.{base}`); the AS resolves the tenant from that host and generates the key in its own
 * KMS. Basic-auth protected against the AS `internal-clients`. Idempotent: an existing key is left
 * in place. This is explicit provisioning, never a sign-time self-seed.
 */
@JsExportCompat
interface ProvisionSigningKeyHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.token.provision-signing-key-endpoint"

        /**
         * Single source of truth for the `aud` of a signing-key provisioning bearer, bound to the
         * TARGET tenant. The platform mints the token with `audienceFor(targetTenant)`; the receiving
         * tenant AS validates the presented bearer's `aud` against `audienceFor(hostResolvedTenant)`.
         * Because the audience embeds the tenant id, a token minted for tenant A is rejected by tenant
         * B — this is what closes cross-tenant replay of the east-west provisioning credential.
         *
         * Referenced by BOTH sides: the platform sender (EDK tenant-service, which depends on this
         * public module) and the receiver impl below. Keep the format here so the two never drift.
         */
        fun audienceFor(tenantId: String): String = "urn:sphereon:provision:signing-key:tenant:$tenantId"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/internal/provision/signing-key",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "provisionSigningKey",
                commandId = COMMAND_ID,
                tags = setOf("token", "internal"),
                summary = "Internal endpoint that provisions the tenant AS signing key into the AS's KMS",
            )
    }
}
