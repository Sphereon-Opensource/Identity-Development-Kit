/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.ktor.http.client.provider

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin

/**
 * Validates every request this client sends against [policy]. The check runs in the send phase, which the redirect
 * plugin re-enters for each hop, so a redirect target is validated exactly like the original URL before it is
 * followed. Resolved-address checks are added by the platform engine (JVM); this is the literal-URL layer.
 */
internal fun HttpClient.installUrlValidation(policy: UrlValidationPolicy) {
    plugin(HttpSend).intercept { request ->
        policy.validate(request.url.build())
        execute(request)
    }
}
