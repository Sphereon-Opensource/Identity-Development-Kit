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

package com.sphereon.core.api.conf

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for interpolating property values containing placeholders.
 *
 * Supported patterns:
 * - `${VAR}` - Simple substitution
 * - `${VAR:default}` - With default value
 * - `${env:VAR}` - Explicit env source
 * - `${scope:key}` - Explicit scope prefix (app, tenant, principal)
 * - `${secret:provider:path}` - Secret reference (resolved by SecretProvider)
 * - `${db.${env}}` - Recursive (max depth configurable)
 *
 * Protection-aware interpolation:
 * When using a [ProtectedPropertyResolver], scope-prefixed interpolations (e.g., `${app:db.password}`)
 * are checked for PROTECTED status. If a property is marked PROTECTED at a higher scope,
 * lower scopes cannot interpolate its value.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PropertyInterpolator", exact = true)
interface PropertyInterpolator {
    /**
     * Interpolate placeholders in a value string.
     *
     * @param value The value containing placeholders
     * @param resolver Property resolver for looking up referenced values
     * @return The interpolated string, or error if resolution fails
     */
    suspend fun interpolate(value: String, resolver: PropertyResolver): IdkResult<String, IdkError>

    /**
     * Interpolate placeholders with scope-aware protection checking.
     *
     * When the resolver is a [ProtectedPropertyResolver], this method checks
     * protection status BEFORE reading values. If a scope-prefixed interpolation
     * (e.g., `${app:db.password}`) references a PROTECTED property, and the
     * requesting scope is lower than where the property is protected, the
     * interpolation is denied without exposing the value.
     *
     * @param value The value containing placeholders
     * @param resolver Property resolver for looking up referenced values
     * @param requestingScope The scope level requesting the interpolation
     * @return The interpolated string, or error if resolution fails or protection denies access
     */
    suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel
    ): IdkResult<String, IdkError>

    /**
     * Interpolate placeholders with full control over resolution options.
     *
     * @param value The value containing placeholders
     * @param resolver Property resolver for looking up referenced values
     * @param requestingScope The scope level requesting the interpolation
     * @param maxDepth Maximum recursion depth for nested interpolations (overrides default)
     * @param resolveSecrets Whether to resolve ${secret:...} references
     * @return The interpolated string, or error if resolution fails
     */
    suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        resolveSecrets: Boolean
    ): IdkResult<String, IdkError> = interpolate(value, resolver, requestingScope) // Default delegates

    /**
     * Check if a value contains placeholders that need interpolation.
     *
     * @param value The value to check
     * @return True if the value contains `${...}` patterns
     */
    fun containsPlaceholders(value: String): Boolean

    /**
     * Check if a value contains a secret reference.
     *
     * @param value The value to check
     * @return True if the value contains `${secret:...}` pattern
     */
    fun isSecretReference(value: String): Boolean

    /**
     * Parse placeholders from a value string.
     *
     * @param value The value to parse
     * @return List of parsed placeholder tokens
     */
    fun parsePlaceholders(value: String): List<PlaceholderToken>
}

/**
 * Represents a parsed placeholder token.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PlaceholderToken", exact = true)
@CoverageExcludedDataClass
data class PlaceholderToken(
    val fullMatch: String,
    val type: PlaceholderType,
    val key: String,
    val defaultValue: String?,
    val provider: String?,
    val path: String?
) {
    companion object {
        fun simple(match: String, key: String, defaultValue: String? = null) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SIMPLE,
            key = key,
            defaultValue = defaultValue,
            provider = null,
            path = null
        )

        fun env(match: String, key: String, defaultValue: String? = null) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.ENV,
            key = key,
            defaultValue = defaultValue,
            provider = null,
            path = null
        )

        fun scope(match: String, scope: String, key: String, defaultValue: String? = null) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SCOPE,
            key = key,
            defaultValue = defaultValue,
            provider = scope,
            path = null
        )

        fun secret(match: String, provider: String, path: String, key: String? = null) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SECRET,
            key = key ?: path,
            defaultValue = null,
            provider = provider,
            path = path
        )
    }
}

/**
 * Types of placeholder patterns.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PlaceholderType", exact = true)
enum class PlaceholderType {
    /** Simple `${key}` or `${key:default}` */
    SIMPLE,
    /** Explicit `${env:KEY}` */
    ENV,
    /** Explicit `${scope:key}` where scope is app/tenant/principal */
    SCOPE,
    /** Secret `${secret:provider:path}` or `${secret:provider:path:key}` */
    SECRET
}

