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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.resolution.IdentifierContext
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Result of parsing a client_id.
 *
 * @property clientIdScheme The detected Client Identifier Prefix scheme
 * @property clientId The client identifier without the prefix (after the colon)
 * @property clientIdWithScheme The complete client_id value including prefix (for OID4VP)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParsedClientId", exact = true)
@JsExportCompat
data class ParsedClientId(
    val clientIdScheme: ClientIdScheme,
    val clientId: String,
    val clientIdWithScheme: String,
)

/**
 * Extension function to convert ParsedClientId to IdentifierContext.
 *
 * @param issuer Optional issuer to include in the context
 * @param metadata Optional metadata to include in the context
 * @return IdentifierContext with clientId and clientIdScheme set from the parsed result
 */
fun ParsedClientId.toIdentifierContext(
    issuer: String? = null,
    metadata: Map<String, String> = emptyMap(),
): IdentifierContext =
    IdentifierContext(
        issuer = issuer,
        clientId = clientId,
        clientIdScheme = clientIdScheme.prefix,
        metadata = metadata,
    )

/**
 * Input arguments for [ParseClientIdCommand].
 *
 * @property clientId The raw client_id string to parse
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseClientIdArgs", exact = true)
@JsExportCompat
data class ParseClientIdArgs(
    val clientId: String,
)

/**
 * Command for parsing a client_id to determine its scheme.
 *
 * Parses the client_id prefix/format according to OpenID4VP 1.0 Final
 * to determine how the verifier's identity should be verified.
 *
 * Examples:
 * - `redirect_uri:https://client.example.org/cb` -> REDIRECT_URI scheme, clientId: https://client.example.org/cb
 * - `decentralized_identifier:did:example:123` -> DECENTRALIZED_IDENTIFIER scheme, clientId: did:example:123
 * - `verifier_attestation:verifier.example` -> VERIFIER_ATTESTATION scheme, clientId: verifier.example
 * - `my-client-id` -> PRE_REGISTERED scheme (no prefix), clientId: my-client-id
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseClientIdCommand", exact = true)
@JsExportCompat
interface ParseClientIdCommand : ServiceCommand<ParseClientIdArgs, ParsedClientId, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.client.parse"
    }
}

/**
 * Command service interface for parsing client IDs.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseClientIdCommandService", exact = true)
interface ParseClientIdCommandService {
    suspend fun parseClientId(clientId: String): IdkResult<ParsedClientId, IdkError>
}
