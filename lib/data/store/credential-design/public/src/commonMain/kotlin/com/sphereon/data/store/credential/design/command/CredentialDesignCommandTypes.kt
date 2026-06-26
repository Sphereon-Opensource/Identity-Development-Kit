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

package com.sphereon.data.store.credential.design.command

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.credential.design.model.CreateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.CreateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.CreateRenderVariantInput
import com.sphereon.data.store.credential.design.model.CreateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.GetDesignAssetInput
import com.sphereon.data.store.credential.design.model.ImportExternalDesignInput
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.model.UpdateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.UpdateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.UpdateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.UploadDesignAssetInput
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.uuid.Uuid

// Credential Design CRUD
@JsExportCompat
@Serializable
data class CreateCredentialDesignArgs(
    val tenantId: String,
    val input: CreateCredentialDesignInput,
)

@JsExportCompat
@Serializable
data class GetCredentialDesignArgs(
    val tenantId: String,
    val id: Uuid,
)

// Shared args for entity-agnostic operations
@JsExportCompat
@Serializable
data class GetDesignArgs(
    val tenantId: String,
    val id: Uuid,
)

@JsExportCompat
@Serializable
data class FindByBindingArgs(
    val tenantId: String,
    val binding: DesignBinding,
)

@JsExportCompat
@Serializable
data class FindByBindingKeyArgs(
    val tenantId: String,
    val bindingKey: DesignBindingKey,
    val bindingValue: String,
)

@JsExportCompat
@Serializable
data class ListDesignsArgs
    @JvmOverloads
    constructor(
        val tenantId: String,
        val filter: DesignFilter = DesignFilter(),
    )

@JsExportCompat
@Serializable
data class UpdateCredentialDesignArgs(
    val tenantId: String,
    val id: Uuid,
    val input: UpdateCredentialDesignInput,
)

@JsExportCompat
@Serializable
data class DeleteDesignArgs(
    val tenantId: String,
    val id: Uuid,
)

// Issuer Design
@JsExportCompat
@Serializable
data class CreateIssuerDesignArgs(
    val tenantId: String,
    val input: CreateIssuerDesignInput,
)

@JsExportCompat
@Serializable
data class UpdateIssuerDesignArgs(
    val tenantId: String,
    val id: Uuid,
    val input: UpdateIssuerDesignInput,
)

// Verifier Design
@JsExportCompat
@Serializable
data class CreateVerifierDesignArgs(
    val tenantId: String,
    val input: CreateVerifierDesignInput,
)

@JsExportCompat
@Serializable
data class UpdateVerifierDesignArgs(
    val tenantId: String,
    val id: Uuid,
    val input: UpdateVerifierDesignInput,
)

// Render Variants
@JsExportCompat
@Serializable
data class CreateRenderVariantArgs(
    val tenantId: String,
    val input: CreateRenderVariantInput,
)

@JsExportCompat
@Serializable
data class GetRenderVariantArgs(
    val tenantId: String,
    val id: Uuid,
)

@JsExportCompat
@Serializable
data class UpdateRenderVariantArgs(
    val tenantId: String,
    val id: Uuid,
    val input: CreateRenderVariantInput,
)

@JsExportCompat
@Serializable
data class ListRenderVariantsArgs
    @JvmOverloads
    constructor(
        val tenantId: String,
        val filter: DesignFilter = DesignFilter(),
    )

@JsExportCompat
@Serializable
data class DeleteRenderVariantArgs(
    val tenantId: String,
    val id: Uuid,
)

// Import / Refresh
@JsExportCompat
@Serializable
data class ImportExternalDesignArgs(
    val tenantId: String,
    val input: ImportExternalDesignInput,
)

@JsExportCompat
@Serializable
data class RefreshDesignArgs(
    val tenantId: String,
    val designId: Uuid,
)

// Snapshots
@JsExportCompat
@Serializable
data class GetSourceSnapshotArgs(
    val tenantId: String,
    val snapshotId: Uuid,
)

@JsExportCompat
@Serializable
data class RefreshSourceSnapshotArgs(
    val tenantId: String,
    val snapshotId: Uuid,
)

// Resolution
@JsExportCompat
@Serializable
data class ResolveCredentialDesignArgs(
    val tenantId: String,
    val input: ResolveCredentialDesignInput,
)

@JsExportCompat
@Serializable
data class ResolveIssuerDesignArgs(
    val tenantId: String,
    val input: ResolveEntityDesignInput,
)

@JsExportCompat
@Serializable
data class ResolveVerifierDesignArgs(
    val tenantId: String,
    val input: ResolveEntityDesignInput,
)

// Assets
@JsExportCompat
@Serializable
data class UploadDesignAssetArgs(
    val tenantId: String,
    val input: UploadDesignAssetInput,
)

@JsExportCompat
@Serializable
data class GetDesignAssetArgs(
    val tenantId: String,
    val input: GetDesignAssetInput,
)

@JsExportCompat
@Serializable
data class GetDesignAssetByHashArgs(
    val tenantId: String,
    val hash: String,
)
