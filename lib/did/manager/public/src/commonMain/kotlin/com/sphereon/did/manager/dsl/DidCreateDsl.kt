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

package com.sphereon.did.manager.dsl

import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose

/**
 * DSL marker for DID creation builders.
 */
@DslMarker
annotation class DidCreationDsl

/**
 * Configuration for key generation.
 */
sealed class KeyConfig {
    /**
     * Configuration for auto-generating a key via KMS.
     *
     * @property keyType The key type (OKP, EC, RSA)
     * @property curve The elliptic curve (Ed25519, P-256, etc.)
     * @property algorithm Optional signature algorithm
     * @property kmsProvider Optional KMS provider ID (uses default if not specified)
     * @property purposes Verification purposes for this key
     * @property verificationMethodId Custom ID for the verification method
     */
    data class AutoGenerate(
        val keyType: KeyTypeMapping,
        val curve: Curve? = null,
        val algorithm: SignatureAlgorithm? = null,
        val kmsProvider: String? = null,
        val purposes: List<VerificationPurpose> = emptyList(),
        val verificationMethodId: String? = null,
        val alias: String? = null,
    ) : KeyConfig()

    /**
     * Configuration for using an existing key by alias.
     *
     * @property alias The key alias in KMS
     * @property providerId Optional KMS provider ID
     * @property purposes Verification purposes for this key
     * @property verificationMethodId Custom ID for the verification method
     */
    data class ExistingKeyByAlias(
        val alias: String,
        val providerId: String? = null,
        val purposes: List<VerificationPurpose> = emptyList(),
        val verificationMethodId: String? = null,
    ) : KeyConfig()

    /**
     * Configuration for using an existing JWK directly.
     *
     * @property jwk The public key JWK
     * @property purposes Verification purposes for this key
     * @property verificationMethodId Custom ID for the verification method
     */
    data class ExistingJwk(
        val jwk: Jwk,
        val purposes: List<VerificationPurpose> = emptyList(),
        val verificationMethodId: String? = null,
    ) : KeyConfig()
}

/**
 * Builder for auto-generating keys via KMS.
 *
 * Example:
 * ```
 * autoGenerateKey {
 *     keyType(KeyTypeMapping.OKP)
 *     curve(Curve.Ed25519)
 *     purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
 * }
 * ```
 */
@DidCreationDsl
class AutoGenerateKeyBuilder {
    private var keyType: KeyTypeMapping? = null
    private var curve: Curve? = null
    private var algorithm: SignatureAlgorithm? = null
    private var kmsProvider: String? = null
    private var purposes: List<VerificationPurpose> = emptyList()
    private var verificationMethodId: String? = null

    fun keyType(type: KeyTypeMapping) {
        this.keyType = type
    }

    fun curve(curve: Curve) {
        this.curve = curve
    }

    fun algorithm(alg: SignatureAlgorithm) {
        this.algorithm = alg
    }

    fun kmsProvider(provider: String) {
        this.kmsProvider = provider
    }

    fun purposes(vararg purposes: VerificationPurpose) {
        this.purposes = purposes.toList()
    }

    fun verificationMethodId(id: String) {
        this.verificationMethodId = id
    }

    fun build(): KeyConfig.AutoGenerate =
        KeyConfig.AutoGenerate(
            keyType = keyType ?: error("keyType is required for auto-generated keys"),
            curve = curve,
            algorithm = algorithm,
            kmsProvider = kmsProvider,
            purposes = purposes,
            verificationMethodId = verificationMethodId,
        )
}

/**
 * Builder for using an existing key by alias.
 *
 * Example:
 * ```
 * useExistingKey {
 *     alias("my-signing-key")
 *     providerId("software-kms")
 *     purposes(VerificationPurpose.AUTHENTICATION)
 * }
 * ```
 */
@DidCreationDsl
class ExistingKeyByAliasBuilder {
    private var alias: String? = null
    private var providerId: String? = null
    private var purposes: List<VerificationPurpose> = emptyList()
    private var verificationMethodId: String? = null

    fun alias(alias: String) {
        this.alias = alias
    }

    fun providerId(id: String) {
        this.providerId = id
    }

    fun purposes(vararg purposes: VerificationPurpose) {
        this.purposes = purposes.toList()
    }

    fun verificationMethodId(id: String) {
        this.verificationMethodId = id
    }

    fun build(): KeyConfig.ExistingKeyByAlias =
        KeyConfig.ExistingKeyByAlias(
            alias = alias ?: error("alias is required for existing key lookup"),
            providerId = providerId,
            purposes = purposes,
            verificationMethodId = verificationMethodId,
        )
}

