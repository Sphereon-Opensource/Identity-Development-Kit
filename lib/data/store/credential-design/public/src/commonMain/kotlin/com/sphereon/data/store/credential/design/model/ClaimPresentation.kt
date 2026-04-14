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

package com.sphereon.data.store.credential.design.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

@JsExportCompat
@Serializable
data class ClaimLabel
    @JvmOverloads
    constructor(
        val locale: String,
        val label: String,
        val description: String? = null,
        @JsExportIgnoreCompat
        val entryValues: Map<String, String>? = null,
    )

@JsExportCompat
@Serializable
data class ClaimPresentation
    @JvmOverloads
    constructor(
        val path: DesignClaimPath,
        val labels: List<ClaimLabel>,
        val mandatory: Boolean = false,
        val sdPolicy: SdPolicy = SdPolicy.ALLOWED,
        val order: Int = 0,
        val group: String? = null,
        val svgId: String? = null,
        val valueKind: ClaimValueKind? = null,
        val widgetHint: ClaimWidgetHint? = null,
        val markdownAllowed: Boolean = false,
        val entryCodes: List<String>? = null,
        val unit: String? = null,
    )
