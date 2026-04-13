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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.data.store.credential.design.model.AssetReference
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.ResolvedDesignAsset
import com.sphereon.data.store.credential.design.model.ResolvedIssuerDesign
import com.sphereon.data.store.credential.design.model.ResolvedVerifierDesign
import com.sphereon.data.store.credential.design.model.SourceSnapshotRecord
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord

// Credential Design CRUD
interface CreateCredentialDesignServiceCommand : ServiceCommand<CreateCredentialDesignArgs, CredentialDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.create"
    }
}

interface GetCredentialDesignServiceCommand : ServiceCommand<GetCredentialDesignArgs, CredentialDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.get"
    }
}

interface FindCredentialDesignByBindingServiceCommand : ServiceCommand<FindByBindingArgs, List<CredentialDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.find-by-binding"
    }
}

interface FindCredentialDesignByBindingKeyServiceCommand : ServiceCommand<FindByBindingKeyArgs, List<CredentialDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.find-by-binding-key"
    }
}

interface ListCredentialDesignsServiceCommand : ServiceCommand<ListDesignsArgs, List<CredentialDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.list"
    }
}

interface UpdateCredentialDesignServiceCommand : ServiceCommand<UpdateCredentialDesignArgs, CredentialDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.update"
    }
}

interface DeleteCredentialDesignServiceCommand : ServiceCommand<DeleteDesignArgs, Boolean> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.delete"
    }
}

// Issuer Design CRUD
interface CreateIssuerDesignServiceCommand : ServiceCommand<CreateIssuerDesignArgs, IssuerDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.create"
    }
}

interface GetIssuerDesignServiceCommand : ServiceCommand<GetDesignArgs, IssuerDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.get"
    }
}

interface FindIssuerDesignByBindingServiceCommand : ServiceCommand<FindByBindingArgs, List<IssuerDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.find-by-binding"
    }
}

interface FindIssuerDesignByBindingKeyServiceCommand : ServiceCommand<FindByBindingKeyArgs, List<IssuerDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.find-by-binding-key"
    }
}

interface ListIssuerDesignsServiceCommand : ServiceCommand<ListDesignsArgs, List<IssuerDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.list"
    }
}

interface UpdateIssuerDesignServiceCommand : ServiceCommand<UpdateIssuerDesignArgs, IssuerDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.update"
    }
}

interface DeleteIssuerDesignServiceCommand : ServiceCommand<DeleteDesignArgs, Boolean> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.delete"
    }
}

// Verifier Design CRUD
interface CreateVerifierDesignServiceCommand : ServiceCommand<CreateVerifierDesignArgs, VerifierDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.create"
    }
}

interface GetVerifierDesignServiceCommand : ServiceCommand<GetDesignArgs, VerifierDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.get"
    }
}

interface FindVerifierDesignByBindingServiceCommand : ServiceCommand<FindByBindingArgs, List<VerifierDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.find-by-binding"
    }
}

interface FindVerifierDesignByBindingKeyServiceCommand : ServiceCommand<FindByBindingKeyArgs, List<VerifierDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.find-by-binding-key"
    }
}

interface ListVerifierDesignsServiceCommand : ServiceCommand<ListDesignsArgs, List<VerifierDesignRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.list"
    }
}

interface UpdateVerifierDesignServiceCommand : ServiceCommand<UpdateVerifierDesignArgs, VerifierDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.update"
    }
}

interface DeleteVerifierDesignServiceCommand : ServiceCommand<DeleteDesignArgs, Boolean> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.delete"
    }
}

// Render Variants
interface CreateRenderVariantServiceCommand : ServiceCommand<CreateRenderVariantArgs, RenderVariantRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.create"
    }
}

interface GetRenderVariantServiceCommand : ServiceCommand<GetRenderVariantArgs, RenderVariantRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.get"
    }
}

interface UpdateRenderVariantServiceCommand : ServiceCommand<UpdateRenderVariantArgs, RenderVariantRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.update"
    }
}

interface ListRenderVariantsServiceCommand : ServiceCommand<ListRenderVariantsArgs, List<RenderVariantRecord>> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.list"
    }
}

interface DeleteRenderVariantServiceCommand : ServiceCommand<DeleteRenderVariantArgs, Boolean> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.delete"
    }
}

// Import / Refresh
interface ImportCredentialDesignServiceCommand : ServiceCommand<ImportExternalDesignArgs, CredentialDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.credential-import"
    }
}

interface ImportIssuerDesignServiceCommand : ServiceCommand<ImportExternalDesignArgs, IssuerDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.issuer-import"
    }
}

interface ImportVerifierDesignServiceCommand : ServiceCommand<ImportExternalDesignArgs, VerifierDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.verifier-import"
    }
}

interface RefreshCredentialDesignServiceCommand : ServiceCommand<RefreshDesignArgs, CredentialDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.credential-refresh"
    }
}

interface RefreshIssuerDesignServiceCommand : ServiceCommand<RefreshDesignArgs, IssuerDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.issuer-refresh"
    }
}

interface RefreshVerifierDesignServiceCommand : ServiceCommand<RefreshDesignArgs, VerifierDesignRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.verifier-refresh"
    }
}

// Snapshots
interface GetSourceSnapshotServiceCommand : ServiceCommand<GetSourceSnapshotArgs, SourceSnapshotRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.snapshots.get"
    }
}

interface RefreshSourceSnapshotServiceCommand : ServiceCommand<RefreshSourceSnapshotArgs, SourceSnapshotRecord> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.snapshot-refresh"
    }
}

// Resolution
interface ResolveCredentialDesignServiceCommand : ServiceCommand<ResolveCredentialDesignArgs, ResolvedCredentialDesign> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.resolution.credential-resolve"
    }
}

interface ResolveIssuerDesignServiceCommand : ServiceCommand<ResolveIssuerDesignArgs, ResolvedIssuerDesign> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.resolution.issuer-resolve"
    }
}

interface ResolveVerifierDesignServiceCommand : ServiceCommand<ResolveVerifierDesignArgs, ResolvedVerifierDesign> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.resolution.verifier-resolve"
    }
}

// Assets
interface UploadDesignAssetServiceCommand : ServiceCommand<UploadDesignAssetArgs, AssetReference> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.assets.upload"
    }
}

interface GetDesignAssetServiceCommand : ServiceCommand<GetDesignAssetArgs, ResolvedDesignAsset> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.assets.get"
    }
}