/**
 * Builder for verification methods (for multi-key DIDs like did:web).
 */
@DidCreationDsl
class VerificationMethodBuilder(
    private val id: String,
) {
    private var keyConfig: KeyConfig? = null
    private var purposes: List<VerificationPurpose> = emptyList()
    private var verificationMethodType: VerificationMethodType? = null

    fun autoGenerateKey(block: AutoGenerateKeyBuilder.() -> Unit) {
        val builder = AutoGenerateKeyBuilder().apply(block)
        keyConfig = builder.build().copy(verificationMethodId = id)
    }

    fun useExistingKey(block: ExistingKeyByAliasBuilder.() -> Unit) {
        val builder = ExistingKeyByAliasBuilder().apply(block)
        keyConfig = builder.build().copy(verificationMethodId = id)
    }

    fun useExistingKey(jwk: Jwk) {
        keyConfig = KeyConfig.ExistingJwk(jwk = jwk, verificationMethodId = id)
    }

    fun purposes(vararg purposes: VerificationPurpose) {
        this.purposes = purposes.toList()
    }

    fun type(type: VerificationMethodType) {
        this.verificationMethodType = type
    }

    internal fun build(): KeyConfig {
        val config = keyConfig ?: error("Key configuration is required for verification method: $id")
        // Apply purposes if they weren't set in the key config
        return when (config) {
            is KeyConfig.AutoGenerate -> {
                if (config.purposes.isEmpty() && purposes.isNotEmpty()) {
                    config.copy(purposes = purposes)
                } else {
                    config
                }
            }

            is KeyConfig.ExistingKeyByAlias -> {
                if (config.purposes.isEmpty() && purposes.isNotEmpty()) {
                    config.copy(purposes = purposes)
                } else {
                    config
                }
            }

            is KeyConfig.ExistingJwk -> {
                if (config.purposes.isEmpty() && purposes.isNotEmpty()) {
                    config.copy(purposes = purposes)
                } else {
                    config
                }
            }
        }
    }
}

/**
 * Builder for DID services.
 */
@DidCreationDsl
class ServiceBuilder(
    private val id: String,
) {
    private var type: String? = null
    private var endpoint: String? = null
    private var endpointMap: Map<String, Any>? = null

    fun type(type: String) {
        this.type = type
    }

    fun endpoint(endpoint: String) {
        this.endpoint = endpoint
    }

    fun endpoint(endpoints: Map<String, Any>) {
        this.endpointMap = endpoints
    }

    fun build(): DidService =
        DidService(
            id = id,
            type = type ?: error("Service type is required"),
            serviceEndpoint = endpoint ?: endpointMap?.toString() ?: error("Service endpoint is required"),
        )
}

/**
 * Main builder for creating DIDs with a friendly DSL.
 *
 * ## Examples
 *
 * ### Create did:key with auto-generated Ed25519 key
 * ```kotlin
 * val options = didCreateOptions {
 *     method("key")
 *     autoGenerateKey {
 *         keyType(KeyTypeMapping.OKP)
 *         curve(Curve.Ed25519)
 *         purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
 *     }
 *     alias("my-signing-did")
 * }
 * ```
 *
 * ### Create did:web with multiple keys
 * ```kotlin
 * val options = didCreateOptions {
 *     method("web")
 *     domain("example.com")
 *
 *     verificationMethod("key-1") {
 *         autoGenerateKey {
 *             keyType(KeyTypeMapping.OKP)
 *             curve(Curve.Ed25519)
 *         }
 *         purposes(VerificationPurpose.AUTHENTICATION)
 *     }
 *
 *     verificationMethod("key-2") {
 *         useExistingKey {
 *             alias("my-encryption-key")
 *         }
 *         purposes(VerificationPurpose.KEY_AGREEMENT)
 *     }
 *
 *     service("hub") {
 *         type("LinkedDomains")
 *         endpoint("https://example.com/.well-known/did-configuration.json")
 *     }
 * }
 * ```
 */
@DidCreationDsl
class DidCreateBuilder {
    private var method: String? = null
    private var alias: String? = null
    private var domain: String? = null
    private var path: List<String>? = null
    private var controller: String? = null
    private val keyConfigs: MutableList<KeyConfig> = mutableListOf()
    private val services: MutableList<DidService> = mutableListOf()
    private var verificationMethodType: VerificationMethodType? = null

    /**
     * Sets the DID method (e.g., "key", "web", "jwk").
     */
    fun method(method: String) {
        this.method = method
    }

    /**
     * Sets a human-readable alias for the DID.
     */
    fun alias(alias: String) {
        this.alias = alias
    }

