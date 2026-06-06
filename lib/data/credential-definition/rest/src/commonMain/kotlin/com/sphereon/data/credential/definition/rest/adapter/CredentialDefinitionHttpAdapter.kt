package com.sphereon.data.credential.definition.rest.adapter

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
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
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP adapter for the free-form credential-definition authoring API, mounted at `/api/v1`. Routes by
 * relative path to each endpoint command. Auth is the OIDC bearer token; the tenant is resolved from
 * the session, never from headers.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class CredentialDefinitionHttpAdapter(
    execution: SessionExecution,
    createDefinition: CreateCredentialDefinitionEndpointCommand,
    getDefinition: GetCredentialDefinitionEndpointCommand,
    listDefinitions: ListCredentialDefinitionsEndpointCommand,
    updateDefinition: UpdateCredentialDefinitionEndpointCommand,
    deleteDefinition: DeleteCredentialDefinitionEndpointCommand,
    setClaims: SetCredentialDefinitionClaimsEndpointCommand,
    addClaim: AddCredentialDefinitionClaimEndpointCommand,
    updateClaim: UpdateCredentialDefinitionClaimEndpointCommand,
    removeClaim: RemoveCredentialDefinitionClaimEndpointCommand,
    snapshotVersion: SnapshotCredentialDefinitionVersionEndpointCommand,
    setLifecycle: SetCredentialDefinitionLifecycleEndpointCommand,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = CredentialDefinitionApiConstants.BASE_PATH,
            ),
    ) {
    override val endpointCommands: List<HttpEndpointCommand> =
        listOf(
            createDefinition,
            getDefinition,
            listDefinitions,
            updateDefinition,
            deleteDefinition,
            setClaims,
            addClaim,
            updateClaim,
            removeClaim,
            snapshotVersion,
            setLifecycle,
        )

    override val openApiHints =
        OpenApiHints(
            tags = setOf(CredentialDefinitionApiConstants.Tags.CREDENTIAL_DEFINITION),
            operationIdPrefix = "credentialDefinition",
        )

    companion object {
        const val ID = "credential-definition-freeform"
    }
}
