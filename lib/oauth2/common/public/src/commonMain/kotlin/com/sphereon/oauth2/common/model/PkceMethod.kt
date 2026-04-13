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

package com.sphereon.oauth2.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PKCE (Proof Key for Code Exchange) method (RFC 7636)
 *
 * Used by both:
 * - Authorization Servers to advertise supported methods in metadata
 * - Clients to specify which method they're using
 * - Authorization Servers to validate code challenges
 */
@Serializable
enum class PkceMethod(
    val value: String,
) {
    @SerialName("plain")
    PLAIN("plain"),

    @SerialName("S256")
    S256("S256"),
}
