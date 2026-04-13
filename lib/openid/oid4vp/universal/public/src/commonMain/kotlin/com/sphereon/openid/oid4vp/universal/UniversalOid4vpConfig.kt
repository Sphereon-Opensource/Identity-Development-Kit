/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal

import kotlinx.serialization.Serializable

/**
 * Configuration for the Universal OID4VP module.
 *
 * Properties are loaded from IDK's ConfigService using the prefix "oid4vp.universal".
 *
 * Example:
 * ```properties
 * oid4vp.universal.external-base-url=https://verifier.example.com
 * ```
 *
 * @property externalBaseUrl The externally reachable base URL for this OID4VP verifier.
 *                           Used to construct absolute request URIs in openid4vp:// authorization
 *                           requests so that mobile wallets can fetch the request object.
 *                           Example: "https://verifier.example.com" will produce request URIs like
 *                           "https://verifier.example.com/oid4vp/request-uri/{correlationId}".
 *                           If null, request URIs are returned as relative paths.
 */
@Serializable
data class UniversalOid4vpConfig(
    val externalBaseUrl: String? = null,
    val responseUri: String? = null
) {
    companion object {
        const val CONFIG_PREFIX = "oid4vp.universal"
    }
}

/**
 * Provider interface for [UniversalOid4vpConfig].
 */
interface UniversalOid4vpConfigProvider {
    fun getConfig(): UniversalOid4vpConfig
}
