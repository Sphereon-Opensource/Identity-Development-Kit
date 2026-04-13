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

package com.sphereon.openid.oid4vp.auth.input

import com.sphereon.openid.oid4vp.auth.model.UserType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Input data for creating a new user via the VDX User Microservice.
 *
 * This is a local model that mirrors the VDX User API request format but is not
 * imported from VDX to maintain separation between the auth-bridge and VDX projects.
 *
 * @property username The unique username within the tenant (required).
 * @property email The user's email address (optional).
 * @property enabled Whether the user account is enabled (default: true).
 * @property userType The type of user (default: EXTERNAL for OID4VP-authenticated users).
 * @property displayName The display name of the user (optional).
 */
@Serializable
data class CreateUserInput(
    @SerialName("username")
    val username: String,

    @SerialName("email")
    val email: String? = null,

    @SerialName("enabled")
    val enabled: Boolean = true,

    @SerialName("userType")
    val userType: UserType = UserType.EXTERNAL,

    @SerialName("displayName")
    val displayName: String? = null
)
