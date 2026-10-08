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

package com.sphereon.data.store.credential.design.impl.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.data.store.credential.design.command.CreateCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.CreateIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.CreateRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.CreateVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.DeleteVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.FindCredentialDesignByBindingKeyServiceCommand
import com.sphereon.data.store.credential.design.command.FindCredentialDesignByBindingServiceCommand
import com.sphereon.data.store.credential.design.command.FindIssuerDesignByBindingKeyServiceCommand
import com.sphereon.data.store.credential.design.command.FindIssuerDesignByBindingServiceCommand
import com.sphereon.data.store.credential.design.command.FindVerifierDesignByBindingKeyServiceCommand
import com.sphereon.data.store.credential.design.command.FindVerifierDesignByBindingServiceCommand
import com.sphereon.data.store.credential.design.command.GetCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.GetDesignAssetByHashServiceCommand
import com.sphereon.data.store.credential.design.command.GetDesignAssetServiceCommand
import com.sphereon.data.store.credential.design.command.GetIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.GetRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.GetSourceSnapshotServiceCommand
import com.sphereon.data.store.credential.design.command.GetVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ImportCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ImportIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ImportVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ListCredentialDesignsServiceCommand
import com.sphereon.data.store.credential.design.command.ListDesignAssetsServiceCommand
import com.sphereon.data.store.credential.design.command.ListIssuerDesignsServiceCommand
import com.sphereon.data.store.credential.design.command.ListRenderVariantsServiceCommand
import com.sphereon.data.store.credential.design.command.ListVerifierDesignsServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshSourceSnapshotServiceCommand
import com.sphereon.data.store.credential.design.command.RefreshVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ResolveCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ResolveIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.ResolveVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateCredentialDesignServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateIssuerDesignServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateRenderVariantServiceCommand
import com.sphereon.data.store.credential.design.command.UpdateVerifierDesignServiceCommand
import com.sphereon.data.store.credential.design.command.UploadDesignAssetServiceCommand
import com.sphereon.data.store.credential.design.command.UploadTenantAssetServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface CredentialDesignCommandDescriptors {
    // Credential Design CRUD
    @Provides @IntoMap
    @StringKey(CreateCredentialDesignServiceCommand.COMMAND_ID)
    fun createCredentialDesign(impl: CreateCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetCredentialDesignServiceCommand.COMMAND_ID)
    fun getCredentialDesign(impl: GetCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FindCredentialDesignByBindingServiceCommand.COMMAND_ID)
    fun findCredentialDesignByBinding(impl: FindCredentialDesignByBindingServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FindCredentialDesignByBindingKeyServiceCommand.COMMAND_ID)
    fun findCredentialDesignByBindingKey(impl: FindCredentialDesignByBindingKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListCredentialDesignsServiceCommand.COMMAND_ID)
    fun listCredentialDesigns(impl: ListCredentialDesignsServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateCredentialDesignServiceCommand.COMMAND_ID)
    fun updateCredentialDesign(impl: UpdateCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteCredentialDesignServiceCommand.COMMAND_ID)
    fun deleteCredentialDesign(impl: DeleteCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    // Issuer Design CRUD
    @Provides @IntoMap
    @StringKey(CreateIssuerDesignServiceCommand.COMMAND_ID)
    fun createIssuerDesign(impl: CreateIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetIssuerDesignServiceCommand.COMMAND_ID)
    fun getIssuerDesign(impl: GetIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FindIssuerDesignByBindingServiceCommand.COMMAND_ID)
    fun findIssuerDesignByBinding(impl: FindIssuerDesignByBindingServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FindIssuerDesignByBindingKeyServiceCommand.COMMAND_ID)
    fun findIssuerDesignByBindingKey(impl: FindIssuerDesignByBindingKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListIssuerDesignsServiceCommand.COMMAND_ID)
    fun listIssuerDesigns(impl: ListIssuerDesignsServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateIssuerDesignServiceCommand.COMMAND_ID)
    fun updateIssuerDesign(impl: UpdateIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteIssuerDesignServiceCommand.COMMAND_ID)
    fun deleteIssuerDesign(impl: DeleteIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    // Verifier Design CRUD
    @Provides @IntoMap
    @StringKey(CreateVerifierDesignServiceCommand.COMMAND_ID)
    fun createVerifierDesign(impl: CreateVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetVerifierDesignServiceCommand.COMMAND_ID)
    fun getVerifierDesign(impl: GetVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FindVerifierDesignByBindingServiceCommand.COMMAND_ID)
    fun findVerifierDesignByBinding(impl: FindVerifierDesignByBindingServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(FindVerifierDesignByBindingKeyServiceCommand.COMMAND_ID)
    fun findVerifierDesignByBindingKey(impl: FindVerifierDesignByBindingKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListVerifierDesignsServiceCommand.COMMAND_ID)
    fun listVerifierDesigns(impl: ListVerifierDesignsServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateVerifierDesignServiceCommand.COMMAND_ID)
    fun updateVerifierDesign(impl: UpdateVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteVerifierDesignServiceCommand.COMMAND_ID)
    fun deleteVerifierDesign(impl: DeleteVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    // Render Variants
    @Provides @IntoMap
    @StringKey(CreateRenderVariantServiceCommand.COMMAND_ID)
    fun createRenderVariant(impl: CreateRenderVariantServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetRenderVariantServiceCommand.COMMAND_ID)
    fun getRenderVariant(impl: GetRenderVariantServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateRenderVariantServiceCommand.COMMAND_ID)
    fun updateRenderVariant(impl: UpdateRenderVariantServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteRenderVariantServiceCommand.COMMAND_ID)
    fun deleteRenderVariant(impl: DeleteRenderVariantServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListRenderVariantsServiceCommand.COMMAND_ID)
    fun listRenderVariants(impl: ListRenderVariantsServiceCommandImpl): ServiceCommand<*, *, *> = impl

    // Import / Refresh
    @Provides @IntoMap
    @StringKey(ImportCredentialDesignServiceCommand.COMMAND_ID)
    fun importCredentialDesign(impl: ImportCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ImportIssuerDesignServiceCommand.COMMAND_ID)
    fun importIssuerDesign(impl: ImportIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ImportVerifierDesignServiceCommand.COMMAND_ID)
    fun importVerifierDesign(impl: ImportVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RefreshCredentialDesignServiceCommand.COMMAND_ID)
    fun refreshCredentialDesign(impl: RefreshCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RefreshIssuerDesignServiceCommand.COMMAND_ID)
    fun refreshIssuerDesign(impl: RefreshIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RefreshVerifierDesignServiceCommand.COMMAND_ID)
    fun refreshVerifierDesign(impl: RefreshVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetSourceSnapshotServiceCommand.COMMAND_ID)
    fun getSourceSnapshot(impl: GetSourceSnapshotServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RefreshSourceSnapshotServiceCommand.COMMAND_ID)
    fun refreshSourceSnapshot(impl: RefreshSourceSnapshotServiceCommandImpl): ServiceCommand<*, *, *> = impl

    // Resolution
    @Provides @IntoMap
    @StringKey(ResolveCredentialDesignServiceCommand.COMMAND_ID)
    fun resolveCredentialDesign(impl: ResolveCredentialDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveIssuerDesignServiceCommand.COMMAND_ID)
    fun resolveIssuerDesign(impl: ResolveIssuerDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveVerifierDesignServiceCommand.COMMAND_ID)
    fun resolveVerifierDesign(impl: ResolveVerifierDesignServiceCommandImpl): ServiceCommand<*, *, *> = impl

    // Assets
    @Provides @IntoMap
    @StringKey(UploadDesignAssetServiceCommand.COMMAND_ID)
    fun uploadDesignAsset(impl: UploadDesignAssetServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetDesignAssetServiceCommand.COMMAND_ID)
    fun getDesignAsset(impl: GetDesignAssetServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetDesignAssetByHashServiceCommand.COMMAND_ID)
    fun getDesignAssetByHash(impl: GetDesignAssetByHashServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListDesignAssetsServiceCommand.COMMAND_ID)
    fun listDesignAssets(impl: ListDesignAssetsServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UploadTenantAssetServiceCommand.COMMAND_ID)
    fun uploadTenantAsset(impl: UploadTenantAssetServiceCommandImpl): ServiceCommand<*, *, *> = impl
}
