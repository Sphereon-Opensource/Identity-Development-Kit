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

package com.sphereon.crypto.kms

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EcdhUtils
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.GetAllCapabilitiesArgs
import com.sphereon.crypto.core.kms.GetAllCapabilitiesCommand
import com.sphereon.crypto.core.kms.GetAllCapabilitiesResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyResolverRegistry
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderFactory
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.ManagedKeyStoreService
import com.sphereon.crypto.core.kms.QueryProviderArgs
import com.sphereon.crypto.core.kms.QueryProviderCommand
import com.sphereon.crypto.core.kms.QueryProviderResult
import com.sphereon.crypto.core.kms.QueryProvidersArgs
import com.sphereon.crypto.core.kms.QueryProvidersCommand
import com.sphereon.crypto.core.kms.QueryProvidersResult
import com.sphereon.crypto.core.kms.command.CreateRawSignatureArgs
import com.sphereon.crypto.kms.command.CreateRawSignatureCommandImpl
import com.sphereon.crypto.core.kms.command.CreateRawSignatureResult
import com.sphereon.crypto.core.kms.command.DecryptArgs
import com.sphereon.crypto.core.kms.command.DecryptCommand
import com.sphereon.crypto.core.kms.command.DecryptResult
import com.sphereon.crypto.core.kms.command.DeleteKeyArgs
import com.sphereon.crypto.core.kms.command.DeleteKeyCommand
import com.sphereon.crypto.core.kms.command.DeleteKeyResult
import com.sphereon.crypto.core.kms.command.EncryptArgs
import com.sphereon.crypto.core.kms.command.EncryptCommand
import com.sphereon.crypto.core.kms.command.EncryptResult
import com.sphereon.crypto.core.kms.command.GenerateKeyArgs
import com.sphereon.crypto.core.kms.command.GenerateKeyCommand
import com.sphereon.crypto.core.kms.command.GenerateKeyResult
import com.sphereon.crypto.core.kms.command.GetKeyArgs
import com.sphereon.crypto.core.kms.command.GetKeyCommand
import com.sphereon.crypto.core.kms.command.GetKeyResult
import com.sphereon.crypto.core.kms.command.ListKeysArgs
import com.sphereon.crypto.core.kms.command.ListKeysCommand
import com.sphereon.crypto.core.kms.command.ListKeysResult
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementArgs
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementCommand
import com.sphereon.crypto.core.kms.command.PerformKeyAgreementResult
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyArgs
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyCommand
import com.sphereon.crypto.core.kms.command.ResolvePublicKeyResult
import com.sphereon.crypto.core.kms.command.SignDigestArgs
import com.sphereon.crypto.core.kms.command.SignDigestCommand
import com.sphereon.crypto.core.kms.command.SignDigestResult
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.core.kms.command.StoreKeyArgs
import com.sphereon.crypto.core.kms.command.StoreKeyCommand
import com.sphereon.crypto.core.kms.command.StoreKeyResult
import com.sphereon.crypto.core.kms.command.UnwrapKeyArgs
import com.sphereon.crypto.core.kms.command.UnwrapKeyCommand
import com.sphereon.crypto.core.kms.command.UnwrapKeyResult
import com.sphereon.crypto.core.kms.command.VerifyDigestArgs
import com.sphereon.crypto.core.kms.command.VerifyDigestCommand
import com.sphereon.crypto.core.kms.command.VerifyDigestResult
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureArgs
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureCommand
import com.sphereon.crypto.core.kms.command.VerifyRawSignatureResult
import com.sphereon.crypto.core.kms.command.WrapKeyArgs
import com.sphereon.crypto.core.kms.command.WrapKeyCommand
import com.sphereon.crypto.core.kms.command.WrapKeyResult
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.core.toKeyReferenceOrNull
import com.sphereon.crypto.core.toSigningKeyReferenceOrNull
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * KeyManagerService is an open class responsible for managing key management systems and key resolver services.
 * It provides functionality to register and retrieve key management systems (KMS) and key resolvers, as well as
 * generate and resolve public keys.
 *
 * This implementation delegates provider and resolver management to [KmsProviderRegistry] and [KeyResolverRegistry],
 * enabling commands to work independently without circular dependencies.
 *
 * @param providerRegistry The registry for KMS providers
 * @param resolverRegistry The registry for key resolvers
 * @param keyStoreService The managed key store service
 * @param execution The session execution context
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyManagerService>())
@ContributesBinding(SessionScope::class, binding = binding<SimpleSignatureService>())
@ObjCName("KeyManagerService")
open class KeyManagerServiceImpl
    @Inject
    constructor(
        private val providerRegistry: KmsProviderRegistry,
        private val resolverRegistry: KeyResolverRegistry,
        private val keyStoreService: ManagedKeyStoreService,
        private val execution: SessionExecution,
        // Query commands
        private val queryProviderCommand: QueryProviderCommand,
        private val queryProvidersCommand: QueryProvidersCommand,
        private val getAllCapabilitiesCommand: GetAllCapabilitiesCommand,
        // Signature commands
        // KeyManagerService is the LOCAL crypto implementation. Its internal delegation
        // must not re-enter config routing for the same command, otherwise a remotely
        // routed signature command can cycle through service-token creation back here.
        private val createRawSignatureCommand: CreateRawSignatureCommandImpl,
        private val verifyRawSignatureCommand: VerifyRawSignatureCommand,
        private val signDigestCommand: SignDigestCommand,
        private val verifyDigestCommand: VerifyDigestCommand,
        // Encryption commands
        private val encryptCommand: EncryptCommand,
        private val decryptCommand: DecryptCommand,
        private val wrapKeyCommand: WrapKeyCommand,
        private val unwrapKeyCommand: UnwrapKeyCommand,
        private val performKeyAgreementCommand: PerformKeyAgreementCommand,
        // Key management commands
        private val generateKeyCommand: GenerateKeyCommand,
        private val listKeysCommand: ListKeysCommand,
        private val getKeyCommand: GetKeyCommand,
        private val storeKeyCommand: StoreKeyCommand,
        private val deleteKeyCommand: DeleteKeyCommand,
        // Key resolution commands
        private val resolvePublicKeyCommand: ResolvePublicKeyCommand,
    ) : KeyManagerService {
        // Delegate keyStore to the injected keyStoreService
        override val keyStore: ManagedKeyStoreService
            get() = keyStoreService

        // Implement KeyStoreService methods by delegating to keyStore
        override val settings: com.sphereon.crypto.core.kms.model.KeyProviderSettings?
            get() = keyStore.settings

        // Delegate provider methods to providerRegistry
        override fun defaultProviderId() = providerRegistry.defaultProviderId()

        override fun getProviderIds() = providerRegistry.getProviderIds()

        override suspend fun getProviderById(id: String) = providerRegistry.getProviderById(id)

        override suspend fun getProvider(
            providerId: String?,
            alg: SignatureAlgorithm?,
        ) = providerRegistry.getProvider(providerId, alg)

        override suspend fun getKmsBySignatureAlgorithm(signatureAlgorithm: SignatureAlgorithm) =
            providerRegistry.getKmsBySignatureAlgorithm(signatureAlgorithm)

        override fun registerProvider(
            provider: KmsProvider,
            makeDefaultKms: Boolean?,
        ) = providerRegistry.registerProvider(provider, makeDefaultKms)

        // Delegate resolver methods to resolverRegistry
        override fun defaultResolverId() = resolverRegistry.defaultResolverId()

        override fun getResolverIds() = resolverRegistry.getResolverIds()

        override fun getResolverById(id: String) = resolverRegistry.getResolverById(id)

        override fun getResolverByKeyTypeOrIdentifier(
            identifierMethod: IdentifierMethod?,
            keyType: KeyTypeMapping?,
            resolverId: String?,
        ) = resolverRegistry.getResolverByKeyTypeOrIdentifier(identifierMethod, keyType, resolverId)

        override fun registerResolver(
            resolver: KeyResolverService,
            makeDefaultResolver: Boolean?,
        ) = resolverRegistry.registerResolver(resolver, makeDefaultResolver)

        override suspend fun queryProvider(query: KmsProviderQuery): IdkResult<QueryProviderResult, IdkError> {
            val command =
                queryProviderCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "QueryProviderCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = QueryProviderArgs(query = query)
            return command.execute(args)
        }

        override suspend fun queryProviders(query: KmsProviderQuery): IdkResult<QueryProvidersResult, IdkError> {
            val command =
                queryProvidersCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "QueryProvidersCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = QueryProvidersArgs(query = query)
            return command.execute(args)
        }

        override suspend fun getAllCapabilities(includeDisabled: Boolean): IdkResult<GetAllCapabilitiesResult, IdkError> {
            val command =
                getAllCapabilitiesCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "GetAllCapabilitiesCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = GetAllCapabilitiesArgs(includeDisabled = includeDisabled)
            return command.execute(args)
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <KT : KeyType> resolvePublicKey(
            keyInfo: KeyInfoType<KT>,
            identifierMethod: IdentifierMethod?,
            trustedCerts: Array<String>?,
            verifyX509CertificateChain: Boolean?,
        ): ResolvedKeyInfoType<KT> {
            val command = resolvePublicKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    ResolvePublicKeyArgs(
                        keyInfo = keyInfo,
                        identifierMethod = identifierMethod,
                        trustedCerts = trustedCerts,
                        verifyX509CertificateChain = verifyX509CertificateChain,
                    )
                val result = command.execute(args)
                val resolvedKeyInfo =
                    result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Resolve public key failed") }.resolvedKeyInfo
                        ?: throw PKIException("Resolved key info is null")
                return resolvedKeyInfo as ResolvedKeyInfoType<KT>
            }
            // Fallback to direct call
            return getResolverByKeyTypeOrIdentifier(identifierMethod = identifierMethod, keyType = keyInfo.keyType, resolverId = keyInfo.providerId).resolvePublicKey(
                keyInfo,
                identifierMethod,
                trustedCerts,
                verifyX509CertificateChain,
            )
        }

        override suspend fun generateKey(
            providerId: String?,
            alias: String?,
            use: JwkUse?,
            keyOperations: Array<out KeyOperations>?,
            alg: SignatureAlgorithm?,
            keyVisibility: KeyVisibility?,
        ): ManagedKeyPair {
            val command = generateKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    GenerateKeyArgs(
                        providerId = providerId,
                        alias = alias,
                        use = use,
                        keyOperations = keyOperations,
                        alg = alg,
                        keyVisibility = keyVisibility,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Key generation failed") }.keyPair
                    ?: throw PKIException("Generated key pair is null")
            }
            // Fallback to direct provider call
            val kmsProvider = getProvider(providerId, alg)
            val keyPair = kmsProvider.generateKeyAsync(alias, use, keyOperations, alg)
            return keyPair
        }

        @Deprecated("Use generateKey instead", ReplaceWith("generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)"))
        override suspend fun generateKeyAsync(
            providerId: String?,
            alias: String?,
            use: JwkUse?,
            keyOperations: Array<out KeyOperations>?,
            alg: SignatureAlgorithm?,
            keyVisibility: KeyVisibility?,
        ): ManagedKeyPair = generateKey(providerId, alias, use, keyOperations, alg, keyVisibility)

        override suspend fun createRawSignature(
            keyInfo: KeyInfoType<*>,
            input: ByteArray,
            requireX5Chain: Boolean,
        ): ByteArray {
            val command = createRawSignatureCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    CreateRawSignatureArgs(
                        keyInfo = keyInfo.toSigningKeyReferenceOrNull() ?: keyInfo.toKeyReferenceOrNull() ?: keyInfo,
                        input = input,
                        requireX5Chain = requireX5Chain,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Signature creation failed") }.signature
            }
            // Fallback to direct provider call when commands not available (e.g., in tests)
            return getProvider(providerId = keyInfo.providerId, alg = keyInfo.signatureAlgorithm).createRawSignature(keyInfo, input, requireX5Chain)
        }

        override suspend fun isValidRawSignature(
            keyInfo: KeyInfoType<*>,
            input: ByteArray,
            signature: ByteArray,
        ): Boolean {
            // Resolve public key from x5c if no key/kid/alias is provided
            val resolvedKeyInfo =
                if (keyInfo.key == null && keyInfo.kid == null && keyInfo.alias == null && !keyInfo.x5c.isNullOrEmpty()) {
                    resolvePublicKey(keyInfo, identifierMethod = IdentifierMethod.x5c)
                } else {
                    keyInfo
                }

            val command = verifyRawSignatureCommand
            val exec = execution
            if (command != null && exec != null) {
                val args = VerifyRawSignatureArgs(keyInfo = resolvedKeyInfo, input = input, signature = signature)
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Signature verification failed") }.isValid
            }
            // Fallback to direct provider call when commands not available (e.g., in tests)
            val kms = resolvedKeyInfo.providerId
            val alg = resolvedKeyInfo.signatureAlgorithm
            if (kms !== null || alg !== null) {
                return getProvider(kms, alg).isValidRawSignature(resolvedKeyInfo, input, signature)
            }
            return getProviderById(defaultProviderId()).isValidRawSignature(resolvedKeyInfo, input, signature)
        }

        override suspend fun signDigest(
            keyInfo: KeyInfoType<*>,
            digest: ByteArray,
            signatureAlgorithm: SignatureAlgorithm,
            signatureEncoding: SignatureEncoding,
            requireX5Chain: Boolean,
        ): ByteArray {
            val command = signDigestCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    SignDigestArgs(
                        keyInfo = keyInfo.toSigningKeyReferenceOrNull() ?: keyInfo.toKeyReferenceOrNull() ?: keyInfo,
                        digest = digest,
                        signatureAlgorithm = signatureAlgorithm,
                        signatureEncoding = signatureEncoding,
                        requireX5Chain = requireX5Chain,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Digest signature creation failed") }.signature
            }
            return getProvider(providerId = keyInfo.providerId, alg = signatureAlgorithm)
                .signDigest(keyInfo, digest, signatureAlgorithm, signatureEncoding, requireX5Chain)
        }

        override suspend fun verifyDigest(
            keyInfo: KeyInfoType<*>,
            digest: ByteArray,
            signature: ByteArray,
            signatureAlgorithm: SignatureAlgorithm,
            signatureEncoding: SignatureEncoding,
        ): Boolean {
            val resolvedKeyInfo =
                if (keyInfo.key == null && keyInfo.kid == null && keyInfo.alias == null && !keyInfo.x5c.isNullOrEmpty()) {
                    resolvePublicKey(keyInfo, identifierMethod = IdentifierMethod.x5c)
                } else {
                    keyInfo
                }

            val command = verifyDigestCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    VerifyDigestArgs(
                        keyInfo = resolvedKeyInfo,
                        digest = digest,
                        signature = signature,
                        signatureAlgorithm = signatureAlgorithm,
                        signatureEncoding = signatureEncoding,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Digest signature verification failed") }.isValid
            }
            return getProvider(resolvedKeyInfo.providerId, signatureAlgorithm)
                .verifyDigest(resolvedKeyInfo, digest, signature, signatureAlgorithm, signatureEncoding)
        }

        // Encryption operations - delegate to commands when available

        override suspend fun encrypt(
            keyInfo: KeyInfoType<*>,
            plaintext: ByteArray,
            algorithm: ContentEncryptionAlgorithm,
            additionalAuthenticatedData: ByteArray?,
        ): EncryptionResult {
            val command = encryptCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    EncryptArgs(
                        keyInfo = keyInfo,
                        plaintext = plaintext,
                        algorithm = algorithm,
                        additionalAuthenticatedData = additionalAuthenticatedData,
                    )
                val result = command.execute(args)
                val encResult = result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Encryption failed") }
                return EncryptionResult(
                    ciphertext = encResult.ciphertext,
                    iv = encResult.iv,
                    authTag = encResult.authTag,
                )
            }
            // Fallback to direct provider call
            return getProvider(keyInfo.providerId, keyInfo.signatureAlgorithm).encrypt(keyInfo, plaintext, algorithm, additionalAuthenticatedData)
        }

        override suspend fun decrypt(
            keyInfo: KeyInfoType<*>,
            ciphertext: ByteArray,
            algorithm: ContentEncryptionAlgorithm,
            iv: ByteArray,
            authTag: ByteArray,
            additionalAuthenticatedData: ByteArray?,
        ): ByteArray {
            val command = decryptCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    DecryptArgs(
                        keyInfo = keyInfo,
                        ciphertext = ciphertext,
                        algorithm = algorithm,
                        iv = iv,
                        authTag = authTag,
                        additionalAuthenticatedData = additionalAuthenticatedData,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Decryption failed") }.plaintext
            }
            // Fallback to direct provider call
            return getProvider(keyInfo.providerId, keyInfo.signatureAlgorithm).decrypt(keyInfo, ciphertext, algorithm, iv, authTag, additionalAuthenticatedData)
        }

        override suspend fun wrapKey(
            wrappingKeyInfo: KeyInfoType<*>,
            keyToWrap: ByteArray,
            algorithm: KeyWrapAlgorithm,
        ): ByteArray {
            val command = wrapKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    WrapKeyArgs(
                        wrappingKeyInfo = wrappingKeyInfo,
                        keyToWrap = keyToWrap,
                        algorithm = algorithm,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Key wrap failed") }.wrappedKey
            }
            // Fallback to direct provider call
            return getProvider(wrappingKeyInfo.providerId, wrappingKeyInfo.signatureAlgorithm).wrapKey(wrappingKeyInfo, keyToWrap, algorithm)
        }

        override suspend fun unwrapKey(
            unwrappingKeyInfo: KeyInfoType<*>,
            wrappedKey: ByteArray,
            algorithm: KeyWrapAlgorithm,
        ): ByteArray {
            val command = unwrapKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    UnwrapKeyArgs(
                        unwrappingKeyInfo = unwrappingKeyInfo,
                        wrappedKey = wrappedKey,
                        algorithm = algorithm,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Key unwrap failed") }.unwrappedKey
            }
            // Fallback to direct provider call
            return getProvider(unwrappingKeyInfo.providerId, unwrappingKeyInfo.signatureAlgorithm).unwrapKey(unwrappingKeyInfo, wrappedKey, algorithm)
        }

        /**
         * Performs ECDH key agreement to derive a shared secret.
         *
         * This method converts the provided key info objects to JWK format and delegates
         * to [EcdhUtils.performKeyAgreementForDecryption] for the actual ECDH computation.
         *
         * @param privateKeyInfo Key info containing the private key (must be an EC key with 'd' parameter)
         * @param publicKeyInfo Key info containing the public key (must be an EC key with 'x' and 'y' coordinates)
         * @param algorithm The key agreement algorithm (ECDH-ES or ECDH-ES+AxxxKW)
         * @param keyDataLen Optional key data length (not currently used, reserved for future key derivation)
         * @return The derived shared secret as a byte array
         * @throws IllegalArgumentException if keys are not EC keys or missing required parameters
         * @throws IllegalStateException if key conversion fails
         */

        override suspend fun performKeyAgreement(
            privateKeyInfo: KeyInfoType<*>,
            publicKeyInfo: KeyInfoType<*>,
            algorithm: KeyAgreementAlgorithm,
            keyDataLen: Int?,
        ): ByteArray {
            val command = performKeyAgreementCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    PerformKeyAgreementArgs(
                        privateKeyInfo = privateKeyInfo,
                        publicKeyInfo = publicKeyInfo,
                        algorithm = algorithm,
                        keyDataLen = keyDataLen,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Key agreement failed") }.sharedSecret
            }
            // Fallback to direct implementation
            return performKeyAgreementDirect(privateKeyInfo, publicKeyInfo, algorithm, keyDataLen)
        }

        /**
         * Direct implementation of key agreement without command wrapper.
         * Used as fallback when commands are not available.
         */
        private suspend fun performKeyAgreementDirect(
            privateKeyInfo: KeyInfoType<*>,
            publicKeyInfo: KeyInfoType<*>,
            _algorithm: KeyAgreementAlgorithm,
            _keyDataLen: Int?,
        ): ByteArray {
            // Convert key info to JWK format
            val privateKeyJwk =
                CoseJoseKeyMappingService.toJwkKeyInfo(privateKeyInfo).key
                    ?: throw IllegalArgumentException("Private key info must contain a key")
            val publicKeyJwk =
                CoseJoseKeyMappingService.toJwkKeyInfo(publicKeyInfo).key
                    ?: throw IllegalArgumentException("Public key info must contain a key")

            // Validate that both are EC keys (ECDH only works with EC keys)
            require(privateKeyJwk.kty == JwaKeyType.EC) {
                "Private key must be an EC key for ECDH key agreement, got: ${privateKeyJwk.kty}"
            }
            require(publicKeyJwk.kty == JwaKeyType.EC) {
                "Public key must be an EC key for ECDH key agreement, got: ${publicKeyJwk.kty}"
            }
            require(privateKeyJwk.d != null) {
                "Private key must have 'd' parameter for key agreement"
            }
            require(publicKeyJwk.x != null && publicKeyJwk.y != null) {
                "Public key must have 'x' and 'y' coordinates for key agreement"
            }

            // Determine the curve from the private key
            val curve =
                privateKeyJwk.crv?.let { Curve.fromJose(it) }
                    ?: publicKeyJwk.crv?.let { Curve.fromJose(it) }
                    ?: Curve.P_256 // Default to P-256 if not specified

            // Perform the key agreement using EcdhUtils
            return EcdhUtils.performKeyAgreementForDecryption(
                ourPrivateKeyJwk = privateKeyJwk,
                senderEphemeralPublicKeyJwk = publicKeyJwk,
                curve = curve,
            )
        }

        override suspend fun storeKey(
            keyInfo: ResolvedKeyInfoType<*>,
            providerId: String,
            alias: String,
            certChain: Array<com.sphereon.crypto.core.x509.Certificate>?,
        ): com.sphereon.crypto.core.ManagedKeyInfoType<*> {
            val command = storeKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args =
                    StoreKeyArgs(
                        keyInfo = keyInfo,
                        providerId = providerId,
                        alias = alias,
                        certChain = certChain,
                    )
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Store key failed") }.key
                    ?: throw PKIException("Store key result is null")
            }
            // Fallback to direct call
            return keyStore.storeKey(keyInfo, providerId, alias, certChain)
        }

        override suspend fun listKeys(): Array<com.sphereon.crypto.core.ManagedKeyReference> {
            val command = listKeysCommand
            val exec = execution
            if (command != null && exec != null) {
                val args = ListKeysArgs()
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "List keys failed") }.keys
            }
            // Fallback to direct call
            return keyStore.listKeys()
        }

        override suspend fun listKeys(filter: com.sphereon.crypto.core.ManagedKeyReferenceFilter): Array<com.sphereon.crypto.core.ManagedKeyReference> = keyStore.listKeys(filter)

        override suspend fun getKey(keyInfo: com.sphereon.crypto.core.KeyInfoType<*>): com.sphereon.crypto.core.ManagedKeyInfoType<*> {
            val command = getKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args = GetKeyArgs(keyInfo = keyInfo)
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Get key failed") }.key
                    ?: throw PKIException("Get key result is null")
            }
            // Fallback to direct call
            return keyStore.getKey(keyInfo)
        }

        override suspend fun deleteKey(keyInfo: com.sphereon.crypto.core.KeyInfoType<*>): Boolean {
            val command = deleteKeyCommand
            val exec = execution
            if (command != null && exec != null) {
                val args = DeleteKeyArgs(keyInfo = keyInfo)
                val result = command.execute(args)
                return result.getOrElse { throw PKIException(it.message.defaultMessage ?: "Delete key failed") }.deleted
            }
            // Fallback to direct call
            return keyStore.deleteKey(keyInfo)
        }

    /* TODO do we need JS-compatible Promise-based wrappers? like
        @JsName("storeKeyAsync")
        fun storeKeyJs(keyInfo: ResolvedKeyInfoType<*>, providerId: String, alias: String, certChain: Array<com.sphereon.crypto.core.x509.Certificate>?): Promise<Unit> {
            return GlobalScope.promise { storeKey(keyInfo, providerId, alias, certChain) }
        }
     */

        override fun keyVisibility(): KeyVisibility = keyStore.keyVisibility()

        // ========================================================================
        // Command-based API returning IdkResult
        // ========================================================================

        override suspend fun createRawSignatureResult(
            keyInfo: KeyInfoType<*>,
            input: ByteArray,
            requireX5Chain: Boolean,
        ): IdkResult<CreateRawSignatureResult, IdkError> {
            val command =
                createRawSignatureCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "CreateRawSignatureCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                CreateRawSignatureArgs(
                    keyInfo = keyInfo.toSigningKeyReferenceOrNull() ?: keyInfo.toKeyReferenceOrNull() ?: keyInfo,
                    input = input,
                    requireX5Chain = requireX5Chain,
                )
            return command.execute(args)
        }

        override suspend fun verifyRawSignatureResult(
            keyInfo: KeyInfoType<*>,
            input: ByteArray,
            signature: ByteArray,
        ): IdkResult<VerifyRawSignatureResult, IdkError> {
            val command =
                verifyRawSignatureCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "VerifyRawSignatureCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = VerifyRawSignatureArgs(keyInfo = keyInfo, input = input, signature = signature)
            return command.execute(args)
        }

        override suspend fun signDigestResult(
            keyInfo: KeyInfoType<*>,
            digest: ByteArray,
            signatureAlgorithm: SignatureAlgorithm,
            signatureEncoding: SignatureEncoding,
            requireX5Chain: Boolean,
        ): IdkResult<SignDigestResult, IdkError> {
            val command =
                signDigestCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "SignDigestCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                SignDigestArgs(
                    keyInfo = keyInfo.toSigningKeyReferenceOrNull() ?: keyInfo.toKeyReferenceOrNull() ?: keyInfo,
                    digest = digest,
                    signatureAlgorithm = signatureAlgorithm,
                    signatureEncoding = signatureEncoding,
                    requireX5Chain = requireX5Chain,
                )
            return command.execute(args)
        }

        override suspend fun verifyDigestResult(
            keyInfo: KeyInfoType<*>,
            digest: ByteArray,
            signature: ByteArray,
            signatureAlgorithm: SignatureAlgorithm,
            signatureEncoding: SignatureEncoding,
        ): IdkResult<VerifyDigestResult, IdkError> {
            val command =
                verifyDigestCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "VerifyDigestCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                VerifyDigestArgs(
                    keyInfo = keyInfo,
                    digest = digest,
                    signature = signature,
                    signatureAlgorithm = signatureAlgorithm,
                    signatureEncoding = signatureEncoding,
                )
            return command.execute(args)
        }

        override suspend fun encryptResult(
            keyInfo: KeyInfoType<*>,
            plaintext: ByteArray,
            algorithm: ContentEncryptionAlgorithm,
            additionalAuthenticatedData: ByteArray?,
        ): IdkResult<EncryptResult, IdkError> {
            val command =
                encryptCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "EncryptCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                EncryptArgs(
                    keyInfo = keyInfo,
                    plaintext = plaintext,
                    algorithm = algorithm,
                    additionalAuthenticatedData = additionalAuthenticatedData,
                )
            return command.execute(args)
        }

        override suspend fun decryptResult(
            keyInfo: KeyInfoType<*>,
            ciphertext: ByteArray,
            algorithm: ContentEncryptionAlgorithm,
            iv: ByteArray,
            authTag: ByteArray,
            additionalAuthenticatedData: ByteArray?,
        ): IdkResult<DecryptResult, IdkError> {
            val command =
                decryptCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "DecryptCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                DecryptArgs(
                    keyInfo = keyInfo,
                    ciphertext = ciphertext,
                    algorithm = algorithm,
                    iv = iv,
                    authTag = authTag,
                    additionalAuthenticatedData = additionalAuthenticatedData,
                )
            return command.execute(args)
        }

        override suspend fun wrapKeyResult(
            wrappingKeyInfo: KeyInfoType<*>,
            keyToWrap: ByteArray,
            algorithm: KeyWrapAlgorithm,
        ): IdkResult<WrapKeyResult, IdkError> {
            val command =
                wrapKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "WrapKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = WrapKeyArgs(wrappingKeyInfo = wrappingKeyInfo, keyToWrap = keyToWrap, algorithm = algorithm)
            return command.execute(args)
        }

        override suspend fun unwrapKeyResult(
            unwrappingKeyInfo: KeyInfoType<*>,
            wrappedKey: ByteArray,
            algorithm: KeyWrapAlgorithm,
        ): IdkResult<UnwrapKeyResult, IdkError> {
            val command =
                unwrapKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "UnwrapKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = UnwrapKeyArgs(unwrappingKeyInfo = unwrappingKeyInfo, wrappedKey = wrappedKey, algorithm = algorithm)
            return command.execute(args)
        }

        override suspend fun performKeyAgreementResult(
            privateKeyInfo: KeyInfoType<*>,
            publicKeyInfo: KeyInfoType<*>,
            algorithm: KeyAgreementAlgorithm,
            keyDataLen: Int?,
        ): IdkResult<PerformKeyAgreementResult, IdkError> {
            val command =
                performKeyAgreementCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "PerformKeyAgreementCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                PerformKeyAgreementArgs(
                    privateKeyInfo = privateKeyInfo,
                    publicKeyInfo = publicKeyInfo,
                    algorithm = algorithm,
                    keyDataLen = keyDataLen,
                )
            return command.execute(args)
        }

        override suspend fun generateKeyResult(
            providerId: String?,
            alias: String?,
            use: JwkUse?,
            keyOperations: Array<out KeyOperations>?,
            alg: SignatureAlgorithm?,
            keyVisibility: KeyVisibility?,
        ): IdkResult<GenerateKeyResult, IdkError> {
            val command =
                generateKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "GenerateKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                GenerateKeyArgs(
                    providerId = providerId,
                    alias = alias,
                    use = use,
                    keyOperations = keyOperations,
                    alg = alg,
                    keyVisibility = keyVisibility,
                )
            return command.execute(args)
        }

        override suspend fun listKeysResult(providerId: String?): IdkResult<ListKeysResult, IdkError> {
            val command =
                listKeysCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "ListKeysCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = ListKeysArgs(providerId = providerId)
            return command.execute(args)
        }

        override suspend fun getKeyResult(keyInfo: KeyInfoType<*>): IdkResult<GetKeyResult, IdkError> {
            val command =
                getKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "GetKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = GetKeyArgs(keyInfo = keyInfo)
            return command.execute(args)
        }

        override suspend fun storeKeyResult(
            keyInfo: ResolvedKeyInfoType<*>,
            providerId: String,
            alias: String,
            certChain: Array<com.sphereon.crypto.core.x509.Certificate>?,
        ): IdkResult<StoreKeyResult, IdkError> {
            val command =
                storeKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "StoreKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = StoreKeyArgs(keyInfo = keyInfo, providerId = providerId, alias = alias, certChain = certChain)
            return command.execute(args)
        }

        override suspend fun deleteKeyResult(keyInfo: KeyInfoType<*>): IdkResult<DeleteKeyResult, IdkError> {
            val command =
                deleteKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "DeleteKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args = DeleteKeyArgs(keyInfo = keyInfo)
            return command.execute(args)
        }

        override suspend fun resolvePublicKeyResult(
            keyInfo: KeyInfoType<*>,
            identifierMethod: IdentifierMethod?,
            trustedCerts: Array<String>?,
            verifyX509CertificateChain: Boolean?,
        ): IdkResult<ResolvePublicKeyResult, IdkError> {
            val command =
                resolvePublicKeyCommand
                    ?: return IdkError.NOT_FOUND_ERROR(resource = "ResolvePublicKeyCommand", message = "Command not available").asErrorResult()
            val exec =
                execution
                    ?: return IdkError.UNKNOWN_ERROR(message = "SessionExecution not available").asErrorResult()

            val args =
                ResolvePublicKeyArgs(
                    keyInfo = keyInfo,
                    identifierMethod = identifierMethod,
                    trustedCerts = trustedCerts,
                    verifyX509CertificateChain = verifyX509CertificateChain,
                )
            return command.execute(args)
        }
    }
