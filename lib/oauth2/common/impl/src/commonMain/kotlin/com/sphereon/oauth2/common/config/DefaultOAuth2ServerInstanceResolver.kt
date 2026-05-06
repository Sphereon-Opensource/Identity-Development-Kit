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

package com.sphereon.oauth2.common.config

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default IDK implementation: matches the request URL against each configured
 * server's `issuer`, then `issuerTemplate`. Single-AS deployments short-circuit
 * to the only configured id. Multi-AS deployments fall back to
 * [OAuth2ServersConfig.defaultServer] when no discriminator matches.
 *
 * Tenant-aware routing strategies are contributed by higher layers via a
 * binding that replaces this default.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OAuth2ServerInstanceResolver>())
class DefaultOAuth2ServerInstanceResolver(
    private val configProvider: OAuth2ServersConfigProvider,
) : OAuth2ServerInstanceResolver {
    override suspend fun resolve(request: GenericHttpRequest): IdkResult<String, IdkError> {
        val all = configProvider.getConfig()
        val instances = all.servers

        if (instances.size == 1) {
            return Ok(instances.keys.single())
        }

        val requestUrl = request.requestUrl()
        val match = instances.entries.firstOrNull { (_, cfg) -> matches(requestUrl, cfg) }
        if (match != null) {
            return Ok(match.key)
        }

        val defaultId = all.defaultServer
        if (instances.containsKey(defaultId)) {
            return Ok(defaultId)
        }

        return Err(
            IdkError.NOT_FOUND_ERROR(
                message =
                    "Cannot resolve OAuth2 AS instance for request URL '$requestUrl' " +
                        "(configured ids: ${instances.keys}, defaultServer '$defaultId' not in servers map).",
            ),
        )
    }

    private fun matches(
        requestUrl: String,
        cfg: OAuth2ServerInstanceConfig,
    ): Boolean {
        cfg.issuer?.let { issuer ->
            if (requestUrl.startsWith(issuer.trimEnd('/'))) return true
        }
        cfg.issuerTemplate?.let { template ->
            if (templateMatches(template, requestUrl)) return true
        }
        return false
    }

    private fun templateMatches(
        template: String,
        requestUrl: String,
    ): Boolean {
        val regexBody =
            buildString {
                var i = 0
                while (i < template.length) {
                    val ch = template[i]
                    if (ch == '{') {
                        val close = template.indexOf('}', i)
                        if (close < 0) {
                            append(escapeRegex(template.substring(i)))
                            break
                        }
                        append("[^/]+")
                        i = close + 1
                    } else {
                        append(escapeRegex(ch.toString()))
                        i++
                    }
                }
            }
        val pattern = Regex("^$regexBody(/.*)?$")
        return pattern.containsMatchIn(requestUrl) || pattern.matches(requestUrl)
    }

    private fun escapeRegex(literal: String): String =
        buildString(literal.length) {
            for (c in literal) {
                if (c in REGEX_META) append('\\')
                append(c)
            }
        }

    private companion object {
        private val REGEX_META = setOf('.', '+', '*', '?', '(', ')', '[', ']', '{', '}', '|', '^', '$', '\\', '/')
    }
}

/**
 * Reconstruct the full request URL from forwarded headers + path so the resolver can
 * compare against `issuer` / `issuerTemplate` discriminators. Mirrors the
 * forwarded-header handling used by the OAuth2 HTTP adapter's `resolveBaseUrl`.
 */
private fun GenericHttpRequest.requestUrl(): String {
    val proto =
        headers["x-forwarded-proto"]
            ?: headers["X-Forwarded-Proto"]
            ?: headers["X-FORWARDED-PROTO"]
    val scheme = if (proto.equals("https", ignoreCase = true)) "https" else "http"
    val host = headers["host"] ?: headers["Host"] ?: "localhost"
    return "$scheme://$host$path"
}
