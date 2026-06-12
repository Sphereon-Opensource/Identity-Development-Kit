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

package com.sphereon.crypto.kms.provider.rest

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.CertificateOptions
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.EncryptionResult
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyStorageType
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.OperationCapability
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.Signature
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateChainToX5c
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
import com.sphereon.crypto.kms.rest.api.generated.models.CreateRawSignature
import com.sphereon.crypto.kms.rest.api.generated.models.CreateRawSignatureResponse
import com.sphereon.crypto.kms.rest.api.generated.models.DecryptRequest
import com.sphereon.crypto.kms.rest.api.generated.models.DecryptResponse
import com.sphereon.crypto.kms.rest.api.generated.models.EncryptRequest
import com.sphereon.crypto.kms.rest.api.generated.models.EncryptResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ErrorResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKey
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.GetKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKey
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementRequest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ListKeysResponse
import com.sphereon.crypto.kms.rest.api.generated.models.UnwrapKeyRequest
import com.sphereon.crypto.kms.rest.api.generated.models.UnwrapKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.VerifyRawSignature
import com.sphereon.crypto.kms.rest.api.generated.models.VerifyRawSignatureResponse
import com.sphereon.crypto.kms.rest.api.generated.models.WrapKeyRequest
import com.sphereon.crypto.kms.rest.api.generated.models.WrapKeyResponse
import com.sphereon.crypto.kms.rest.api.mapper.toRest
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
import com.sphereon.crypto.kms.rest.api.mapper.toSdkReferences
import com.sphereon.ktor.http.client.provider.HttpClientLogger
import com.sphereon.ktor.http.client.provider.LegacyHttpClientFactory
import com.sphereon.ktor.http.client.provider.LegacyHttpClientOptions
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.crypto.kms.rest.api.generated.models.ContentEncryptionAlgorithm as ContentEncryptionAlgorithmRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementAlgorithm as KeyAgreementAlgorithmRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyWrapAlgorithm as KeyWrapAlgorithmRest

@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientKmsProvider", exact = true)
interface RestClientKmsProvider : KmsProvider

/**
 * Non-Hardware-based CryptoProvider provides Elliptic Curve and RSA cryptographic operations delegating to well-known platform implementations like OpenSSL3, WebCrypto, Apple, Jdk,
 *
 * Warning: To be used for testing purposes. Use hardware-based crypto providers for production!
 * If you need ephemeral keys then you can use this provider together with the default in memory private key store and thus that is the exception to the above warning.
 *
 */
@dev.zacsweers.metro.AssistedFactory
fun interface RestClientKmsProviderImplFactory {
    fun create(
        @Assisted config: KmsProviderConfigBase,
        @Assisted execution: SessionExecution,
    ): RestClientKmsProviderImpl
}

