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

package com.sphereon.did.manager.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidManager
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.manager.dsl.DidCreateBuilder
import com.sphereon.did.manager.dsl.DidCreationDslResult
import com.sphereon.did.manager.dsl.KeyConfig
import com.sphereon.did.manager.dsl.didCreateOptions
import com.sphereon.did.models.VerificationMethodConfig
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.di.session.SessionScope
import kotlinx.datetime.Clock
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Service for processing DID creation DSL and generating keys via KMS.
 *
 * This service bridges the user-friendly DSL with the actual DID creation process by:
 * 1. Processing [KeyConfig] entries to generate or lookup keys
 * 2. Creating [VerificationMethodConfig] entries for the DID manager
 * 3. Delegating to [DidManager] for the actual DID creation
 */
interface DidCreationDslProcessor {
    /**
     * Creates a DID using the DSL builder pattern.
     *
     * @param block The DSL configuration block
     * @return The created ManagedDid or an error
     */
    suspend fun create(block: DidCreateBuilder.() -> Unit): IdkResult<ManagedDid, IdkError>

    /**
     * Creates a DID from an already-built DSL result.
     *
     * @param dslResult The DSL result containing options and key configs
     * @return The created ManagedDid or an error
     */
    suspend fun create(dslResult: DidCreationDslResult): IdkResult<ManagedDid, IdkError>
}

