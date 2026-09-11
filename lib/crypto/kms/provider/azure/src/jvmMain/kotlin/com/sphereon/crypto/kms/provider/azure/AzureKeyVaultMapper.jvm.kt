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

import com.azure.core.credential.TokenCredential
import com.azure.core.http.policy.ExponentialBackoffOptions
import com.azure.core.util.ClientOptions
import com.azure.core.util.Header
import com.azure.identity.ClientCertificateCredential
import com.azure.identity.ClientCertificateCredentialBuilder
import com.azure.identity.ClientSecretCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.InteractiveBrowserCredential
import com.azure.identity.InteractiveBrowserCredentialBuilder
import com.azure.identity.UsernamePasswordCredential
import com.azure.identity.UsernamePasswordCredentialBuilder
import com.azure.security.keyvault.certificates.models.KeyVaultCertificate
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm as AzureSignatureAlgorithm
import com.azure.security.keyvault.keys.models.JsonWebKey
import com.azure.security.keyvault.keys.models.KeyCurveName
import com.azure.security.keyvault.keys.models.KeyOperation
import com.azure.security.keyvault.keys.models.KeyProperties
import com.azure.security.keyvault.keys.models.KeyType
import com.azure.security.keyvault.keys.models.KeyVaultKey
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.SignClientException
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import java.time.Duration
import java.io.ByteArrayInputStream

/**
 * Converts KeyProperties to a Key ID format used by Azure Key Vault.
 * Includes version information if available.
 *
 * @return Formatted key ID string combining name and version
 */
fun KeyProperties.toKid() = name + (version?.let { "${KEY_NAME_VERSION_SEP}$it" } ?: "")

/**
 * Converts Azure Key Vault client configuration to Azure SDK ClientOptions.
 *
 * @return ClientOptions with configured headers and application ID, or null if no headers
 */
fun AzureKmsProviderConfig.toClientOptions(): ClientOptions? {
    if (headers.isNullOrEmpty()) {
        return null
    }
    return ClientOptions().setApplicationId(applicationId).setHeaders(headers.map { Header(it.name, it.values) })
}

/**
 * Converts exponential backoff retry options to Azure SDK ExponentialBackoffOptions.
 *
 * @return Configured ExponentialBackoffOptions with retry parameters
 */
fun ExponentialBackoffRetryOpts.toExponentialBackoffOptions(): ExponentialBackoffOptions {
    return ExponentialBackoffOptions().setMaxRetries(maxRetries).setBaseDelay(if (baseDelayInMS == null) null else Duration.ofMillis(baseDelayInMS))
        .setMaxDelay(if (maxDelayInMS == null) null else Duration.ofMillis(maxDelayInMS))
}

/**
 * Converts credential options to Azure SDK TokenCredential based on the credential mode.
 *
 * @param tenantId Azure tenant identifier
 * @return Configured TokenCredential for authentication
 */
fun CredentialOpts.toTokenCredential(tenantId: String): TokenCredential {
    return when (credentialMode) {
        CredentialMode.SERVICE_CLIENT_SECRET -> secretCredentialOpts?.toClientSecretCredential(tenantId) ?: throw SignClientException("No client secret options provided")

        CredentialMode.SERVICE_CLIENT_CERTIFICATE -> certificateCredentialOpts?.toClientCertificateCredential(
            tenantId
        ) ?: throw SignClientException("No client certificate options provided")

        CredentialMode.USER_INTERACTIVE_BROWSER -> interactiveBrowserCredentialOpts?.toInteractiveBrowserCredential(
            tenantId
        ) ?: throw SignClientException("No interactive browser options provided")

        CredentialMode.USER_USERNAME_PASSWORD -> usernamePasswordCredentialOpts?.toUsernamePasswordCredential(
            tenantId
        ) ?: throw SignClientException("No username password options provided")
    }
}

/**
 * Converts secret credential options to Azure SDK ClientSecretCredential.
 *
 * @param tenantId Azure tenant identifier
 * @return Configured ClientSecretCredential
 */
fun SecretCredentialOpts.toClientSecretCredential(tenantId: String): ClientSecretCredential {
    val material = clientSecretMaterial
        ?: throw SignClientException("Client secret material was not resolved by the server-owned secret runtime")
    return ClientSecretCredentialBuilder().clientId(clientId).clientSecret(material).tenantId(tenantId).build()
}

/**
 * Converts certificate credential options to Azure SDK ClientCertificateCredential.
 *
 * @param tenantId Azure tenant identifier
 * @return Configured ClientCertificateCredential
 */
fun CertificateCredentialOpts.toClientCertificateCredential(tenantId: String): ClientCertificateCredential {
    val material = certificateMaterial
        ?: throw SignClientException("Client certificate material was not resolved by the server-owned secret runtime")
    return ByteArrayInputStream(material).use { stream ->
        ClientCertificateCredentialBuilder().clientId(clientId).pemCertificate(stream).tenantId(tenantId).build()
    }
}

