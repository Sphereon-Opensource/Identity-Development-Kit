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

package com.sphereon.oauth2.server.authorization.signing

import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig

/** A local, non-serialized selection made from one authorization-server configuration read. */
class CapturedAsServerConfig private constructor(
    val serverKey: String?,
    val server: OAuth2ServerInstanceConfig?,
    val explicitlyConfigured: Boolean,
) {
    companion object {
        /** A configured root requires a trusted identifier that selects one server from this root. */
        fun select(root: OAuth2ServersConfig, requestedKey: String?): CapturedAsServerConfig {
            if (!root.explicitlyConfigured) {
                require(requestedKey == null) { "Authorization server '$requestedKey' is not configured" }
                return CapturedAsServerConfig(null, null, false)
            }
            require(!requestedKey.isNullOrBlank()) { "A configured authorization server requires a trusted server key" }
            val key = root.matchedServerKey(requestedKey)
                ?: error("Authorization server '$requestedKey' is missing or ambiguous in the captured configuration")
            val server = root.servers[key] ?: error("Authorization server '$key' is not configured")
            require(server.mode == AuthorizationServerMode.HOSTED) { "Authorization server '$key' is not hosted" }
            return CapturedAsServerConfig(key, server, true)
        }
    }
}

enum class AsSigningRequirement { NOT_REQUIRED, OPTIONAL, REQUIRED }

/** Local signing handle and algorithm observation; neither is a serialized public proof. */
data class AsSigningSelection(
    val identifier: ManagedOptsKeyInfo?,
    val algorithms: Set<String>,
)

/** Selects from one revision-accepted ACTIVE descriptor collection for a captured hosted AS. */
interface AsServerSigningIdentifierResolver {
    suspend fun selectSigning(
        captured: CapturedAsServerConfig,
        requirement: AsSigningRequirement,
        requestedAlgorithm: String? = null,
    ): AsSigningSelection
}
