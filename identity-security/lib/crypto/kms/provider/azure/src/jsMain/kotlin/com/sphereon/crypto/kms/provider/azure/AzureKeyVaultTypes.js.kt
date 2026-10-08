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

import org.khronos.webgl.Uint8Array

/**
 * External interface representing an Azure Key Vault key from the Azure SDK for JavaScript.
 * Contains key details, metadata, and operational properties.
 */
external interface AzureKeyVaultKey {
    val key: AzureKeyvaultKeyDetails
    val id: String
    val name: String
    val keyOperations: Array<String>
    val keyType: String
    val properties: KeyProperties
}

/**
 * External interface representing the cryptographic details of an Azure Key Vault key.
 * Contains key material and parameters for both EC and RSA keys.
 */
external interface AzureKeyvaultKeyDetails {
    val kid: String
    val kty: String
    val keyOps: Array<String>

    // EC key properties
    val crv: String?
    val x: ByteArray?
    val y: ByteArray?

    // RSA key properties
    val alg: String?
    val n: ByteArray?
    val e: ByteArray?
    val d: ByteArray?
}

/**
 * External interface representing Azure Key Vault key properties and metadata.
 * Contains lifecycle information, policies, and operational settings.
 */
external interface KeyProperties {
    val tags: dynamic // Can be null/undefined
    val enabled: Boolean
    val notBefore: dynamic // Can be null/undefined
    val expiresOn: dynamic // Can be null/undefined
    val createdOn: String // ISO date string
    val updatedOn: String // ISO date string
    val recoverableDays: Int
    val recoveryLevel: String
    val exportable: Boolean
    val releasePolicy: dynamic // Can be null/undefined
    val hsmPlatform: String
    val vaultUrl: String
    val version: String
    val name: String
    val managed: dynamic // Can be null/undefined
    val id: String
}

/**
 * Converts KeyProperties to a Key ID format used by Azure Key Vault.
 *
 * @return Formatted key ID string combining name and version
 */
fun KeyProperties.toKid() = "$name${KEY_NAME_VERSION_SEP}$version"

/**
 * External interface representing the result of a sign operation from Azure Key Vault.
 * Contains the signature bytes, algorithm used, and key identifier.
 */
external interface AzureKeyvaultSignDataResult {
    val result: org.khronos.webgl.Uint8Array
    val algorithm: String
    val keyID: String
}

/**
 * External interface representing the result of a signature verification operation.
 * Contains the verification result and key identifier used.
 */
external interface AzureKeyvaultVerifyDataResult {
    val result: Boolean
    val keyID: String
}

/**
 * External interface representing a Key Vault key response from the Azure SDK.
 * Contains key information and properties returned from key operations.
 */
external interface KeyVaultKeyResponse {
    val name: String
    val id: String
    val key: AzureKeyvaultKeyDetails
    val properties: KeyProperties
}

/**
 * External interface for options when creating a new key in Azure Key Vault.
 * Allows specification of key operations, lifecycle settings, and metadata.
 */
external interface CreateKeyOptions {
    var keyOps: Array<String>?
    var enabled: Boolean?
    var notBefore: dynamic
    var expiresOn: dynamic
    var tags: dynamic
}

/**
 * External interface for options when importing an existing key into Azure Key Vault.
 * Specifies the key name, key material, and HSM usage preference.
 */
external interface ImportKeyOptions {
    var name: String
    var key: dynamic // JsonWebKey
    var hsm: Boolean?
}

/**
 * External interface representing a JSON Web Key (JWK) for Azure Key Vault operations.
 * Contains key material and parameters for both EC and RSA keys in JWK format.
 */
external interface JsonWebKey {
    var kty: String
    var key_ops: Array<String>?
    var crv: String?
    var kid: String?
    var alg: String?
    var use: String?

    var x: Uint8Array?
    var y: Uint8Array?
    var d: Uint8Array?

    // For RSA keys
    var n: Uint8Array?
    var e: Uint8Array?
}
