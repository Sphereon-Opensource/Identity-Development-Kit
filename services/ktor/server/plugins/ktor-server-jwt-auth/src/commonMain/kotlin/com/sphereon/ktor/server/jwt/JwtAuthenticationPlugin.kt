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
 *
 */

package com.sphereon.ktor.server.jwt

import com.sphereon.di.context.IdentityResolutionInput
import com.sphereon.di.session.SessionContext
import com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.JwtValidationErrorType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Call attribute key under which the resolved [SessionContext] is stored.
 *
 * Route handlers retrieve the context via
 * `call.attributes[SessionContextAttributeKey]`.
 */
val SessionContextAttributeKey: AttributeKey<SessionContext> = AttributeKey("SessionContext")

/**
 * Ktor [io.ktor.server.application.ApplicationPlugin] that chains
 * JWT validation, identity resolution, and session-context construction.
 *
 * Flow per request:
 *  1. If the path matches an [JwtAuthenticationConfig.anonymousPaths] glob, skip token
 *     validation and resolve an anonymous session.
 *  2. Read the bearer token from the `Authorization` header (or from the configured
 *     cookie when the header is absent).
 *  3. Invoke [com.sphereon.oauth2.jwt.validation.JwtValidationService.validateAccessToken].
 *  4. Pass the validated claims plus headers into
 *     [com.sphereon.di.context.IdentityResolutionPipeline].
 *  5. Build a [SessionContext] via
 *     [com.sphereon.di.session.SessionContextFactory] and stash it on the call.
 *
 * On validation failure the plugin halts the pipeline with 401 and an RFC 6750
 * `WWW-Authenticate: Bearer error="invalid_token"` header. Error descriptions
 * are terse category labels so internal details stay out of the wire.
 *
 * The three IDK services are resolved per request through
 * [JwtAuthenticationConfig.jwtValidationService],
 * [JwtAuthenticationConfig.identityResolutionPipeline], and
 * [JwtAuthenticationConfig.sessionContextFactory]. The defaults assume
 * [com.sphereon.ktor.server.inject.KotlinInjectPlugin] is installed BEFORE
 * this plugin so the per-call session graph is available.
 *
 * Usage with kotlin-inject (common case, no overrides needed):
 * ```
 * install(KotlinInjectPlugin) { appGraph = myAppGraph }
 * install(JwtAuthentication) {
 *     requireAuth = true
 *     anonymousPaths = listOf("/health", "/ready", "/api/v1/public-prefix")
 * }
 * ```
 *
 * Usage without kotlin-inject (supply your own resolvers):
 * ```
 * install(JwtAuthentication) {
 *     jwtValidationService = { myValidator }
 *     identityResolutionPipeline = { myPipeline }
 *     sessionContextFactory = { myFactory }
 * }
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
val JwtAuthentication =
    createApplicationPlugin(
        name = "JwtAuthentication",
        createConfiguration = ::JwtAuthenticationConfig,
    ) {
        val config = pluginConfig
        val requireAuth = config.requireAuth
        val anonymousPaths = config.anonymousPaths
        val cookieName = config.cookieName
        val expectedAudience = config.expectedAudience

        onCall { call ->
            val path = call.request.path()

            if (matchesAnonymousPath(path, anonymousPaths)) {
                bindAnonymousSession(call, config.identityResolutionPipeline(call), config.sessionContextFactory(call))
                return@onCall
            }

            val token = extractBearerToken(call, cookieName)

            if (token == null) {
                if (!requireAuth) {
                    bindAnonymousSession(
                        call,
                        config.identityResolutionPipeline(call),
                        config.sessionContextFactory(call),
                    )
                    return@onCall
                }
                respondUnauthorized(call, "Missing bearer token")
                return@onCall
            }

            val options = AccessTokenValidationOptions(expectedAudience = expectedAudience)
            val validationResult = config.jwtValidationService(call).validateAccessToken(token, options)
            if (validationResult.isErr) {
                respondUnauthorized(call, describeError(validationResult.error))
                return@onCall
            }
            val validated = validationResult.value

            val input =
                IdentityResolutionInput(
                    headers = collectHeaders(call),
                    tokenClaims = validated.claims,
                    hostHeader = call.request.header(HttpHeaders.Host),
                    pathPrefix = path,
                )
            val resolution = config.identityResolutionPipeline(call).resolve(input)

            val sessionId = Uuid.random().toString()
            val sessionContext = config.sessionContextFactory(call).create(sessionId, resolution, emptyMap())
            call.attributes.put(SessionContextAttributeKey, sessionContext)
        }
    }

@OptIn(ExperimentalUuidApi::class)
private suspend fun bindAnonymousSession(
    call: ApplicationCall,
    pipeline: com.sphereon.di.context.IdentityResolutionPipeline,
    factory: com.sphereon.di.session.SessionContextFactory,
) {
    val resolution =
        pipeline.resolve(
            IdentityResolutionInput(
                headers = collectHeaders(call),
                tokenClaims = null,
                hostHeader = call.request.header(HttpHeaders.Host),
                pathPrefix = call.request.path(),
            ),
        )
    val sessionId = Uuid.random().toString()
    call.attributes.put(SessionContextAttributeKey, factory.create(sessionId, resolution, emptyMap()))
}

private fun collectHeaders(call: ApplicationCall): Map<String, String> {
    val headers = call.request.headers
    val result = mutableMapOf<String, String>()
    for (name in headers.names()) {
        val value = headers[name] ?: continue
        result[name] = value
    }
    return result
}

/**
 * Extract the access token from the `Authorization` header, accepting both `Bearer` (RFC 6750)
 * and `DPoP` (RFC 9449 §7.1) schemes. RFC 9449 mandates that DPoP-bound access tokens MUST be
 * sent using the `DPoP` scheme — refusing it here breaks any flow that obtained a
 * sender-constrained token at the AS.
 */
