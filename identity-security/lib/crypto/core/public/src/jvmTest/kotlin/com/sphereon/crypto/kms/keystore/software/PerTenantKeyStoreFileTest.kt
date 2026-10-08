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

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * End-to-end test that the JVM software keystore writes a DISTINCT keystore FILE per tenant,
 * including when the config starts with an explicit path.
 *
 * Mirrors how [SoftwareKeyStoreFactoryImpl.create] rewrites the config path per tenant via
 * [TenantKeyStorePathResolver] before constructing the [SoftwareKeyStoreService]. Here we apply the
 * same resolver directly (the factory needs a full DI graph) and then drive the real service file
 * IO.
 */
class PerTenantKeyStoreFileTest {
    private val root: File = Files.createTempDirectory("per-tenant-ks-test").toFile().also { it.deleteOnExit() }

    private fun baseConfig(path: String? = null,) =
        Pkcs12KeyStoreConfig(
            id = "software",
            password = "test-password",
            path = path,
            keystoreRoot = root.absolutePath,
            persist = true,
            overwriteAlias = true,
            keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
        )

    private fun hmacKey(alias: String): ResolvedKeyInfo<Jwk> {
        val jwk =
            Jwk(
                kty = JwaKeyType.oct,
                k = Random.nextBytes(32).encodeToBase64Url(),
                alg = JwaAlgorithm.HS256,
                kid = alias,
            )
        return ResolvedKeyInfo(
            key = jwk,
            keyVisibility = KeyVisibility.PRIVATE,
            keyType = KeyTypeMapping.Symmetric,
            alias = alias,
            providerId = "software",
            kid = alias,
            signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
        )
    }

    @Test
    fun twoTenantsProduceTwoDistinctKeystoreFiles() =
        runTest {
            // Resolve a per-tenant config exactly like the tenant-aware factory does.
            val configA = TenantKeyStorePathResolver.withResolvedPath(baseConfig(), "tenant-a")
            val configB = TenantKeyStorePathResolver.withResolvedPath(baseConfig(), "tenant-b")

            val pathA = configA.path!!
            val pathB = configB.path!!
            assertNotEquals(pathA, pathB, "Per-tenant paths must differ")
            assertEquals(File(root, "tenant-a/software.p12").absolutePath, File(pathA).absolutePath)
            assertEquals(File(root, "tenant-b/software.p12").absolutePath, File(pathB).absolutePath)

            val serviceA = SoftwareKeyStoreService(configA)
            val serviceB = SoftwareKeyStoreService(configB)

            // Store a key in each tenant's keystore (autoCreate writes the file on first use).
            serviceA.storeKey(hmacKey("tenant-a-key"), "software", "tenant-a-key")
            serviceB.storeKey(hmacKey("tenant-b-key"), "software", "tenant-b-key")
            serviceA.awaitPendingPersistenceCompletion()
            serviceB.awaitPendingPersistenceCompletion()

            // Two distinct files exist on disk.
            assertTrue(File(pathA).isFile, "Tenant A keystore file must exist: $pathA")
            assertTrue(File(pathB).isFile, "Tenant B keystore file must exist: $pathB")

            // Reload each from disk and confirm cross-tenant isolation: A's key is not in B's file.
            val reloadedA = SoftwareKeyStoreService(baseConfig(path = pathA))
            val reloadedB = SoftwareKeyStoreService(baseConfig(path = pathB))
            val aliasesA = reloadedA.listKeys().mapNotNull { it.alias }
            val aliasesB = reloadedB.listKeys().mapNotNull { it.alias }

            assertTrue(aliasesA.contains("tenant-a-key"), "Tenant A file must contain tenant A's key")
            assertTrue(aliasesB.contains("tenant-b-key"), "Tenant B file must contain tenant B's key")
            assertFalse(aliasesA.contains("tenant-b-key"), "Tenant A file must NOT contain tenant B's key")
            assertFalse(aliasesB.contains("tenant-a-key"), "Tenant B file must NOT contain tenant A's key")
        }

    @Test
    fun explicitPathStillProducesTenantFiles() =
        runTest {
            val shared = File(root, "shared/edk-keystore.p12").absolutePath
            val configA = TenantKeyStorePathResolver.withResolvedPath(baseConfig(path = shared), "tenant-a")
            val configB = TenantKeyStorePathResolver.withResolvedPath(baseConfig(path = shared), "tenant-b")

            assertNotEquals(configA.path, configB.path, "Explicit configured paths must still be tenant-scoped")
            assertEquals(File(root, "tenant-a/shared/edk-keystore.p12").absolutePath, File(configA.path!!).absolutePath)
            assertEquals(File(root, "tenant-b/shared/edk-keystore.p12").absolutePath, File(configB.path!!).absolutePath)

            val serviceA = SoftwareKeyStoreService(configA)
            val serviceB = SoftwareKeyStoreService(configB)

            serviceA.storeKey(hmacKey("tenant-a-explicit-key"), "software", "tenant-a-explicit-key")
            serviceB.storeKey(hmacKey("tenant-b-explicit-key"), "software", "tenant-b-explicit-key")
            serviceA.awaitPendingPersistenceCompletion()
            serviceB.awaitPendingPersistenceCompletion()

            assertFalse(File(shared).exists(), "The original explicit path must not be used as a shared tenant file")
            assertTrue(File(configA.path!!).isFile, "Tenant A explicit-path keystore file must exist")
            assertTrue(File(configB.path!!).isFile, "Tenant B explicit-path keystore file must exist")

            val reloadedA = SoftwareKeyStoreService(baseConfig(path = configA.path))
            val reloadedB = SoftwareKeyStoreService(baseConfig(path = configB.path))
            val aliasesA = reloadedA.listKeys().mapNotNull { it.alias }
            val aliasesB = reloadedB.listKeys().mapNotNull { it.alias }

            assertTrue(aliasesA.contains("tenant-a-explicit-key"), "Tenant A file must contain tenant A's explicit-path key")
            assertTrue(aliasesB.contains("tenant-b-explicit-key"), "Tenant B file must contain tenant B's explicit-path key")
            assertFalse(aliasesA.contains("tenant-b-explicit-key"), "Tenant A file must NOT contain tenant B's explicit-path key")
            assertFalse(aliasesB.contains("tenant-a-explicit-key"), "Tenant B file must NOT contain tenant A's explicit-path key")
        }
}
