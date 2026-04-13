/*
 * © 2025 Sphereon International B.V.
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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter

import com.sphereon.core.api.encodeUrlComponent
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.dcqlQuery
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommandService
import com.sphereon.openid.oid4vp.verifier.Oid4vpUriScheme
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

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
) : TypedServiceCommandAdapter<BuildAuthorizationRequestUriArgs, StringResult>(
    commandId = BuildAuthorizationRequestUriCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<BuildAuthorizationRequestUriArgs>(),
    outputTypeToken = typeToken<StringResult>(),
), BuildAuthorizationRequestUriCommand, BuildAuthorizationRequestUriCommandService {

    override val commandId: String get() = BuildAuthorizationRequestUriCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildAuthorizationRequestUriArgs

    override suspend fun buildAuthorizationRequestUri(args: BuildAuthorizationRequestUriArgs): IdkResult<StringResult, IdkError> {
        return execute(args)
    }

    override suspend fun doExecute(
        args: BuildAuthorizationRequestUriArgs,
        applyDuring: (BuildAuthorizationRequestUriArgs) -> BuildAuthorizationRequestUriArgs
    ): IdkResult<StringResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Building authorization request URI")

        val request = processedArgs.request
        val scheme = processedArgs.scheme

        // If using request_uri mode (PAR)
        if (processedArgs.useRequestUri) {
            val requestUri = processedArgs.requestUri
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "requestUri is required when useRequestUri is true"
                ))

            val uri = buildUriWithRequestUri(scheme, request.clientId, requestUri, request)
            log.info("Built request_uri mode URI: ${scheme.scheme}://...")
            return Ok(StringResult(uri))
        }

        // Build full parameter URI
        val uri = buildFullParameterUri(scheme, request)

        log.info("Built full parameter URI: ${scheme.scheme}://...")
        return Ok(StringResult(uri))
    }

    /**
     * Build URI with request_uri parameter (PAR mode)
     *
     * Format: openid4vp://?client_id=...&request_uri=...
     */
    private fun buildUriWithRequestUri(
        scheme: Oid4vpUriScheme,
        clientId: String,
        requestUri: String,
        request: AuthorizationRequest
    ): String {
        return buildString {
            append(scheme.scheme)
            append("://?")
            append("client_id=")
            append(clientId.encodeUrlComponent())
            append("&request_uri=")
            append(requestUri.encodeUrlComponent())

            // State for request correlation (OAuth2). The wallet echoes state back in the
            // direct_post response. State is NOT in the request object (JAR) per OID4VP spec.
            request.state?.let {
                append("&state=")
                append(it.encodeUrlComponent())
            }

            // Some wallets require additional context to be present on the initial request URI even when using
            // request objects by reference (RFC 9101 + OpenID4VP). In particular, the holder must often be able
            // to resolve verifier client metadata (jwks/jwks_uri) in order to validate the signed request object.
            //
            // We therefore include optional request parameters when they are present on the AuthorizationRequest:
            // - client_metadata / client_metadata_uri (OpenID4VP 1.0 Section 5.5)
            // - request_uri_method (OpenID4VP extension)
            //
            // Note: values are stored as JsonElements in additionalParameters; we serialize them as JSON strings
            // for query transport, consistent with how the full-parameter URI builder encodes them.
            val additional = request.additionalParameters
            additional["client_metadata"]?.let { metadata ->
                append("&client_metadata=")
                append(Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), metadata).encodeUrlComponent())
            }
            additional["client_metadata_uri"]?.let { uri ->
                val uriString = (uri as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!uriString.isNullOrBlank()) {
                    append("&client_metadata_uri=")
                    append(uriString.encodeUrlComponent())
                }
            }
            additional["request_uri_method"]?.let { method ->
                val methodString = (method as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!methodString.isNullOrBlank()) {
                    append("&request_uri_method=")
                    append(methodString.encodeUrlComponent())
                }
            }
        }
    }

    /**
     * Build URI with all parameters inline
     *
     * Format: openid4vp://?client_id=...&response_type=...&dcql_query=...
     */
    private fun buildFullParameterUri(
        scheme: Oid4vpUriScheme,
        request: AuthorizationRequest
    ): String {
        return buildString {
            append(scheme.scheme)
            append("://?")

            // Required parameters
            append("client_id=")
            append(request.clientId.encodeUrlComponent())

            append("&response_type=")
            append(request.responseType.encodeUrlComponent())

            request.redirectUri?.let {
                append("&redirect_uri=")
                append(it.encodeUrlComponent())
            }

            // DCQL query (OpenID4VP 1.0 Final - NOT presentation_definition!)
            request.dcqlQuery?.let { dcql ->
                append("&dcql_query=")
                append(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), dcql).encodeUrlComponent())
            }

            // Optional parameters
            request.nonce?.let {
                append("&nonce=")
                append(it.encodeUrlComponent())
            }

            request.state?.let {
                append("&state=")
                append(it.encodeUrlComponent())
            }

            request.responseMode?.let {
                append("&response_mode=")
                append(it.encodeUrlComponent())
            }

            request.responseUri?.let {
                append("&response_uri=")
                append(it.encodeUrlComponent())
            }

            // Client metadata or URI
            request.additionalParameters["client_metadata"]?.let { metadata ->
                append("&client_metadata=")
                append(Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), metadata).encodeUrlComponent())
            }

            request.additionalParameters["client_metadata_uri"]?.let { uri ->
                val uriString = (uri as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (uriString != null) {
                    append("&client_metadata_uri=")
                    append(uriString.encodeUrlComponent())
                }
            }

            request.additionalParameters["client_id_scheme"]?.let { schemeValue ->
                val schemeString = (schemeValue as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (schemeString != null) {
                    append("&client_id_scheme=")
                    append(schemeString.encodeUrlComponent())
                }
            }
        }
    }
}
