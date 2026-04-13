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
import kotlin.uuid.Uuid

// Credential Design CRUD
@Serializable
data class CreateCredentialDesignArgs(
    val tenantId: String,
    val input: CreateCredentialDesignInput,
)

@Serializable
data class GetCredentialDesignArgs(
    val tenantId: String,
    val id: Uuid,
)

// Shared args for entity-agnostic operations
@Serializable
data class GetDesignArgs(
    val tenantId: String,
    val id: Uuid,
)

@Serializable
data class FindByBindingArgs(
    val tenantId: String,
    val binding: DesignBinding,
)

@Serializable
data class FindByBindingKeyArgs(
    val tenantId: String,
    val bindingKey: DesignBindingKey,
    val bindingValue: String,
)

@Serializable
data class ListDesignsArgs(
    val tenantId: String,
    val filter: DesignFilter = DesignFilter(),
)

@Serializable
data class UpdateCredentialDesignArgs(
    val tenantId: String,
    val id: Uuid,
    val input: UpdateCredentialDesignInput,
)

@Serializable
data class DeleteDesignArgs(
    val tenantId: String,
    val id: Uuid,
)

// Issuer Design
@Serializable
data class CreateIssuerDesignArgs(
    val tenantId: String,
    val input: CreateIssuerDesignInput,
)

@Serializable
data class UpdateIssuerDesignArgs(
    val tenantId: String,
    val id: Uuid,
    val input: UpdateIssuerDesignInput,
)

// Verifier Design
@Serializable
data class CreateVerifierDesignArgs(
    val tenantId: String,
    val input: CreateVerifierDesignInput,
)

@Serializable
data class UpdateVerifierDesignArgs(
    val tenantId: String,
    val id: Uuid,
    val input: UpdateVerifierDesignInput,
)

// Render Variants
@Serializable
data class CreateRenderVariantArgs(
    val tenantId: String,
    val input: CreateRenderVariantInput,
)

@Serializable
data class GetRenderVariantArgs(
    val tenantId: String,
    val id: Uuid,
)

@Serializable
data class UpdateRenderVariantArgs(
    val tenantId: String,
    val id: Uuid,
    val input: CreateRenderVariantInput,
)

@Serializable
data class ListRenderVariantsArgs(
    val tenantId: String,
    val filter: DesignFilter = DesignFilter(),
)

@Serializable
data class DeleteRenderVariantArgs(
    val tenantId: String,
    val id: Uuid,
)

// Import / Refresh
@Serializable
data class ImportExternalDesignArgs(
    val tenantId: String,
    val input: ImportExternalDesignInput,
)

@Serializable
data class RefreshDesignArgs(
    val tenantId: String,
    val designId: Uuid,
)

// Snapshots
@Serializable
data class GetSourceSnapshotArgs(
    val tenantId: String,
    val snapshotId: Uuid,
)

@Serializable
data class RefreshSourceSnapshotArgs(
    val tenantId: String,
    val snapshotId: Uuid,
)

// Resolution
@Serializable
data class ResolveCredentialDesignArgs(
    val tenantId: String,
    val input: ResolveCredentialDesignInput,
)

@Serializable
data class ResolveIssuerDesignArgs(
    val tenantId: String,
    val input: ResolveEntityDesignInput,
)

@Serializable
data class ResolveVerifierDesignArgs(
    val tenantId: String,
    val input: ResolveEntityDesignInput,
)

// Assets
@Serializable
data class UploadDesignAssetArgs(
    val tenantId: String,
    val input: UploadDesignAssetInput,
)

@Serializable
data class GetDesignAssetArgs(
    val tenantId: String,
    val input: GetDesignAssetInput,
)
