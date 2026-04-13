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

package com.sphereon.did.methods.jwk

import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.capabilities.KeyManagementCapabilities
import com.sphereon.did.capabilities.LifecycleCapabilities
import com.sphereon.did.capabilities.MethodMetadata
import com.sphereon.did.capabilities.RepresentationCapabilities
import com.sphereon.did.capabilities.ResolutionCapabilities
import com.sphereon.did.capabilities.ServiceManagementCapabilities
import com.sphereon.did.capabilities.UsageRecommendations

/**
 * Capabilities for the did:jwk DID method.
 *
 * did:jwk is an immutable DID method where the DID is derived from a
 * base64url-encoded JWK. Like did:key, it doesn't require any network
 * or storage - the DID document is computed from the JWK.
 *
 * The advantage of did:jwk over did:key is that it supports any JWK-compatible
 * key type and preserves all JWK parameters (like key_ops, alg, kid).
 */
object JwkDidCapabilities {

    /**
     * The DID method name.
     */
    const val METHOD: String = "jwk"

    /**
     * Full capabilities declaration for did:jwk.
     */
    val CAPABILITIES: DidMethodCapabilities = DidMethodCapabilities(
        method = METHOD,
        lifecycle = LifecycleCapabilities(
            create = true,
            update = false,  // Immutable
            deactivate = false,  // Cannot deactivate
            delete = false  // Cannot delete
        ),
        keyManagement = KeyManagementCapabilities(
            addition = false,  // Single key per DID
            replacement = false,
            removal = false,
            supportedKeyTypes = listOf(
                // did:jwk supports any JWK-compatible key type
                KeyManagementCapabilities.ED25519,
                KeyManagementCapabilities.X25519,
                KeyManagementCapabilities.SECP256K1,
                KeyManagementCapabilities.P256,
                KeyManagementCapabilities.P384,
                KeyManagementCapabilities.P521,
                KeyManagementCapabilities.RSA
            )
        ),
        serviceManagement = ServiceManagementCapabilities.NONE,
        representations = RepresentationCapabilities(
            jsonWebKey2020 = true,  // Native representation
            multikey = false,
            ed25519VerificationKey2020 = false
        ),
        resolution = ResolutionCapabilities(
            allowsCaching = false,  // No need - computed from JWK
            allowsLocalRecord = true,
            cacheTtlSeconds = null
        ),
        recommendations = UsageRecommendations(
            naturalPersons = true,
            organizations = false,  // Use did:web for organizations
            ephemeral = true,  // Good for short-lived/session keys
            longLived = false  // No key rotation capability
        ),
        metadata = MethodMetadata(
            description = "Static DID derived from a base64url-encoded JWK",
            specificationUrl = "https://github.com/quartzjer/did-jwk/blob/main/spec.md",
            version = "1.0"
        )
    )
}
