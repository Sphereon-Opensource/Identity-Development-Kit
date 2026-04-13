/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.model

import com.sphereon.identity.idv.model.ConfigReference
import com.sphereon.identity.idv.model.SecretReference
import kotlinx.serialization.Serializable

@Serializable
data class OidcClientConfig(
    val id: String,
    val discoveryUrl: String,
    val clientIdRef: ConfigReference,
    val clientSecretRef: SecretReference,
    val scopes: List<String> = listOf("openid"),
    val userInfoEnabled: Boolean = false,
    val authorizationEndpointOverride: String? = null,
    val tokenEndpointOverride: String? = null,
)
