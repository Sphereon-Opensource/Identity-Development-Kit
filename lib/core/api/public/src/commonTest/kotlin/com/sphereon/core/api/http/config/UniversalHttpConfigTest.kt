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

package com.sphereon.core.api.http.config

import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.TenantPathMode
import com.sphereon.core.api.http.describe.TenantResolutionPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniversalHttpConfigTest {

    @Test
    fun dslCreatesConfigWithDefaults() {
        val config = universalHttpConfig {
            defaults {
                serverPrefix = "/api"
                tenantPathMode = TenantPathMode.OFF
            }
        }

        assertEquals("/api", config.defaults.serverPrefix)
        assertEquals(TenantPathMode.OFF, config.defaults.tenantPathMode)
        assertTrue(config.overrides.isEmpty())
    }

    @Test
    fun dslCreatesConfigWithAdapterOverrides() {
        val config = universalHttpConfig {
            defaults {
                serverPrefix = "/api"
            }
            adapter("KMS_KEYS") {
                serverPrefix = "/api/kms"
                tenantPathMode = TenantPathMode.BOTH
            }
        }

        assertEquals("/api", config.defaults.serverPrefix)
        assertTrue(config.overrides.containsKey("KMS_KEYS"))
        assertEquals("/api/kms", config.overrides["KMS_KEYS"]?.serverPrefix)
        assertEquals(TenantPathMode.BOTH, config.overrides["KMS_KEYS"]?.tenantPathMode)
    }

    @Test
    fun dslDisablesAdapter() {
        val config = universalHttpConfig {
            disableAdapter("LEGACY_ADAPTER")
        }

        assertFalse(config.isAdapterEnabled("LEGACY_ADAPTER"))
        assertTrue(config.isAdapterEnabled("OTHER_ADAPTER"))
    }

    @Test
    fun resolveMountUsesOverrideWhenPresent() {
        val config = universalHttpConfig {
            defaults {
                serverPrefix = "/api"
            }
            adapter("KMS_KEYS") {
                serverPrefix = "/api/kms"
            }
        }

        val declaredMount = HttpAdapterMount(
            serverPrefix = "/original",
            adapterBasePath = "/keys"
        )

        val resolved = config.resolveMount("KMS_KEYS", declaredMount)

        assertEquals("/api/kms", resolved.serverPrefix)
        assertEquals("/keys", resolved.adapterBasePath)
    }

    @Test
    fun resolveMountUsesDefaultWhenNoOverride() {
        val config = universalHttpConfig {
            defaults {
                serverPrefix = "/api"
                tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX
            }
        }

        val declaredMount = HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/keys"
        )

        val resolved = config.resolveMount("UNKNOWN_ADAPTER", declaredMount)

        assertEquals("/api", resolved.serverPrefix)
        assertEquals("/keys", resolved.adapterBasePath)
        assertEquals(TenantPathMode.BEFORE_SERVER_PREFIX, resolved.tenantPathMode)
    }

    @Test
    fun resolveMountUsesAdapterDeclaredValueWhenNonDefault() {
        val config = universalHttpConfig {
            defaults {
                serverPrefix = "/api"
                tenantPathMode = TenantPathMode.OFF
            }
        }

        val declaredMount = HttpAdapterMount(
            serverPrefix = "/custom",
            adapterBasePath = "/keys",
            tenantPathMode = TenantPathMode.AFTER_SERVER_PREFIX
        )

        val resolved = config.resolveMount("CUSTOM_ADAPTER", declaredMount)

        // Declared non-empty serverPrefix should be used
        assertEquals("/custom", resolved.serverPrefix)
        // Declared non-OFF tenantPathMode should be used
        assertEquals(TenantPathMode.AFTER_SERVER_PREFIX, resolved.tenantPathMode)
    }

    @Test
    fun resolveMountAppliesCustomTenantSegmentPattern() {
        val config = universalHttpConfig {
            defaults {
                tenantSegmentPattern = "/org/{tenantId}"
            }
            adapter("SPECIAL") {
                tenantSegmentPattern = "/tenant/{tenantId}"
            }
        }

        val declaredMount = HttpAdapterMount(
            serverPrefix = "/api",
            adapterBasePath = "/items"
        )

        val resolvedDefault = config.resolveMount("OTHER", declaredMount)
        assertEquals("/org/{tenantId}", resolvedDefault.tenantSegmentPattern)

        val resolvedOverride = config.resolveMount("SPECIAL", declaredMount)
        assertEquals("/tenant/{tenantId}", resolvedOverride.tenantSegmentPattern)
    }

    @Test
    fun resolveMountAppliesTenantResolutionPriority() {
        val config = universalHttpConfig {
            defaults {
                tenantResolutionPriority = TenantResolutionPriority.PATH_THEN_HEADER
            }
            adapter("HEADER_FIRST") {
                tenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH
            }
        }

        val declaredMount = HttpAdapterMount(
            serverPrefix = "/api",
            adapterBasePath = "/items"
        )

        val resolvedDefault = config.resolveMount("OTHER", declaredMount)
        assertEquals(TenantResolutionPriority.PATH_THEN_HEADER, resolvedDefault.tenantResolutionPriority)

        val resolvedOverride = config.resolveMount("HEADER_FIRST", declaredMount)
        assertEquals(TenantResolutionPriority.HEADER_THEN_PATH, resolvedOverride.tenantResolutionPriority)
    }

    @Test
    fun resolveMountUsesDeclaredTenantSegmentPatternWhenNonDefault() {
        val config = universalHttpConfig {
            defaults {
                tenantSegmentPattern = "/org/{tenantId}"
            }
        }

        // Declared mount has a custom (non-default) tenant segment pattern
        val declaredMount = HttpAdapterMount(
            serverPrefix = "/api",
            adapterBasePath = "/items",
            tenantSegmentPattern = "/custom/{tenantId}"
        )

        val resolved = config.resolveMount("ADAPTER", declaredMount)
        // Should use declared value since it's not the default
        assertEquals("/custom/{tenantId}", resolved.tenantSegmentPattern)
    }

    @Test
    fun resolveMountUsesDeclaredTenantResolutionPriorityWhenNonDefault() {
        val config = universalHttpConfig {
            defaults {
                // defaults to HEADER_THEN_PATH, we set it explicitly here
                tenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH
            }
        }

        // Declared mount has a non-default priority (PATH_THEN_HEADER instead of HEADER_THEN_PATH)
        val declaredMount = HttpAdapterMount(
            serverPrefix = "/api",
            adapterBasePath = "/items",
            tenantResolutionPriority = TenantResolutionPriority.PATH_THEN_HEADER
        )

        val resolved = config.resolveMount("ADAPTER", declaredMount)
        // Should use declared value since it's not HEADER_THEN_PATH (the default)
        assertEquals(TenantResolutionPriority.PATH_THEN_HEADER, resolved.tenantResolutionPriority)
    }

    @Test
    fun defaultConfigConstantExists() {
        val config = UniversalHttpConfig.DEFAULT
        assertEquals("", config.defaults.serverPrefix)
        assertTrue(config.overrides.isEmpty())
    }
}
