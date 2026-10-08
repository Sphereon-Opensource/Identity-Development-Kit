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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.conf.ConfigService
import com.sphereon.did.persistence.DidPersistenceConfig

/**
 * Reads [DidPersistenceConfig] from a [ConfigService] under the
 * [DidPersistenceConfig.PROPERTY_PREFIX] (`did.persistence`) namespace.
 *
 * Recognised keys (all optional; default `type=memory`):
 * - `did.persistence.type` — dialect (`memory`, `sqlite`, `postgresql`, `mysql`, …)
 * - `did.persistence.connectionUrl` — JDBC URL or file path; dialect-specific
 * - `did.persistence.username` — JDBC username
 * - `did.persistence.password` — JDBC password
 * - `did.persistence.poolSize` — JDBC pool size (integer)
 * - `did.persistence.properties.<key>` — free-form per-dialect options
 *
 * The free-form `properties` map collects any sub-key under `did.persistence.properties.*`.
 */
object DidPersistenceConfigBinder {
    fun bind(configService: ConfigService): DidPersistenceConfig {
        val prefix = DidPersistenceConfig.PROPERTY_PREFIX
        val type =
            configService.getPropertyAsString("$prefix.type", DidPersistenceConfig.TYPE_MEMORY)
                ?: DidPersistenceConfig.TYPE_MEMORY
        val connectionUrl = configService.getPropertyAsString("$prefix.connectionUrl", null)
        val username = configService.getPropertyAsString("$prefix.username", null)
        val password = configService.getPropertyAsString("$prefix.password", null)
        val poolSizeRaw = configService.getPropertyAsString("$prefix.poolSize", null)?.takeIf { it.isNotBlank() }
        val poolSize =
            poolSizeRaw?.let { raw ->
                raw.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException(
                        "$prefix.poolSize must be a positive integer; got '$raw'",
                    )
            }

        val propsPrefix = "$prefix.properties."
        val extraProperties =
            configService
                .getSubProperties(prefixes = setOf(propsPrefix), stripPrefix = true)
                .mapValues { it.value.toString() }

        return DidPersistenceConfig(
            type = type.trim(),
            connectionUrl = connectionUrl?.takeIf { it.isNotBlank() },
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() },
            poolSize = poolSize,
            properties = extraProperties,
        )
    }
}
