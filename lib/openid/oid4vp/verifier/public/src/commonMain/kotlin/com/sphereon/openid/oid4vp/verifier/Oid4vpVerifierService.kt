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

package com.sphereon.openid.oid4vp.verifier

import com.sphereon.openid.oid4vp.verifier.store.ResponseCodeStore
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.openid.oid4vp.verifier.store.ClientMetadataConfigurationStore
import com.sphereon.openid.oid4vp.verifier.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriHandler
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * OpenID4VP 1.0 RP (Verifier) Service
 *
 * Main service interface for Relying Party (Verifier) operations in OpenID4VP.
 *
 * This service implements the verifier side of OpenID4VP 1.0 Final:
 * - Create authorization requests with DCQL queries (NOT Presentation Exchange!)
 * - Parse and validate authorization responses with VP tokens
 * - Verify holder binding in credentials
 *
 * CRITICAL: OpenID4VP 1.0 Final uses DCQL, NOT Presentation Exchange!
 * - Request: dcql_query parameter
 * - Response: vp_token (self-descriptive credentials)
 * - NO presentation_definition, NO presentation_submission
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpRpService", exact = true)
//@JsExportCompat
interface Oid4vpVerifierService :
    CreateAuthorizationRequestCommandService,
    ParseAuthorizationResponseCommandService,
    ValidateAuthorizationResponseCommandService,
    VerifyHolderBindingCommandService,
    BuildAuthorizationRequestUriCommandService,
    CreateSignedAuthorizationRequestCommandService,
    HandleDirectPostResponseCommandService,
    RetrieveAuthorizationResponseCommandService {

    /**
     * Access to individual commands for advanced use cases
     */
    val commands: Commands

    /**
     * Access to the response code store for managing stored responses
     */
    val responseCodeStore: ResponseCodeStore

    /**
     * Access to the authorization session store (Universal OID4VP compatible session tracking).
     */
    val authorizationSessionStore: AuthorizationSessionStore

    /**
     * Access to stored DCQL query configurations referenced by `query_id`.
     */
    val dcqlQueryConfigurationStore: DcqlQueryConfigurationStore

    /**
     * Access to stored client metadata configurations.
     */
    val clientMetadataConfigurationStore: ClientMetadataConfigurationStore

    /**
     * Handler for request_uri fetches (on-demand JAR generation).
     */
    val requestUriHandler: RequestUriHandler

    /**
     * Commands interface exposing all RP commands
     */
    interface Commands {
        val createAuthorizationRequest: CreateAuthorizationRequestCommand
        val parseAuthorizationResponse: ParseAuthorizationResponseCommand
        val validateAuthorizationResponse: ValidateAuthorizationResponseCommand
        val verifyHolderBinding: VerifyHolderBindingCommand
        val buildAuthorizationRequestUri: BuildAuthorizationRequestUriCommand
        val createSignedAuthorizationRequest: CreateSignedAuthorizationRequestCommand

        /**
         * Response code protection commands - OpenID4VP 1.0 Section 14.3.3
         */
        val handleDirectPostResponse: HandleDirectPostResponseCommand
        val retrieveAuthorizationResponse: RetrieveAuthorizationResponseCommand
    }
}
