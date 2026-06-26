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

package com.sphereon.core.api.conf

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Maps a logical secret key onto the physical, backend-native address a [SecretProvider] reads
 * from or writes to.
 *
 * ## Why this exists
 *
 * A logical key like `db.password` requested at tenant scope must resolve to a DIFFERENT
 * physical secret than the same logical key requested for another tenant, even when both
 * tenants are served by the SAME shared backend instance (the env floor is the canonical
 * collision case: tenant `acme` and tenant `globex` must NOT read each other's `db.password`).
 *
 * ## Central, uniform sharding
 *
 * This resolver is owned by the IDK and applied CENTRALLY by [ProviderBasedSecretResolver]: the
 * physical address is computed once, before dispatch, for BOTH the pinned branch AND every cascade
 * candidate (including the env floor). Providers therefore receive an ALREADY-PHYSICAL address as
 * their `path` and never re-shard. This guarantees the env floor — which cannot inject an EDK
 * resolver — is sharded identically to every cloud backend.
 *
 * ## Read/write symmetry contract
 *
 * The SAME `(logicalKey, providerType, scope, scopeIdentifier, instanceId, strategy)` MUST map
 * to the SAME address on every call. A value written via [WritableSecretProvider.putSecret] at a
 * tenant scope is read back identically because both the read ([SecretProvider.getSecret]) and the
 * write resolve the physical address through this resolver with the same inputs.
 *
 * ## Sharding
 *
 * The logical key is first normalized (delimiter-only: dotted/UPPER_SNAKE/hyphen/slash/space forms
 * collapse to one canonical token list; case is not a delimiter), then the per-[PartitionStrategy]
 * tenant/instance shard is applied:
 * - [PartitionStrategy.TENANT_PATH_INSTANCE_PATH] — path-style backends (vault, kubernetes-mount):
 *   `tenants/<tenant>/<instance>/<key-as-path>`.
 * - [PartitionStrategy.TENANT_LABEL_INSTANCE_PREFIX] — flat-name backends. The label form is
 *   provider-type-aware (see [providerType]) so the result is charset-safe AND collision-resistant per type.
 * - [PartitionStrategy.DEDICATED_BACKEND] — no shard (the instance is per-tenant).
 * - [PartitionStrategy.GLOBAL] — no shard (cross-tenant secret).
 *
 * App-scope (or null scope identifier) requests are NOT tenant-sharded.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SecretAddressResolver", exact = true)
interface SecretAddressResolver {
    /**
     * Resolve the physical address for a logical secret key.
     *
     * @param logicalKey The logical secret key from the `${secret:...}` reference (e.g. `db.password`).
     * @param providerType The canonical provider type id serving this secret (`env`, `vault`,
     *   `azure`, `aws`, `kubernetes-mount`). Used to pick delimiter/casing/charset conventions.
     * @param scope The configuration level the secret is being resolved for.
     * @param scopeIdentifier The tenant/principal id for [scope] (null for app scope).
     * @param instanceId Optional logical instance id (e.g. an AS/issuer/verifier instance) that
     *   partitions secrets within a tenant; null when the secret is tenant-wide.
     * @param strategy The partition strategy to apply. Callers typically pass
     *   [PartitionStrategy.defaultFor] for the provider type unless a backend descriptor pins one.
     * @return The physical, backend-native address. For flat-name backends this is the native
     *   secret name; for path backends this is the slash-delimited path. A trailing structured
     *   `:key` selector (if any) is NOT part of this address and is carried separately.
     */
    fun physicalAddress(
        logicalKey: String,
        providerType: String,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        instanceId: String?,
        strategy: PartitionStrategy,
    ): String
}

/**
 * Default [SecretAddressResolver].
 *
 * Normalizes the logical key with a delimiter-only tokenizer (lower-case, splits on
 * `.`/`-`/`/`/`_`/whitespace, NOT on case) and then renders the backend-native address, applying the
 * [PartitionStrategy] shard. Addressing style (path vs flat label) is derived from the provider type.
 *
 * - Path-style backends (vault, kubernetes-mount) emit a slash path.
 * - Flat-label backends are provider-type-aware:
 *   - `env`, `aws` (and unknown types): segments joined by `__`, each segment normalized so runs of
 *     separators collapse to a single `_` (never an internal `__`); upper-cased for `env`. This is
 *     unambiguous — the `__` segment delimiter never collides with an in-segment `_`.
 *   - `azure`: Azure secret names allow only `[a-zA-Z0-9-]`. A purely sanitizing transform would be
 *     LOSSY (two distinct tenant/key tuples could alias to the same name), so the azure label form
 *     appends a short deterministic 64-bit hash of the RAW `tenant|instance|logicalKey` tuple
 *     (`<sanitizedTenant>-<sanitizedKey>-<h>`), making it collision-resistant while staying ≤127 chars.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultSecretAddressResolver", exact = true)
class DefaultSecretAddressResolver : SecretAddressResolver {
    override fun physicalAddress(
        logicalKey: String,
        providerType: String,
        scope: ConfigLevel,
        scopeIdentifier: String?,
        instanceId: String?,
        strategy: PartitionStrategy,
    ): String {
        // Delimiter-only normalization: dotted, UPPER_SNAKE, hyphen, slash and space forms of the
        // SAME logical key collapse to one canonical token list. Case is NOT a delimiter, so an
        // acronym/UPPER name like HOME stays a single token (-> env var HOME), never h.o.m.e.
        val normalizedKey = normalizeLogicalKey(logicalKey)

        // Addressing STYLE (path vs flat label) is a property of the backend (providerType); the
        // STRATEGY only decides whether (and how) to apply a tenant/instance shard.
        val shard =
            scope != ConfigLevel.APP && scopeIdentifier != null &&
                strategy != PartitionStrategy.DEDICATED_BACKEND &&
                strategy != PartitionStrategy.GLOBAL

        if (!shard) {
            return renderBody(normalizedKey, providerType)
        }

        return if (isPathStyle(providerType)) {
            val tenant = pathSegment(scopeIdentifier)
            val keyPath = keyAsPath(normalizedKey)
            if (instanceId != null) {
                "$TENANTS_PATH_PREFIX/$tenant/${pathSegment(instanceId)}/$keyPath"
            } else {
                "$TENANTS_PATH_PREFIX/$tenant/$keyPath"
            }
        } else if (isAzure(providerType)) {
            azureLabel(scopeIdentifier, instanceId, normalizedKey)
        } else {
            underscoreLabel(scopeIdentifier, instanceId, normalizedKey, upper = isEnv(providerType))
        }
    }

    /**
     * Render the un-sharded key body in the backend's native addressing style:
     * path backends -> slash path; env -> UPPER `_`; azure -> sanitized `-`; aws/other -> `_`.
     */
    private fun renderBody(
        normalizedKey: String,
        providerType: String,
    ): String =
        when {
            isPathStyle(providerType) -> keyAsPath(normalizedKey)
            isAzure(providerType) -> azureSanitize(normalizedKey)
            isEnv(providerType) -> underscoreSegment(normalizedKey).uppercase()
            else -> underscoreSegment(normalizedKey)
        }

    /**
     * Delimiter-only logical-key normalizer. Splits on `.`/`-`/`/`/`_`/whitespace runs, lower-cases,
     * and rejoins with `.`. Unlike a camelCase normalizer it does NOT split on case, so `HOME`,
     * `DB_PASSWORD`, `db.password`, `db-password` and `db/password` canonicalize predictably.
     */
    private fun normalizeLogicalKey(logicalKey: String): String =
        logicalKey
            .trim()
            .lowercase()
            .split(LOGICAL_KEY_SEPARATORS_REGEX)
            .filter { it.isNotEmpty() }
            .joinToString(".")

    private fun isPathStyle(providerType: String): Boolean = providerType.lowercase() in PATH_STYLE_TYPES

    /** Normalized dot-delimited key -> slash path. */
    private fun keyAsPath(normalizedKey: String): String = normalizedKey.replace('.', '/')

    /** Sanitize a path segment: keep it slash-free and trimmed. */
    private fun pathSegment(raw: String): String = raw.trim().trim('/').replace('/', '_')

    private fun isEnv(providerType: String): Boolean = providerType.lowercase() == "env"

    private fun isAzure(providerType: String): Boolean = providerType.lowercase() == "azure"

    /**
     * `__`-delimited label for env/aws (and unknown flat-name backends). Each segment has any run
     * of `.`/`-`/`/`/space/`_` collapsed to a single `_`, so the `__` segment delimiter can never
     * be confused with an in-segment `_`. Upper-cased for the env floor.
     */
    private fun underscoreLabel(
        tenant: String,
        instanceId: String?,
        normalizedKey: String,
        upper: Boolean,
    ): String {
        val segments =
            buildList {
                add(underscoreSegment(tenant))
                instanceId?.let { add(underscoreSegment(it)) }
                add(underscoreSegment(normalizedKey))
            }
        val joined = segments.joinToString(LABEL_DELIMITER)
        return if (upper) joined.uppercase() else joined
    }

    private fun underscoreSegment(raw: String): String =
        raw
            .trim()
            .replace(SEPARATORS_REGEX, "_")
            .replace(UNDERSCORE_RUN_REGEX, "_")
            .trim('_')

    /**
     * Injective Azure label: `<sanitizedTenant>-<sanitizedKey>-<h>`, where `<h>` is a short
     * deterministic hash over the RAW `tenant|instance|logicalKey` tuple. The lossy sanitization of
     * the human-readable prefix can never alias two distinct tuples because the hash disambiguates
     * them. Truncated to stay within Azure's 127-char secret-name limit.
     */
    private fun azureLabel(
        tenant: String,
        instanceId: String?,
        normalizedKey: String,
    ): String {
        // Hash over the RAW (tenant, instance, key) tuple so lossy sanitization of the readable
        // prefix can never alias two distinct tuples.
        val hash = stableHashHex(tenant, instanceId, normalizedKey)
        val tenantPart = azureSanitize(tenant).take(AZURE_PREFIX_MAX)
        val keyPart = azureSanitize(normalizedKey).take(AZURE_PREFIX_MAX)
        val name = "$tenantPart-$keyPart-$hash".trim('-')
        return name.take(AZURE_NAME_MAX)
    }

    /** Collapse any run of chars outside `[a-zA-Z0-9-]` to a single `-`, trim leading/trailing `-`. */
    private fun azureSanitize(raw: String): String = raw.replace(AZURE_INVALID_REGEX, "-").trim('-')

    companion object {
        const val TENANTS_PATH_PREFIX: String = "tenants"
        const val LABEL_DELIMITER: String = "__"

        /** Azure Key Vault secret names: max 127 chars, alphabet `[a-zA-Z0-9-]`. */
        private const val AZURE_NAME_MAX: Int = 127
        private const val AZURE_PREFIX_MAX: Int = 48

        /** Backends whose native addressing is a slash path rather than a flat name. */
        private val PATH_STYLE_TYPES = setOf("vault", "kubernetes-mount")

        /** Logical-key delimiters: dot, hyphen, slash, underscore, whitespace (runs). NOT case. */
        private val LOGICAL_KEY_SEPARATORS_REGEX = Regex("[.\\-/\\s_]+")
        private val SEPARATORS_REGEX = Regex("[.\\-/\\s_]+")
        private val UNDERSCORE_RUN_REGEX = Regex("_+")
        private val AZURE_INVALID_REGEX = Regex("[^0-9a-zA-Z-]+")

        /**
         * Tiny pure-Kotlin FNV-1a (64-bit) hash over the raw `tenant|instance|logicalKey` tuple,
         * rendered as a 16-char lower-case hex string. Multiplatform-safe (no JVM `MessageDigest`;
         * uses `ULong`). It makes the azure label COLLISION-RESISTANT (a 64-bit disambiguator over
         * the raw tuple) — not mathematically injective, since Azure's 127-char limit bounds the
         * output space — which is sufficient to keep distinct tenant/key tuples from aliasing in
         * practice.
         */
        internal fun stableHashHex(
            tenant: String,
            instanceId: String?,
            logicalKey: String,
        ): String {
            val tuple = "$tenant|${instanceId ?: ""}|$logicalKey"
            var hash = 0xcbf29ce484222325uL // FNV-1a 64-bit offset basis
            for (ch in tuple) {
                hash = hash xor ch.code.toULong()
                hash *= 0x100000001b3uL // FNV-1a 64-bit prime
            }
            return hash.toString(16).padStart(16, '0')
        }
    }
}
