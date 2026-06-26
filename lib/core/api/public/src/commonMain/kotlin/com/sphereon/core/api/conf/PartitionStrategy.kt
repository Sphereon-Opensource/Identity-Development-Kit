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
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * How a logical secret key is sharded into a physical, backend-native address for a given
 * tenant / instance.
 *
 * A secret provider type can be a SHARED app-level backend instance serving many tenants
 * (the env floor is the canonical case). To keep tenant `acme` and tenant `globex` from
 * colliding on the same logical key, the [SecretAddressResolver] applies a per-strategy shard
 * derived from the scope + scope identifier + instance id.
 *
 * The strategy mirrors the platform-config `SecretsBackendDescriptor.partitionStrategy`
 * (`TENANT_PATH_INSTANCE_PATH | TENANT_LABEL_INSTANCE_PREFIX | DEDICATED_BACKEND`) and adds a
 * [GLOBAL] escape for genuinely cross-tenant secrets.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PartitionStrategy", exact = true)
enum class PartitionStrategy {
    /**
     * Path-style sharding for hierarchical backends (Vault KV, Kubernetes mount):
     * `tenants/<tenant>/<instance>/<key-as-path>`. Default for path-addressed backends.
     */
    TENANT_PATH_INSTANCE_PATH,

    /**
     * Label/prefix sharding for flat-name backends (env, Azure Key Vault, AWS Secrets Manager):
     * `<TENANT>__<INSTANCE>__<KEY>`. Default for flat-name backends.
     */
    TENANT_LABEL_INSTANCE_PREFIX,

    /**
     * The backend instance is itself dedicated to a single tenant (e.g. a per-tenant Vault
     * namespace or a per-tenant AWS account), so no tenant/instance shard is applied — the
     * logical key is used verbatim (normalized).
     */
    DEDICATED_BACKEND,

    /**
     * Cross-tenant / platform-wide secret: no shard is applied regardless of the requesting
     * scope. Use only for secrets that are intentionally shared across tenants.
     */
    GLOBAL,
    ;

    companion object {
        /**
         * The default [PartitionStrategy] for a provider type id (`env`, `vault`, `azure`,
         * `aws`, `kubernetes-mount`).
         *
         * - Path-addressed backends (`vault`, `kubernetes-mount`) default to
         *   [TENANT_PATH_INSTANCE_PATH].
         * - Flat-name backends (`env`, `azure`, `aws`) default to
         *   [TENANT_LABEL_INSTANCE_PREFIX].
         *
         * Unknown provider types fall back to the flat-name default, which is the safest
         * collision-avoiding shard for an unmodelled backend.
         */
        @JvmStatic
        fun defaultFor(providerType: String): PartitionStrategy =
            when (providerType.lowercase()) {
                "vault", "kubernetes-mount" -> TENANT_PATH_INSTANCE_PATH
                "env", "azure", "aws" -> TENANT_LABEL_INSTANCE_PREFIX
                else -> TENANT_LABEL_INSTANCE_PREFIX
            }

        /**
         * Parse a [PartitionStrategy] from a raw config string (case-insensitive, kebab-case
         * tolerated), returning null when absent or unrecognized so callers can fall back to a
         * per-type default.
         */
        @JvmStatic
        fun parseOrNull(raw: String?): PartitionStrategy? =
            raw?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
                val normalized = token.uppercase().replace('-', '_')
                entries.firstOrNull { it.name == normalized }
            }
    }
}
