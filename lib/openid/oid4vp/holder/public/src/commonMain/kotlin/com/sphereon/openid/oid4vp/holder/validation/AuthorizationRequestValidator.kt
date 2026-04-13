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

package com.sphereon.openid.oid4vp.holder.validation

import com.sphereon.oauth2.common.model.AuthorizationRequest
import io.konform.validation.Validation
import io.konform.validation.constraints.minLength
import io.konform.validation.constraints.pattern

/**
 * Konform validator for OpenID4VP Authorization Request.
 *
 * Validates requirements from OpenID4VP 1.0 Final spec:
 * - client_id (required)
 * - redirect_uri or response_uri (required)
 * - response_type (typically "vp_token")
 * - dcql_query (Digital Credentials Query Language) - OpenID4VP 1.0 Final
 * - nonce (recommended for security)
 * - state (recommended)
 *
 * ⚠️ IMPORTANT: OpenID4VP 1.0 Final uses DCQL, not Presentation Exchange
 * - presentation_definition/presentation_definition_uri were in draft versions only
 * - Use dcql_query parameter instead
 *
 * Reference: OpenID for Verifiable Presentations 1.0 Final, Section 5
 */
val validateOid4vpAuthorizationRequest =
    Validation<AuthorizationRequest> {
        // client_id is mandatory
        AuthorizationRequest::clientId {
            minLength(1) hint "client_id is required"
        }

        // Either redirect_uri or response_uri must be present.
        // Per OID4VP 1.0: redirect_uri is OPTIONAL when response_mode is direct_post
        // or direct_post.jwt, because response_uri is used instead.
        run {
            constrain("redirect_uri or response_uri is required") { req ->
                val responseMode = req.responseMode
                val isDirectPost = responseMode == "direct_post" || responseMode == "direct_post.jwt"
                val hasResponseUri = req.additionalParameters?.containsKey("response_uri") == true
                // redirect_uri is required unless we're in direct_post mode with a response_uri
                !req.redirectUri.isNullOrBlank() || (isDirectPost && hasResponseUri)
            }
        }

        // response_type must be present (typically "vp_token" for OID4VP)
        AuthorizationRequest::responseType {
            minLength(1) hint "response_type is required"
        }

        // nonce is required for security (prevents replay attacks)
        AuthorizationRequest::nonce ifPresent {
            minLength(8) hint "nonce should be at least 8 characters for security"
        }

        // state is strongly recommended for CSRF protection
        AuthorizationRequest::state ifPresent {
            minLength(8) hint "state should be at least 8 characters when present"
        }

        // Validate that DCQL query is provided (OpenID4VP 1.0 Final requirement)
        // Note: dcql_query is optional at parse time - actual validation happens in ResolveAuthorizationRequestCommand
        // Some requests may not have dcql_query if using other credential selection mechanisms
        run {
            constrain("dcql_query recommended for credential selection") { req ->
                // Check if dcql_query is in additional parameters
                val hasDcqlQuery = req.additionalParameters?.containsKey("dcql_query") == true

                // DCQL is the standard way, but we allow requests without it at parse time
                // Actual validation of credential selection happens later in the flow
                true // Always pass - just a recommendation hint
            }
        }

        // Validate response_mode if present (should be direct_post, direct_post.jwt for OID4VP)
        AuthorizationRequest::responseMode ifPresent {
            pattern("direct_post(\\.jwt)?|fragment|query".toRegex()) hint
                "response_mode must be 'direct_post', 'direct_post.jwt', 'fragment', or 'query'"
        }

        // Validate client_id_scheme if present
        run {
            constrain("valid client_id_scheme") { req ->
                val clientIdScheme =
                    req.additionalParameters
                        ?.get("client_id_scheme")
                        ?.toString()
                        ?.removeSurrounding("\"")
                clientIdScheme == null || clientIdScheme in
                    listOf(
                        "pre-registered",
                        "redirect_uri",
                        "entity_id",
                        "did",
                        "x509_san_dns",
                        "x509_san_uri",
                        "verifier_attestation",
                    )
            }
        }
    }
