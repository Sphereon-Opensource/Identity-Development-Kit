/*
 * Â© 2026 Sphereon International B.V.
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

/**
 * Resolves the database handle used by a DID persistence implementation.
 *
 * The database type is deliberately supplied by the concrete persistence module. The public
 * contract therefore carries no SQLDelight-generated schema or JDBC dependency, while final
 * application graphs can still bind tenant-aware routing explicitly.
 */
interface DidDatabaseResolver<out Database> {
    fun getDatabase(tenantId: String?): Database

    fun getCurrentTenantDatabase(label: String): Database
}
