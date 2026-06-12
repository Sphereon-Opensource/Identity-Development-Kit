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

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Diagnosability contract for missing-key lookups in the JVM software (PKCS12) keystore.
 *
 * Production symptom: encrypting an identifier with key alias "idfr:enc:&lt;tenantId&gt;" on a
 * fresh keystore surfaces as "Encryption failed: null" in the KMS encrypt command. The "null"
 * comes from the keystore's missing-key exception carrying no message ([Throwable.message] is
 * null), so every layer that reports `exception.message` loses the alias being looked up.
 *
 * The contract pinned here: a failed key lookup must throw an exception whose message is
 * non-null and names the alias, so command-layer error wrapping stays diagnosable.
 */
class SoftwareKeyStoreServiceMissingKeyDiagnosticsTest {
    private fun newPkcs12Service(): SoftwareKeyStoreService {
        val dir = Files.createTempDirectory("sks-missing-key-test").toFile()
        dir.deleteOnExit()
        val config =
            Pkcs12KeyStoreConfig(
                id = "test-pkcs12",
                password = "test-password",
                path = dir.resolve("test-keystore.p12").absolutePath,
                persist = true,
                overwriteAlias = true,
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
            )
        return SoftwareKeyStoreService(config)
    }

    @Test
    fun missingAliasLookupThrowsWithAliasInMessage() =
        runTest {
            val service = newPkcs12Service()
            val alias = "idfr:enc:application"

            val exception =
                assertFailsWith<Exception> {
                    service.getKey(KeyInfo<Jwk>(alias = alias))
                }

            assertNotNull(
                exception.message,
                "Missing-key exception must carry a message (a null message surfaces as 'Encryption failed: null' upstream); was ${exception::class.simpleName} with toString: $exception",
            )
            assertTrue(
                exception.message!!.contains(alias),
                "Missing-key exception message must name the alias being looked up; got: ${exception.message}",
            )
        }
}