    /**
     * Sets the domain for did:web (e.g., "example.com").
     */
    fun domain(domain: String) {
        this.domain = domain
    }

    /**
     * Sets the path segments for did:web (e.g., listOf("user", "alice")).
     */
    fun path(vararg segments: String) {
        this.path = segments.toList()
    }

    /**
     * Sets the controller DID (defaults to the DID being created).
     */
    fun controller(controller: String) {
        this.controller = controller
    }

    /**
     * Sets the verification method type.
     */
    fun verificationMethodType(type: VerificationMethodType) {
        this.verificationMethodType = type
    }

    /**
     * Configures auto-generation of a key via KMS.
     *
     * Use this for simple DIDs with a single key.
     */
    fun autoGenerateKey(block: AutoGenerateKeyBuilder.() -> Unit) {
        keyConfigs.add(AutoGenerateKeyBuilder().apply(block).build())
    }

    /**
     * Configures use of an existing key by alias.
     *
     * Use this for simple DIDs with a single key.
     */
    fun useExistingKey(block: ExistingKeyByAliasBuilder.() -> Unit) {
        keyConfigs.add(ExistingKeyByAliasBuilder().apply(block).build())
    }

    /**
     * Configures use of an existing JWK.
     *
     * Use this for simple DIDs with a single key.
     */
    fun useExistingKey(
        jwk: Jwk,
        block: (KeyConfig.ExistingJwk.() -> Unit)? = null,
    ) {
        val config = KeyConfig.ExistingJwk(jwk = jwk)
        keyConfigs.add(config)
    }

    /**
     * Adds a verification method with custom configuration.
     *
     * Use this for multi-key DIDs like did:web.
     */
    fun verificationMethod(
        id: String,
        block: VerificationMethodBuilder.() -> Unit,
    ) {
        keyConfigs.add(VerificationMethodBuilder(id).apply(block).build())
    }

    /**
     * Adds a service to the DID document.
     */
    fun service(
        id: String,
        block: ServiceBuilder.() -> Unit,
    ) {
        services.add(ServiceBuilder(id).apply(block).build())
    }

    /**
     * Builds the DidCreateOptions from the DSL configuration.
     */
    fun build(): DidCreationDslResult {
        val methodName = method ?: error("DID method is required")
        val primaryKeyConfig = keyConfigs.firstOrNull()

        // Extract the primary key's JWK if available
        val publicKeyJwk =
            when (primaryKeyConfig) {
                is KeyConfig.ExistingJwk -> primaryKeyConfig.jwk
                else -> null
            }

        // Extract purposes from the primary key config
        val purposes =
            when (primaryKeyConfig) {
                is KeyConfig.AutoGenerate -> primaryKeyConfig.purposes.takeIf { it.isNotEmpty() }
                is KeyConfig.ExistingKeyByAlias -> primaryKeyConfig.purposes.takeIf { it.isNotEmpty() }
                is KeyConfig.ExistingJwk -> primaryKeyConfig.purposes.takeIf { it.isNotEmpty() }
                null -> null
            }

        // Extract verification method ID from primary key config
        val verificationMethodId =
            when (primaryKeyConfig) {
                is KeyConfig.AutoGenerate -> primaryKeyConfig.verificationMethodId
                is KeyConfig.ExistingKeyByAlias -> primaryKeyConfig.verificationMethodId
                is KeyConfig.ExistingJwk -> primaryKeyConfig.verificationMethodId
                null -> null
            }

        val options =
            DidCreateOptions(
                method = methodName,
                alias = alias,
                domain = domain,
                path = path,
                publicKeyJwk = publicKeyJwk,
                verificationMethodId = verificationMethodId,
                verificationMethodType = verificationMethodType,
                controller = controller,
                purposes = purposes,
                services = services.takeIf { it.isNotEmpty() },
            )

        return DidCreationDslResult(
            options = options,
            keyConfigs = keyConfigs,
        )
    }
}

/**
 * Result of the DID creation DSL.
 *
 * Contains both the standard [DidCreateOptions] and the key configurations
 * that may require additional processing (e.g., KMS key generation).
 *
 * @property options The standard DID creation options
 * @property keyConfigs The key configurations that may need KMS processing
 */
data class DidCreationDslResult(
    val options: DidCreateOptions,
    val keyConfigs: List<KeyConfig>,
)

/**
 * Creates DID creation options using a DSL.
 *
 * @param block The DSL configuration block
 * @return The configured DidCreationDslResult
 */
fun didCreateOptions(block: DidCreateBuilder.() -> Unit): DidCreationDslResult = DidCreateBuilder().apply(block).build()
