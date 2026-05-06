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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeUrlGraph
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.dcqlQuery
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommandService
import com.sphereon.openid.oid4vp.verifier.Oid4vpUriScheme
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json

/**
 * Implementation of BuildAuthorizationRequestUriCommand for OpenID4VP RP (Verifier).
 *
 * Builds authorization request URIs that can be:
 * - Encoded as QR codes for scanning by mobile wallets
 * - Used as deep links for same-device flows
 * - Used as redirect URLs
 *
 * URI schemes:
 * - openid4vp:// (standard OpenID4VP)
 * - openid:// (legacy)
 * - haip:// (High Assurance Identity Profile)
 *
 * Reference: OpenID4VP 1.0 Final Section 5.1 - Request URI
 */
@Inject
@SingleIn(SessionScope::class)
class BuildAuthorizationRequestUriCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<BuildAuthorizationRequestUriArgs, StringResult, IdkError>(
        commandId = BuildAuthorizationRequestUriCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<BuildAuthorizationRequestUriArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    BuildAuthorizationRequestUriCommand,
    BuildAuthorizationRequestUriCommandService {
    override val commandId: String get() = BuildAuthorizationRequestUriCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildAuthorizationRequestUriArgs

    override suspend fun buildAuthorizationRequestUri(args: BuildAuthorizationRequestUriArgs): IdkResult<StringResult, IdkError> = execute(args)

    override suspend fun doExecute(
        args: BuildAuthorizationRequestUriArgs,
        applyDuring: (BuildAuthorizationRequestUriArgs) -> BuildAuthorizationRequestUriArgs,
    ): IdkResult<StringResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Building authorization request URI")

        val request = processedArgs.request
        val prefix = resolveDeeplinkPrefix(processedArgs)
        val logTag = processedArgs.deeplinkPrefix ?: "${processedArgs.scheme.scheme}://"

        // If using request_uri mode (PAR)
        if (processedArgs.useRequestUri) {
            val requestUri =
                processedArgs.requestUri
                    ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "requestUri is required when useRequestUri is true",
                        ),
                    )

            val uri = buildUriWithRequestUri(prefix, request.clientId, requestUri, request)
            log.info("Built request_uri mode URI: $logTag...")
            return Ok(StringResult(uri))
        }

        // Build full parameter URI
        val uri = buildFullParameterUri(prefix, request)

        log.info("Built full parameter URI: $logTag...")
        return Ok(StringResult(uri))
    }

    /**
     * Resolve the deeplink prefix that ends with `?` (so callers can append params with `&`).
     *
     * Priority: explicit [BuildAuthorizationRequestUriArgs.deeplinkPrefix] (a full URL — web
     * wallet, universal link, app link) over the [Oid4vpUriScheme] enum-driven `<scheme>://?`
     * default. If the explicit prefix already contains a `?`, append `&` unless it already
     * ends with one.
     */
    private fun resolveDeeplinkPrefix(args: BuildAuthorizationRequestUriArgs): String {
        val explicit = args.deeplinkPrefix?.takeIf { it.isNotBlank() }
        if (explicit != null) {
            return when {
                !explicit.contains('?') -> "$explicit?"
                explicit.endsWith('?') || explicit.endsWith('&') -> explicit
                else -> "$explicit&"
            }
        }
        return "${args.scheme.scheme}://?"
    }

    /**
     * Build URI with request_uri parameter (PAR mode).
     *
     * Format: `<prefix>client_id=...&request_uri=...` — `prefix` already ends with `?` or `&`.
     */
    private fun buildUriWithRequestUri(
        prefix: String,
        clientId: String,
        requestUri: String,
        request: AuthorizationRequest,
    ): String =
        buildString {
            append(prefix)
            append("client_id=")
            append(clientId.encodeUrlGraph())
            append("&request_uri=")
            append(requestUri.encodeUrlGraph())

            // Outer URI carries only parameters the wallet needs BEFORE fetching the JAR,
            // plus OAuth2 plumbing that lives outside the Request Object:
            //   - `state`  — OAuth2 (RFC 6749 §4.1.1); preserved in the outer URI per
            //                 RFC 9101 and echoed by the wallet in the response.
            //   - `request_uri_method` — tells the wallet GET vs POST for the JAR fetch.
            //
            // Everything else (client_metadata, client_metadata_uri, nonce, dcql_query,
            // response_mode, response_uri, …) lives in the Request Object per OID4VP §5.
            // Duplicating them in the outer URI is redundant and bloats the QR.
            request.state?.let {
                append("&state=")
                append(it.encodeUrlGraph())
            }

            val additional = request.additionalParameters
            additional["request_uri_method"]?.let { method ->
                val methodString = (method as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!methodString.isNullOrBlank()) {
                    append("&request_uri_method=")
                    append(methodString.encodeUrlGraph())
                }
            }
        }

    /**
     * Build URI with all parameters inline.
     *
     * Format: `<prefix>client_id=...&response_type=...&dcql_query=...` — `prefix` already
     * ends with `?` or `&`.
     */
    private fun buildFullParameterUri(
        prefix: String,
        request: AuthorizationRequest,
    ): String =
        buildString {
            append(prefix)

            // Required parameters
            append("client_id=")
            append(request.clientId.encodeUrlGraph())

            append("&response_type=")
            append(request.responseType.encodeUrlGraph())

            request.redirectUri?.let {
                append("&redirect_uri=")
                append(it.encodeUrlGraph())
            }

            // DCQL query (OpenID4VP 1.0 Final - NOT presentation_definition!)
            request.dcqlQuery?.let { dcql ->
                append("&dcql_query=")
                append(
                    Json
                        .encodeToString(
                            kotlinx.serialization.json.JsonObject
                                .serializer(),
                            dcql,
                        ).encodeUrlGraph(),
                )
            }

            // Optional parameters
            request.nonce?.let {
                append("&nonce=")
                append(it.encodeUrlGraph())
            }

            request.state?.let {
                append("&state=")
                append(it.encodeUrlGraph())
            }

            request.responseMode?.let {
                append("&response_mode=")
                append(it.encodeUrlGraph())
            }

            request.responseUri?.let {
                append("&response_uri=")
                append(it.encodeUrlGraph())
            }

            // Client metadata or URI
            request.additionalParameters["client_metadata"]?.let { metadata ->
                append("&client_metadata=")
                append(
                    Json
                        .encodeToString(
                            kotlinx.serialization.json.JsonElement
                                .serializer(),
                            metadata,
                        ).encodeUrlGraph(),
                )
            }

            request.additionalParameters["client_metadata_uri"]?.let { uri ->
                val uriString = (uri as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (uriString != null) {
                    append("&client_metadata_uri=")
                    append(uriString.encodeUrlGraph())
                }
            }

            request.additionalParameters["client_id_scheme"]?.let { schemeValue ->
                val schemeString = (schemeValue as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (schemeString != null) {
                    append("&client_id_scheme=")
                    append(schemeString.encodeUrlGraph())
                }
            }
        }
}
