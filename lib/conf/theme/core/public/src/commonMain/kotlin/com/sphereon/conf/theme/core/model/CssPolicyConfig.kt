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
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Configuration for CSS policy validation.
 * Controls what is allowed in custom CSS overrides.
 */
@JsExportCompat
@Serializable
data class CssPolicyConfig
    @JvmOverloads
    constructor(
        val maxSizeBytes: Int = 50_000,
        @JsExportIgnoreCompat
        val allowedProperties: Set<String>? = null,
        @JsExportIgnoreCompat
        val blockedSelectors: Set<String> = setOf("script", "iframe", "object", "embed", "form"),
    )
