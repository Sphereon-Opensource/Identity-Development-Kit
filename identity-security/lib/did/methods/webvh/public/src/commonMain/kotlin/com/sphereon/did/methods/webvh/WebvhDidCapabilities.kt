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
 *
 */

package com.sphereon.did.methods.webvh

import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.capabilities.KeyManagementCapabilities
import com.sphereon.did.capabilities.LifecycleCapabilities
import com.sphereon.did.capabilities.MethodMetadata
import com.sphereon.did.capabilities.RepresentationCapabilities
import com.sphereon.did.capabilities.ResolutionCapabilities
import com.sphereon.did.capabilities.ServiceManagementCapabilities
import com.sphereon.did.capabilities.UsageRecommendations

/**
 * Capabilities for the `did:webvh` v1.0 DID method.
 *
 * Spec: https://identity.foundation/didwebvh/v1.0/
 *
 * `did:webvh` is a mutable DID method whose DID document and version history
 * is published as a JSON Lines log (`did.jsonl`) at a web location, with
 * each log entry signed by the controller's authorized update keys via a
 * W3C Data Integrity proof. Optional pre-rotation, witnesses, and watchers
 * provide additional integrity properties.
 */
object WebvhDidCapabilities {
    const val METHOD: String = "webvh"
    const val SPEC_VERSION: String = "did:webvh:1.0"

    /** Spec §3.2 default `parameters.ttl` value (seconds). */
    const val DEFAULT_TTL_SECONDS: Long = 3600

    val CAPABILITIES: DidMethodCapabilities =
        DidMethodCapabilities(
            method = METHOD,
            lifecycle =
                LifecycleCapabilities(
                    create = true,
                    update = true,
                    deactivate = true,
                    delete = false,
                ),
            keyManagement =
                KeyManagementCapabilities(
                    addition = true,
                    replacement = true,
                    removal = true,
                    supportedKeyTypes =
                        listOf(
                            KeyManagementCapabilities.ED25519,
                            KeyManagementCapabilities.P256,
                            KeyManagementCapabilities.P384,
                        ),
                ),
            serviceManagement =
                ServiceManagementCapabilities(
                    addition = true,
                    replacement = true,
                    removal = true,
                ),
            representations =
                RepresentationCapabilities(
                    jsonWebKey2020 = true,
                    multikey = true,
                    ed25519VerificationKey2020 = true,
                ),
            resolution =
                ResolutionCapabilities(
                    allowsCaching = true,
                    allowsLocalRecord = false,
                    cacheTtlSeconds = DEFAULT_TTL_SECONDS,
                ),
            recommendations =
                UsageRecommendations(
                    naturalPersons = false,
                    organizations = true,
                    ephemeral = false,
                    longLived = true,
                ),
            metadata =
                MethodMetadata(
                    description = "DID method with verifiable history hosted at a web domain via HTTPS",
                    specificationUrl = "https://identity.foundation/didwebvh/v1.0/",
                    version = "1.0",
                ),
        )
}
