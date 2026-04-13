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

package com.sphereon.identity.reconciliation.model

import com.sphereon.identity.matching.crypto.EncryptedPayload
import com.sphereon.identity.matching.model.IdentifierType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ReconciliationSession(
    val id: String,
    val tenantId: String,
    val status: ReconciliationSessionStatus,
    val identifierHash: String,
    val identifierType: IdentifierType,
    val providerId: String,
    val authorizationUrl: String? = null,
    val state: String? = null,
    val nonce: String? = null,
    val codeVerifier: String? = null,
    val redirectUri: String? = null,
    val tokenEndpoint: String? = null,
    val encryptedIdentity: EncryptedPayload? = null,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
)
