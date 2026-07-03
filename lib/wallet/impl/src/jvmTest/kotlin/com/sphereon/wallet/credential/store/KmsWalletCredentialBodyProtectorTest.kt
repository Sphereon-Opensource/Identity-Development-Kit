/*
 * Copyright 2026 Sphereon International B.V.
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
 */

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.wallet.impl.di.createWalletAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmsWalletCredentialBodyProtectorTest {
    @Test
    fun protectOpenRoundTripUsesRealKmsAndBindsAadToWalletRecordAndInstance() =
        runTest {
            val protector = createProtector()
            val plaintext = "credential-body-secret".encodeToByteArray()

            val protectedBody = protector.protect("wallet-a", "record-1", "instance-1", plaintext)

            assertTrue(protectedBody.isOk)
            assertFalse(protectedBody.value.decodeToString().contains("credential-body-secret"))
            val opened = protector.open("wallet-a", "record-1", "instance-1", protectedBody.value)
            assertTrue(opened.isOk)
            assertEquals("credential-body-secret", opened.value.decodeToString())
            assertTrue(protector.open("wallet-b", "record-1", "instance-1", protectedBody.value).isErr)
            assertTrue(protector.open("wallet-a", "record-2", "instance-1", protectedBody.value).isErr)
            assertTrue(protector.open("wallet-a", "record-1", "instance-2", protectedBody.value).isErr)
        }

    @Test
    fun openRejectsUnsupportedProtectedBodyEnvelope() =
        runTest {
            val protector = createProtector()
            val unsupportedEnvelope =
                """
                {
                  "version": 2,
                  "protection": "kms-a256gcm",
                  "algorithm": "A256GCM",
                  "keyAlias": "wallet-instances/wallet-a/credential-body/a256gcm",
                  "iv": "",
                  "authTag": "",
                  "ciphertext": ""
                }
                """.trimIndent().encodeToByteArray()

            val result = protector.open("wallet-a", "record-1", "instance-1", unsupportedEnvelope)

            assertTrue(result.isErr)
            assertEquals("UNSUPPORTED_OPERATION", result.error.code)
        }

    private fun createProtector(): KmsWalletCredentialBodyProtector {
        val sessionId = "wallet-body-protector-${Uuid.v4String()}"
        val app =
            createWalletAppGraph(
                application = "WalletBodyProtectorTest",
                appId = "com.sphereon.wallet.body-protector-test",
                profile = "test",
                version = "0.1.0",
            )
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId(sessionId)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        val config =
            SoftwareKmsProviderConfig(
                id = "$sessionId-software-kms",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(config, session.asCoreApiServiceGraph().serviceExecution)
        kms.registerProvider(provider, makeDefaultKms = true)
        return KmsWalletCredentialBodyProtector(kms)
    }
}
