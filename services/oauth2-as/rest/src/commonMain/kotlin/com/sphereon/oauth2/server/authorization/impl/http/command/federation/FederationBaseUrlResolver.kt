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

package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider

/*
 * Shared base-URL resolution for the federation HTTP endpoint commands. Honours
 * `X-Forwarded-Proto` / `Host` when no issuer is configured so the upstream IdP redirect URL
 * stays consistent behind reverse proxies.
 */

internal fun GenericHttpRequest.resolveFederationBaseUrl(configProvider: OAuth2ServersConfigProvider): String {
    val configuredIssuer = configProvider.serverConfig.issuer?.trimEnd('/')
    if (configuredIssuer != null) return configuredIssuer
    val host = headers["host"] ?: headers["Host"] ?: "localhost"
    val proto =
        headers["x-forwarded-proto"]
            ?: headers["X-Forwarded-Proto"]
            ?: headers["X-FORWARDED-PROTO"]
    val scheme = if (proto.equals("https", ignoreCase = true)) "https" else "http"
    return "$scheme://$host"
}
