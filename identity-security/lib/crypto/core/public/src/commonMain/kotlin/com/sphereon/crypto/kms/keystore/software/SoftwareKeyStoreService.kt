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

package com.sphereon.crypto.kms.keystore.software

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
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

// The SoftwareKeyStoreService class is implemented in platform-specific source sets
// This expect class allows the factory to reference the platform-specific implementation
@AssistedInject
expect class SoftwareKeyStoreService(
    @Assisted config: KeyStoreConfig, // KIWA-43: Config should be added to KeyStoreService interface
) : KeyStore {
    override val id: String
    override val keyStoreType: String
    override val keyTypesSupported: Array<KeyTypeMapping>
    override val signatureAlgorithmsSupported: Array<SignatureAlgorithm>

    // KIWA-43: Legacy property from KeyStoreService interface - returns null until interface is refactored
    override val settings: KeyProviderSettings?

    override fun keyVisibility(): KeyVisibility

    override suspend fun listKeys(): Array<ManagedKeyReference>

    override suspend fun getKey(keyInfo: KeyInfoType<*>): ManagedKeyInfoType<*>

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        providerId: String,
        alias: String,
        certChain: Array<Certificate>?,
    ): ManagedKeyInfoType<*>

    override suspend fun deleteKey(keyInfo: KeyInfoType<*>): Boolean

    override suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>?,
    )

    override suspend fun listCertificateChainAliases(): Array<String>

    override suspend fun getCertificateChain(alias: String): Array<Certificate>

    override suspend fun deleteCertificateChain(alias: String): Boolean

    override suspend fun storeTrustedCertificate(
        alias: String,
        certificate: Certificate,
    )

    override suspend fun listCertificateAliases(): Array<String>

    override suspend fun getCertificate(alias: String): Certificate

    override suspend fun deleteCertificate(alias: String): Boolean

    override fun exposesPrivateKeysForSigning(): Boolean
}
