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

package com.sphereon.identity.reconciliation.api

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Resolved OIDC connection details ready for use by reconciliation commands.
 * Portal resolves [OidcClientConfig] references into this at runtime.
 */
@JsExportCompat
@Serializable
data class ResolvedOidcConnection
    @JvmOverloads
    constructor(
        val discoveryUrl: String,
        val clientId: String,
        val clientSecret: String?,
        val scopes: List<String> = listOf("openid"),
        val userInfoEnabled: Boolean = false,
        val authorizationEndpointOverride: String? = null,
        val tokenEndpointOverride: String? = null,
    )

/**
 * Resolves OIDC client config references into usable connection details.
 *
 * Implementations are responsible for resolving [ConfigReference] and [SecretReference]
 * from the [OidcClientConfig] model into actual client ID/secret strings.
 */
@JsExportCompat
interface OidcConnectionResolver {
    suspend fun resolve(oidcClientId: String): ResolvedOidcConnection?
}
