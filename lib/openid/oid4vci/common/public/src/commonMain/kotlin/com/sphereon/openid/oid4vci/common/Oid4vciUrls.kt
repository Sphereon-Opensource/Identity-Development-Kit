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

package com.sphereon.openid.oid4vci.common

/**
 * URL construction utilities for OID4VCI endpoints.
 */
object Oid4vciUrls {
    /**
     * Builds the `.well-known/openid-credential-issuer` URL per OID4VCI 1.1 Section 13.2.
     *
     * The `.well-known` segment is inserted between the host (including port) and any path:
     * - `https://issuer.example.com`        → `https://issuer.example.com/.well-known/openid-credential-issuer`
     * - `https://issuer.example.com/tenant1` → `https://issuer.example.com/.well-known/openid-credential-issuer/tenant1`
     */
    fun buildWellKnownUrl(issuerUrl: String): String {
        val normalized = issuerUrl.trimEnd('/')
        val schemeEnd = normalized.indexOf("://") + SCHEME_SEPARATOR_LENGTH
        val pathStart = normalized.indexOf('/', schemeEnd)
        return if (pathStart < 0) {
            "$normalized/.well-known/openid-credential-issuer"
        } else {
            val host = normalized.substring(0, pathStart)
            val path = normalized.substring(pathStart)
            "$host/.well-known/openid-credential-issuer$path"
        }
    }

    private const val SCHEME_SEPARATOR_LENGTH = 3
}