/**
 * Default implementation of PropertyInterpolator.
 *
 * Supports protection-aware interpolation when used with a [ProtectedPropertyResolver].
 * When protection checking is enabled, PROTECTED properties at higher scopes cannot
 * be interpolated from lower scopes.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPropertyInterpolator", exact = true)
class DefaultPropertyInterpolator(
    private val maxDepth: Int = 10,
    private val secretResolver: SecretResolver? = null
) : PropertyInterpolator {

    // Simple pattern for non-nested placeholders (no nested ${} inside)
    // Note: closing brace must be escaped for JS/wasmJs regex Unicode mode compatibility
    private val simplePlaceholderPattern = Regex("""\$\{([^{}]+)\}""")
    private val envPattern = Regex("""^env:(.+)$""")
    private val scopePattern = Regex("""^(app|tenant|principal):(.+)$""")
    private val secretPattern = Regex("""^secret:([^:]+):([^:]+)(?::(.+))?$""")
    private val defaultValuePattern = Regex("""^([^:]+):(.*)$""")

    override suspend fun interpolate(value: String, resolver: PropertyResolver): IdkResult<String, IdkError> {
        return interpolateRecursive(value, resolver, null, null, mutableSetOf(), 0, maxDepth, true)
    }

    override suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel
    ): IdkResult<String, IdkError> {
        val protectedResolver = resolver as? ProtectedPropertyResolver
        return interpolateRecursive(value, resolver, protectedResolver, requestingScope, mutableSetOf(), 0, maxDepth, true)
    }

    override suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        resolveSecrets: Boolean
    ): IdkResult<String, IdkError> {
        val protectedResolver = resolver as? ProtectedPropertyResolver
        val effectiveMaxDepth = maxDepth ?: this.maxDepth
        return interpolateRecursive(value, resolver, protectedResolver, requestingScope, mutableSetOf(), 0, effectiveMaxDepth, resolveSecrets)
    }

    private suspend fun interpolateRecursive(
        value: String,
        resolver: PropertyResolver,
        protectedResolver: ProtectedPropertyResolver?,
        requestingScope: ConfigLevel?,
        visited: MutableSet<String>,
        depth: Int,
        effectiveMaxDepth: Int = maxDepth,
        resolveSecrets: Boolean = true
    ): IdkResult<String, IdkError> {
        if (depth > effectiveMaxDepth) {
            return Err(ConfigErrors.maxDepthExceeded(value, effectiveMaxDepth))
        }

        if (!containsPlaceholders(value)) {
            return Ok(value)
        }

        var result = value

        // Keep resolving innermost placeholders until no more
        while (containsPlaceholders(result)) {
            // Parse only simple (non-nested) placeholders - innermost ones
            val tokens = parseSimplePlaceholders(result)
            if (tokens.isEmpty()) {
                // No simple placeholders found but containsPlaceholders is true
                // This means malformed placeholders - break to avoid infinite loop
                break
            }

            var madeProgress = false
            for (token in tokens) {
                // Track the token key for circular reference detection
                if (visited.contains(token.key)) {
                    return Err(ConfigErrors.circularReference(token.key, visited.toList() + token.key))
                }

                visited.add(token.key)

                // Check protection BEFORE resolving for scope-prefixed references
                if (protectedResolver != null && requestingScope != null && token.type == PlaceholderType.SCOPE) {
                    val canInterpolate = protectedResolver.canInterpolateProperty(token.key, requestingScope)
                    if (canInterpolate.isErr) {
                        // Protection denied - do not expose value
                        visited.remove(token.key)
                        return Err(canInterpolate.error)
                    }
                }

                val resolvedValue = when (token.type) {
                    PlaceholderType.SIMPLE -> resolveSimple(token, resolver, protectedResolver, requestingScope)
                    PlaceholderType.ENV -> resolveEnv(token, resolver)
                    PlaceholderType.SCOPE -> resolveScope(token, resolver, protectedResolver, requestingScope)
                    PlaceholderType.SECRET -> {
                        // Skip secret resolution if resolveSecrets is false
                        if (!resolveSecrets) {
                            // Keep the original placeholder unresolved
                            visited.remove(token.key)
                            result = result // No change
                            continue
                        }
                        resolveSecret(token, requestingScope, resolver)
                    }
                }

                if (resolvedValue.isErr) {
                    // Check if this is a protection error - those should never fall through to defaults
                    // as that would reveal information about protected property existence
                    val isProtectionError = resolvedValue.error.code == "ILLEGAL_ARGUMENT_ERROR" &&
                        (resolvedValue.error.message.defaultMessage.contains("PROTECTED") ||
                         resolvedValue.error.message.defaultMessage.contains("FINAL"))

                    if (isProtectionError) {
                        visited.remove(token.key)
                        return Err(resolvedValue.error)
                    }

                    if (token.defaultValue != null) {
                        result = result.replace(token.fullMatch, token.defaultValue)
                        madeProgress = true
                    } else {
                        visited.remove(token.key)
                        return Err(resolvedValue.error)
                    }
                } else {
                    // Recursively interpolate the resolved value (for chained references)
                    val recursiveResult = interpolateRecursive(
                        resolvedValue.value,
                        resolver,
                        protectedResolver,
                        requestingScope,
                        visited.toMutableSet(),
                        depth + 1,
                        effectiveMaxDepth,
                        resolveSecrets
                    )
                    if (recursiveResult.isErr) {
                        visited.remove(token.key)
                        return recursiveResult
                    }
                    result = result.replace(token.fullMatch, recursiveResult.value)
                    madeProgress = true
                }

                visited.remove(token.key)
            }

            if (!madeProgress) {
                break
            }
        }

        return Ok(result)
    }

    /**
     * Parse only simple (non-nested) placeholders.
     * For `${db.${env}}`, this will only match `${env}` (the innermost one).
     */
    private fun parseSimplePlaceholders(value: String): List<PlaceholderToken> {
        return simplePlaceholderPattern.findAll(value).map { match ->
            val content = match.groupValues[1]
            parseToken(match.value, content)
        }.toList()
    }

    private fun resolveSimple(
        token: PlaceholderToken,
        resolver: PropertyResolver,
        protectedResolver: ProtectedPropertyResolver?,
        requestingScope: ConfigLevel?
    ): IdkResult<String, IdkError> {
        // For simple references, check protection if protection checking is enabled
        if (protectedResolver != null && requestingScope != null) {
            val canInterpolate = protectedResolver.canInterpolateProperty(token.key, requestingScope)
            if (canInterpolate.isErr) {
                return Err(canInterpolate.error)
            }
        }

        val value = resolver.getPropertyAsString(token.key)
        return if (value != null) {
            Ok(value)
        } else if (token.defaultValue != null) {
            Ok(token.defaultValue)
        } else {
            Err(ConfigErrors.propertyNotFound(token.key))
        }
    }

    private fun resolveEnv(token: PlaceholderToken, resolver: PropertyResolver): IdkResult<String, IdkError> {
        val envValue = Env.get(token.key)
        return if (envValue != null) {
            Ok(envValue)
        } else if (token.defaultValue != null) {
            Ok(token.defaultValue)
        } else {
            Err(ConfigErrors.propertyNotFound("env:${token.key}"))
        }
    }

    private fun resolveScope(
        token: PlaceholderToken,
        resolver: PropertyResolver,
        protectedResolver: ProtectedPropertyResolver?,
        requestingScope: ConfigLevel?
    ): IdkResult<String, IdkError> {
        val scopePrefix = token.provider ?: return Err(ConfigErrors.propertyNotFound(token.key))

        // Check protection BEFORE accessing the value
        // The protection check was already done in interpolateRecursive for SCOPE tokens,
        // but we double-check here for safety
        if (protectedResolver != null && requestingScope != null) {
            val canInterpolate = protectedResolver.canInterpolateProperty(token.key, requestingScope)
            if (canInterpolate.isErr) {
                return Err(canInterpolate.error)
            }
        }

        val scopeLevel = when (scopePrefix.lowercase()) {
            "app" -> ConfigLevel.APP
            "tenant" -> ConfigLevel.TENANT
            "principal" -> ConfigLevel.PRINCIPAL
            else -> return Err(ConfigErrors.propertyNotFound(token.key))
        }

        val value = if (resolver is ScopeAwarePropertyResolver) {
            resolver.getPropertyAsStringAtScope(token.key, scopeLevel)
        } else {
            // Fallback to legacy prefix-based lookup when scope-aware resolver is not available
            resolver.getPropertyAsString("$scopePrefix.${token.key}")
        }
        return if (value != null) {
            Ok(value)
        } else if (token.defaultValue != null) {
            Ok(token.defaultValue)
        } else {
            val keyForError = if (resolver is ScopeAwarePropertyResolver) {
                "${scopePrefix}.${token.key}"
            } else {
                "$scopePrefix.${token.key}"
            }
            Err(ConfigErrors.propertyNotFound(keyForError))
        }
    }

    private suspend fun resolveSecret(
        token: PlaceholderToken,
        requestingScope: ConfigLevel?,
        callingResolver: PropertyResolver? = null
    ): IdkResult<String, IdkError> {
        val provider = token.provider ?: return Err(ConfigErrors.secretResolutionFailed(token.key, "unknown", "No provider specified"))
        val path = token.path ?: return Err(ConfigErrors.secretResolutionFailed(token.key, provider, "No path specified"))

        if (secretResolver == null) {
            return Err(ConfigErrors.secretResolutionFailed(token.key, provider, "No secret resolver configured"))
        }

        // Secrets are automatically protected from cross-scope interpolation by default
        // unless explicitly allowed. For now, secrets can only be resolved at APP scope.
        if (requestingScope != null && requestingScope != ConfigLevel.APP) {
            // Check if this is a tenant-scoped secret path (e.g., tenants/{id}/*)
            // which would be allowed for that specific tenant
            if (!isSecretPathAllowedForScope(path, requestingScope)) {
                return Err(
                    ProtectionErrors.interpolationNotAllowed(
                        "secret:$provider:$path",
                        ConfigLevel.APP,
                        requestingScope
                    )
                )
            }
        }

        return secretResolver.resolve(provider, path, token.key, requestingScope, null, callingResolver)
    }

    /**
     * Check if a secret path is allowed for the given scope.
     *
     * By default, secrets are only accessible at APP scope. Exceptions:
     * - Tenant-specific paths (tenants/tenant-id/...) are accessible at TENANT scope
     * - Principal-specific paths are accessible at PRINCIPAL scope
     *
     * This is a basic implementation; production deployments may need more sophisticated
     * path-based access control.
     */
    private fun isSecretPathAllowedForScope(path: String, scope: ConfigLevel): Boolean {
        // System-level secrets are always APP-only
        if (path.startsWith("system/") || path.startsWith("app/")) {
            return false
        }

        // Tenant-specific secrets are allowed at TENANT scope or higher
        if (path.startsWith("tenant/") || path.startsWith("tenants/")) {
            return scope.level <= ConfigLevel.TENANT.level
        }

        // Principal-specific secrets are allowed at PRINCIPAL scope or higher
        if (path.startsWith("principal/") || path.startsWith("principals/") || path.startsWith("user/") || path.startsWith("users/")) {
            return scope.level <= ConfigLevel.PRINCIPAL.level
        }

        // Default: only APP scope can access unrecognized paths
        return false
    }

    override fun containsPlaceholders(value: String): Boolean {
        // Check if there's any complete ${...} pattern (with matching closing brace)
        // First quick check for ${
        if (!value.contains("\${")) {
            return false
        }
        // Verify there's at least one properly balanced placeholder
        return hasBalancedPlaceholder(value)
    }

    /**
     * Check if the value contains at least one properly balanced ${...} placeholder.
     */
    private fun hasBalancedPlaceholder(value: String): Boolean {
        var i = 0
        while (i < value.length - 1) {
            if (value[i] == '$' && value[i + 1] == '{') {
                var depth = 0
                var j = i
                while (j < value.length) {
                    when (value[j]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                return true // Found a complete placeholder
                            }
                        }
                    }
                    j++
                }
                // If we get here, we found ${ but no matching }
                i = j
            } else {
                i++
            }
        }
        return false
    }

    override fun isSecretReference(value: String): Boolean {
        return value.contains("\${secret:")
    }

    override fun parsePlaceholders(value: String): List<PlaceholderToken> {
        // For public API, parse all balanced placeholders including nested ones
        return parseBalancedPlaceholders(value)
    }

    /**
     * Parse all balanced placeholder expressions, handling nested braces.
     * For `${db.${env}}`, this returns the full `${db.${env}}` as a token.
     */
    private fun parseBalancedPlaceholders(value: String): List<PlaceholderToken> {
        val tokens = mutableListOf<PlaceholderToken>()
        var i = 0
        while (i < value.length - 1) {
            if (value[i] == '$' && value[i + 1] == '{') {
                val start = i
                var depth = 0
                var j = i
                while (j < value.length) {
                    when (value[j]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val fullMatch = value.substring(start, j + 1)
                                val content = value.substring(start + 2, j)
                                tokens.add(parseToken(fullMatch, content))
                                i = j
                                break
                            }
                        }
                    }
                    j++
                }
            }
            i++
        }
        return tokens
    }

    private fun parseToken(fullMatch: String, content: String): PlaceholderToken {
        // Check for secret pattern: ${secret:provider:path} or ${secret:provider:path:key}
        secretPattern.matchEntire(content)?.let { secretMatch ->
            val provider = secretMatch.groupValues[1]
            val path = secretMatch.groupValues[2]
            val key = secretMatch.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }
            return PlaceholderToken.secret(fullMatch, provider, path, key)
        }

        // Check for env pattern: ${env:KEY}
        envPattern.matchEntire(content)?.let { envMatch ->
            val key = envMatch.groupValues[1]
            val (actualKey, defaultValue) = parseDefaultValue(key)
            return PlaceholderToken.env(fullMatch, actualKey, defaultValue)
        }

        // Check for scope pattern: ${app:key}, ${tenant:key}, ${principal:key}
        scopePattern.matchEntire(content)?.let { scopeMatch ->
            val scope = scopeMatch.groupValues[1]
            val key = scopeMatch.groupValues[2]
            val (actualKey, defaultValue) = parseDefaultValue(key)
            return PlaceholderToken.scope(fullMatch, scope, actualKey, defaultValue)
        }

        // Simple pattern: ${key} or ${key:default}
        val (key, defaultValue) = parseDefaultValue(content)
        return PlaceholderToken.simple(fullMatch, key, defaultValue)
    }

    private fun parseDefaultValue(content: String): Pair<String, String?> {
        // Find the first colon that's not escaped
        val colonIndex = content.indexOf(':')
        return if (colonIndex > 0) {
            val key = content.substring(0, colonIndex)
            val defaultValue = content.substring(colonIndex + 1)
            key to defaultValue
        } else {
            content to null
        }
    }
}

