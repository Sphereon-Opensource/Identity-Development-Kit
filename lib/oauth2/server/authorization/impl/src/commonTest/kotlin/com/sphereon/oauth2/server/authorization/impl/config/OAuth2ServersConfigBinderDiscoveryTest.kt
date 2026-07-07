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
 */

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.isEnabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that [OAuth2ServersConfigBinder.discoverServerIds] (exercised indirectly through
 * [OAuth2ServersConfigBinder.getConfig]) discovers configured server ids via a keyspace scan
 * over `oauth2.servers.*`.
 *
 * The contract under test:
 *  1. every id with at least one property under `oauth2.servers.<id>.<...>` appears in the
 *     resulting servers map, regardless of the operator's chosen id name (`production`,
 *     `auth-eu`, `staging-rp`, etc.);
 *  2. multiple properties under the same id deduplicate to a single map entry;
 *  3. the reserved `oauth2.servers.default-server` sibling key, which selects which discovered
 *     id is the default, never appears in the discovered set as a server named `default-server`;
 *  4. an empty keyspace falls back to the built-in `default` server entry so a fresh deployment
 *     still has a usable [OAuth2ServersConfig].
 */
class OAuth2ServersConfigBinderDiscoveryTest {
    private val prefix = OAuth2ServerInstanceConfig.CONFIG_PREFIX

    @Test
    fun discoversOperatorChosenServerIdsViaKeyspaceScan() {
        val properties =
            mapOf<String, Any>(
                "$prefix.production.mode" to "HOSTED",
                "$prefix.production.issuer" to "https://auth.example.com",
                "$prefix.auth-eu.mode" to "HOSTED",
                "$prefix.auth-eu.issuer-template" to "https://auth.eu.example.com/{tenant-id}",
                "$prefix.staging-rp.mode" to "EXTERNAL",
                "$prefix.staging-rp.issuer" to "https://staging.example.com",
            )
        val binder = newBinder(properties)

        val config = binder.getConfig()
        assertEquals(setOf("production", "auth-eu", "staging-rp"), config.servers.keys)
        assertNotNull(config.servers["production"])
        assertNotNull(config.servers["auth-eu"])
        assertNotNull(config.servers["staging-rp"])
    }

    @Test
    fun reservedDefaultServerKeyIsNotDiscoveredAsAServerId() {
        val properties =
            mapOf<String, Any>(
                "$prefix.default-server" to "production",
                "$prefix.production.mode" to "HOSTED",
                "$prefix.production.issuer" to "https://auth.example.com",
            )
        val binder = newBinder(properties)

        val config = binder.getConfig()
        // The reserved "default-server" sibling key selects which id is default; it is not itself
        // a server id and must not appear in the servers map.
        assertEquals(setOf("production"), config.servers.keys)
        assertNull(config.servers["default-server"], "default-server must not be discovered as a server id")
        assertEquals("production", config.defaultServer)
    }

    @Test
    fun normalizedReservedDefaultServerKeyIsNotDiscoveredAsDefaultServerId() {
        val properties =
            mapOf<String, Any>(
                "$prefix.default.server" to "production",
                "$prefix.production.mode" to "HOSTED",
                "$prefix.production.issuer" to "https://auth.example.com",
            )
        val binder = newBinder(properties)

        val config = binder.getConfig()
        assertEquals(setOf("production"), config.servers.keys)
        assertNull(config.servers["default"], "normalized default.server selector must not create a default server id")
        assertEquals("production", config.defaultServer)
    }

    @Test
    fun deduplicatesMultiplePropertiesUnderTheSameServerId() {
        val properties =
            mapOf<String, Any>(
                "$prefix.production.mode" to "HOSTED",
                "$prefix.production.issuer" to "https://auth.example.com",
                "$prefix.production.access-token-lifetime-seconds" to 1800,
                "$prefix.production.session.idle-ttl-seconds" to 600,
            )
        val binder = newBinder(properties)

        val config = binder.getConfig()
        assertEquals(setOf("production"), config.servers.keys)
    }

    @Test
    fun emptyKeyspaceFallsBackToBuiltInDefaultServer() {
        val binder = newBinder(emptyMap())
        val config = binder.getConfig()

        // No properties means the binder must still provide a working default server entry so
        // resolveIssuer / getDefaultServer don't crash on a fresh deployment.
        assertTrue(config.servers.isNotEmpty(), "default server map must not be empty for an unconfigured binder")
        assertEquals("default", config.defaultServer)
    }

    @Test
    fun defaultServerSelectorRecoversWhenPrefixSnapshotIsStaleEmpty() {
        val properties =
            mapOf<String, Any>(
                "$prefix.default-server" to "phase168830",
                "$prefix.phase168830.mode" to "HOSTED",
                "$prefix.phase168830.issuer" to "https://phase168830.saas.localtest.me",
                "$prefix.phase168830.oidc" to "SUPPORTED",
            )
        val binder =
            newBinder(
                properties,
                subPropertiesOverride = { _, _, _ -> emptyMap() },
            )

        val config = binder.getConfig()
        assertEquals(setOf("phase168830"), config.servers.keys)
        assertEquals("phase168830", config.defaultServer)
        assertEquals("https://phase168830.saas.localtest.me", config.getDefaultServer().issuer)
        assertTrue(config.getDefaultServer().oidc.isEnabled)
    }

    @Test
    fun defaultServerSelectorRemainsAuthoritativeForNormalizedSlugSegments() {
        val serverId = "tenant-alpha"
        val properties =
            mapOf<String, Any>(
                "$prefix.default-server" to serverId,
                "$prefix.$serverId.mode" to "HOSTED",
                "$prefix.$serverId.issuer" to "https://tenant-alpha.saas.localtest.me",
            )
        val binder = newBinder(properties, normalizeKeys = true)

        val config = binder.getConfig()
        assertEquals(setOf(serverId), config.servers.keys)
        assertEquals(serverId, config.defaultServer)
        assertEquals("https://tenant-alpha.saas.localtest.me", config.getDefaultServer().issuer)
    }

    private fun newBinder(
        properties: Map<String, Any>,
        subPropertiesOverride: ((Set<String>, Boolean, Map<String, Any>) -> Map<String, Any>)? = null,
        normalizeKeys: Boolean = false,
    ): OAuth2ServersConfigBinder {
        val configService = TypeAwarePrincipalConfigService(properties, subPropertiesOverride, normalizeKeys)
        val execution = TestSessionExecution(configService)
        return OAuth2ServersConfigBinder(execution)
    }
}
