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

import com.sphereon.crypto.core.KeyVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for per-tenant software keystore path resolution.
 *
 * Verifies the design contract:
 * - When `keystore.path` is UNSET, two different tenants resolve to two DISTINCT files under
 *   `<root>/<tenantId>/<providerName>.p12`.
 * - When `keystore.path` IS explicitly set, two different tenants still resolve to two DISTINCT
 *   files under `<root>/<tenantId>/<configured-file-or-subpath>`.
 */
class TenantKeyStorePathResolverTest {
    private fun pkcs12(
        id: String = "software",
        path: String? = null,
        keystoreRoot: String? = null,
    ) = Pkcs12KeyStoreConfig(
        id = id,
        password = "test-password",
        path = path,
        keystoreRoot = keystoreRoot,
        keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
    )

    @Test
    fun unsetPathDerivesPerTenantPerProviderFile() {
        val config = pkcs12(id = "software")

        val tenantA = TenantKeyStorePathResolver.resolvePath(config, "tenant-a")
        val tenantB = TenantKeyStorePathResolver.resolvePath(config, "tenant-b")

        assertEquals("/keystore/tenant-a/software.p12", tenantA)
        assertEquals("/keystore/tenant-b/software.p12", tenantB)
        assertNotEquals(tenantA, tenantB, "Two tenants must resolve to two distinct keystore files")
    }

    @Test
    fun providerNameComesFromConfigId() {
        val a = TenantKeyStorePathResolver.resolvePath(pkcs12(id = "software"), "acme")
        val b = TenantKeyStorePathResolver.resolvePath(pkcs12(id = "backup-kms"), "acme")

        assertEquals("/keystore/acme/software.p12", a)
        assertEquals("/keystore/acme/backup-kms.p12", b)
        assertNotEquals(a, b, "Two provider names for the same tenant must resolve to distinct files")
    }

    @Test
    fun explicitPathStillDerivesPerTenantFile() {
        val shared = "/shared/edk-keystore.p12"
        val config = pkcs12(path = shared)

        val tenantA = TenantKeyStorePathResolver.resolvePath(config, "tenant-a")
        val tenantB = TenantKeyStorePathResolver.resolvePath(config, "tenant-b")

        assertEquals("/shared/tenant-a/edk-keystore.p12", tenantA)
        assertEquals("/shared/tenant-b/edk-keystore.p12", tenantB)
        assertNotEquals(tenantA, tenantB, "An explicit path must still resolve to distinct tenant files")
    }

    @Test
    fun explicitSubpathUnderConfiguredRootIsKeptUnderTenantDirectory() {
        val config = pkcs12(path = "/var/lib/edk/keystores/shared/edk-keystore.p12", keystoreRoot = "/var/lib/edk/keystores")
        val resolved = TenantKeyStorePathResolver.resolvePath(config, "tenant-a")

        assertEquals("/var/lib/edk/keystores/tenant-a/shared/edk-keystore.p12", resolved)
    }

    @Test
    fun configurableRootIsHonoured() {
        val config = pkcs12(id = "software", keystoreRoot = "/var/lib/edk/keystores")
        val resolved = TenantKeyStorePathResolver.resolvePath(config, "tenant-a")
        assertEquals("/var/lib/edk/keystores/tenant-a/software.p12", resolved)
    }

    @Test
    fun jksConfigUsesJksExtension() {
        val config =
            JksKeyStoreConfig(
                id = "software",
                password = "test-password",
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
            )
        val resolved = TenantKeyStorePathResolver.resolvePath(config, "tenant-a")
        assertEquals("/keystore/tenant-a/software.jks", resolved)
    }

    @Test
    fun tenantIdIsMadePathSafe() {
        val config = pkcs12(id = "software")
        // A malicious / messy tenant id must not escape its directory.
        val resolved = TenantKeyStorePathResolver.resolvePath(config, "../../etc")
        assertTrue(resolved.startsWith("/keystore/"), "Resolved path must remain under the keystore root: $resolved")
        assertTrue(!resolved.contains("/../"), "Resolved path must not contain traversal segments: $resolved")
    }

    @Test
    fun blankTenantIdFallsBackToDefault() {
        val config = pkcs12(id = "software")
        assertEquals("/keystore/default/software.p12", TenantKeyStorePathResolver.resolvePath(config, null))
        assertEquals("/keystore/default/software.p12", TenantKeyStorePathResolver.resolvePath(config, "   "))
    }

    @Test
    fun withResolvedPathRewritesUnsetPathPerTenant() {
        val config = pkcs12(id = "software")
        val a = TenantKeyStorePathResolver.withResolvedPath(config, "tenant-a")
        val b = TenantKeyStorePathResolver.withResolvedPath(config, "tenant-b")
        assertEquals("/keystore/tenant-a/software.p12", a.path)
        assertEquals("/keystore/tenant-b/software.p12", b.path)
        assertNotEquals(a.path, b.path)
    }

    @Test
    fun withResolvedPathRewritesExplicitPathPerTenant() {
        val config = pkcs12(path = "/shared/edk-keystore.p12")
        val a = TenantKeyStorePathResolver.withResolvedPath(config, "tenant-a")

        assertNotEquals(config.path, a.path)
        assertEquals("/shared/tenant-a/edk-keystore.p12", a.path)
    }
}
