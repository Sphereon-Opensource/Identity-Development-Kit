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

package com.sphereon.di.context

import kotlinx.serialization.json.JsonElement

/**
 * Principal type classification for policy decisions.
 *
 * Distinguishes between human users, machine workloads, internal services,
 * and unauthenticated callers.
 */
enum class PrincipalType {
    /** Human end-user authenticated via OIDC/OAuth2 */
    USER,
    /** Machine-to-machine client (e.g., OAuth2 client_credentials) */
    WORKLOAD,
    /** Internal service identity (e.g., gRPC peer) */
    SERVICE,
    /** No authentication provided */
    ANONYMOUS
}

/**
 * Tracks how an identity value was resolved, enabling security policy
 * decisions about trust level.
 */
enum class ResolutionSource {
    /** Resolved from a verified JWT/OIDC token */
    TOKEN,
    /** Resolved from an HTTP header (e.g., X-Tenant-Id) */
    HEADER,
    /** Resolved from the Host header */
    HOST,
    /** Resolved from the URL path prefix */
    PATH,
    /** Resolved from a system default */
    DEFAULT,
    /** Resolution source could not be determined */
    UNKNOWN
}

/**
 * Input data for the identity resolution pipeline.
 *
 * Aggregates all available identity signals from an incoming request.
 *
 * @property headers HTTP headers from the request (header name → value)
 * @property tokenClaims Parsed JWT claims if a bearer token was present
 * @property hostHeader The Host header value if available
 * @property pathPrefix URL path prefix if available (for path-based tenant routing)
 */
data class IdentityResolutionInput(
    val headers: Map<String, String> = emptyMap(),
    val tokenClaims: Map<String, JsonElement>? = null,
    val hostHeader: String? = null,
    val pathPrefix: String? = null
)

/**
 * Result of identity resolution, containing the resolved tenant, principal,
 * and metadata about how they were determined.
 *
 * @property tenantId The resolved tenant identifier, or null if resolution failed
 * @property principalId The resolved principal identifier, or null if anonymous
 * @property principalType Classification of the resolved principal
 * @property metadata Additional resolution metadata for auditing and policy
 */
data class IdentityResolutionResult(
    val tenantId: String?,
    val principalId: String?,
    val principalType: PrincipalType,
    val metadata: IdentityMetadata
)

/**
 * Metadata about identity resolution for auditing, debugging, and policy enrichment.
 *
 * @property issuer Token issuer (iss claim) if resolved from token
 * @property audience Token audience (aud claim) if resolved from token
 * @property resolvedFrom How the primary identity was resolved
 * @property additionalClaims Extra claims extracted during resolution
 * @property headerTenantId Tenant ID from X-Tenant-Id header (even if token was used as primary)
 * @property headerPrincipalId Principal ID from X-Principal-Id header (even if token was used as primary)
 */
data class IdentityMetadata(
    val issuer: String? = null,
    val audience: String? = null,
    val resolvedFrom: ResolutionSource = ResolutionSource.UNKNOWN,
    val additionalClaims: Map<String, JsonElement> = emptyMap(),
    val headerTenantId: String? = null,
    val headerPrincipalId: String? = null
)

/**
 * Pipeline contract for resolving identity from request data.
 *
 * IDK provides a simple default implementation that runs all registered
 * resolvers in priority order. EDK replaces this with a security-aware
 * pipeline that enforces token-first resolution strategies.
 */
interface IdentityResolutionPipeline {
    /**
     * Resolve identity from the provided input signals.
     *
     * @param input Aggregated identity signals from the incoming request
     * @return Resolution result with tenant, principal, type, and metadata
     */
    suspend fun resolve(input: IdentityResolutionInput): IdentityResolutionResult
}
