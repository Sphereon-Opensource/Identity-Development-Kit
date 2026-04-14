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

package com.sphereon.did.capabilities

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Declares capabilities of a DID method.
 *
 * This structured capability system provides a comprehensive description of what
 * operations and features a DID method supports. It is organized into logical
 * groups for clarity and extensibility.
 *
 * @property method The DID method name (e.g., "key", "jwk", "web")
 * @property lifecycle Lifecycle operation capabilities (create, update, deactivate, delete)
 * @property keyManagement Key management capabilities and supported key types
 * @property serviceManagement Service endpoint management capabilities
 * @property representations Supported verification method representation formats
 * @property resolution Resolution and caching capabilities
 * @property recommendations Usage recommendations (guidance, not enforcement)
 * @property metadata Descriptive metadata about the method
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidMethodCapabilities", exact = true)
@JsExportCompat
@Serializable
data class DidMethodCapabilities
    @JvmOverloads
    constructor(
        val method: String,
        val lifecycle: LifecycleCapabilities = LifecycleCapabilities(),
        val keyManagement: KeyManagementCapabilities = KeyManagementCapabilities(),
        val serviceManagement: ServiceManagementCapabilities = ServiceManagementCapabilities(),
        val representations: RepresentationCapabilities = RepresentationCapabilities(),
        val resolution: ResolutionCapabilities = ResolutionCapabilities(),
        val recommendations: UsageRecommendations = UsageRecommendations(),
        val metadata: MethodMetadata = MethodMetadata(),
    ) {
        /**
         * Checks if this method supports creation of new DIDs.
         */
        fun canCreate(): Boolean = lifecycle.create

        /**
         * Checks if this method supports updating existing DIDs.
         */
        fun canUpdate(): Boolean = lifecycle.update

        /**
         * Checks if this method supports deactivating DIDs.
         */
        fun canDeactivate(): Boolean = lifecycle.deactivate

        /**
         * Checks if this method is immutable (create-only, no updates).
         */
        fun isImmutable(): Boolean = !lifecycle.isMutable()

        /**
         * Checks if this method supports any modification operations.
         */
        fun isMutable(): Boolean = lifecycle.isMutable()

        /**
         * Checks if this method supports key management operations.
         */
        fun supportsKeyManagement(): Boolean = keyManagement.supportsKeyManagement()

        /**
         * Checks if this method supports service management operations.
         */
        fun supportsServiceManagement(): Boolean = serviceManagement.supportsServiceManagement()

        /**
         * Checks if caching is allowed for this method.
         */
        fun allowsCaching(): Boolean = resolution.allowsCaching

        companion object {
            /**
             * Default capabilities for did:key method.
             */
            @JvmStatic
            val KEY: DidMethodCapabilities =
                DidMethodCapabilities(
                    method = "key",
                    lifecycle = LifecycleCapabilities.IMMUTABLE,
                    keyManagement =
                        KeyManagementCapabilities(
                            addition = false,
                            supportedKeyTypes =
                                listOf(
                                    KeyManagementCapabilities.ED25519,
                                    KeyManagementCapabilities.X25519,
                                    KeyManagementCapabilities.SECP256K1,
                                    KeyManagementCapabilities.P256,
                                    KeyManagementCapabilities.P384,
                                ),
                        ),
                    serviceManagement = ServiceManagementCapabilities.NONE,
                    representations = RepresentationCapabilities.JWK_AND_MULTIKEY,
                    resolution = ResolutionCapabilities.NO_CACHING,
                    recommendations = UsageRecommendations.EPHEMERAL_PERSONAL,
                    metadata =
                        MethodMetadata(
                            description = "Static DID derived from public key",
                            specificationUrl = "https://w3c-ccg.github.io/did-method-key/",
                        ),
                )

            /**
             * Default capabilities for did:jwk method.
             */
            @JvmStatic
            val JWK: DidMethodCapabilities =
                DidMethodCapabilities(
                    method = "jwk",
                    lifecycle = LifecycleCapabilities.IMMUTABLE,
                    keyManagement =
                        KeyManagementCapabilities(
                            addition = false,
                            supportedKeyTypes =
                                listOf(
                                    KeyManagementCapabilities.ED25519,
                                    KeyManagementCapabilities.SECP256K1,
                                    KeyManagementCapabilities.P256,
                                    KeyManagementCapabilities.P384,
                                ),
                        ),
                    serviceManagement = ServiceManagementCapabilities.NONE,
                    representations = RepresentationCapabilities.JWK_ONLY,
                    resolution = ResolutionCapabilities.NO_CACHING,
                    recommendations = UsageRecommendations.EPHEMERAL_PERSONAL,
                    metadata =
                        MethodMetadata(
                            description = "Static DID containing base64url-encoded JWK",
                            specificationUrl = "https://github.com/quartzjer/did-jwk/blob/main/spec.md",
                        ),
                )

            /**
             * Default capabilities for did:web method.
             */
            @JvmStatic
            val WEB: DidMethodCapabilities =
                DidMethodCapabilities(
                    method = "web",
                    lifecycle = LifecycleCapabilities.FULL,
                    keyManagement =
                        KeyManagementCapabilities(
                            addition = true,
                            replacement = true,
                            removal = true,
                            supportedKeyTypes =
                                listOf(
                                    KeyManagementCapabilities.ED25519,
                                    KeyManagementCapabilities.SECP256K1,
                                    KeyManagementCapabilities.P256,
                                    KeyManagementCapabilities.P384,
                                ),
                        ),
                    serviceManagement = ServiceManagementCapabilities.FULL,
                    representations = RepresentationCapabilities.JWK_AND_MULTIKEY,
                    resolution = ResolutionCapabilities.WEB_CACHING,
                    recommendations = UsageRecommendations.ORGANIZATIONAL,
                    metadata =
                        MethodMetadata(
                            description = "DID method using web domain verification",
                            specificationUrl = "https://w3c-ccg.github.io/did-method-web/",
                        ),
                )

            /**
             * Capabilities for a universal resolver that supports multiple methods.
             */
            @JvmStatic
            val UNIVERSAL_RESOLVER: DidMethodCapabilities =
                DidMethodCapabilities(
                    method = "*",
                    lifecycle = LifecycleCapabilities.IMMUTABLE, // Resolution only
                    keyManagement = KeyManagementCapabilities.NONE,
                    serviceManagement = ServiceManagementCapabilities.NONE,
                    representations = RepresentationCapabilities.ALL,
                    resolution =
                        ResolutionCapabilities(
                            allowsCaching = true,
                            allowsLocalRecord = true,
                            cacheTtlSeconds = 300,
                        ),
                    recommendations = UsageRecommendations(),
                    metadata =
                        MethodMetadata(
                            description = "Universal resolver supporting multiple DID methods",
                            specificationUrl = "https://dev.uniresolver.io/",
                        ),
                )
        }
    }
