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
 *
 */

package com.sphereon.openid.oid4vp.auth.model

import kotlinx.serialization.Serializable

/**
 * Configuration for the OID4VP Authentication Bridge.
 *
 * This configuration can be loaded from IDK's ConfigService using the prefix "oid4vp.auth-bridge".
 *
 * Example properties:
 * ```
 * oid4vp.auth-bridge.default-query-id=my-query-id
 * oid4vp.auth-bridge.session-ttl-seconds=300
 * oid4vp.auth-bridge.auto-create-user=true
 * oid4vp.auth-bridge.user-identifier-claim-path=sub
 * oid4vp.auth-bridge.universal-api.base-url=http://localhost:8080/oid4vp
 * oid4vp.auth-bridge.universal-api.connection-timeout-ms=30000
 * oid4vp.auth-bridge.universal-api.request-timeout-ms=120000
 * oid4vp.auth-bridge.user-api.base-url=http://localhost:8080/api/users/v1
 * oid4vp.auth-bridge.user-api.connection-timeout-ms=30000
 * oid4vp.auth-bridge.user-api.request-timeout-ms=60000
 * ```
 *
 * @property defaultQueryId Default query ID to use when not specified in requests.
 *                          If null, clients must always provide a queryId in their requests.
 * @property sessionTtlSeconds Session time-to-live in seconds (default: 300 = 5 minutes).
 * @property autoCreateUser Whether to automatically create users when not found (default: true).
 * @property requireReconciliation Whether to require identity reconciliation when user not found (default: false).
 *                                 When true and user not found: session transitions to IDV_REQUIRED instead of auto-creating or erroring.
 * @property userIdentifierClaimPath JSON path to extract user identifier from credentials (default: "sub").
 * @property universalApiBaseUrl Base URL of the Universal OID4VP REST API.
 * @property universalApiConnectionTimeoutMs Connection timeout in milliseconds for Universal API calls (default: 30000).
 * @property universalApiRequestTimeoutMs Request timeout in milliseconds for Universal API calls (default: 120000).
 * @property userApiBaseUrl Base URL of the VDX User Microservice REST API.
 * @property userApiConnectionTimeoutMs Connection timeout in milliseconds for User API calls (default: 30000).
 * @property userApiRequestTimeoutMs Request timeout in milliseconds for User API calls (default: 60000).
 */
@Serializable
data class Oid4vpAuthBridgeConfig(
    val defaultQueryId: String? = null,

    /**
     * JSON-serialized DCQL query to use inline when no query_id is configured or the query is not
     * found in the store. This bypasses the DcqlQueryConfigurationStore entirely.
     */
    val defaultDcqlQuery: String? = null,

    /**
     * Client ID for OID4VP authorization requests (verifier identity).
     */
    val clientId: String? = null,

    /**
     * Response URI where the wallet sends the VP response (direct_post).
     * Typically the auth bridge's own URL + /oid4vp/backend/auth/requests/{id}/response.
     */
    val responseUri: String? = null,

    val sessionTtlSeconds: Long = DEFAULT_SESSION_TTL_SECONDS,

    val autoCreateUser: Boolean = DEFAULT_AUTO_CREATE_USER,

    val requireReconciliation: Boolean = DEFAULT_REQUIRE_RECONCILIATION,

    val userIdentifierClaimPath: String = DEFAULT_USER_IDENTIFIER_CLAIM_PATH,

    val universalApiBaseUrl: String = DEFAULT_UNIVERSAL_API_BASE_URL,

    val universalApiConnectionTimeoutMs: Long = DEFAULT_UNIVERSAL_API_CONNECTION_TIMEOUT_MS,

    val universalApiRequestTimeoutMs: Long = DEFAULT_UNIVERSAL_API_REQUEST_TIMEOUT_MS,

    val userApiBaseUrl: String = DEFAULT_USER_API_BASE_URL,

    val userApiConnectionTimeoutMs: Long = DEFAULT_USER_API_CONNECTION_TIMEOUT_MS,

    val userApiRequestTimeoutMs: Long = DEFAULT_USER_API_REQUEST_TIMEOUT_MS
) {
    companion object {
        const val CONFIG_PREFIX = "oid4vp.auth-bridge"

        /**
         * Default session TTL in seconds (5 minutes).
         */
        const val DEFAULT_SESSION_TTL_SECONDS: Long = 300

        /**
         * Default claim path to extract user identifier.
         */
        const val DEFAULT_USER_IDENTIFIER_CLAIM_PATH: String = "sub"

        /**
         * Default value for auto-create user.
         */
        const val DEFAULT_AUTO_CREATE_USER: Boolean = true

        /**
         * Default value for require reconciliation.
         */
        const val DEFAULT_REQUIRE_RECONCILIATION: Boolean = false

        /**
         * Default base URL for Universal OID4VP API.
         */
        const val DEFAULT_UNIVERSAL_API_BASE_URL: String = "http://localhost:8080/oid4vp"

        /**
         * Default connection timeout for Universal API in milliseconds (30 seconds).
         */
        const val DEFAULT_UNIVERSAL_API_CONNECTION_TIMEOUT_MS: Long = 30_000L

        /**
         * Default request timeout for Universal API in milliseconds (2 minutes).
         */
        const val DEFAULT_UNIVERSAL_API_REQUEST_TIMEOUT_MS: Long = 120_000L

        /**
         * Default base URL for User Microservice API.
         */
        const val DEFAULT_USER_API_BASE_URL: String = "http://localhost:8080/api/users/v1"

        /**
         * Default connection timeout for User API in milliseconds (30 seconds).
         */
        const val DEFAULT_USER_API_CONNECTION_TIMEOUT_MS: Long = 30_000L

        /**
         * Default request timeout for User API in milliseconds (60 seconds).
         */
        const val DEFAULT_USER_API_REQUEST_TIMEOUT_MS: Long = 60_000L
    }
}
