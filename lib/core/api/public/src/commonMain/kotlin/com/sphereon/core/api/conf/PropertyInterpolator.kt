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

package com.sphereon.core.api.conf

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Interface for interpolating property values containing placeholders.
 *
 * Supported patterns:
 * - `${VAR}` - Simple substitution
 * - `${VAR:default}` - With default value
 * - `${env:VAR}` - Explicit env source (convenience alias for `${secret:@env:VAR}`)
 * - `${scope:key}` - Explicit scope prefix (app, tenant, principal)
 * - `${secret:<logical.key>[:<key>]}` - Cascade secret reference: resolves via the
 *   tenant-selected provider, then the app-selected provider, then env.
 * - `${secret:@<provider>:<logical.key>[:<key>]}` - Pinned secret reference targeting a
 *   specific provider (e.g. `@env`, `@vault`, `@azure`, `@aws`, `@kubernetes-mount`).
 * - `${db.${env}}` - Recursive (max depth configurable)
 *
 * The `<logical.key>` of a secret reference is passed through verbatim here; the
 * [SecretAddressResolver] normalizes it during resolution so dotted, `UPPER_SNAKE_CASE`, hyphen,
 * slash, and space forms all address the same logical secret (e.g. `example.secret.ref.value` ==
 * `EXAMPLE_SECRET_REF_VALUE` == `example/secret/ref/value`) and then renders the backend-native,
 * tenant-sharded physical address. Centralizing normalization there keeps the env floor and every
 * provider consistent.
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
    suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
    ): IdkResult<String, IdkError>

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
        requestingScope: ConfigLevel,
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
        resolveSecrets: Boolean,
    ): IdkResult<String, IdkError> = interpolate(value, resolver, requestingScope) // Default delegates

    /**
     * Interpolate placeholders with full control over resolution options AND scope identity.
     *
     * The [scopeIdentifier] (tenantId for [ConfigLevel.TENANT], principalId for
     * [ConfigLevel.PRINCIPAL]) is threaded through to secret resolution so that cascade
     * (`${secret:<key>}`) references can select the tenant/principal-scoped provider.
     * Without it, cascade resolution can only fall back to the app/env floor.
     *
     * @param value The value containing placeholders
     * @param resolver Property resolver for looking up referenced values
     * @param requestingScope The scope level requesting the interpolation
     * @param maxDepth Maximum recursion depth for nested interpolations (overrides default)
     * @param resolveSecrets Whether to resolve ${secret:...} references
     * @param scopeIdentifier The tenant or principal ID for scope-specific secret resolution
     * @return The interpolated string, or error if resolution fails
     */
    suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        resolveSecrets: Boolean,
        scopeIdentifier: String?,
    ): IdkResult<String, IdkError> = interpolate(value, resolver, requestingScope, maxDepth, resolveSecrets) // Default delegates

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
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PlaceholderToken", exact = true)
@CoverageExcludedDataClass
data class PlaceholderToken(
    val fullMatch: String,
    val type: PlaceholderType,
    val key: String,
    val defaultValue: String?,
    val provider: String?,
    val path: String?,
) {
    companion object {
        @JvmStatic
        fun simple(
            match: String,
            key: String,
            defaultValue: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SIMPLE,
            key = key,
            defaultValue = defaultValue,
            provider = null,
            path = null,
        )

        @JvmStatic
        fun env(
            match: String,
            key: String,
            defaultValue: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.ENV,
            key = key,
            defaultValue = defaultValue,
            provider = null,
            path = null,
        )

        @JvmStatic
        fun scope(
            match: String,
            scope: String,
            key: String,
            defaultValue: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SCOPE,
            key = key,
            defaultValue = defaultValue,
            provider = scope,
            path = null,
        )

        /**
         * Build a secret token. The [provider] is nullable: `null` means a cascade
         * reference (`${secret:<key>}`) that resolves through the tenant-selected
         * provider, then the app-selected provider, then env. A non-null [provider]
         * pins resolution to that exact provider (`${secret:@<provider>:<key>}`).
         */
        @JvmStatic
        fun secret(
            match: String,
            provider: String?,
            path: String,
            key: String? = null,
        ) = PlaceholderToken(
            fullMatch = match,
            type = PlaceholderType.SECRET,
            key = key ?: path,
            defaultValue = null,
            provider = provider,
            path = path,
        )
    }
}

