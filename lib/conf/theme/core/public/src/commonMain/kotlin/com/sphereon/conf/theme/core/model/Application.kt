/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

/**
 * One deployed, brandable instance the tenant runs: a specific authorization server,
 * web wallet, portal, mobile wallet, or the admin console. Platform-managed instances
 * enter the registry from the platform instance registries; external applications are
 * registered through REST.
 *
 * @property applicationId Identifier of the application
 * @property tenantId The tenant that runs the application
 * @property productType The product the application is an instance of
 * @property name Human-readable name
 * @property description Human-readable description
 * @property managed True for platform-provisioned instances, which are read-only in the registry
 * @property instanceRef Reference to the platform instance record backing a managed application
 * @property createdAt When this application was registered
 * @property updatedAt When this application was last updated
 */
@JsExportCompat
@Serializable
data class Application
    @JvmOverloads
    constructor(
        val applicationId: String,
        val tenantId: String,
        val productType: ProductType,
        val name: String,
        val description: String? = null,
        val managed: Boolean = false,
        val instanceRef: String? = null,
        val createdAt: Instant? = null,
        val updatedAt: Instant? = null,
    )
