package com.sphereon.data.credential.definition.http

import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants.BASE_PATH
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants.CommandIds
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants.Paths
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants.Tags

private val JSON = setOf(MediaType.ApplicationJson)
private val DEFINITION_TAGS = setOf(Tags.CREDENTIAL_DEFINITION)

interface CreateCredentialDefinitionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_CREATE
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "$BASE_PATH${Paths.DEFINITIONS}",
                consumes = JSON,
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "createCredentialDefinition",
                tags = DEFINITION_TAGS,
                summary = "Create a draft free-form credential definition",
            )
    }
}

interface GetCredentialDefinitionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_GET
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_BY_ID}",
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "getCredentialDefinition",
                tags = DEFINITION_TAGS,
                summary = "Get a free-form credential definition by id",
            )
    }
}

interface ListCredentialDefinitionsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_LIST
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "$BASE_PATH${Paths.DEFINITIONS}",
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "listCredentialDefinitions",
                tags = DEFINITION_TAGS,
                summary = "List free-form credential definitions for the calling tenant",
            )
    }
}

interface UpdateCredentialDefinitionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_UPDATE
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PATCH,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_BY_ID}",
                consumes = JSON,
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "updateCredentialDefinition",
                tags = DEFINITION_TAGS,
                summary = "Update a definition's name or description",
            )
    }
}

interface DeleteCredentialDefinitionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_DELETE
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_BY_ID}",
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "deleteCredentialDefinition",
                tags = DEFINITION_TAGS,
                summary = "Delete a free-form credential definition by id",
            )
    }
}

interface SetCredentialDefinitionClaimsEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_SET_CLAIMS
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_CLAIMS}",
                consumes = JSON,
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "setCredentialDefinitionClaims",
                tags = DEFINITION_TAGS,
                summary = "Replace the whole claims list of a definition",
            )
    }
}

interface AddCredentialDefinitionClaimEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_ADD_CLAIM
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_CLAIMS}",
                consumes = JSON,
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "addCredentialDefinitionClaim",
                tags = DEFINITION_TAGS,
                summary = "Add a claim to a definition",
            )
    }
}

interface UpdateCredentialDefinitionClaimEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_UPDATE_CLAIM
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_CLAIM_BY_PATH}",
                consumes = JSON,
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "updateCredentialDefinitionClaim",
                tags = DEFINITION_TAGS,
                summary = "Replace a claim in a definition",
            )
    }
}

interface RemoveCredentialDefinitionClaimEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_REMOVE_CLAIM
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_CLAIM_BY_PATH}",
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "removeCredentialDefinitionClaim",
                tags = DEFINITION_TAGS,
                summary = "Remove a claim from a definition",
            )
    }
}

interface SnapshotCredentialDefinitionVersionEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_SNAPSHOT_VERSION
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_VERSION}",
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "snapshotCredentialDefinitionVersion",
                tags = DEFINITION_TAGS,
                summary = "Bump the definition's snapshot version",
            )
    }
}

interface SetCredentialDefinitionLifecycleEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = CommandIds.HTTP_SET_LIFECYCLE
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "$BASE_PATH${Paths.DEFINITION_LIFECYCLE}",
                consumes = JSON,
                produces = JSON,
                commandId = COMMAND_ID,
                handlerCommandId = COMMAND_ID,
                operationId = "setCredentialDefinitionLifecycle",
                tags = DEFINITION_TAGS,
                summary = "Toggle definition lifecycle status (draft <-> published)",
            )
    }
}
