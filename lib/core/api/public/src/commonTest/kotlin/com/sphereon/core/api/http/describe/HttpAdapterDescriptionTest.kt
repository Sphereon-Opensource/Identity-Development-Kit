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

package com.sphereon.core.api.http.describe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpMethodTest {

    @Test
    fun enumHasSevenMethods() {
        assertEquals(7, HttpMethod.entries.size)
    }

    @Test
    fun getExists() {
        assertEquals("GET", HttpMethod.GET.name)
    }

    @Test
    fun postExists() {
        assertEquals("POST", HttpMethod.POST.name)
    }

    @Test
    fun putExists() {
        assertEquals("PUT", HttpMethod.PUT.name)
    }

    @Test
    fun deleteExists() {
        assertEquals("DELETE", HttpMethod.DELETE.name)
    }

    @Test
    fun patchExists() {
        assertEquals("PATCH", HttpMethod.PATCH.name)
    }

    @Test
    fun optionsExists() {
        assertEquals("OPTIONS", HttpMethod.OPTIONS.name)
    }

    @Test
    fun headExists() {
        assertEquals("HEAD", HttpMethod.HEAD.name)
    }
}

class MediaTypeTest {

    @Test
    fun applicationJsonHasCorrectValue() {
        assertEquals("application/json", MediaType.ApplicationJson.value)
    }

    @Test
    fun applicationFormUrlEncodedHasCorrectValue() {
        assertEquals("application/x-www-form-urlencoded", MediaType.ApplicationFormUrlEncoded.value)
    }

    @Test
    fun textPlainHasCorrectValue() {
        assertEquals("text/plain", MediaType.TextPlain.value)
    }

    @Test
    fun applicationOctetStreamHasCorrectValue() {
        assertEquals("application/octet-stream", MediaType.ApplicationOctetStream.value)
    }

    @Test
    fun customHasCustomValue() {
        val custom = MediaType.Custom("application/xml")
        assertEquals("application/xml", custom.value)
    }
}

class MediaTypeMatchesTest {

    @Test
    fun exactMatchReturnsTrue() {
        assertTrue(MediaType.ApplicationJson.matches(MediaType.ApplicationJson))
    }

    @Test
    fun matchesIgnoresCharset() {
        val withCharset = MediaType.Custom("application/json; charset=utf-8")
        assertTrue(MediaType.ApplicationJson.matches(withCharset))
    }

    @Test
    fun matchesIsCaseInsensitive() {
        val uppercase = MediaType.Custom("APPLICATION/JSON")
        assertTrue(MediaType.ApplicationJson.matches(uppercase))
    }

    @Test
    fun differentTypesReturnFalse() {
        assertFalse(MediaType.ApplicationJson.matches(MediaType.TextPlain))
    }
}

class MediaTypeParseTest {

    @Test
    fun parseNullReturnsNull() {
        assertNull(MediaType.parse(null))
    }

    @Test
    fun parseBlankReturnsNull() {
        assertNull(MediaType.parse(""))
        assertNull(MediaType.parse("   "))
    }

    @Test
    fun parseApplicationJson() {
        val result = MediaType.parse("application/json")
        assertEquals(MediaType.ApplicationJson, result)
    }

    @Test
    fun parseApplicationJsonWithCharset() {
        val result = MediaType.parse("application/json; charset=utf-8")
        assertEquals(MediaType.ApplicationJson, result)
    }

    @Test
    fun parseApplicationFormUrlEncoded() {
        val result = MediaType.parse("application/x-www-form-urlencoded")
        assertEquals(MediaType.ApplicationFormUrlEncoded, result)
    }

    @Test
    fun parseTextPlain() {
        val result = MediaType.parse("text/plain")
        assertEquals(MediaType.TextPlain, result)
    }

    @Test
    fun parseApplicationOctetStream() {
        val result = MediaType.parse("application/octet-stream")
        assertEquals(MediaType.ApplicationOctetStream, result)
    }

    @Test
    fun parseUnknownReturnsCustom() {
        val result = MediaType.parse("application/xml")
        assertTrue(result is MediaType.Custom)
        assertEquals("application/xml", result?.value)
    }

    @Test
    fun parseIsCaseInsensitive() {
        val result = MediaType.parse("APPLICATION/JSON")
        assertEquals(MediaType.ApplicationJson, result)
    }
}

class TenantPathModeTest {

    @Test
    fun enumHasFourModes() {
        assertEquals(4, TenantPathMode.entries.size)
    }

    @Test
    fun offExists() {
        assertEquals("OFF", TenantPathMode.OFF.name)
    }

