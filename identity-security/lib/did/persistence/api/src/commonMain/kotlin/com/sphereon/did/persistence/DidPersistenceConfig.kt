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

package com.sphereon.did.persistence

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * Configuration for the DID persistence layer.
 *
 * Read from `did.persistence.*` application properties. Selector logic lives in
 * `DidRepositorySelectorModule` (in `lib-did-manager-impl`); each dialect contributes its own
 * [DidRepositoryFactory] keyed by [type] so that consumers compose the dialects they need by
 * placing the corresponding modules on the classpath.
 *
 * IDK ships two dialects: [TYPE_MEMORY] and [TYPE_SQLITE]. EDK consumers add
 * [TYPE_POSTGRESQL] and [TYPE_MYSQL] by including their factory modules.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidPersistenceConfig", exact = true)
@JsExportCompat
@Serializable
data class DidPersistenceConfig
    @JvmOverloads
    constructor(
        /**
         * Dialect discriminator. One of [TYPE_MEMORY], [TYPE_SQLITE], [TYPE_POSTGRESQL],
         * [TYPE_MYSQL], or any string a custom factory contributes itself under.
         */
        val type: String = TYPE_MEMORY,
        /**
         * Connection target. Interpretation is dialect-specific:
         * - SQLite: file path (e.g., `/var/lib/idk/dids.db`) or `:memory:` for ephemeral.
         * - PostgreSQL / MySQL: full JDBC URL (`jdbc:postgresql://host:5432/db`).
         * - Memory: ignored.
         */
        val connectionUrl: String? = null,
        val username: String? = null,
        val password: String? = null,
        /** Connection pool size for JDBC dialects. Ignored by memory and sqlite. */
        val poolSize: Int? = null,
        /**
         * Free-form key/value bag for dialect-specific options that don't fit the typed
         * fields above. Keys are dialect-defined; consult the dialect's documentation.
         */
        val properties: Map<String, String> = emptyMap(),
    ) {
        companion object {
            const val TYPE_MEMORY: String = "memory"
            const val TYPE_SQLITE: String = "sqlite"
            const val TYPE_POSTGRESQL: String = "postgresql"
            const val TYPE_MYSQL: String = "mysql"

            /** Property prefix for [DidPersistenceConfig] in the application config. */
            const val PROPERTY_PREFIX: String = "did.persistence"
        }
    }
