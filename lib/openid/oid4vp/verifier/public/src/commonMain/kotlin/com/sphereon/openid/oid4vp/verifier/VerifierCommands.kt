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

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ============================================================================
// RP (Verifier) Command Interfaces for OpenID4VP 1.0 Final
// ============================================================================

/**
 * Command for creating an OpenID4VP authorization request.
 *
 * OpenID4VP 1.0 Final:
 * - Creates authorization request with DCQL query (NOT Presentation Exchange!)
 * - Supports various response modes (direct_post, fragment, query)
 * - Generates appropriate client_id based on scheme
 *
 * @see CreateAuthorizationRequestArgs
 * @see CreatedAuthorizationRequest
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationRequestCommand", exact = true)
@JsExportCompat
interface CreateAuthorizationRequestCommand : ServiceCommand<CreateAuthorizationRequestArgs, CreatedAuthorizationRequest> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.createrequest"
    }
}

/**
 * Command service interface for creating authorization requests.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationRequestCommandService", exact = true)
interface CreateAuthorizationRequestCommandService {
    suspend fun createAuthorizationRequest(args: CreateAuthorizationRequestArgs): IdkResult<CreatedAuthorizationRequest, IdkError>
}

/**
 * Command for parsing an OpenID4VP authorization response.
 *
 * OpenID4VP 1.0 Final:
 * - Parses vp_token from response (NOT presentation_submission!)
 * - Handles single and multiple VP tokens
 * - Extracts state for correlation
 *
 * @see ParseAuthorizationResponseArgs
 * @see ParsedAuthorizationResponse
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseAuthorizationResponseCommand", exact = true)
@JsExportCompat
interface ParseAuthorizationResponseCommand : ServiceCommand<ParseAuthorizationResponseArgs, ParsedAuthorizationResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.parseresponse"
    }
}

/**
 * Command service interface for parsing authorization responses.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ParseAuthorizationResponseCommandService", exact = true)
interface ParseAuthorizationResponseCommandService {
    suspend fun parseAuthorizationResponse(args: ParseAuthorizationResponseArgs): IdkResult<ParsedAuthorizationResponse, IdkError>
}

/**
 * Command for validating an OpenID4VP authorization response against the original request.
 *
 * OpenID4VP 1.0 Final:
 * - Validates vp_token against DCQL query
 * - Verifies credential formats match requested formats
 * - Validates required claims are present
 *
 * @see ValidateAuthorizationResponseArgs
 * @see ValidationResult
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateAuthorizationResponseCommand", exact = true)
@JsExportCompat
interface ValidateAuthorizationResponseCommand : ServiceCommand<ValidateAuthorizationResponseArgs, ValidationResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.validate"
    }
}

/**
 * Command service interface for validating authorization responses.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ValidateAuthorizationResponseCommandService", exact = true)
interface ValidateAuthorizationResponseCommandService {
    suspend fun validateAuthorizationResponse(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError>
}

/**
 * Command for verifying holder binding in a VP token.
 *
 * OpenID4VP 1.0 Final:
 * - Verifies KB-JWT for SD-JWT credentials
 * - Verifies DeviceAuth for mDoc credentials
 * - Verifies proof for JWT VP credentials
 *
 * @see VerifyHolderBindingArgs
 * @see HolderBindingResult
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyHolderBindingCommand", exact = true)
@JsExportCompat
interface VerifyHolderBindingCommand : ServiceCommand<VerifyHolderBindingArgs, HolderBindingResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.verifybinding"
    }
}

/**
 * Command service interface for verifying holder binding.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyHolderBindingCommandService", exact = true)
interface VerifyHolderBindingCommandService {
    suspend fun verifyHolderBinding(args: VerifyHolderBindingArgs): IdkResult<HolderBindingResult, IdkError>
}

/**
 * Command for building an OpenID4VP authorization request URI.
 *
 * Builds a URI string that can be:
 * - Encoded as QR code
 * - Used as deep link
 * - Sent via redirect
 *
 * Supports schemes: openid4vp://, openid://, haip://
 *
 * @see BuildAuthorizationRequestUriArgs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BuildAuthorizationRequestUriCommand", exact = true)
@JsExportCompat
interface BuildAuthorizationRequestUriCommand : ServiceCommand<BuildAuthorizationRequestUriArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.builduri"
    }
}

/**
 * Command service interface for building authorization request URIs.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BuildAuthorizationRequestUriCommandService", exact = true)
interface BuildAuthorizationRequestUriCommandService {
    suspend fun buildAuthorizationRequestUri(args: BuildAuthorizationRequestUriArgs): IdkResult<StringResult, IdkError>
}

// ============================================================================
// JAR (JWT-secured Authorization Request) Commands - RFC 9101
// ============================================================================

/**
 * Command for creating a signed authorization request (JAR - RFC 9101).
 *
 * This command creates an authorization request and signs it as a JWT.
 * The signed request can then be:
 * - Included directly in the URI via the `request` parameter
 * - Pushed to a PAR endpoint and referenced via `request_uri`
 *
 * OpenID4VP 1.0 with JAR:
 * - Creates the authorization request with DCQL query
 * - Signs the request as a JWT using the verifier's private key
 * - Returns both the original request and the signed JAR token
 *
 * @see CreateSignedAuthorizationRequestArgs
 * @see SignedAuthorizationRequestResult
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateSignedAuthorizationRequestCommand", exact = true)
@JsExportCompat
interface CreateSignedAuthorizationRequestCommand : ServiceCommand<CreateSignedAuthorizationRequestArgs, SignedAuthorizationRequestResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.signrequest"
    }
}

/**
 * Command service interface for creating signed authorization requests.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateSignedAuthorizationRequestCommandService", exact = true)
interface CreateSignedAuthorizationRequestCommandService {
    suspend fun createSignedAuthorizationRequest(args: CreateSignedAuthorizationRequestArgs): IdkResult<SignedAuthorizationRequestResult, IdkError>
}

// ============================================================================
// Response Code Protection Commands - OpenID4VP 1.0 Section 14.3.3
// ============================================================================

/**
 * Command for handling a direct_post authorization response and generating a response_code.
 *
 * OpenID4VP 1.0 Section 14.3.3 - Protection of Authorization Response Data:
 *
 * When the RP backend receives an authorization response via direct_post:
 * 1. Parse and validate the authorization response
 * 2. Generate a unique, cryptographically secure response_code
 * 3. Store the validated response associated with the response_code
 * 4. Return redirect_uri with response_code to the wallet
 *
 * This protects the authorization response data by:
 * - Preventing VP tokens from being exposed in URLs/logs
 * - Making response_codes single-use to prevent replay attacks
 * - Setting expiration on response_codes to limit attack window
 *
 * @see HandleDirectPostResponseArgs
 * @see DirectPostHandledResponse
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandleDirectPostResponseCommand", exact = true)
@JsExportCompat
interface HandleDirectPostResponseCommand : ServiceCommand<HandleDirectPostResponseArgs, DirectPostHandledResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.directpost"
    }
}

/**
 * Command service interface for handling direct_post responses.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HandleDirectPostResponseCommandService", exact = true)
interface HandleDirectPostResponseCommandService {
    suspend fun handleDirectPostResponse(args: HandleDirectPostResponseArgs): IdkResult<DirectPostHandledResponse, IdkError>
}

/**
 * Command for retrieving an authorization response using a response_code.
 *
 * OpenID4VP 1.0 Section 14.3.3 - Protection of Authorization Response Data:
 *
 * When the user agent is redirected to the RP frontend with a response_code:
 * 1. The frontend extracts the response_code from the URL
 * 2. Uses this command to retrieve the validated authorization response
 * 3. The response_code is invalidated after successful retrieval (single-use)
 *
 * Error cases:
 * - response_code not found: INVALID_RESPONSE_CODE
 * - response_code expired: EXPIRED_RESPONSE_CODE
 * - response_code already used: USED_RESPONSE_CODE
 *
 * @see RetrieveAuthorizationResponseArgs
 * @see RetrievedAuthorizationResponse
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrieveAuthorizationResponseCommand", exact = true)
@JsExportCompat
interface RetrieveAuthorizationResponseCommand : ServiceCommand<RetrieveAuthorizationResponseArgs, RetrievedAuthorizationResponse> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.verifier.retrieve"
    }
}

/**
 * Command service interface for retrieving authorization responses.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrieveAuthorizationResponseCommandService", exact = true)
interface RetrieveAuthorizationResponseCommandService {
    suspend fun retrieveAuthorizationResponse(args: RetrieveAuthorizationResponseArgs): IdkResult<RetrievedAuthorizationResponse, IdkError>
}
