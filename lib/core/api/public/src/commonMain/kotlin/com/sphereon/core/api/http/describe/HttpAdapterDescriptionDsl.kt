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

package com.sphereon.core.api.http.describe

/**
 * DSL entry point for creating an [HttpAdapterDescription] without noisy `listOf(...)` / `setOf(...)`.
 */
fun httpAdapterDescription(
    id: String,
    block: HttpAdapterDescriptionBuilder.() -> Unit
): HttpAdapterDescription = HttpAdapterDescriptionBuilder(id).apply(block).build()

class HttpAdapterDescriptionBuilder internal constructor(
    private val id: String
) {
    private var mount: HttpAdapterMount? = null
    private val endpoints: MutableList<HttpEndpointDescriptor> = mutableListOf()
    private var openApiHints: OpenApiHints? = null

    fun mount(block: HttpAdapterMountBuilder.() -> Unit) {
        mount = HttpAdapterMountBuilder().apply(block).build()
    }

    fun endpoint(
        method: HttpMethod,
        pathPattern: String,
        block: HttpEndpointDescriptorBuilder.() -> Unit = {}
    ) {
        endpoints += HttpEndpointDescriptorBuilder(method, pathPattern).apply(block).build()
    }

    fun openApiHints(block: OpenApiHintsBuilder.() -> Unit) {
        openApiHints = OpenApiHintsBuilder().apply(block).build()
    }

    internal fun build(): HttpAdapterDescription {
        val finalMount = requireNotNull(mount) { "HttpAdapterDescription requires mount{...}" }
        return HttpAdapterDescription(
            id = id,
            mount = finalMount,
            endpoints = endpoints.toList(),
            openApiHints = openApiHints
        )
    }
}

class HttpAdapterMountBuilder {
    var serverPrefix: String = ""
    var adapterBasePath: String = "/"
    var tenantPathMode: TenantPathMode = TenantPathMode.OFF
    var tenantSegmentPattern: String = HttpAdapterMount.DEFAULT_TENANT_SEGMENT_PATTERN
    var tenantResolutionPriority: TenantResolutionPriority = TenantResolutionPriority.HEADER_THEN_PATH

    internal fun build(): HttpAdapterMount = HttpAdapterMount(
        serverPrefix = serverPrefix,
        adapterBasePath = adapterBasePath,
        tenantPathMode = tenantPathMode,
        tenantSegmentPattern = tenantSegmentPattern,
        tenantResolutionPriority = tenantResolutionPriority
    )
}

class HttpEndpointDescriptorBuilder internal constructor(
    private val method: HttpMethod,
    private val pathPattern: String
) {
    private val consumes: MutableSet<MediaType> = linkedSetOf()
    private val produces: MutableSet<MediaType> = linkedSetOf()
    private var operationId: String? = null
    private val tags: MutableSet<String> = linkedSetOf()
    private var summary: String? = null

    fun consumes(vararg types: MediaType) {
        consumes += types
    }

    fun produces(vararg types: MediaType) {
        produces += types
    }

    fun operationId(value: String) {
        operationId = value
    }

    fun tags(vararg values: String) {
        tags += values
    }

    fun summary(value: String) {
        summary = value
    }

    internal fun build(): HttpEndpointDescriptor = HttpEndpointDescriptor(
        method = method,
        pathPattern = pathPattern,
        consumes = consumes.toSet(),
        produces = produces.toSet(),
        operationId = operationId,
        tags = tags.toSet(),
        summary = summary
    )
}

class OpenApiHintsBuilder {
    private val tags: MutableSet<String> = linkedSetOf()
    private var operationIdPrefix: String? = null
    private val operationIds: MutableSet<String> = linkedSetOf()

    fun tags(vararg values: String) {
        tags += values
    }

    fun operationIdPrefix(value: String) {
        operationIdPrefix = value
    }

    fun operationIds(vararg values: String) {
        operationIds += values
    }

    internal fun build(): OpenApiHints = OpenApiHints(
        tags = tags.toSet(),
        operationIdPrefix = operationIdPrefix,
        operationIds = operationIds.toSet()
    )
}
