/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.jose.JwkType

/**
 * Multi-external identifier service interface that aggregates multiple external identifier services
 */
interface MultiExternalIdentifierService : ExternalIdentifierService

/**
 * Interface for JWK external identifier resolution service
 */
interface JwkExternalIdentifierResolutionService : ExternalIdentifierService {
    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.Jwk, IdkErrorType>
}

/**
 * Interface for JWKS URL external identifier resolution service
 */
interface JwksUrlExternalIdentifierResolutionService : ExternalIdentifierService {
    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.JwksUrl, IdkErrorType>
}

/**
 * Interface for CNF external identifier resolution service.
 */
interface CnfExternalIdentifierResolutionService : ExternalIdentifierService {
    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.Cnf, IdkErrorType>
}

/**
 * Interface for resolving JWK key material from a DID verification method.
 *
 * This is an abstraction that allows DID resolution to be plugged in without
 * creating a direct dependency from lib-crypto-core to lib-did-resolver.
 * Implementations are provided by lib-did-resolver-impl or similar modules.
 */
interface VerificationMethodKeyResolver {
    /**
     * Resolves a DID verification method and extracts its public key JWK.
     *
     * @param did The DID to resolve (e.g., "did:key:z6Mk...")
     * @param verificationMethodId Optional verification method ID (fragment like "key-1")
     * @return The public key JWK from the resolved verification method, or an error
     */
    suspend fun resolveVerificationMethodKey(did: String, verificationMethodId: String?): IdkResult<JwkType, IdkErrorType>
}