/**
 * Converts username/password credential options to Azure SDK UsernamePasswordCredential.
 *
 * @param tenantId Azure tenant identifier
 * @return Configured UsernamePasswordCredential
 */
fun UsernamePasswordCredentialOpts.toUsernamePasswordCredential(tenantId: String): UsernamePasswordCredential {
    return UsernamePasswordCredentialBuilder().clientId(clientId).username(userName).password(password).tenantId(tenantId).build()
}

/**
 * Converts interactive browser credential options to Azure SDK InteractiveBrowserCredential.
 *
 * @param tenantId Azure tenant identifier
 * @return Configured InteractiveBrowserCredential
 */
fun InteractiveBrowserCredentialOpts.toInteractiveBrowserCredential(tenantId: String): InteractiveBrowserCredential {
    return InteractiveBrowserCredentialBuilder().clientId(clientId).redirectUrl(redirectUrl).tenantId(tenantId).build()
}

/**
 * Converts Azure SDK KeyOperation to internal KeyOperations enum.
 *
 * @return Corresponding KeyOperations value
 */
fun KeyOperation.toKeyOperations(): KeyOperations {
    return when (this) {
        KeyOperation.ENCRYPT -> KeyOperations.ENCRYPT
        KeyOperation.DECRYPT -> KeyOperations.DECRYPT
        KeyOperation.SIGN -> KeyOperations.SIGN
        KeyOperation.VERIFY -> KeyOperations.VERIFY
        KeyOperation.WRAP_KEY -> KeyOperations.WRAP_KEY
        KeyOperation.UNWRAP_KEY -> KeyOperations.UNWRAP_KEY
        else -> {
            throw SignClientException("Unsupported key operation for Azure Key Vault")
        }
    }
}

/**
 * Converts Azure KeyVaultKey to JWK (JSON Web Key) format.
 *
 * @return JWK representation of the Azure Key Vault key
 */
fun KeyVaultKey.toJwk(): Jwk {
    val jsonWebKey: JsonWebKey = this.key

    val keyType =
        when (jsonWebKey.keyType) {
            KeyType.EC, KeyType.EC_HSM -> JwaKeyType.EC
            KeyType.RSA, KeyType.RSA_HSM -> JwaKeyType.RSA
            else -> jsonWebKey.keyType?.toString()?.let { JwaKeyType.fromValue(it) }
        }
    val builder = Jwk.Builder().withKid(this.properties.toKid())
        .withKty(keyType)
        .withAlg(mapJwkToAlgorithm(jsonWebKey)) // Algorithm
        .withKeyOps(jsonWebKey.keyOps?.map { JoseKeyOperations.fromValue(it.toKeyOperations().jose.value) }?.toTypedArray())
    when (jsonWebKey.keyType) {
        KeyType.EC, KeyType.EC_HSM -> {
            builder
                .withCrv(
                    when (jsonWebKey.curveName) {
                        KeyCurveName.P_256 -> JwaCurve.P_256
                        KeyCurveName.P_384 -> JwaCurve.P_384
                        KeyCurveName.P_521 -> JwaCurve.P_521
                        else -> null
                    },
                )
                .withX(jsonWebKey.x?.encodeToBase64Url())
                .withY(jsonWebKey.y?.encodeToBase64Url())
        }
        KeyType.RSA, KeyType.RSA_HSM -> {
            builder
                .withN(jsonWebKey.n?.encodeToBase64Url())
                .withE(jsonWebKey.e?.encodeToBase64Url())
        }
        else -> Unit
    }
    return builder.build()
}

/**
 * Converts internal KeyOperations to Azure SDK KeyOperation.
 *
 * @return Corresponding Azure KeyOperation
 */
fun KeyOperations.toAzureKeyOperation(): KeyOperation {
    return when (this) {
        KeyOperations.ENCRYPT -> KeyOperation.ENCRYPT
        KeyOperations.DECRYPT -> KeyOperation.DECRYPT
        KeyOperations.SIGN -> KeyOperation.SIGN
        KeyOperations.UNWRAP_KEY -> KeyOperation.UNWRAP_KEY
        KeyOperations.VERIFY -> KeyOperation.VERIFY
        KeyOperations.WRAP_KEY -> KeyOperation.WRAP_KEY
        KeyOperations.DERIVE_BITS -> throw SignClientException("Azure Key Vault does not support DERIVE_BITS operation")
        KeyOperations.DERIVE_KEY -> throw SignClientException("Azure Key Vault does not support DERIVE_KEY operation")
        KeyOperations.MAC_CREATE -> throw SignClientException("Azure Key Vault does not support MAC_CREATE operation")
        KeyOperations.MAC_VERIFY -> throw SignClientException("Azure Key Vault does not support MAC_VERIFY operation")
    }
}

