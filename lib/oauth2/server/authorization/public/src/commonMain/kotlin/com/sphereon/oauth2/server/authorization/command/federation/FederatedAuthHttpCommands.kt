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

package com.sphereon.oauth2.server.authorization.command.federation

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.compat.JsExportCompat

/*
 * HTTP endpoint contracts for the federation routes. Each endpoint is its own command, paired
 * with a ServiceCommand that holds the business logic (see FederatedAuthCommands.kt). The HTTP
 * command parses query params and renders the response; delegation to the ServiceCommand is the
 * whole point of the split (feedback_command_layers.md).
 */

@JsExportCompat
interface FederationAuthorizeHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.federation-authorize-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/federation/authorize",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "federationAuthorize",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("federation"),
                summary = "Initiate federated authentication by redirecting to the upstream IdP",
            )
    }
}

@JsExportCompat
interface ReconciliationAuthorizeHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.reconciliation-authorize-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/reconciliation/authorize",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "reconciliationAuthorize",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("federation", "reconciliation"),
                summary = "Initiate reconciliation (IDV) flow against an upstream IdP",
            )
    }
}

@JsExportCompat
interface FederationCallbackHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.federation-callback-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/federation/callback",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "federationCallback",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("federation"),
                summary = "Handle the upstream IdP callback for both federation and reconciliation flows",
            )
    }
}

@JsExportCompat
interface ReconciliationCallbackHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.reconciliation-callback-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/reconciliation/callback",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "reconciliationCallback",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("federation", "reconciliation"),
                summary = "Handle the upstream IdP callback for a reconciliation (IDV) flow",
            )
    }
}

@JsExportCompat
interface ListFederationProvidersHttpEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.list-federation-providers-endpoint"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/federation/providers",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "federationProviders",
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                tags = setOf("federation"),
                summary = "List the enabled federation providers for login UI provider selection",
            )
    }
}
