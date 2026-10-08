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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes

/**
 * Resolves the on-disk keystore FILE path for a software KMS keystore, keyed by tenant and
 * provider name.
 *
 * Resolution rules (see the per-tenant-kms-keystore-separation design):
 * - Every tenant-aware resolution includes a tenant directory in the physical file path.
 * - When [SoftwareKeyStoreConfig.path] is explicitly set, the configured path is used as the
 *   keystore file or subpath, but it is still placed under `<root>/<tenantId>/...`.
 * - When [SoftwareKeyStoreConfig.path] is null/blank, derive a distinct, per-tenant, per-provider
 *   file: `<root>/<tenantId>/<providerName>.<ext>`.
 *
 * This is pure string logic so it lives in commonMain and is reused by the platform-specific
 * keystore factory. Actual file IO stays in jvmMain.
 */
object TenantKeyStorePathResolver {
    /** Default keystore root directory used when no [SoftwareKeyStoreConfig.keystoreRoot] is set. */
    const val DEFAULT_KEYSTORE_ROOT: String = "/keystore"

    private const val PATH_SEPARATOR = "/"
    private const val FALLBACK_TENANT_ID = "default"
    private const val FALLBACK_PROVIDER_NAME = "keystore"

    /**
     * Returns the effective keystore file path for the given config and tenant.
     *
     * @param config the software keystore config (carries explicit [SoftwareKeyStoreConfig.path] or
     *   the per-tenant derivation inputs)
     * @param tenantId the active tenant id; null/blank falls back to [FALLBACK_TENANT_ID]
     * @return the derived per-tenant/per-provider path
     */
    fun resolvePath(
        config: SoftwareKeyStoreConfig,
        tenantId: String?,
    ): String {
        val safeTenant = pathSafeSegment(tenantId, FALLBACK_TENANT_ID)
        val safeProvider = pathSafeSegment(config.id, FALLBACK_PROVIDER_NAME)
        val ext = extensionFor(config.keyStoreType)
        val fallbackFileName = "$safeProvider.$ext"
        val explicit = config.path?.trim()?.takeUnless { it.isBlank() }
        val configuredRoot = config.keystoreRoot?.trim()?.takeUnless { it.isBlank() }
        val root = normalizeRoot(configuredRoot ?: rootFromExplicitPath(explicit) ?: DEFAULT_KEYSTORE_ROOT)
        val relativePath =
            explicit
                ?.let { explicitRelativePath(it, root) }
                ?.let { pathSafeRelativePath(it, fallbackFileName) }
                ?: fallbackFileName

        return joinPath(root, safeTenant, relativePath)
    }

    /**
     * Returns a copy of [config] with [SoftwareKeyStoreConfig.path] resolved per-tenant.
     *
     * Only the concrete [Pkcs12KeyStoreConfig], [BksKeyStoreConfig] and [JksKeyStoreConfig] persist to files; other
     * software configs (e.g. Apple keychain) are returned unchanged.
     */
    fun withResolvedPath(
        config: SoftwareKeyStoreConfig,
        tenantId: String?,
    ): SoftwareKeyStoreConfig {
        val resolved = resolvePath(config, tenantId)
        return when (config) {
            is Pkcs12KeyStoreConfig -> config.copy(path = resolved)
            is BksKeyStoreConfig -> config.copy(path = resolved)
            is JksKeyStoreConfig -> config.copy(path = resolved)
            else -> config
        }
    }

    internal fun tenantPathSegment(tenantId: String?): String = pathSafeSegment(tenantId, FALLBACK_TENANT_ID)

    private fun normalizeRoot(root: String): String {
        val normalized = root.replace('\\', '/').trimEnd('/')
        return normalized.ifBlank { PATH_SEPARATOR }
    }

    private fun joinPath(
        root: String,
        tenant: String,
        relativePath: String,
    ): String =
        if (root == PATH_SEPARATOR) {
            "$PATH_SEPARATOR$tenant$PATH_SEPARATOR$relativePath"
        } else {
            "$root$PATH_SEPARATOR$tenant$PATH_SEPARATOR$relativePath"
        }

    private fun rootFromExplicitPath(path: String?): String? {
        val normalized = path?.replace('\\', '/')?.trimEnd('/')?.takeUnless { it.isBlank() } ?: return null
        val index = normalized.lastIndexOf('/')
        return when {
            index > 0 -> normalized.substring(0, index)
            index == 0 -> PATH_SEPARATOR
            else -> null
        }
    }

    private fun explicitRelativePath(
        path: String,
        root: String,
    ): String {
        val normalizedPath = path.replace('\\', '/').trim('/')
        val normalizedRoot = normalizeRoot(root).trimEnd('/')
        if (normalizedRoot.isNotBlank()) {
            val rootPrefix = normalizedRoot.trim('/')
            if (rootPrefix.isNotBlank() && normalizedPath == rootPrefix) {
                return ""
            }
            if (rootPrefix.isNotBlank() && normalizedPath.startsWith("$rootPrefix/")) {
                return normalizedPath.removePrefix("$rootPrefix/").trim('/')
            }
        }
        return normalizedPath.substringAfterLast('/')
    }

    private fun pathSafeRelativePath(
        raw: String,
        fallback: String,
    ): String {
        val segments =
            raw.split('/').mapNotNull { segment ->
                val safe = pathSafeSegment(segment, "")
                safe.takeUnless { it.isBlank() }
            }
        return segments.joinToString(PATH_SEPARATOR).ifBlank { fallback }
    }

    private fun extensionFor(keyStoreType: String): String =
        when (keyStoreType) {
            PredefinedKeyStoreTypes.JKS.keyStoreType -> "jks"
            else -> "p12"
        }

    /**
     * Makes a single path segment safe: collapses path separators and other reserved characters to
     * `_` so a tenant id or provider name can never escape its directory or inject separators.
     */
    private fun pathSafeSegment(
        raw: String?,
        fallback: String,
    ): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) {
            return fallback
        }
        val sanitized =
            buildString(value.length) {
                for (ch in value) {
                    append(
                        when (ch) {
                            '/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000' -> '_'

                            '.' -> if (length == 0) '_' else ch

                            // disallow leading dot (".."/".")

                            else -> ch
                        },
                    )
                }
            }
        // Guard against the segment collapsing to "." / ".." style traversal.
        return if (sanitized.isBlank() || sanitized == "." || sanitized == "..") fallback else sanitized
    }
}