/**
 * Maps Azure JsonWebKey to corresponding JWA algorithm based on key type and curve.
 *
 * @param jwk JsonWebKey to analyze
 * @return Corresponding JwaAlgorithm or null if not determinable
 */
fun mapJwkToAlgorithm(jwk: JsonWebKey): JwaAlgorithm? {
    return when (jwk.keyType) {
        KeyType.EC, KeyType.EC_HSM -> when {
            jwk.curveName.toString() == "P-256" -> JwaAlgorithm.ES256
            jwk.curveName.toString() == "P-384" -> JwaAlgorithm.ES384
            jwk.curveName.toString() == "P-521" -> JwaAlgorithm.ES512
            else -> null
        }

        // Azure RSA keys are capable of both RSASSA-PKCS1-v1.5 and RSASSA-PSS. The Key Vault
        // public-key response does not carry a signing-algorithm restriction, so synthesizing
        // RS256 here would make a selector/lookup reject valid PS* requests.
        KeyType.RSA, KeyType.RSA_HSM -> null
        else -> null
    }
}

/**
 * Converts internal Curve enum to Azure SDK KeyCurveName.
 *
 * @return Corresponding Azure KeyCurveName
 */
fun Curve.toAzureKeyCurveName(): KeyCurveName {
    return when (this) {
        Curve.P_256 -> KeyCurveName.P_256
        Curve.P_384 -> KeyCurveName.P_384
        Curve.P_521 -> KeyCurveName.P_521
        Curve.Ed25519 -> throw SignClientException("Curve Ed25519 is not supported")
        Curve.X25519 -> throw SignClientException("Curve X25519 is not supported")
        else -> throw SignClientException("Unsupported curve: $this")
    }
}

/**
 * Determines the appropriate signature algorithm for an Azure KeyVaultKey.
 *
 * @return Corresponding SignatureAlgorithm based on key type and curve
 */
fun KeyVaultKey.toSignatureAlgorithm(): SignatureAlgorithm? {
    return when (this.key.keyType) {
        KeyType.EC, KeyType.EC_HSM -> {
            when (this.key.curveName) {
                KeyCurveName.P_256 -> SignatureAlgorithm.ECDSA_SHA256
                KeyCurveName.P_384 -> SignatureAlgorithm.ECDSA_SHA384
                KeyCurveName.P_521 -> SignatureAlgorithm.ECDSA_SHA512
                else -> throw SignClientException("Unsupported curve: ${this.key.curveName}")
            }
        }

        KeyType.RSA, KeyType.RSA_HSM -> {
            // Azure's public RSA response does not identify an RS*/PS* restriction. Selecting
            // PS256 here would fabricate policy; callers must supply the algorithm explicitly.
            null
        }

        else -> throw SignClientException("Unsupported key type: ${this.key.keyType}")
    }
}

/**
 * Map an Azure KeyVaultKey into our ManagedKeyInfo<Jwk>.
 *
 * @return ManagedKeyInfo containing the converted key information
 */
fun KeyVaultKey.toManagedKeyInfo(): ManagedKeyInfoType<Jwk> {
    val vaultUrl = this.properties.id.substringBefore("/keys")  // https://sphereon.vault.azure.net/keys/aKey/1234

    // build kid in the "<name>:<version>" format
    val name = this.properties.name
    val version = this.properties.version.orEmpty()
    val kid = if (version.isBlank()) {
        name
    } else {
        "$name:$version"
    }
    val jwkWithKid = this.toJwk().copy(kid = kid)
    val resolved = ResolvedKeyInfo.fromKey(jwkWithKid)
    return ManagedKeyInfo(
        alias = name, providerId = vaultUrl, resolvedKeyInfo = resolved
    )
}

/**
 * Converts Azure KeyVaultCertificate to managed certificate information with JWK.
 * Uses only the public leaf CER returned by Azure Key Vault. Full-chain retrieval is
 * intentionally unsupported here; following certificate AIA URLs would create an
 * unconstrained outbound-network boundary outside the provider API.
 *
 * @return ManagedKeyInfo containing the certificate and key information
 */
suspend fun KeyVaultCertificate.toManagedCertInfo(): ManagedKeyInfoType<Jwk> {
    val name = properties.name
    val version = properties.version
    val kid = "$name:$version"

    val leafCert: Certificate = certificateFromDer(cer)
    val x5c = azureCertificateX5c(leafCert.der)

    // build the JWK
    val jwk = Jwk.from(leafCert.getPublicKeyJwk(x5c = x5c))
    val resolved = ResolvedKeyInfo.fromKey(jwk)
    return ManagedKeyInfo(
        alias = kid, providerId = properties.id.substringBefore("/certificates"), resolvedKeyInfo = resolved
    )
}

/** RFC 7517 x5c uses standard padded base64, unlike JWK thumbprints. */
internal fun azureCertificateX5c(der: ByteArray): Array<String> = arrayOf(der.encodeToBase64())