    @Test
    fun beforeServerPrefixExists() {
        assertEquals("BEFORE_SERVER_PREFIX", TenantPathMode.BEFORE_SERVER_PREFIX.name)
    }

    @Test
    fun afterServerPrefixExists() {
        assertEquals("AFTER_SERVER_PREFIX", TenantPathMode.AFTER_SERVER_PREFIX.name)
    }

    @Test
    fun bothExists() {
        assertEquals("BOTH", TenantPathMode.BOTH.name)
    }
}

class TenantResolutionPriorityTest {

    @Test
    fun enumHasTwoValues() {
        assertEquals(2, TenantResolutionPriority.entries.size)
    }

    @Test
    fun headerThenPathExists() {
        assertEquals("HEADER_THEN_PATH", TenantResolutionPriority.HEADER_THEN_PATH.name)
    }

    @Test
    fun pathThenHeaderExists() {
        assertEquals("PATH_THEN_HEADER", TenantResolutionPriority.PATH_THEN_HEADER.name)
    }
}

class HttpAdapterMountTest {

    @Test
    fun defaultTenantSegmentPatternIsCorrect() {
        assertEquals("/t/{tenantId}", HttpAdapterMount.DEFAULT_TENANT_SEGMENT_PATTERN)
    }

    @Test
    fun mountHasServerPrefix() {
        val mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        assertEquals("/api", mount.serverPrefix)
    }

    @Test
    fun mountHasAdapterBasePath() {
        val mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        assertEquals("/users", mount.adapterBasePath)
    }

    @Test
    fun mountDefaultsTenantPathModeToOff() {
        val mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        assertEquals(TenantPathMode.OFF, mount.tenantPathMode)
    }

    @Test
    fun mountDefaultsTenantSegmentPattern() {
        val mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        assertEquals(HttpAdapterMount.DEFAULT_TENANT_SEGMENT_PATTERN, mount.tenantSegmentPattern)
    }

    @Test
    fun mountDefaultsTenantResolutionPriority() {
        val mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        assertEquals(TenantResolutionPriority.HEADER_THEN_PATH, mount.tenantResolutionPriority)
    }

    @Test
    fun mountsAreEqual() {
        val mount1 = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        val mount2 = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        assertEquals(mount1, mount2)
    }
}

class HttpEndpointDescriptorTest {

    @Test
    fun endpointHasMethod() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        assertEquals(HttpMethod.GET, endpoint.method)
    }

    @Test
    fun endpointHasPathPattern() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users/{id}")
        assertEquals("/users/{id}", endpoint.pathPattern)
    }

    @Test
    fun endpointDefaultsConsumesToEmpty() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        assertTrue(endpoint.consumes.isEmpty())
    }

    @Test
    fun endpointDefaultsProducesToEmpty() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        assertTrue(endpoint.produces.isEmpty())
    }

    @Test
    fun endpointDefaultsOperationIdToNull() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        assertNull(endpoint.operationId)
    }

    @Test
    fun endpointDefaultsTagsToEmpty() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        assertTrue(endpoint.tags.isEmpty())
    }

    @Test
    fun endpointDefaultsSummaryToNull() {
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        assertNull(endpoint.summary)
    }

    @Test
    fun endpointWithAllFields() {
        val endpoint = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/users",
            consumes = setOf(MediaType.ApplicationJson),
            produces = setOf(MediaType.ApplicationJson),
            operationId = "createUser",
            tags = setOf("users"),
            summary = "Create a new user"
        )
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/users", endpoint.pathPattern)
        assertEquals(setOf(MediaType.ApplicationJson), endpoint.consumes)
        assertEquals(setOf(MediaType.ApplicationJson), endpoint.produces)
        assertEquals("createUser", endpoint.operationId)
        assertEquals(setOf("users"), endpoint.tags)
        assertEquals("Create a new user", endpoint.summary)
    }
}

class OpenApiHintsTest {

    @Test
    fun hintsDefaultsTagsToEmpty() {
        val hints = OpenApiHints()
        assertTrue(hints.tags.isEmpty())
    }

    @Test
    fun hintsDefaultsOperationIdPrefixToNull() {
        val hints = OpenApiHints()
        assertNull(hints.operationIdPrefix)
    }

    @Test
    fun hintsDefaultsOperationIdsToEmpty() {
        val hints = OpenApiHints()
        assertTrue(hints.operationIds.isEmpty())
    }

