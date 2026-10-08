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

package com.sphereon.crypto.kms.provider.azure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.promise
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignOutput
import com.sphereon.crypto.core.sign.model.Signature
import kotlin.js.Promise

/**
 * JavaScript-exported wrapper for AzureKeyVaultCryptoProvider.
 * Provides Promise-based API for use in JavaScript environments.
 *
 * @param config Azure KMS provider configuration
 * @param settings Key provider settings
 */
@JsExport
@JsName("AzureKeyVaultCryptoProvider")
class AzureKeyVaultCryptoProviderJS(
    config: AzureKmsProviderConfig,
//    settings: KeyProviderSettings
) {
    private val provider: AzureKeyVaultCryptoProvider
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        require(config.credentialOpts.secretCredentialOpts != null) { "Azure Key Vault requires a secret credential" }
        provider = AzureKeyVaultCryptoProvider(config)
    }

    /**
     * Request parameters for key generation operations.
     */
    @JsName("GenerateKeyRequest")
    class GenerateKeyRequest(
        val alias: String? = null,
        val use: JwkUse? = null,
        val keyOperations: Array<KeyOperations>? = null,
        val alg: SignatureAlgorithm? = null
    )

    /**
     * Generates a new key in Azure Key Vault asynchronously.
     *
     * @param request Key generation parameters
     * @return Promise resolving to the generated ManagedKeyPair
     */
    @JsName("generateKeyAsync")
    fun generateKeyAsyncJS(request: GenerateKeyRequest): Promise<ManagedKeyPair> {
        return scope.promise {
            provider.generateKeyAsync(request.alias, request.use, request.keyOperations, request.alg)
        }
    }

    /**
     * Request parameters for raw signature creation.
     */
    class CreateRawSignatureRequest(
        val keyInfo: KeyInfoType<*>,
        val input: ByteArray,
        val requireX5Chain: Boolean? = false
    )


    /**
     * Creates a raw signature asynchronously.
     *
     * @param request Signature creation parameters
     * @return Promise resolving to the signature bytes
     */
    @JsName("createRawSignature")
    fun createRawSignatureJS(request: CreateRawSignatureRequest): Promise<ByteArray> {
        return scope.promise {
            provider.createRawSignature(
                request.keyInfo,
                request.input,
                request.requireX5Chain ?: false
            )
        }
    }

    /**
     * Request parameters for raw signature validation.
     */
    class IsValidRawSignatureRequest(
        val keyInfo: KeyInfoType<*>,
        val input: ByteArray,
        val signature: ByteArray
    )

    /**
     * Validates a raw signature asynchronously.
     *
     * @param request Signature validation parameters
     * @return Promise resolving to validation result
     */
    @JsName("isValidRawSignature")
    fun isValidRawSignatureJS(
        request: IsValidRawSignatureRequest
    ): Promise<Boolean> {
        return scope.promise {
            provider.isValidRawSignature(
                request.keyInfo,
                request.input,
                request.signature
            )
        }
    }

    /**
     * Fetches key information asynchronously.
     *
     * @param keyInfo Key identifier string
     * @return Promise resolving to ManagedKeyInfo
     */
    @JsName("fetchKeyAsync")
    fun fetchKeyAsyncJS(keyInfo: String): Promise<ManagedKeyInfoType<Jwk>> {
        return scope.promise {
            provider.fetchKeyAsync(keyInfo)
        }
    }

    /**
     * Request parameters for signature creation.
     */
    class CreateSignatureRequest(
        val signInput: SignInput,
        val keyInfo: KeyInfoType<*>? = null,
        val signatureAlgorithm: SignatureAlgorithm? = null
    )

    /**
     * Creates a complete signature asynchronously.
     *
     * @param request Signature creation parameters
     * @return Promise resolving to SignOutput
     */
    @JsName("createSignatureAsync")
    fun createSignatureAsyncJS(request: CreateSignatureRequest): Promise<SignOutput> {
        return scope.promise {
            provider.createSignature(
                request.signInput,
                request.keyInfo,
                request.signatureAlgorithm
            )
        }
    }

    /**
     * Request parameters for signature validation.
     */
    class IsValidSignatureRequest(
        val signInput: SignInput,
        val signature: Signature
    )

    /**
     * Validates a signature asynchronously.
     *
     * @param request Signature validation parameters
     * @return Promise resolving to validation result
     */
    @JsName("isValidSignatureAsync")
    fun isValidSignatureAsyncJS(request: IsValidSignatureRequest): Promise<Boolean> {
        return scope.promise {
            provider.isValidSignature(request.signInput, request.signature)
        }
    }
}
