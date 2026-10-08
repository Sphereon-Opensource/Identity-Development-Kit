package com.sphereon.data.credential.definition.rest.adapter

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.CommandBackedHttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommandRegistry
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.OpenApiHints
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
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
@ContributesIntoMap(SessionScope::class, binding = binding<HttpAdapter>())
@StringKey(CredentialDefinitionHttpAdapter.ID)
class CredentialDefinitionHttpAdapter(
    execution: SessionExecution,
    endpointCommandRegistry: HttpEndpointCommandRegistry,
) : CommandBackedHttpAdapter(
        id = ID,
        execution = execution,
        endpointCommandRegistry = endpointCommandRegistry,
        mount =
            HttpAdapterMount(
                serverPrefix = "",
                adapterBasePath = CredentialDefinitionApiConstants.BASE_PATH,
            ),
    ) {

    override val openApiHints =
        OpenApiHints(
            tags = setOf(CredentialDefinitionApiConstants.Tags.CREDENTIAL_DEFINITION),
            operationIdPrefix = "credentialDefinition",
        )

    companion object {
        const val ID = "credential-definition.freeform.http"
    }
}
