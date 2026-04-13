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

package com.sphereon.did.methods.web

import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.capabilities.KeyManagementCapabilities
import com.sphereon.did.capabilities.LifecycleCapabilities
import com.sphereon.did.capabilities.MethodMetadata
import com.sphereon.did.capabilities.RepresentationCapabilities
import com.sphereon.did.capabilities.ResolutionCapabilities
import com.sphereon.did.capabilities.ServiceManagementCapabilities
import com.sphereon.did.capabilities.UsageRecommendations

/**
 * Capabilities for the did:web DID method.
 *
 * did:web is a mutable DID method where the DID document is hosted at a
 * web location. It supports the full DID lifecycle: create, update,
 * deactivate, and delete.
 *
 * The DID is derived from a domain name and optional path, and the
 * DID document is fetched via HTTPS.
 */
object WebDidCapabilities {

    /**
     * The DID method name.
     */
    const val METHOD: String = "web"

    /**
     * Default cache TTL for did:web resolution (5 minutes).
     */
    const val DEFAULT_CACHE_TTL_SECONDS: Long = 300

    /**
     * Full capabilities declaration for did:web.
     */
    val CAPABILITIES: DidMethodCapabilities = DidMethodCapabilities(
        method = METHOD,
        lifecycle = LifecycleCapabilities(
            create = true,
            update = true,  // Mutable - can update
            deactivate = true,  // Can deactivate
            delete = true  // Can delete (remove from web server)
        ),
        keyManagement = KeyManagementCapabilities(
            addition = true,  // Can add keys
            replacement = true,  // Can replace keys
            removal = true,  // Can remove keys
            supportedKeyTypes = listOf(
                // did:web supports any JWK-compatible key type
                KeyManagementCapabilities.ED25519,
                KeyManagementCapabilities.X25519,
                KeyManagementCapabilities.SECP256K1,
                KeyManagementCapabilities.P256,
                KeyManagementCapabilities.P384,
                KeyManagementCapabilities.P521,
                KeyManagementCapabilities.RSA
            )
        ),
        serviceManagement = ServiceManagementCapabilities(
            addition = true,
            replacement = true,
            removal = true
        ),
        representations = RepresentationCapabilities(
            jsonWebKey2020 = true,
            multikey = true,
            ed25519VerificationKey2020 = true
        ),
        resolution = ResolutionCapabilities(
            allowsCaching = true,  // Caching recommended
            allowsLocalRecord = true,
            cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS
        ),
        recommendations = UsageRecommendations(
            naturalPersons = false,  // Use did:key for natural persons
            organizations = true,  // Best for organizations
            ephemeral = false,  // Not suitable for ephemeral use
            longLived = true  // Good for long-lived DIDs
        ),
        metadata = MethodMetadata(
            description = "DID document hosted at a web domain via HTTPS",
            specificationUrl = "https://w3c-ccg.github.io/did-method-web/",
            version = "1.0"
        )
    )
}
