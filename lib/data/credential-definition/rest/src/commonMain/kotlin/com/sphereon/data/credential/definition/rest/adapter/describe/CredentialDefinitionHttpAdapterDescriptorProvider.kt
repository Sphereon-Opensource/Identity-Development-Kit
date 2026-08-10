package com.sphereon.data.credential.definition.rest.adapter.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants
import com.sphereon.data.credential.definition.http.AddCredentialDefinitionClaimEndpointCommand
import com.sphereon.data.credential.definition.http.CreateCredentialDefinitionEndpointCommand
import com.sphereon.data.credential.definition.http.DeleteCredentialDefinitionEndpointCommand
import com.sphereon.data.credential.definition.http.GetCredentialDefinitionEndpointCommand
import com.sphereon.data.credential.definition.http.ListCredentialDefinitionsEndpointCommand
import com.sphereon.data.credential.definition.http.RemoveCredentialDefinitionClaimEndpointCommand
import com.sphereon.data.credential.definition.http.SetCredentialDefinitionClaimsEndpointCommand
import com.sphereon.data.credential.definition.http.SetCredentialDefinitionLifecycleEndpointCommand
import com.sphereon.data.credential.definition.http.SnapshotCredentialDefinitionVersionEndpointCommand
import com.sphereon.data.credential.definition.http.UpdateCredentialDefinitionClaimEndpointCommand
import com.sphereon.data.credential.definition.http.UpdateCredentialDefinitionEndpointCommand
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for the free-form credential-definition endpoints. Required alongside
 * the SessionScope [com.sphereon.data.credential.definition.rest.adapter.CredentialDefinitionHttpAdapter]
 * so the routes register at server startup (the SessionScope adapter only handles dispatch).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class CredentialDefinitionHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = "credential-definition.freeform.http"

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = CredentialDefinitionApiConstants.BASE_PATH,
                ),
            endpoints =
                listOf(
                    CreateCredentialDefinitionEndpointCommand.ENDPOINT,
                    GetCredentialDefinitionEndpointCommand.ENDPOINT,
                    ListCredentialDefinitionsEndpointCommand.ENDPOINT,
                    UpdateCredentialDefinitionEndpointCommand.ENDPOINT,
                    DeleteCredentialDefinitionEndpointCommand.ENDPOINT,
                    SetCredentialDefinitionClaimsEndpointCommand.ENDPOINT,
                    AddCredentialDefinitionClaimEndpointCommand.ENDPOINT,
                    UpdateCredentialDefinitionClaimEndpointCommand.ENDPOINT,
                    RemoveCredentialDefinitionClaimEndpointCommand.ENDPOINT,
                    SnapshotCredentialDefinitionVersionEndpointCommand.ENDPOINT,
                    SetCredentialDefinitionLifecycleEndpointCommand.ENDPOINT,
                ),
        )
}
