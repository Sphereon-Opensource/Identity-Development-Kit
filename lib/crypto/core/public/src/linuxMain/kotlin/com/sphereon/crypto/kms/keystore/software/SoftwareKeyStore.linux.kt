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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyStore
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.x509.Certificate
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject

/**
 * Software-based keystore is not available on Linux Native platforms.
 *
 * This implementation throws exceptions indicating that software KMS is not supported
 * on Linux Native. For Linux, use JVM-based keystores or hardware security modules.
 */
@AssistedInject
actual class SoftwareKeyStoreService actual constructor(
    @Assisted config: KeyStoreConfig
) : KeyStore {
    private val config: SoftwareKeyStoreConfig = config as SoftwareKeyStoreConfig

    actual override val id = config.id
    actual override val keyStoreType = config.keyStoreType
    actual override val keyTypesSupported: Array<KeyTypeMapping> = emptyArray()
    actual override val signatureAlgorithmsSupported: Array<SignatureAlgorithm> = emptyArray()

    actual override val settings: KeyProviderSettings?
        get() = throw PKIException("Software keystore is not available on Linux Native platform")

    init {
        throw PKIException("Software keystore is not available on Linux Native platform. Please use JVM-based keystores or hardware security modules instead.")
    }

    actual override suspend fun listKeys(): Array<ManagedKeyInfoType<*>> {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*> {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?
    ): ManagedKeyInfoType<*> {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override fun keyVisibility(): KeyVisibility {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override fun exposesPrivateKeysForSigning(): Boolean = false

    actual override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?
    ) {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun listCertificateChainAliases(): Array<String> {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun getCertificateChain(alias: String): Array<Certificate> {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun deleteCertificateChain(alias: String): Boolean {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun storeTrustedCertificate(alias: String, certificate: Certificate) {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun listCertificateAliases(): Array<String> {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun getCertificate(alias: String): Certificate {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }

    actual override suspend fun deleteCertificate(alias: String): Boolean {
        throw PKIException("Software keystore is not available on Linux Native platform")
    }
}
