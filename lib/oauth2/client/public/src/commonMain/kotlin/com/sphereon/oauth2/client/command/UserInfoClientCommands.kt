/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmOverloads

/**
 * Arguments for fetching UserInfo from an OIDC Provider
 */
@JsExportCompat
data class FetchUserInfoArgs(
    val accessToken: String,
    val userinfoEndpoint: String,
)

/**
 * UserInfo response from the OIDC Provider
 */
@JsExportCompat
@Serializable
data class FetchUserInfoResult
    @JvmOverloads
    constructor(
        val sub: String,
        @property:JsExportIgnoreCompat
        val claims: Map<String, JsonElement> = emptyMap(),
    )

/**
 * Fetch UserInfo command
 *
 * OpenID Connect Core 1.0 Section 5.3: UserInfo Endpoint
 *
 * Fetches claims about the authenticated End-User from the
 * UserInfo endpoint using a Bearer access token.
 */
@JsExportCompat
interface FetchUserInfoCommand : ServiceCommand<FetchUserInfoArgs, FetchUserInfoResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.client.userinfo.fetch"
    }
}
