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
import com.sphereon.data.store.asset.model.AssetReference
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.uuid.Uuid

@JsExportCompat
@Serializable
data class RenderVariantRecord
    @JvmOverloads
    constructor(
        val id: Uuid,
        val tenantId: String,
        val kind: RenderVariantKind,
        val alias: String? = null,
        val localeApplicability: List<String> = emptyList(),
        val sourceSnapshotId: Uuid? = null,
        val logo: AssetReference? = null,
        val backgroundImage: AssetReference? = null,
        val backgroundColor: String? = null,
        val textColor: String? = null,
        val accentColor: String? = null,
        val svgTemplate: SvgTemplate? = null,
        val w3cRenderMethod: W3cRenderMethodReference? = null,
        val pdfTemplate: AssetReference? = null,
    )