    @Test
    fun hintsWithAllFields() {
        val hints = OpenApiHints(
            tags = setOf("users", "admin"),
            operationIdPrefix = "user",
            operationIds = setOf("createUser", "deleteUser")
        )
        assertEquals(setOf("users", "admin"), hints.tags)
        assertEquals("user", hints.operationIdPrefix)
        assertEquals(setOf("createUser", "deleteUser"), hints.operationIds)
    }
}

class HttpAdapterDescriptionTest {

    @Test
    fun descriptionHasId() {
        val mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api")
        val description = HttpAdapterDescription(id = "user-adapter", mount = mount, endpoints = emptyList())
        assertEquals("user-adapter", description.id)
    }

    @Test
    fun descriptionHasMount() {
        val mount = HttpAdapterMount(serverPrefix = "/api", adapterBasePath = "/users")
        val description = HttpAdapterDescription(id = "user-adapter", mount = mount, endpoints = emptyList())
        assertEquals(mount, description.mount)
    }

    @Test
    fun descriptionHasEndpoints() {
        val mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api")
        val endpoint = HttpEndpointDescriptor(method = HttpMethod.GET, pathPattern = "/users")
        val description = HttpAdapterDescription(id = "user-adapter", mount = mount, endpoints = listOf(endpoint))
        assertEquals(1, description.endpoints.size)
        assertEquals(endpoint, description.endpoints[0])
    }

    @Test
    fun descriptionDefaultsOpenApiHintsToNull() {
        val mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api")
        val description = HttpAdapterDescription(id = "user-adapter", mount = mount, endpoints = emptyList())
        assertNull(description.openApiHints)
    }

    @Test
    fun descriptionWithOpenApiHints() {
        val mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/api")
        val hints = OpenApiHints(tags = setOf("users"))
        val description = HttpAdapterDescription(
            id = "user-adapter",
            mount = mount,
            endpoints = emptyList(),
            openApiHints = hints
        )
        assertEquals(hints, description.openApiHints)
    }
}

// ========== DSL Builder Tests ==========

class HttpAdapterDescriptionDslTest {

    @Test
    fun httpAdapterDescriptionDslCreatesDescription() {
        val description = httpAdapterDescription("test-adapter") {
            mount {
                serverPrefix = "/api"
                adapterBasePath = "/users"
            }
        }
        assertEquals("test-adapter", description.id)
        assertEquals("/api", description.mount.serverPrefix)
        assertEquals("/users", description.mount.adapterBasePath)
    }

    @Test
    fun httpAdapterDescriptionDslWithEndpoints() {
        val description = httpAdapterDescription("test-adapter") {
            mount {
                serverPrefix = ""
                adapterBasePath = "/api"
            }
            endpoint(HttpMethod.GET, "/users")
            endpoint(HttpMethod.POST, "/users")
        }
        assertEquals(2, description.endpoints.size)
        assertEquals(HttpMethod.GET, description.endpoints[0].method)
        assertEquals("/users", description.endpoints[0].pathPattern)
        assertEquals(HttpMethod.POST, description.endpoints[1].method)
    }

    @Test
    fun httpAdapterDescriptionDslWithOpenApiHints() {
        val description = httpAdapterDescription("test-adapter") {
            mount {
                adapterBasePath = "/api"
            }
            openApiHints {
                tags("users", "admin")
                operationIdPrefix("user")
            }
        }
        assertEquals(setOf("users", "admin"), description.openApiHints?.tags)
        assertEquals("user", description.openApiHints?.operationIdPrefix)
    }
}

class HttpAdapterMountBuilderTest {

    @Test
    fun mountBuilderDefaultsServerPrefixToEmpty() {
        val description = httpAdapterDescription("test") {
            mount {
                adapterBasePath = "/api"
            }
        }
        assertEquals("", description.mount.serverPrefix)
    }

    @Test
    fun mountBuilderDefaultsAdapterBasePathToSlash() {
        val description = httpAdapterDescription("test") {
            mount { }
        }
        assertEquals("/", description.mount.adapterBasePath)
    }

    @Test
    fun mountBuilderDefaultsTenantPathModeToOff() {
        val description = httpAdapterDescription("test") {
            mount { }
        }
        assertEquals(TenantPathMode.OFF, description.mount.tenantPathMode)
    }

    @Test
    fun mountBuilderSetsTenantPathMode() {
        val description = httpAdapterDescription("test") {
            mount {
                tenantPathMode = TenantPathMode.BEFORE_SERVER_PREFIX
            }
        }
        assertEquals(TenantPathMode.BEFORE_SERVER_PREFIX, description.mount.tenantPathMode)
    }