/**
 * Interface for resolving secrets from various providers.
 * This is the IDK-level interface; EDK provides implementations for Vault, Azure KV, etc.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretResolver", exact = true)
interface SecretResolver {
    /**
     * Resolve a secret value.
     *
     * @param provider The secret provider identifier (e.g., "env", "vault", "azure")
     * @param path The secret path
     * @param key Optional key within the secret (for structured secrets)
     * @return The resolved secret value, or error if resolution fails
     */
    suspend fun resolve(provider: String, path: String, key: String?): IdkResult<String, IdkError>

    /**
     * Resolve a secret value with scope context for tenant/principal isolation.
     *
     * @param provider The secret provider identifier (e.g., "env", "vault", "azure")
     * @param path The secret path
     * @param key Optional key within the secret (for structured secrets)
     * @param scope The configuration scope level for access control
     * @param scopeIdentifier The tenant or principal ID for scope-specific secrets
     * @return The resolved secret value, or error if resolution fails
     */
    suspend fun resolve(
        provider: String,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?
    ): IdkResult<String, IdkError> = resolve(provider, path, key) // Default delegates to simple version

    /**
     * Resolve a secret value with scope context and the calling scope's property resolver.
     *
     * The [resolver] provides access to scope-specific configuration, enabling cloud providers
     * to resolve tenant-specific connection details (e.g., different vault URLs per tenant).
     *
     * @param provider The secret provider identifier (e.g., "env", "vault", "azure")
     * @param path The secret path
     * @param key Optional key within the secret (for structured secrets)
     * @param scope The configuration scope level for access control
     * @param scopeIdentifier The tenant or principal ID for scope-specific secrets
     * @param resolver The PropertyResolver from the calling scope (tenant/principal config)
     * @return The resolved secret value, or error if resolution fails
     */
    suspend fun resolve(
        provider: String,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
        resolver: PropertyResolver?
    ): IdkResult<String, IdkError> = resolve(provider, path, key, scope, scopeIdentifier)
}

