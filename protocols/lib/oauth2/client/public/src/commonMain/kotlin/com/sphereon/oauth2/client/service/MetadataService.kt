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

package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchJwksCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for fetching and managing OAuth 2.0 metadata
 *
 * Provides access to:
 * - Authorization Server Metadata (RFC 8414)
 * - JWK Sets (RFC 7517)
 *
 * This service follows the Command/Service pattern, delegating to command implementations
 * for testability and consistency with other IDK services.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MetadataService", exact = true)
interface MetadataService {
    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all metadata commands
     */
    interface Commands {
        val fetchAuthorizationServerMetadata: FetchAuthorizationServerMetadataCommand
        val fetchJwks: FetchJwksCommand
    }

    /**
     * Fetches authorization server metadata from well-known endpoints
     *
     * @param issuer The authorization server's issuer identifier (HTTPS URL)
     * @return IdkResult containing metadata or error
     */
    suspend fun fetchAuthorizationServerMetadata(issuer: String): IdkResult<AuthorizationServerMetadata, IdkError>

    /**
     * Fetches JWK Set from the specified jwks_uri
     *
     * @param jwksUri The URL to the JWK Set document (HTTPS URL)
     * @return IdkResult containing JWK Set or error
     */
    suspend fun fetchJwks(jwksUri: String): IdkResult<JwkSet, IdkError>
}