    @Test
    fun mountBuilderSetsTenantSegmentPattern() {
        val description = httpAdapterDescription("test") {
            mount {
                tenantSegmentPattern = "/tenant/{tid}"
            }
        }
        assertEquals("/tenant/{tid}", description.mount.tenantSegmentPattern)
    }

    @Test
    fun mountBuilderSetsTenantResolutionPriority() {
        val description = httpAdapterDescription("test") {
            mount {
                tenantResolutionPriority = TenantResolutionPriority.PATH_THEN_HEADER
            }
        }
        assertEquals(TenantResolutionPriority.PATH_THEN_HEADER, description.mount.tenantResolutionPriority)
    }
}

class HttpEndpointDescriptorBuilderTest {

    @Test
    fun endpointBuilderSetsMethodAndPath() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.GET, "/users/{id}")
        }
        val endpoint = description.endpoints.first()
        assertEquals(HttpMethod.GET, endpoint.method)
        assertEquals("/users/{id}", endpoint.pathPattern)
    }

    @Test
    fun endpointBuilderSetsConsumes() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.POST, "/users") {
                consumes(MediaType.ApplicationJson, MediaType.ApplicationFormUrlEncoded)
            }
        }
        val endpoint = description.endpoints.first()
        assertEquals(setOf(MediaType.ApplicationJson, MediaType.ApplicationFormUrlEncoded), endpoint.consumes)
    }

    @Test
    fun endpointBuilderSetsProduces() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.GET, "/users") {
                produces(MediaType.ApplicationJson, MediaType.TextPlain)
            }
        }
        val endpoint = description.endpoints.first()
        assertEquals(setOf(MediaType.ApplicationJson, MediaType.TextPlain), endpoint.produces)
    }

    @Test
    fun endpointBuilderSetsOperationId() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.GET, "/users") {
                operationId("listUsers")
            }
        }
        val endpoint = description.endpoints.first()
        assertEquals("listUsers", endpoint.operationId)
    }

    @Test
    fun endpointBuilderSetsTags() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.GET, "/users") {
                tags("users", "admin")
            }
        }
        val endpoint = description.endpoints.first()
        assertEquals(setOf("users", "admin"), endpoint.tags)
    }

    @Test
    fun endpointBuilderSetsSummary() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.GET, "/users") {
                summary("List all users")
            }
        }
        val endpoint = description.endpoints.first()
        assertEquals("List all users", endpoint.summary)
    }

    @Test
    fun endpointBuilderWithAllFields() {
        val description = httpAdapterDescription("test") {
            mount { }
            endpoint(HttpMethod.POST, "/users") {
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                operationId("createUser")
                tags("users")
                summary("Create a new user")
            }
        }
        val endpoint = description.endpoints.first()
        assertEquals(HttpMethod.POST, endpoint.method)
        assertEquals("/users", endpoint.pathPattern)
        assertEquals(setOf(MediaType.ApplicationJson), endpoint.consumes)
        assertEquals(setOf(MediaType.ApplicationJson), endpoint.produces)
        assertEquals("createUser", endpoint.operationId)
        assertEquals(setOf("users"), endpoint.tags)
        assertEquals("Create a new user", endpoint.summary)
    }
}

class OpenApiHintsBuilderTest {

    @Test
    fun openApiHintsBuilderSetsTags() {
        val description = httpAdapterDescription("test") {
            mount { }
            openApiHints {
                tags("users", "admin", "api")
            }
        }
        assertEquals(setOf("users", "admin", "api"), description.openApiHints?.tags)
    }

    @Test
    fun openApiHintsBuilderSetsOperationIdPrefix() {
        val description = httpAdapterDescription("test") {
            mount { }
            openApiHints {
                operationIdPrefix("user")
            }
        }
        assertEquals("user", description.openApiHints?.operationIdPrefix)
    }

    @Test
    fun openApiHintsBuilderSetsOperationIds() {
        val description = httpAdapterDescription("test") {
            mount { }
            openApiHints {
                operationIds("createUser", "deleteUser", "updateUser")
            }
        }
        assertEquals(setOf("createUser", "deleteUser", "updateUser"), description.openApiHints?.operationIds)
    }

    @Test
    fun openApiHintsBuilderWithAllFields() {
        val description = httpAdapterDescription("test") {
            mount { }
            openApiHints {
                tags("users")
                operationIdPrefix("user")
                operationIds("createUser", "deleteUser")
            }
        }
        val hints = description.openApiHints
        assertEquals(setOf("users"), hints?.tags)
        assertEquals("user", hints?.operationIdPrefix)
        assertEquals(setOf("createUser", "deleteUser"), hints?.operationIds)
    }
}
