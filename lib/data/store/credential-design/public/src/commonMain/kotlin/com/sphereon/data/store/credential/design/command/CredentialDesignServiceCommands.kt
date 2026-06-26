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

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
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
@JsExportCompat
interface CreateCredentialDesignServiceCommand : ServiceCommand<CreateCredentialDesignArgs, CredentialDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.create"
    }
}

@JsExportCompat
interface GetCredentialDesignServiceCommand : ServiceCommand<GetCredentialDesignArgs, CredentialDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.get"
    }
}

@JsExportCompat
interface FindCredentialDesignByBindingServiceCommand : ServiceCommand<FindByBindingArgs, List<CredentialDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.find-by-binding"
    }
}

@JsExportCompat
interface FindCredentialDesignByBindingKeyServiceCommand : ServiceCommand<FindByBindingKeyArgs, List<CredentialDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.find-by-binding-key"
    }
}

@JsExportCompat
interface ListCredentialDesignsServiceCommand : ServiceCommand<ListDesignsArgs, List<CredentialDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.list"
    }
}

@JsExportCompat
interface UpdateCredentialDesignServiceCommand : ServiceCommand<UpdateCredentialDesignArgs, CredentialDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.update"
    }
}

@JsExportCompat
interface DeleteCredentialDesignServiceCommand : ServiceCommand<DeleteDesignArgs, Boolean, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.designs.delete"
    }
}

// Issuer Design CRUD
@JsExportCompat
interface CreateIssuerDesignServiceCommand : ServiceCommand<CreateIssuerDesignArgs, IssuerDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.create"
    }
}

@JsExportCompat
interface GetIssuerDesignServiceCommand : ServiceCommand<GetDesignArgs, IssuerDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.get"
    }
}

@JsExportCompat
interface FindIssuerDesignByBindingServiceCommand : ServiceCommand<FindByBindingArgs, List<IssuerDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.find-by-binding"
    }
}

@JsExportCompat
interface FindIssuerDesignByBindingKeyServiceCommand : ServiceCommand<FindByBindingKeyArgs, List<IssuerDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.find-by-binding-key"
    }
}

@JsExportCompat
interface ListIssuerDesignsServiceCommand : ServiceCommand<ListDesignsArgs, List<IssuerDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.list"
    }
}

@JsExportCompat
interface UpdateIssuerDesignServiceCommand : ServiceCommand<UpdateIssuerDesignArgs, IssuerDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.update"
    }
}

@JsExportCompat
interface DeleteIssuerDesignServiceCommand : ServiceCommand<DeleteDesignArgs, Boolean, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.issuers.delete"
    }
}

// Verifier Design CRUD
@JsExportCompat
interface CreateVerifierDesignServiceCommand : ServiceCommand<CreateVerifierDesignArgs, VerifierDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.create"
    }
}

@JsExportCompat
interface GetVerifierDesignServiceCommand : ServiceCommand<GetDesignArgs, VerifierDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.get"
    }
}

@JsExportCompat
interface FindVerifierDesignByBindingServiceCommand : ServiceCommand<FindByBindingArgs, List<VerifierDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.find-by-binding"
    }
}

@JsExportCompat
interface FindVerifierDesignByBindingKeyServiceCommand : ServiceCommand<FindByBindingKeyArgs, List<VerifierDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.find-by-binding-key"
    }
}

@JsExportCompat
interface ListVerifierDesignsServiceCommand : ServiceCommand<ListDesignsArgs, List<VerifierDesignRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.list"
    }
}

@JsExportCompat
interface UpdateVerifierDesignServiceCommand : ServiceCommand<UpdateVerifierDesignArgs, VerifierDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.update"
    }
}

@JsExportCompat
interface DeleteVerifierDesignServiceCommand : ServiceCommand<DeleteDesignArgs, Boolean, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.verifiers.delete"
    }
}

// Render Variants
@JsExportCompat
interface CreateRenderVariantServiceCommand : ServiceCommand<CreateRenderVariantArgs, RenderVariantRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.create"
    }
}

@JsExportCompat
interface GetRenderVariantServiceCommand : ServiceCommand<GetRenderVariantArgs, RenderVariantRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.get"
    }
}

@JsExportCompat
interface UpdateRenderVariantServiceCommand : ServiceCommand<UpdateRenderVariantArgs, RenderVariantRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.update"
    }
}

@JsExportCompat
interface ListRenderVariantsServiceCommand : ServiceCommand<ListRenderVariantsArgs, List<RenderVariantRecord>, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.list"
    }
}

@JsExportCompat
interface DeleteRenderVariantServiceCommand : ServiceCommand<DeleteRenderVariantArgs, Boolean, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.render-variants.delete"
    }
}

// Import / Refresh
@JsExportCompat
interface ImportCredentialDesignServiceCommand : ServiceCommand<ImportExternalDesignArgs, CredentialDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.credential-import"
    }
}

@JsExportCompat
interface ImportIssuerDesignServiceCommand : ServiceCommand<ImportExternalDesignArgs, IssuerDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.issuer-import"
    }
}

@JsExportCompat
interface ImportVerifierDesignServiceCommand : ServiceCommand<ImportExternalDesignArgs, VerifierDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.verifier-import"
    }
}

@JsExportCompat
interface RefreshCredentialDesignServiceCommand : ServiceCommand<RefreshDesignArgs, CredentialDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.credential-refresh"
    }
}

@JsExportCompat
interface RefreshIssuerDesignServiceCommand : ServiceCommand<RefreshDesignArgs, IssuerDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.issuer-refresh"
    }
}

@JsExportCompat
interface RefreshVerifierDesignServiceCommand : ServiceCommand<RefreshDesignArgs, VerifierDesignRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.verifier-refresh"
    }
}

// Snapshots
@JsExportCompat
interface GetSourceSnapshotServiceCommand : ServiceCommand<GetSourceSnapshotArgs, SourceSnapshotRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.snapshots.get"
    }
}

@JsExportCompat
interface RefreshSourceSnapshotServiceCommand : ServiceCommand<RefreshSourceSnapshotArgs, SourceSnapshotRecord, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.external.snapshot-refresh"
    }
}

// Resolution
@JsExportCompat
interface ResolveCredentialDesignServiceCommand : ServiceCommand<ResolveCredentialDesignArgs, ResolvedCredentialDesign, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.resolution.credential-resolve"
    }
}

@JsExportCompat
interface ResolveIssuerDesignServiceCommand : ServiceCommand<ResolveIssuerDesignArgs, ResolvedIssuerDesign, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.resolution.issuer-resolve"
    }
}

@JsExportCompat
interface ResolveVerifierDesignServiceCommand : ServiceCommand<ResolveVerifierDesignArgs, ResolvedVerifierDesign, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.resolution.verifier-resolve"
    }
}

// Assets
@JsExportCompat
interface UploadDesignAssetServiceCommand : ServiceCommand<UploadDesignAssetArgs, AssetReference, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.assets.upload"
    }
}

@JsExportCompat
interface GetDesignAssetServiceCommand : ServiceCommand<GetDesignAssetArgs, ResolvedDesignAsset, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.assets.get"
    }
}

@JsExportCompat
interface GetDesignAssetByHashServiceCommand : ServiceCommand<GetDesignAssetByHashArgs, ResolvedDesignAsset, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "credential-design.assets.get-by-hash"
    }
}
