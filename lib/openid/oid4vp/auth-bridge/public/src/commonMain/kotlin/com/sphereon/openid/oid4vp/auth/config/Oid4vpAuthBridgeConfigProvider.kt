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

package com.sphereon.openid.oid4vp.auth.config

import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthBridgeConfig

/**
 * Interface for providing OID4VP Auth Bridge configuration.
 *
 * Implementations bind configuration from ConfigService or other sources.
 * The default implementation [com.sphereon.openid.oid4vp.auth.impl.config.Oid4vpAuthBridgeConfigBinder]
 * reads from IDK's ConfigService.
 */
interface Oid4vpAuthBridgeConfigProvider {
    /**
     * Get the current OID4VP Auth Bridge configuration.
     */
    fun getConfig(): Oid4vpAuthBridgeConfig
}