/**
 * Simple secret resolver that supports env and map providers.
 * This is the default IDK implementation; EDK extends with cloud providers.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BasicSecretResolver", exact = true)
class BasicSecretResolver(
    private val secretMaps: Map<String, Map<String, String>> = emptyMap()
) : SecretResolver {

    override suspend fun resolve(provider: String, path: String, key: String?): IdkResult<String, IdkError> {
        return resolve(provider, path, key, null, null)
    }

    override suspend fun resolve(
        provider: String,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?
    ): IdkResult<String, IdkError> {
        // Basic resolver doesn't enforce scope restrictions - delegated to caller
        return when (provider) {
            "env" -> resolveEnvSecret(path)
            "map" -> resolveMapSecret(path, key)
            else -> Err(ConfigErrors.secretResolutionFailed(key ?: path, provider, "Unknown provider: $provider"))
        }
    }

    private fun resolveEnvSecret(envVar: String): IdkResult<String, IdkError> {
        val value = Env.get(envVar)
        return if (value != null) {
            Ok(value)
        } else {
            Err(ConfigErrors.secretResolutionFailed(envVar, "env", "Environment variable not found"))
        }
    }

    private fun resolveMapSecret(mapName: String, key: String?): IdkResult<String, IdkError> {
        val secretMap = secretMaps[mapName]
            ?: return Err(ConfigErrors.secretResolutionFailed(mapName, "map", "Secret map not found"))

        val secretKey = key ?: return Err(ConfigErrors.secretResolutionFailed(mapName, "map", "No key specified for map secret"))

        val value = secretMap[secretKey]
            ?: return Err(ConfigErrors.secretResolutionFailed(secretKey, "map", "Key not found in secret map"))

        return Ok(value)
    }
}
