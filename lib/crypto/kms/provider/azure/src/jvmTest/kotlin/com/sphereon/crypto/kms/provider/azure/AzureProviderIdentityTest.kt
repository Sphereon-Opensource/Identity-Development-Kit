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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AzureProviderIdentityTest {
    private val config = AzureKmsProviderConfig(
        id = "azure-shared-signing",
        applicationId = "edk-signing",
        keyvaultUrl = "https://fixture.vault.azure.net",
        tenantId = "00000000-0000-0000-0000-000000000001",
        credentialOpts = CredentialOpts(
            credentialMode = CredentialMode.SERVICE_CLIENT_SECRET,
            secretCredentialOpts = SecretCredentialOpts(
                clientId = "00000000-0000-0000-0000-000000000002",
                clientSecretId = "broker-bound",
                clientSecretMaterial = "unused",
            ),
        ),
        hsmType = HSMType.KEYVAULT,
    )

    @Test
    fun providerIdIsTheConfiguredIdNotTheApplicationId() {
        val provider = AzureKeyVaultCryptoProvider(config)
        assertEquals("azure-shared-signing", provider.id)
    }

    @Test
    fun clientOptionsCarryTheApplicationIdWithoutHeaders() {
        assertEquals("edk-signing", assertNotNull(config.toClientOptions()).applicationId)
    }
}
