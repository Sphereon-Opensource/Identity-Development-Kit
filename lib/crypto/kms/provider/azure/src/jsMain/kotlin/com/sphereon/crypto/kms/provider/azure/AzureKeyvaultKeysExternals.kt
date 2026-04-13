@file:JsModule("@azure/keyvault-keys")

package com.sphereon.crypto.kms.provider.azure

import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external class KeyClient(keyvaultUrl: String, credential: ClientSecretCredential) {
    fun createEcKey(keyName: String, options: CreateEcKeyOptions): Promise<AzureKeyVaultKey>
    fun createRsaKey(keyName: String, options: CreateRsaKeyOptions): Promise<AzureKeyVaultKey>
    fun getKey(keyName: String): Promise<AzureKeyVaultKey>
    fun listPropertiesOfKeys(): AsyncIterableIterator<KeyProperties>
    fun beginDeleteKey(keyName: String): Promise<AzureKeyVaultKey>
    fun importKey(name: String, key: JsonWebKey): Promise<AzureKeyVaultKey>
}

external class CryptographyClient(key: AzureKeyVaultKey, credential: ClientSecretCredential) {
    // sign/verify: take pre-hashed digest — no internal hashing
    fun sign(algorithm: String, digest: Uint8Array): Promise<AzureKeyvaultSignDataResult>
    fun verify(algorithm: String, digest: Uint8Array, signature: Uint8Array): Promise<AzureKeyvaultVerifyDataResult>
    // signData/verifyData: take raw data — hash internally
    fun signData(algorithm: String, data: Uint8Array): Promise<AzureKeyvaultSignDataResult>
    fun verifyData(algorithm: String, data: Uint8Array, signature: Uint8Array): Promise<AzureKeyvaultVerifyDataResult>
    fun encrypt(algorithm: String, plaintext: Uint8Array): Promise<AzureKeyvaultEncryptResult>
    fun decrypt(algorithm: String, ciphertext: Uint8Array): Promise<AzureKeyvaultDecryptResult>
    fun wrapKey(algorithm: String, key: Uint8Array): Promise<AzureKeyvaultWrapResult>
    fun unwrapKey(algorithm: String, encryptedKey: Uint8Array): Promise<AzureKeyvaultUnwrapResult>
}

external interface CreateEcKeyOptions {
    var curve: String?
    var keyOperations: Array<String>?
}

external interface CreateRsaKeyOptions {
    var keySize: Int?
    var keyOperations: Array<String>?
}