/**
 * Types of placeholder patterns.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PlaceholderType", exact = true)
enum class PlaceholderType {
    /** Simple `${key}` or `${key:default}` */
    SIMPLE,

    /** Explicit `${env:KEY}` */
    ENV,

    /** Explicit `${scope:key}` where scope is app/tenant/principal */
    SCOPE,

    /**
     * Secret reference. Cascade form `${secret:<key>}` / `${secret:<key>:<subkey>}`
     * (provider omitted) or pinned form `${secret:@<provider>:<key>}` /
     * `${secret:@<provider>:<key>:<subkey>}`.
     */
    SECRET,
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
    private val secretResolver: SecretResolver? = null,
) : PropertyInterpolator {
    // Simple pattern for non-nested placeholders (no nested ${} inside)
    // Note: closing brace must be escaped for JS/wasmJs regex Unicode mode compatibility
    private val simplePlaceholderPattern = Regex("""\$\{([^{}]+)\}""")
    private val envPattern = Regex("""^env:(.+)$""")
    private val scopePattern = Regex("""^(app|tenant|principal):(.+)$""")

    // Secret grammar. The `secret:` prefix is always present.
    //   group1 = optional pinned provider id (only when a leading `@` is present); null = cascade
    //   group2 = logical key (path)
    //   group3 = optional sub-key
    private val secretPattern = Regex("""^secret:(?:@([^:]+):)?([^:]+)(?::(.+))?$""")
    private val defaultValuePattern = Regex("""^([^:]+):(.*)$""")

    override suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
    ): IdkResult<String, IdkError> = interpolateRecursive(value, resolver, null, null, null, mutableSetOf(), 0, maxDepth, true)

    override suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
    ): IdkResult<String, IdkError> {
        val protectedResolver = resolver as? ProtectedPropertyResolver
        return interpolateRecursive(value, resolver, protectedResolver, requestingScope, null, mutableSetOf(), 0, maxDepth, true)
    }

    override suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        resolveSecrets: Boolean,
    ): IdkResult<String, IdkError> = interpolate(value, resolver, requestingScope, maxDepth, resolveSecrets, null)

    override suspend fun interpolate(
        value: String,
        resolver: PropertyResolver,
        requestingScope: ConfigLevel,
        maxDepth: Int?,
        resolveSecrets: Boolean,
        scopeIdentifier: String?,
    ): IdkResult<String, IdkError> {
        val protectedResolver = resolver as? ProtectedPropertyResolver
        val effectiveMaxDepth = maxDepth ?: this.maxDepth
        return interpolateRecursive(
            value,
            resolver,
            protectedResolver,
            requestingScope,
            scopeIdentifier,
            mutableSetOf(),
            0,
            effectiveMaxDepth,
            resolveSecrets,
        )
    }

    private suspend fun interpolateRecursive(
        value: String,
        resolver: PropertyResolver,
        protectedResolver: ProtectedPropertyResolver?,
        requestingScope: ConfigLevel?,
        scopeIdentifier: String?,
        visited: MutableSet<String>,
        depth: Int,
        effectiveMaxDepth: Int = maxDepth,
        resolveSecrets: Boolean = true,
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

                val resolvedValue =
                    when (token.type) {
                        PlaceholderType.SIMPLE -> {
                            resolveSimple(token, resolver, protectedResolver, requestingScope)
                        }

                        PlaceholderType.ENV -> {
                            resolveEnv(token, resolver)
                        }

                        PlaceholderType.SCOPE -> {
                            resolveScope(token, resolver, protectedResolver, requestingScope)
                        }

                        PlaceholderType.SECRET -> {
                            // Skip secret resolution if resolveSecrets is false
                            if (!resolveSecrets) {
                                // Keep the original placeholder unresolved
                                visited.remove(token.key)
                                result = result // No change
                                continue
                            }
                            resolveSecret(token, requestingScope, scopeIdentifier, resolver)
                        }
                    }

                if (resolvedValue.isErr) {
                    // Check if this is a protection error - those should never fall through to defaults
                    // as that would reveal information about protected property existence
                    val isProtectionError =
                        resolvedValue.error.code == "ILLEGAL_ARGUMENT_ERROR" &&
                            (
                                resolvedValue.error.message.defaultMessage
                                    .contains("PROTECTED") ||
                                    resolvedValue.error.message.defaultMessage
                                        .contains("FINAL")
                            )

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
                    val recursiveResult =
                        interpolateRecursive(
                            resolvedValue.value,
                            resolver,
                            protectedResolver,
                            requestingScope,
                            scopeIdentifier,
                            visited.toMutableSet(),
                            depth + 1,
                            effectiveMaxDepth,
                            resolveSecrets,
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
    private fun parseSimplePlaceholders(value: String): List<PlaceholderToken> =
        simplePlaceholderPattern
            .findAll(value)
            .map { match ->
                val content = match.groupValues[1]
                parseToken(match.value, content)
            }.toList()

    private fun resolveSimple(
        token: PlaceholderToken,
        resolver: PropertyResolver,
        protectedResolver: ProtectedPropertyResolver?,
        requestingScope: ConfigLevel?,
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

    private fun resolveEnv(
        token: PlaceholderToken,
        resolver: PropertyResolver,
    ): IdkResult<String, IdkError> {
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
        requestingScope: ConfigLevel?,
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

        val scopeLevel =
            when (scopePrefix.lowercase()) {
                "app" -> ConfigLevel.APP
                "tenant" -> ConfigLevel.TENANT
                "principal" -> ConfigLevel.PRINCIPAL
                else -> return Err(ConfigErrors.propertyNotFound(token.key))
            }

        val value =
            if (resolver is ScopeAwarePropertyResolver) {
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
            val keyForError =
                if (resolver is ScopeAwarePropertyResolver) {
                    "$scopePrefix.${token.key}"
                } else {
                    "$scopePrefix.${token.key}"
                }
            Err(ConfigErrors.propertyNotFound(keyForError))
        }
    }

    private suspend fun resolveSecret(
        token: PlaceholderToken,
        requestingScope: ConfigLevel?,
        scopeIdentifier: String?,
        callingResolver: PropertyResolver? = null,
    ): IdkResult<String, IdkError> {
        // provider may be null: that is the cascade form (`${secret:<key>}`), where the
        // resolver selects the tenant -> app -> env provider chain itself.
        val provider = token.provider
        val path =
            token.path
                ?: return Err(ConfigErrors.secretResolutionFailed(token.key, provider ?: "cascade", "No path specified"))

        if (secretResolver == null) {
            return Err(ConfigErrors.secretResolutionFailed(token.key, provider ?: "cascade", "No secret resolver configured"))
        }

        // NOTE: Per-scope isolation (which provider a tenant/principal may reach, and how a
        // tenant's secrets are sharded away from other tenants') is enforced downstream by the
        // SecretAddressResolver / per-scope provider selection (Phase 2). The interpolator no
        // longer hard-codes an APP-only path gate here, because that pre-empted legitimate
        // tenant-scoped cascade resolution. The scopeIdentifier is threaded through so the
        // resolver can scope/shard correctly.
        return secretResolver.resolve(provider, path, token.key, requestingScope, scopeIdentifier, callingResolver)
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
                        '{' -> {
                            depth++
                        }

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

    override fun isSecretReference(value: String): Boolean = value.contains("\${secret:")

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
                        '{' -> {
                            depth++
                        }

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

    private fun parseToken(
        fullMatch: String,
        content: String,
    ): PlaceholderToken {
        // Check for secret pattern:
        //   cascade: ${secret:<key>} / ${secret:<key>:<subkey>}        (provider omitted)
        //   pinned : ${secret:@<provider>:<key>} / ${secret:@<provider>:<key>:<subkey>}
        secretPattern.matchEntire(content)?.let { secretMatch ->
            // group1 is empty string when no leading @<provider>: was present -> cascade
            val provider = secretMatch.groupValues[SECRET_PROVIDER_GROUP_INDEX].takeIf { it.isNotEmpty() }
            // The logical key is passed through verbatim; normalization (so dotted == UPPER_SNAKE ==
            // hyphen == slash forms collapse to one canonical address) is owned solely by the
            // SecretAddressResolver during resolution, so it happens once and consistently for the
            // env floor and every provider.
            val path = secretMatch.groupValues[SECRET_PATH_GROUP_INDEX]
            val key = secretMatch.groupValues.getOrNull(SECRET_KEY_GROUP_INDEX)?.takeIf { it.isNotEmpty() }
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

    companion object {
        private const val SECRET_PROVIDER_GROUP_INDEX = 1
        private const val SECRET_PATH_GROUP_INDEX = 2
        private const val SECRET_KEY_GROUP_INDEX = 3
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
     * @param provider The secret provider identifier (e.g., "env", "vault", "azure"), or
     *   `null` for cascade resolution (tenant-selected -> app-selected -> env).
     * @param path The (normalized) secret logical key / path
     * @param key Optional key within the secret (for structured secrets)
     * @return The resolved secret value, or error if resolution fails
     */
    suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
    ): IdkResult<String, IdkError>

    /**
     * Resolve a secret value with scope context for tenant/principal isolation.
     *
     * @param provider The secret provider identifier, or `null` for cascade resolution.
     * @param path The (normalized) secret logical key / path
     * @param key Optional key within the secret (for structured secrets)
     * @param scope The configuration scope level for access control
     * @param scopeIdentifier The tenant or principal ID for scope-specific secrets
     * @return The resolved secret value, or error if resolution fails
     */
    suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
    ): IdkResult<String, IdkError> = resolve(provider, path, key) // Default delegates to simple version

    /**
     * Resolve a secret value with scope context and the calling scope's property resolver.
     *
     * The [resolver] provides access to scope-specific configuration, enabling cloud providers
     * to resolve tenant-specific connection details (e.g., different vault URLs per tenant).
     *
     * @param provider The secret provider identifier, or `null` for cascade resolution.
     * @param path The (normalized) secret logical key / path
     * @param key Optional key within the secret (for structured secrets)
     * @param scope The configuration scope level for access control
     * @param scopeIdentifier The tenant or principal ID for scope-specific secrets
     * @param resolver The PropertyResolver from the calling scope (tenant/principal config)
     * @return The resolved secret value, or error if resolution fails
     */
    suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
        resolver: PropertyResolver?,
    ): IdkResult<String, IdkError> = resolve(provider, path, key, scope, scopeIdentifier)
}

/**
 * Simple secret resolver that supports env and map providers.
 * This is the default IDK implementation; EDK extends with cloud providers.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("BasicSecretResolver", exact = true)
class BasicSecretResolver(
    private val secretMaps: Map<String, Map<String, String>> = emptyMap(),
) : SecretResolver {
    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
    ): IdkResult<String, IdkError> = resolve(provider, path, key, null, null)

    override suspend fun resolve(
        provider: String?,
        path: String,
        key: String?,
        scope: ConfigLevel?,
        scopeIdentifier: String?,
    ): IdkResult<String, IdkError> {
        // Basic resolver doesn't enforce scope restrictions - delegated to caller.
        // A null provider is the cascade form; the IDK floor for cascade is env.
        return when (provider) {
            null, "env" -> resolveEnvSecret(path)
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

    private fun resolveMapSecret(
        mapName: String,
        key: String?,
    ): IdkResult<String, IdkError> {
        val secretMap =
            secretMaps[mapName]
                ?: return Err(ConfigErrors.secretResolutionFailed(mapName, "map", "Secret map not found"))

        val secretKey = key ?: return Err(ConfigErrors.secretResolutionFailed(mapName, "map", "No key specified for map secret"))

        val value =
            secretMap[secretKey]
                ?: return Err(ConfigErrors.secretResolutionFailed(secretKey, "map", "Key not found in secret map"))

        return Ok(value)
    }
}
