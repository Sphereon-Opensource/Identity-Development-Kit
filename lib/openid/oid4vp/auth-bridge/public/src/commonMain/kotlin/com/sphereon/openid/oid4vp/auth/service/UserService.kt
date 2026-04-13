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

package com.sphereon.openid.oid4vp.auth.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vp.auth.input.CreateUserInput
import com.sphereon.openid.oid4vp.auth.model.ResolvedUser

/**
 * Service for user identity management operations.
 *
 * This service provides operations to lookup and create users in the VDX User Microservice.
 * It separates the concerns of user lookup and user creation into two distinct methods.
 *
 * ## Usage
 *
 * ```kotlin
 * // Lookup user by identifier
 * val existingUser = userService.lookupUser(identifier).getOrNull()
 *
 * // Create user if not found
 * if (existingUser == null && autoCreate) {
 *     val newUser = userService.createUser(CreateUserInput(username = identifier))
 * }
 * ```
 *
 * ## Implementation
 *
 * The default implementation [com.sphereon.openid.oid4vp.auth.impl.service.UserServiceImpl]
 * calls the VDX User Microservice REST API endpoints:
 * - GET /api/users/v1/users?username={identifier} for lookup
 * - POST /api/users/v1/users for creation
 */
interface UserService {
    /**
     * Look up a user by their identifier (username).
     *
     * @param identifier The user identifier to search for (typically extracted from credentials).
     * @return The user if found, null if not found, or an error.
     */
    suspend fun lookupUser(identifier: String): IdkResult<ResolvedUser?, IdkError>

    /**
     * Create a new user.
     *
     * @param input The user creation input.
     * @return The created user, or an error if creation failed.
     */
    suspend fun createUser(input: CreateUserInput): IdkResult<ResolvedUser, IdkError>
}