@JsExportCompat
@AssistedInject
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestClientKmsProviderImpl", exact = true)
class RestClientKmsProviderImpl(
    @Assisted config: KmsProviderConfigBase,
    @Assisted private val execution: SessionExecution,
) : RestClientKmsProvider {
    private val logService = execution.log

    val http: HttpClient by lazy { createHttpClient() }

    private val config: RestClientKmsProviderConfigType =
        config as? RestClientKmsProviderConfigType ?: throw IllegalArgumentException("Config must be IRestClientKmsProviderConfig")
    override val kmsProviderType = config.kmsProviderType
    override val id = config.id
    override val order = config.order

    /**
     * Returns an array of supported elliptic curves for cryptographic operations.
     *
     * @return An array of CurveMapping objects representing the supported elliptic curves.
     */
    override fun supportedCurves(): Array<Curve> = arrayOf(Curve.P_256, Curve.P_384, Curve.P_521)

    /**
     * Checks if the provided elliptic curve is supported by the SoftwareKmsProvider.
     *
     * @param curve The elliptic curve to be checked.
     * @return True if the curve is supported, false otherwise.
     */
    override fun isSupportedCurve(curve: Curve): Boolean = supportedCurves().contains(curve)

    /**
     * Returns an array of supported hash algorithms.
     *
     * @return An array containing the supported HashAlgorithm values: SHA256, SHA384, and SHA512.
     */
    override fun supportedDigests(): Array<DigestAlg> =
        supportedSignatureAlgorithms()
            .filter { it.digestAlgorithm !== null }
            .map { it.digestAlgorithm!! }
            .toSet()
            .toTypedArray()

    /**
     * Generates a cryptographic key pair based on the provided elliptic curve.
     *
     * @param curve The elliptic curve mapping used to generate the key pair.
     * @return A `CryptoProviderKeyPair` object containing the generated key pair
     *         with their respective JWK and COSE representations.
     */
    override suspend fun generateKeyAsync(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        certificateOptions: CertificateOptions?,
    ): ManagedKeyPair {
        val response =
            http.post {
                contentType(ContentType.Application.Json)
                url("${config.restKmsUrl}/providers/${config.restProviderId}/keys")
                applyAuthHeaders()
                setBody(
                    GenerateKey(
                        alias = alias,
                        use = use?.toRest(),
                        keyOperations = keyOperations.toRest(),
                        alg = alg?.toRest(),
                    ),
                )
            }
        handleErrors(response)

        val generateKeyResponse = response.body<GenerateKeyResponse>()
        return generateKeyResponse.keyPair.toSdk()
    }

    /**
     * Generates a signature for the given input data using the provided key information.
     *
     * @param keyInfo Information about the signing key.
     * @param input The data to be signed.
     * @return The generated signature as a byte array.
     * @throws IllegalArgumentException If the private key is not provided or not supported.
     */
    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        requireX5Chain: Boolean,
    ): ByteArray {
        val response =
            http.post {
                url("${config.restKmsUrl}/signatures/raw/create")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    CreateRawSignature(
                        keyInfo = keyInfo.toRest(),
                        input = Base64ByteArray(input),
                    ),
                )
            }
        handleErrors(response)
        val rawSignatureResponse = response.body<CreateRawSignatureResponse>()
        return rawSignatureResponse.signature.value
    }

    /**
     * Verifies the signature of the input data using the provided key information.
     *
     * @param keyInfo Key information that includes the public key and other details.
     * @param input The original data which the signature is supposed to represent.
     * @param signature The signature that needs to be verified.
     * @return true if the signature is valid, false otherwise.
     * @throws IllegalArgumentException if a private key is used to verify the signature.
     */
    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean {
        val response =
            http.post {
                url("${config.restKmsUrl}/signatures/raw/verify")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(VerifyRawSignature(keyInfo = keyInfo.toRest(), input = Base64ByteArray(input), signature = Base64ByteArray(signature)))
            }
        handleErrors(response)
        val verifyRawSignatureResponse = response.body<VerifyRawSignatureResponse>()
        return verifyRawSignatureResponse.isValid
    }

    override suspend fun createSignature(
        signInput: SignInput,
        keyInfo: KeyInfoType<*>?,
        signatureAlgorithm: SignatureAlgorithm?,
    ): SignOutput = throw UnsupportedOperationException("RestKmsProvider does not support createSignature")

    override suspend fun isValidSignature(
        signInput: SignInput,
        signature: Signature,
    ): Boolean = throw UnsupportedOperationException("RestKmsProvider does not support isValidSignature")

    // TODO: fixme - hardcoded key types
    override fun supportedKeyTypes(): Array<KeyTypeMapping> = arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA)

    /**
     * Returns an array of supported EcDSA and RSA algorithm mappings.
     *
     * FIXME: hardcoded list should be derived from provider capabilities
     *
     * @return An array containing AlgorithmMappings
     */
    override fun supportedSignatureAlgorithms(): Array<SignatureAlgorithm> =
        arrayOf(
            SignatureAlgorithm.ECDSA_SHA256,
            SignatureAlgorithm.ECDSA_SHA384,
            SignatureAlgorithm.ECDSA_SHA512,
            SignatureAlgorithm.RSA_RAW,
            SignatureAlgorithm.RSA_SHA256,
            SignatureAlgorithm.RSA_SHA384,
            SignatureAlgorithm.RSA_SHA512,
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
        )

    override val settings: KeyProviderSettings? = null

    override suspend fun listKeys(): Array<ManagedKeyReference> {
        val response =
            http.get {
                url("${config.restKmsUrl}/providers/${config.restProviderId}/keys")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
            }
        val result = response.body<ListKeysResponse>()
        return result.toSdkReferences()
    }

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        val aliasOrKid = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Cannot get a key without an alias or kid")
        val response =
            http.get {
                url("${config.restKmsUrl}/providers/${config.restProviderId}/keys/$aliasOrKid")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
            }
        handleErrors(response)
        val result = response.body<GetKeyResponse>()
        return result.keyInfo.toSdk()
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*> {
        require(providerId == config.restProviderId || providerId == config.id) {
            "Invalid provider id: $providerId, must be ${config.restProviderId} or ${config.id}"
        }
        val x5c = certChain?.let { certificateChainToX5c(certChain) }
        val keyInfoWithAlias = ResolvedKeyInfo.fromDTO(keyInfo).copy(alias = alias, providerId = config.restProviderId, x5c = x5c)
        val response =
            http.post {
                url("${config.restKmsUrl}/providers/${config.restProviderId}/keys/import")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    ImportKey(
                        keyInfo = keyInfoWithAlias.toRest(),
                        certChain = x5c,
                    ),
                )
            }
        val importKeyResponse = response.body<ImportKeyResponse>()
        return importKeyResponse.keyInfo.toSdk()
    }

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        val aliasOrKid = keyInfo.alias ?: keyInfo.kid ?: throw IllegalArgumentException("Cannot get a key without an alias or kid")

        val response =
            http.delete {
                contentType(ContentType.Application.Json)
                url("${config.restKmsUrl}/providers/${config.restProviderId}/keys/$aliasOrKid")
                applyAuthHeaders()
            }
        handleErrors(response)
        return response.status.isSuccess()
    }

    // fixme
    override fun keyVisibility(): KeyVisibility = KeyVisibility.PUBLIC

    // ========== KmsProviderCapabilities ==========

    override fun getCapabilities(): KmsProviderCapabilities =
        KmsProviderCapabilities(
            providerId = id,
            providerType = kmsProviderType,
            // Storage capabilities - REST provider delegates to remote
            storageTypes = arrayOf(KeyStorageType.PERSISTENT),
            supportsKeyImport = true,
            supportsKeyExport = true,
            exposePrivateKeys = false, // REST doesn't expose private keys directly
            // Operations
            operations =
                arrayOf(
                    OperationCapability(operation = KmsProviderOperation.GENERATE_KEY, supported = true),
                    OperationCapability(operation = KmsProviderOperation.IMPORT_KEY, supported = true),
                    OperationCapability(operation = KmsProviderOperation.EXPORT_KEY, supported = true),
                    OperationCapability(operation = KmsProviderOperation.DELETE_KEY, supported = true),
                    OperationCapability(operation = KmsProviderOperation.SIGN, supported = true),
                    OperationCapability(operation = KmsProviderOperation.VERIFY, supported = true),
                    OperationCapability(
                        operation = KmsProviderOperation.ENCRYPT,
                        supported = true,
                        contentEncryptionAlgorithms = ContentEncryptionAlgorithm.entries.toTypedArray(),
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.DECRYPT,
                        supported = true,
                        contentEncryptionAlgorithms = ContentEncryptionAlgorithm.entries.toTypedArray(),
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.WRAP_KEY,
                        supported = true,
                        keyWrapAlgorithms = KeyWrapAlgorithm.entries.toTypedArray(),
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.UNWRAP_KEY,
                        supported = true,
                        keyWrapAlgorithms = KeyWrapAlgorithm.entries.toTypedArray(),
                    ),
                    OperationCapability(
                        operation = KmsProviderOperation.KEY_AGREEMENT,
                        supported = true,
                        keyAgreementAlgorithms = KeyAgreementAlgorithm.entries.toTypedArray(),
                    ),
                ),
            // Key type support
            supportedKeyTypes = supportedKeyTypes(),
            supportedCurves = supportedCurves(),
            // Algorithm support
            supportedCryptoAlgorithms = emptyArray(),
            supportedDigestAlgorithms = supportedDigests(),
            signatureAlgorithms = supportedSignatureAlgorithms(),
            contentEncryptionAlgorithms = ContentEncryptionAlgorithm.entries.toTypedArray(),
            // Additional capabilities
            supportsX509 = false,
            supportsAttestation = false,
            supportsHardwareBacking = false,
            // Public key resolution
            supportsPublicKeyResolution = true,
            resolutionMethods = emptyArray(),
        )

    // ========== EncryptionService ==========

    override suspend fun encrypt(
        keyInfo: KeyInfoType<*>,
        plaintext: ByteArray,
        algorithm: com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm,
        additionalAuthenticatedData: ByteArray?,
    ): com.sphereon.crypto.core.kms.EncryptionResult {
        val response =
            http.post {
                url("${config.restKmsUrl}/encryption/encrypt")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    EncryptRequest(
                        keyInfo = keyInfo.toRest(),
                        plaintext = Base64ByteArray(plaintext),
                        algorithm = ContentEncryptionAlgorithmRest.valueOf(algorithm.name),
                        additionalAuthenticatedData = additionalAuthenticatedData?.let { Base64ByteArray(it) },
                    ),
                )
            }
        handleErrors(response)
        val result = response.body<EncryptResponse>()
        return EncryptionResult(ciphertext = result.ciphertext.value, iv = result.iv.value, authTag = result.authTag.value)
    }

    override suspend fun decrypt(
        keyInfo: KeyInfoType<*>,
        ciphertext: ByteArray,
        algorithm: com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm,
        iv: ByteArray,
        authTag: ByteArray,
        additionalAuthenticatedData: ByteArray?,
    ): ByteArray {
        val response =
            http.post {
                url("${config.restKmsUrl}/encryption/decrypt")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    DecryptRequest(
                        keyInfo = keyInfo.toRest(),
                        ciphertext = Base64ByteArray(ciphertext),
                        algorithm = ContentEncryptionAlgorithmRest.valueOf(algorithm.name),
                        iv = Base64ByteArray(iv),
                        authTag = Base64ByteArray(authTag),
                        additionalAuthenticatedData = additionalAuthenticatedData?.let { Base64ByteArray(it) },
                    ),
                )
            }
        handleErrors(response)
        return response.body<DecryptResponse>().plaintext.value
    }

    override suspend fun wrapKey(
        wrappingKeyInfo: KeyInfoType<*>,
        keyToWrap: ByteArray,
        algorithm: com.sphereon.crypto.core.kms.KeyWrapAlgorithm,
    ): ByteArray {
        val response =
            http.post {
                url("${config.restKmsUrl}/encryption/wrap")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    WrapKeyRequest(
                        wrappingKeyInfo = wrappingKeyInfo.toRest(),
                        keyToWrap = Base64ByteArray(keyToWrap),
                        algorithm = KeyWrapAlgorithmRest.valueOf(algorithm.name),
                    ),
                )
            }
        handleErrors(response)
        return response.body<WrapKeyResponse>().wrappedKey.value
    }

    override suspend fun unwrapKey(
        unwrappingKeyInfo: KeyInfoType<*>,
        wrappedKey: ByteArray,
        algorithm: com.sphereon.crypto.core.kms.KeyWrapAlgorithm,
    ): ByteArray {
        val response =
            http.post {
                url("${config.restKmsUrl}/encryption/unwrap")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    UnwrapKeyRequest(
                        unwrappingKeyInfo = unwrappingKeyInfo.toRest(),
                        wrappedKey = Base64ByteArray(wrappedKey),
                        algorithm = KeyWrapAlgorithmRest.valueOf(algorithm.name),
                    ),
                )
            }
        handleErrors(response)
        return response.body<UnwrapKeyResponse>().unwrappedKey.value
    }

    override suspend fun performKeyAgreement(
        privateKeyInfo: KeyInfoType<*>,
        publicKeyInfo: KeyInfoType<*>,
        algorithm: com.sphereon.crypto.core.kms.KeyAgreementAlgorithm,
        keyDataLen: Int?,
    ): ByteArray {
        val response =
            http.post {
                url("${config.restKmsUrl}/encryption/key-agreement")
                contentType(ContentType.Application.Json)
                applyAuthHeaders()
                setBody(
                    KeyAgreementRequest(
                        privateKeyInfo = privateKeyInfo.toRest(),
                        publicKeyInfo = publicKeyInfo.toRest(),
                        algorithm = KeyAgreementAlgorithmRest.valueOf(algorithm.name),
                        keyDataLen = keyDataLen,
                    ),
                )
            }
        handleErrors(response)
        return response.body<KeyAgreementResponse>().sharedSecret.value
    }

    private fun createHttpClient(): HttpClient {
        val simpleOptions = config.httpClientOptions
        val restOptions = simpleOptions.applyTo(LegacyHttpClientOptions.createDefault { logger = HttpClientLogger(logService = logService) })
        return LegacyHttpClientFactory.createClient(restOptions)
    }

    /**
     * Applies authentication and context headers to the HTTP request based on configuration.
     */
    private fun HttpRequestBuilder.applyAuthHeaders() {
        val authConfig = config.authConfig

        // Add authentication token if using header-based auth
        if (authConfig.methods.contains("header") && authConfig.token != null) {
            header(authConfig.authHeader, "Bearer ${authConfig.token}")
        }

        // Resolve tenant ID from context or config
        val tenantId = resolveTenantId(authConfig)
        if (tenantId != null) {
            header(authConfig.tenantHeader, tenantId)
        }

        // Resolve principal ID from context or config
        val principalId = resolvePrincipalId(authConfig)
        if (principalId != null) {
            header(authConfig.principalHeader, principalId)
        }
    }

    /**
     * Resolves the tenant ID to use based on configuration and current context.
     */
    private fun resolveTenantId(authConfig: RestClientAuthConfig): String? {
        if (authConfig.useTenantFromContext) {
            val contextTenantId = execution.sessionContext.context.tenant.tenantId
            // Use context tenant unless it's anonymous, then fallback to config
            if (contextTenantId != "<anonymous>") {
                return contextTenantId
            }
        }
        return authConfig.tenantId
    }

    /**
     * Resolves the principal ID to use based on configuration and current context.
     */
    private fun resolvePrincipalId(authConfig: RestClientAuthConfig): String? {
        if (authConfig.usePrincipalFromContext) {
            val contextPrincipal = execution.sessionContext.context.principal
            // Use context principal unless it's anonymous, then fallback to config
            if (contextPrincipal != null && contextPrincipal != "<anonymous>") {
                return contextPrincipal.toString()
            }
        }
        return authConfig.principalId
    }

    private suspend fun handleErrors(response: HttpResponse) {
        // Check if the response is an error
        if (!response.status.isSuccess()) {
            val errorResponse = response.body<ErrorResponse>()
            throw IllegalArgumentException(errorResponse.message)
        }
    }
}
