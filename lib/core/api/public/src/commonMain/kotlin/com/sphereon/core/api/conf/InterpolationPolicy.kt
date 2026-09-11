/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Explicit interpolation authority for one configuration field.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("InterpolationPolicy", exact = true)
enum class InterpolationPolicy {
    /** No placeholder may be consumed by this field. */
    DENY,

    /** Known configuration properties may be referenced, but process environment may not. */
    PROPERTY_REFERENCES_ONLY,

    /** Operator-owned APP configuration may also consume an explicitly declared `${env:...}`. */
    APP_ENVIRONMENT,
}

/**
 * Chooses interpolation authority for a concrete property key.
 *
 * EDK product catalogs can provide exact-key policies without moving their field catalog into
 * pure IDK. Returning [InterpolationPolicy.DENY] is the safe default for unclassified
 * security-sensitive fields.
 */
fun interface InterpolationPolicyProvider {
    fun policyFor(
        propertyKey: String,
        sourceScope: ConfigLevel,
    ): InterpolationPolicy

    /**
     * Stable identity for cache entries produced under this policy.
     *
     * Custom providers that cannot supply a stable identity return null. Resolution caches must
     * then be bypassed rather than risk reusing material produced under a different authority.
     */
    val cacheIdentity: String?
        get() = null
}

/**
 * One exact-key catalog shared by APP, TENANT, and PRINCIPAL resolver graphs.
 *
 * Product runtimes replace the default empty catalog once at APP scope. User-scope providers
 * consume that same catalog; they must not introduce a second set of policy decisions.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("InterpolationPolicyCatalog", exact = true)
@CoverageExcludedDataClass
data class InterpolationPolicyCatalog(
    val exactPolicies: Map<String, InterpolationPolicy> = emptyMap(),
    val patternPolicies: List<InterpolationPolicyPattern> = emptyList(),
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("InterpolationPolicyPattern", exact = true)
@CoverageExcludedDataClass
data class InterpolationPolicyPattern(
    val normalizedKeyRegex: String,
    val policy: InterpolationPolicy,
)

/**
 * Default policy for generic configuration.
 *
 * Credential, identity, destination, and trust-boundary fields are denied unless explicitly
 * listed in [explicitPolicies]. Every other field defaults to property references only. An APP
 * environment reference is therefore possible only when its exact normalized key is explicitly
 * mapped to [InterpolationPolicy.APP_ENVIRONMENT].
 */
class DefaultInterpolationPolicyProvider private constructor(
    explicitPolicies: Map<String, InterpolationPolicy>,
    patternPolicies: List<InterpolationPolicyPattern>,
) : InterpolationPolicyProvider {
    constructor(explicitPolicies: Map<String, InterpolationPolicy> = emptyMap()) : this(explicitPolicies, emptyList())

    constructor(catalog: InterpolationPolicyCatalog) : this(catalog.exactPolicies, catalog.patternPolicies)

    private val keyNormalizer = PropertyKeyNormalizerImpl.Default
    private val normalizedPolicies = explicitPolicies.mapKeys { (key, _) -> keyNormalizer.normalize(key) }
    private val normalizedPatternPolicies = patternPolicies.map { Regex(it.normalizedKeyRegex) to it.policy }
    override val cacheIdentity: String =
        if (normalizedPolicies.isEmpty() && normalizedPatternPolicies.isEmpty()) {
            DEFAULT_INTERPOLATION_POLICY_CACHE_IDENTITY
        } else {
            "interpolation-policy:v1:catalog:${stablePolicyDigest(normalizedPolicies, patternPolicies)}"
        }

    override fun policyFor(
        propertyKey: String,
        sourceScope: ConfigLevel,
    ): InterpolationPolicy {
        val normalizedKey = keyNormalizer.normalize(propertyKey)
        val configuredPolicy =
            normalizedPolicies[normalizedKey]
                ?: normalizedPatternPolicies.firstOrNull { (pattern, _) -> pattern.matches(normalizedKey) }?.second
        configuredPolicy?.let {
            return if (
                it == InterpolationPolicy.APP_ENVIRONMENT &&
                sourceScope != ConfigLevel.APP
            ) {
                InterpolationPolicy.DENY
            } else {
                it
            }
        }
        return if (isSecuritySensitiveInterpolationKey(normalizedKey)) {
            InterpolationPolicy.DENY
        } else {
            InterpolationPolicy.PROPERTY_REFERENCES_ONLY
        }
    }
}

class FixedInterpolationPolicyProvider(
    private val policy: InterpolationPolicy,
) : InterpolationPolicyProvider {
    override val cacheIdentity: String = "interpolation-policy:v1:fixed:${policy.name}"

    override fun policyFor(
        propertyKey: String,
        sourceScope: ConfigLevel,
    ): InterpolationPolicy = policy
}

const val DEFAULT_INTERPOLATION_POLICY_CACHE_IDENTITY: String = "interpolation-policy:v1:default-empty"

private fun stablePolicyDigest(
    policies: Map<String, InterpolationPolicy>,
    patternPolicies: List<InterpolationPolicyPattern>,
): String {
    var hash = 0xcbf29ce484222325UL
    buildList {
        addAll(policies.map { (key, policy) -> "exact:$key=${policy.name}" }.sorted())
        addAll(patternPolicies.mapIndexed { index, policy -> "pattern:$index:${policy.normalizedKeyRegex}=${policy.policy.name}" })
    }.forEach { entry ->
            "$entry\n".encodeToByteArray().forEach { byte ->
                hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
            }
        }
    return hash.toString(16)
}

internal fun isSecuritySensitiveInterpolationKey(normalizedKey: String): Boolean {
    val segments =
        normalizedKey
            .lowercase()
            .split('.', '-', '_')
            .filter(String::isNotBlank)
            .toSet()
    return segments.any {
        it in
            setOf(
                "secret",
                "password",
                "credential",
                "credentials",
                "destination",
                "endpoint",
                "url",
                "uri",
                "issuer",
                "audience",
                "token",
                "authorization",
                "clientid",
                "clientsecret",
                "apikey",
                "accesskey",
                "privatekey",
            )
    } ||
        normalizedKey.contains("client-id") ||
        normalizedKey.contains("client.id") ||
        normalizedKey.contains("client-secret") ||
        normalizedKey.contains("secret-id") ||
        normalizedKey.contains("api-key") ||
        normalizedKey.contains("access-key") ||
        normalizedKey.contains("private-key")
}