private fun extractBearerToken(
    call: ApplicationCall,
    cookieName: String?
): String? {
    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader != null) {
        val trimmed = authHeader.trim()
        val space = trimmed.indexOf(' ')
        if (space > 0) {
            val scheme = trimmed.substring(0, space)
            if (scheme.equals("Bearer", ignoreCase = true) || scheme.equals("DPoP", ignoreCase = true)) {
                val candidate = trimmed.substring(space + 1).trim()
                if (candidate.isNotEmpty()) {
                    return candidate
                }
            }
        }
    }
    if (cookieName != null) {
        val cookieValue = call.request.cookies[cookieName]?.trim()
        if (!cookieValue.isNullOrEmpty()) {
            return cookieValue
        }
    }
    return null
}

private suspend fun respondUnauthorized(
    call: ApplicationCall,
    errorDescription: String
) {
    val sanitized = errorDescription.replace('"', '\'')
    call.response.header(
        HttpHeaders.WWWAuthenticate,
        """Bearer error="invalid_token", error_description="$sanitized"""",
    )
    call.respond(HttpStatusCode.Unauthorized)
}

private val ERROR_DESCRIPTIONS: Map<JwtValidationErrorType, String> =
    mapOf(
        JwtValidationErrorType.MISSING_TOKEN to "Missing bearer token",
        JwtValidationErrorType.INVALID_TOKEN_FORMAT to "Malformed token",
        JwtValidationErrorType.SIGNATURE_INVALID to "Invalid signature",
        JwtValidationErrorType.TOKEN_EXPIRED to "Token expired",
        JwtValidationErrorType.TOKEN_NOT_YET_VALID to "Token not yet valid",
        JwtValidationErrorType.UNTRUSTED_ISSUER to "Invalid issuer",
        JwtValidationErrorType.INVALID_AUDIENCE to "Invalid audience",
        JwtValidationErrorType.MISSING_REQUIRED_CLAIM to "Missing required claim",
        JwtValidationErrorType.JWKS_UNAVAILABLE to "Key set unavailable",
        JwtValidationErrorType.KEY_NOT_FOUND to "Signing key not found",
        JwtValidationErrorType.ALGORITHM_NOT_ALLOWED to "Algorithm not allowed",
        JwtValidationErrorType.IDP_CONFIGURATION_ERROR to "IdP misconfigured",
        JwtValidationErrorType.DISCOVERY_FAILED to "IdP discovery failed",
        JwtValidationErrorType.VALIDATION_ERROR to "Token validation failed",
    )

private fun describeError(error: JwtValidationError): String = ERROR_DESCRIPTIONS[error.type] ?: "Token validation failed"

internal fun matchesAnonymousPath(
    path: String,
    patterns: List<String>
): Boolean {
    if (patterns.isEmpty()) {
        return false
    }
    return patterns.any { matchesGlob(path, it) }
}

internal fun matchesGlob(
    path: String,
    pattern: String
): Boolean =
    when {
        // /foo/double-star — prefix match across any number of segments.
        pattern.endsWith("/**") -> {
            val prefix = pattern.dropLast(DOUBLE_STAR_TAIL_LENGTH)
            path == prefix || path.startsWith("$prefix/")
        }

        // double-star alone — match anything.
        pattern == "**" -> {
            true
        }

        // single-star — single-segment wildcard within the pattern.
        pattern.contains('*') -> {
            Regex("^${globToRegex(pattern)}$").matches(path)
        }

        else -> {
            path == pattern
        }
    }

private fun globToRegex(pattern: String): String =
    buildString(pattern.length * 2) {
        for (ch in pattern) {
            when (ch) {
                '*' -> {
                    append("[^/]*")
                }

                '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\', '?' -> {
                    append('\\').append(ch)
                }

                else -> {
                    append(ch)
                }
            }
        }
    }

private const val DOUBLE_STAR_TAIL_LENGTH = 3
