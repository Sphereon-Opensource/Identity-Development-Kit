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
 */

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.testutil.createCryptoTestAppComponent
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests using official IETF RFC 7516 test vectors.
 *
 * These tests verify compliance with the JWE specification by using
 * the exact test vectors from RFC 7516 Appendix A.
 *
 * RFC 7516: https://datatracker.ietf.org/doc/html/rfc7516
 */
class JweRfcTestVectorsTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jweService: JweService

    val app = createCryptoTestAppComponent(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("jwe-rfc-test")

    @BeforeTest
    fun setUp() {
        // Register the software KMS provider
        val config = SoftwareKmsProviderConfig(
            id = "jwe-rfc-test-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        app as SoftwareKmsProviderFactoryImpl.Component
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceComponent().serviceExecution)

        // Get KeyManagerService and JweService from the session component
        keyManagerService = session.component.asKeyManagerServiceComponent().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jweService = (session.component as JweServiceImpl.Component).jweService
    }

    /**
     * RFC 7516 Appendix A.1 - Example JWE using RSAES-OAEP and AES GCM
     *
     * This test verifies that we can decrypt the official test vector from the RFC.
     * We cannot test encryption with exact match because:
     * 1. The CEK (Content Encryption Key) is randomly generated
     * 2. The IV (Initialization Vector) is randomly generated
     * 3. The encrypted key will differ due to OAEP padding randomness
     *
     * This test uses the complete private key from RFC 7516 Appendix A.1.3,
     * which includes all parameters (n, e, d, p, q, dp, dq, qi) as specified in the RFC.
     */
    @Test
    fun testRfc7516AppendixA1_RSA_OAEP_A256GCM_Decryption() = runTest {
        // RFC 7516 Appendix A.1 - Complete compact serialization
        val jweCompactString = "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ." +
            "OKOawDo13gRp2ojaHV7LFpZcgV7T6DVZKTyKOMTYUmKoTCVJRgckCL9kiMT03JGeipsEdY3mx_etLbbWSrFr05kLzcSr4qKAq7YN7e9jwQRb23nfa6c9d-StnImGyFDbSv04uVuxIp5Zms1gNxKKK2Da14B8S4rzVRltdYwam_lDp5XnZAYpQdb76FdIKLaVmqgfwX7XWRxv2322i-vDxRfqNzo_tETKzpVLzfiwQyeyPGLBIO56YJ7eObdv0je81860ppamavo35UgoRdbYaBcoh9QcfylQr66oc6vFWXRcZ_ZT2LawVCWTIy3brGPi6UklfCpIMfIjf7iGdXKHzg." +
            "48V1_ALb6US04U3b." +
            "5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6jiSdiwkIr3ajwQzaBtQD_A." +
            "XFBoMYUZodetZdvTiFvSkQ"

        // Expected plaintext from RFC
        val expectedPlaintext = "The true sign of intelligence is not knowledge but imagination."

        // RSA private key from RFC 7516 Appendix A.1.3 (complete JWK with all parameters)
        val privateKeyJwk = Jwk(
            kty = JwaKeyType.RSA,
            n = "oahUIoWw0K0usKNuOR6H4wkf4oBUXHTxRvgb48E-BVvxkeDNjbC4he8rUWcJoZmds2h7M70imEVhRU5djINXtqllXI4DFqcI1DgjT9LewND8MW2Krf3Spsk_ZkoFnilakGygTwpZ3uesH-PFABNIUYpOiN15dsQRkgr0vEhxN92i2asbOenSZeyaxziK72UwxrrKoExv6kc5twXTq4h-QChLOln0_mtUZwfsRaMStPs6mS6XrgxnxbWhojf663tuEQueGC-FCMfra36C9knDFGzKsNa7LZK2djYgyD3JR_MB_4NUJW_TqOQtwHYbxevoJArm-L5StowjzGy-_bq6Gw",
            e = "AQAB",
            d = "kLdtIj6GbDks_ApCSTYQtelcNttlKiOyPzMrXHeI-yk1F7-kpDxY4-WY5NWV5KntaEeXS1j82E375xxhWMHXyvjYecPT9fpwR_M9gV8n9Hrh2anTpTD93Dt62ypW3yDsJzBnTnrYu1iwWRgBKrEYY46qAZIrA2xAwnm2X7uGR1hghkqDp0Vqj3kbSCz1XyfCs6_LehBwtxHIyh8Ripy40p24moOAbgxVw3rxT_vlt3UVe4WO3JkJOzlpUf-KTVI2Ptgm-dARxTEtE-id-4OJr0h-K-VFs3VSndVTIznSxfyrj8ILL6MG_Uv8YAu7VILSB3lOW085-4qE3DzgrTjgyQ",
            p = "1r52Xk46c-LsfB5P442p7atdPUrxQSy4mti_tZI3Mgf2EuFVbUoDBvaRQ-SWxkbkmoEzL7JXroSBjSrK3YIQgYdMgyAEPTPjXv_hI2_1eTSPVZfzL0lffNn03IXqWF5MDFuoUYE0hzb2vhrlN_rKrbfDIwUbTrjjgieRbwC6Cl0",
            q = "wLb35x7hmQWZsWJmB_vle87ihgZ19S8lBEROLIsZG4ayZVe9Hi9gDVCOBmUDdaDYVTSNx_8Fyw1YYa9XGrGnDew00J28cRUoeBB_jKI1oma0Orv1T9aXIWxKwd4gvxFImOWr3QRL9KEBRzk2RatUBnmDZJTIAfwTs0g68UZHvtc",
            dP = "ZK-YwE7diUh0qR1tR7w8WHtolDx3MZ_OTowiFvgfeQ3SiresXjm9gZ5KLhMXvo-uz-KUJWDxS5pFQ_M0evdo1dKiRTjVw_x4NyqyXPM5nULPkcpU827rnpZzAJKpdhWAgqrXGKAECQH0Xt4taznjnd_zVpAmZZq60WPMBMfKcuE",
            dQ = "Dq0gfgJ1DdFGXiLvQEZnuKEN0UUmsJBxkjydc3j4ZYdBiMRAy86x0vHCjywcMlYYg4yoC4YZa9hNVcsjqA3FeiL19rk8g6Qn29Tt0cj8qqyFpz9vNDBUfCAiJVeESOjJDZPYHdHY8v1b-o-Z2X5tvLx-TCekf7oxyeKDUqKWjis",
            qInv = "VIMpMYbPf47dT1w_zDUXfPimsSegnMOA1zTaX7aGk_8urY6R8-ZW1FxU7AlWAyLWybqq6t16VFd7hQd0y6flUK4SlOydB61gwanOsXGOAOv82cHq0E3eL4HrtZkUuKvnPrMnsUUFlfUdybVzxyjz9JF_XyaY14ardLSjf4L_FNY"
        )

        // Create ResolvedKeyInfo for the private key
        val resolvedKeyInfo = com.sphereon.crypto.core.ResolvedKeyInfo(
            key = privateKeyJwk,
            keyVisibility = KeyVisibility.PRIVATE,
            alias = "rfc-test-key"
        )

        // Store the key in the KMS
        val managedKeyInfo = keyManagerService.storeKey(
            keyInfo = resolvedKeyInfo,
            providerId = "jwe-rfc-test-provider",
            alias = "rfc-test-key"
        )

        // Create decryptor identifier using the stored managed key
        val decryptor = ManagedOptsKeyInfo(
            identifier = managedKeyInfo,
            context = IdentifierContext(
                clientId = "rfc-test",
                clientIdScheme = "jwe",
                issuer = "https://rfc7516.test"
            )
        )

        // Parse the JWE compact serialization
        val jweCompact = JweCompact.parse(jweCompactString)

        // Verify header
        assertEquals("RSA-OAEP", jweCompact.header.alg, "Algorithm should be RSA-OAEP")
        assertEquals("A256GCM", jweCompact.header.enc, "Encryption should be A256GCM")

        // Decrypt the JWE
        val decryptResult = jweService.decryptJwe(
            DecryptJweArgs(
                jwe = jweCompact,
                decryptor = decryptor
            )
        )

        // Verify decryption succeeded
        if (!decryptResult.isOk) {
            println("Decryption failed: ${decryptResult.error}")
        }
        assertTrue(decryptResult.isOk, "Decryption should succeed for RFC test vector")

        // Verify plaintext matches
        val decryptedPlaintext = decryptResult.value.plaintext?.decodeToString()
        assertNotNull(decryptedPlaintext, "Decrypted plaintext should not be null")
        assertEquals(
            expectedPlaintext,
            decryptedPlaintext,
            "Decrypted plaintext should match RFC test vector"
        )
    }

    /**
     * Test parsing of the RFC 7516 Appendix A.1 compact serialization.
     *
     * Verifies that we can correctly parse all 5 components of the compact format:
     * 1. Protected Header (base64url encoded)
     * 2. Encrypted Key (base64url encoded)
     * 3. Initialization Vector (base64url encoded)
     * 4. Ciphertext (base64url encoded)
     * 5. Authentication Tag (base64url encoded)
     */
    @Test
    fun testRfc7516AppendixA1_ParseCompactSerialization() {
        val jweCompactString = "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ." +
            "OKOawDo13gRp2ojaHV7LFpZcgV7T6DVZKTyKOMTYUmKoTCVJRgckCL9kiMT03JGeipsEdY3mx_etLbbWSrFr05kLzcSr4qKAq7YN7e9jwQRb23nfa6c9d-StnImGyFDbSv04uVuxIp5Zms1gNxKKK2Da14B8S4rzVRltdYwam_lDp5XnZAYpQdb76FdIKLaVmqgfwX7XWRxv2322i-vDxRfqNzo_tETKzpVLzfiwQyeyPGLBIO56YJ7eObdv0je81860ppamavo35UgoRdbYaBcoh9QcfylQr66oc6vFWXRcZ_ZT2LawVCWTIy3brGPi6UklfCpIMfIjf7iGdXKHzg." +
            "48V1_ALb6US04U3b." +
            "5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6jiSdiwkIr3ajwQzaBtQD_A." +
            "XFBoMYUZodetZdvTiFvSkQ"

        // Parse
        val jweCompact = JweCompact.parse(jweCompactString)

        // Verify structure
        assertNotNull(jweCompact, "Parsed JWE should not be null")
        assertEquals("RSA-OAEP", jweCompact.header.alg)
        assertEquals("A256GCM", jweCompact.header.enc)

        // Verify all components are present and non-empty
        assertTrue(jweCompact.encryptedKey.isNotEmpty(), "Encrypted key should not be empty")
        assertTrue(jweCompact.iv.isNotEmpty(), "IV should not be empty")
        assertTrue(jweCompact.ciphertext.isNotEmpty(), "Ciphertext should not be empty")
        assertTrue(jweCompact.authTag.isNotEmpty(), "Auth tag should not be empty")

        // Verify we can serialize back
        val serialized = jweCompact.serialize()
        assertNotNull(serialized, "Serialized form should not be null")
        assertTrue(serialized.contains("."), "Serialized form should contain dots")

        // Count dots (should be 4 dots separating 5 parts)
        val dotCount = serialized.count { it == '.' }
        assertEquals(4, dotCount, "Compact serialization should have exactly 4 dots")
    }

    /**
     * Test round-trip encryption/decryption with RSA-OAEP and A256GCM.
     *
     * This test uses the same algorithms as RFC 7516 Appendix A.1 but with
     * our own generated keys and plaintext.
     */
    @Test
    fun testRoundTrip_RSA_OAEP_A256GCM() = runTest {
        // Generate RSA key pair
        val managedKeyPair = keyManagerService.generateKeyAsync(
            alg = com.sphereon.crypto.core.generic.SignatureAlgorithm.RSA_SHA256
        )
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        // Create identifiers
        val recipient = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "round-trip-test",
                clientIdScheme = "jwe",
                issuer = "https://example.com"
            )
        )
        val decryptor = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "round-trip-test",
                clientIdScheme = "jwe",
                issuer = "https://example.com"
            )
        )

        // Use same plaintext as RFC test vector
        val plaintext = "The true sign of intelligence is not knowledge but imagination.".encodeToByteArray()

        // Encrypt
        val prepareResult = jweService.prepareJwe(
            PrepareJweArgs(
                plaintext = plaintext,
                recipient = recipient,
                keyEncryptionAlg = "RSA-OAEP",
                contentEncryptionAlg = "A256GCM"
            )
        )
        assertTrue(prepareResult.isOk, "Prepare should succeed")

        val createResult = jweService.createJweCompact(
            CreateJweCompactArgs(preparedJwe = prepareResult.value)
        )
        assertTrue(createResult.isOk, "Create should succeed")

        val jweCompact = createResult.value

        // Verify structure
        assertEquals("RSA-OAEP", jweCompact.header.alg)
        assertEquals("A256GCM", jweCompact.header.enc)

        // Decrypt
        val decryptResult = jweService.decryptJwe(
            DecryptJweArgs(
                jwe = jweCompact,
                decryptor = decryptor
            )
        )
        assertTrue(decryptResult.isOk, "Decrypt should succeed")

        // Verify plaintext
        assertEquals(
            plaintext.decodeToString(),
            decryptResult.value.plaintext?.decodeToString(),
            "Decrypted plaintext should match original"
        )
    }

    /**
     * Test RSA-OAEP-256 variant (SHA-256 digest).
     *
     * While not in the RFC test vectors, this is a commonly used variant.
     */
    @Test
    fun testRoundTrip_RSA_OAEP_256_A128GCM() = runTest {
        val managedKeyPair = keyManagerService.generateKeyAsync(
            alg = com.sphereon.crypto.core.generic.SignatureAlgorithm.RSA_SHA256
        )
        val keyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val recipient = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test",
                clientIdScheme = "jwe",
                issuer = "https://example.com"
            )
        )
        val decryptor = ManagedOptsKeyInfo(
            identifier = keyInfo,
            context = IdentifierContext(
                clientId = "test",
                clientIdScheme = "jwe",
                issuer = "https://example.com"
            )
        )

        val plaintext = "Testing RSA-OAEP-256 with A128GCM".encodeToByteArray()

        // Encrypt
        val prepareResult = jweService.prepareJwe(
            PrepareJweArgs(
                plaintext = plaintext,
                recipient = recipient,
                keyEncryptionAlg = "RSA-OAEP-256",
                contentEncryptionAlg = "A128GCM"
            )
        )
        assertTrue(prepareResult.isOk)

        val createResult = jweService.createJweCompact(
            CreateJweCompactArgs(preparedJwe = prepareResult.value)
        )
        assertTrue(createResult.isOk)

        // Decrypt
        val decryptResult = jweService.decryptJwe(
            DecryptJweArgs(
                jwe = createResult.value,
                decryptor = decryptor
            )
        )
        assertTrue(decryptResult.isOk)

        assertEquals(
            plaintext.decodeToString(),
            decryptResult.value.plaintext?.decodeToString()
        )
    }
}