/**
 * Implementation of [DidCreationDslProcessor].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidCreationDslProcessor>())
class DidCreationDslProcessorImpl(
    private val didManager: DidManager,
    private val keyManagerService: KeyManagerService
) : DidCreationDslProcessor {

    override suspend fun create(block: DidCreateBuilder.() -> Unit): IdkResult<ManagedDid, IdkError> {
        val dslResult = didCreateOptions(block)
        return create(dslResult)
    }

    override suspend fun create(dslResult: DidCreationDslResult): IdkResult<ManagedDid, IdkError> {
        val options = dslResult.options
        val keyConfigs = dslResult.keyConfigs

        // If no key configs, use options as-is (legacy path)
        if (keyConfigs.isEmpty()) {
            return didManager.create(options)
        }

        // Process key configs to generate/lookup keys and create verification method configs
        val verificationMethods = mutableListOf<VerificationMethodConfig>()
        var primaryJwk: Jwk? = options.publicKeyJwk

        for ((index, keyConfig) in keyConfigs.withIndex()) {
            val processedKey = processKeyConfig(keyConfig, index).getOrElse {
                return Err(it)
            }

            // First key becomes the primary key if no explicit JWK was set
            if (index == 0 && primaryJwk == null) {
                primaryJwk = processedKey.jwk
            }

            verificationMethods.add(processedKey.verificationMethodConfig)
        }

        // Build the final options with processed verification methods
        val finalOptions = options.copy(
            publicKeyJwk = primaryJwk,
            verificationMethods = verificationMethods
        )

        return didManager.create(finalOptions)
    }

    private data class ProcessedKeyConfig(
        val jwk: Jwk,
        val verificationMethodConfig: VerificationMethodConfig
    )

    private suspend fun processKeyConfig(
        keyConfig: KeyConfig,
        index: Int
    ): IdkResult<ProcessedKeyConfig, IdkError> {
        return when (keyConfig) {
            is KeyConfig.AutoGenerate -> processAutoGenerate(keyConfig, index)
            is KeyConfig.ExistingKeyByAlias -> processExistingKeyByAlias(keyConfig, index)
            is KeyConfig.ExistingJwk -> processExistingJwk(keyConfig, index)
        }
    }

    private suspend fun processAutoGenerate(
        config: KeyConfig.AutoGenerate,
        index: Int
    ): IdkResult<ProcessedKeyConfig, IdkError> {
        // Determine signature algorithm from key type and curve
        val algorithm = config.algorithm ?: deriveSignatureAlgorithm(config)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Cannot determine signature algorithm for key type ${config.keyType} and curve ${config.curve}"
            ))

        // Generate the key via KMS
        val alias = config.alias ?: "did-vm-${Clock.System.now().epochSeconds}-$index"
        val keyPair = try {
            keyManagerService.generateKeyAsync(
                providerId = config.kmsProvider,
                alias = alias,
                alg = algorithm
            )
        } catch (e: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(
                message = "Failed to generate key: ${e.message}"
            ))
        }

        val publicJwk = keyPair.jose.publicJwk
        val providerId = config.kmsProvider ?: keyManagerService.defaultProviderId()
        val verificationMethodId = config.verificationMethodId ?: "key-${index + 1}"
        val purposes = config.purposes.ifEmpty { listOf(VerificationPurpose.AUTHENTICATION) }

        val vmConfig = VerificationMethodConfig(
            kmsKeyAlias = keyPair.alias,
            kmsProviderId = providerId,
            verificationMethodId = verificationMethodId,
            purposes = purposes,
            publicKeyJwk = publicJwk
        )

        return Ok(ProcessedKeyConfig(
            jwk = publicJwk,
            verificationMethodConfig = vmConfig
        ))
    }

    private suspend fun processExistingKeyByAlias(
        config: KeyConfig.ExistingKeyByAlias,
        index: Int
    ): IdkResult<ProcessedKeyConfig, IdkError> {
        val providerId = config.providerId ?: keyManagerService.defaultProviderId()

        // Lookup the key from KMS - KeyManagerService implements KeyStoreService directly
        val keyInfo = try {
            keyManagerService.getKey(
                com.sphereon.crypto.core.KeyInfo<com.sphereon.crypto.core.jose.JwkType>(
                    alias = config.alias,
                    providerId = providerId
                )
            )
        } catch (e: Exception) {
            return Err(IdkError.NOT_FOUND_ERROR(
                message = "Key with alias '${config.alias}' not found in provider '$providerId': ${e.message}"
            ))
        }

        val publicJwk = when (val key = keyInfo.key) {
            is Jwk -> key
            is com.sphereon.crypto.core.jose.JwkType -> Jwk.from(key)
            else -> return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Key with alias '${config.alias}' is not a JWK"
            ))
        }

        val verificationMethodId = config.verificationMethodId ?: "key-${index + 1}"
        val purposes = config.purposes.ifEmpty { listOf(VerificationPurpose.AUTHENTICATION) }

        val vmConfig = VerificationMethodConfig(
            kmsKeyAlias = config.alias,
            kmsProviderId = providerId,
            verificationMethodId = verificationMethodId,
            purposes = purposes,
            publicKeyJwk = publicJwk
        )

        return Ok(ProcessedKeyConfig(
            jwk = publicJwk,
            verificationMethodConfig = vmConfig
        ))
    }

    private fun processExistingJwk(
        config: KeyConfig.ExistingJwk,
        index: Int
    ): IdkResult<ProcessedKeyConfig, IdkError> {
        // For existing JWK, we don't have a KMS alias - we'll use a placeholder
        // The DID provider will use the JWK directly
        val verificationMethodId = config.verificationMethodId ?: "key-${index + 1}"
        val purposes = config.purposes.ifEmpty { listOf(VerificationPurpose.AUTHENTICATION) }

        // For direct JWK usage, we create a config with a synthetic alias
        // The provider will use the publicKeyJwk from the config
        val vmConfig = VerificationMethodConfig(
            kmsKeyAlias = "jwk:$verificationMethodId",
            kmsProviderId = "embedded",
            verificationMethodId = verificationMethodId,
            purposes = purposes,
            publicKeyJwk = config.jwk
        )

        return Ok(ProcessedKeyConfig(
            jwk = config.jwk,
            verificationMethodConfig = vmConfig
        ))
    }

    private fun deriveSignatureAlgorithm(config: KeyConfig.AutoGenerate): SignatureAlgorithm? {
        return when {
            config.curve == com.sphereon.crypto.core.generic.Curve.Ed25519 -> SignatureAlgorithm.ED25519
            config.curve == com.sphereon.crypto.core.generic.Curve.P_256 -> SignatureAlgorithm.ECDSA_SHA256
            config.curve == com.sphereon.crypto.core.generic.Curve.P_384 -> SignatureAlgorithm.ECDSA_SHA384
            config.curve == com.sphereon.crypto.core.generic.Curve.P_521 -> SignatureAlgorithm.ECDSA_SHA512
            config.curve == com.sphereon.crypto.core.generic.Curve.Secp256k1 -> SignatureAlgorithm.ES256K
            else -> null
        }
    }

    @ContributesTo(SessionScope::class)
    interface Component {
        val didCreationDslProcessor: DidCreationDslProcessor
    }
}

/**
 * Extension function to create a DID using the DSL directly on DidManager.
 *
 * Note: This requires the DidCreationDslProcessor to be available in the DI container.
 * For a standalone DidManager without DI, use DidCreationDslProcessor directly.
 *
 * Example usage:
 * ```kotlin
 * val processor = sessionComponent.didCreationDslProcessor
 * val result = processor.create {
 *     method("key")
 *     autoGenerateKey {
 *         keyType(KeyTypeMapping.OKP)
 *         curve(Curve.Ed25519)
 *         purposes(VerificationPurpose.AUTHENTICATION)
 *     }
 *     alias("my-signing-did")
 * }
 * ```
 */
