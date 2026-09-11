@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.rest.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.requireJsonBody
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.response.ResponseBuilder
import com.sphereon.data.credential.definition.CredentialClaim
import com.sphereon.data.credential.definition.CredentialDefinition
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants.CommandIds
import com.sphereon.data.credential.definition.CredentialDefinitionLifecycleStatus
import com.sphereon.data.credential.definition.CredentialTypeBindingRef
import com.sphereon.data.credential.definition.command.AddCredentialDefinitionClaimArgs
import com.sphereon.data.credential.definition.command.AddCredentialDefinitionClaimServiceCommand
import com.sphereon.data.credential.definition.command.CreateCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.CreateCredentialDefinitionServiceCommand
import com.sphereon.data.credential.definition.command.DeleteCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.DeleteCredentialDefinitionResult
import com.sphereon.data.credential.definition.command.DeleteCredentialDefinitionServiceCommand
import com.sphereon.data.credential.definition.command.GetCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.GetCredentialDefinitionServiceCommand
import com.sphereon.data.credential.definition.command.ListCredentialDefinitionsArgs
import com.sphereon.data.credential.definition.command.ListCredentialDefinitionsResult
import com.sphereon.data.credential.definition.command.ListCredentialDefinitionsServiceCommand
import com.sphereon.data.credential.definition.command.RemoveCredentialDefinitionClaimArgs
import com.sphereon.data.credential.definition.command.RemoveCredentialDefinitionClaimServiceCommand
import com.sphereon.data.credential.definition.command.SetCredentialDefinitionClaimsArgs
import com.sphereon.data.credential.definition.command.SetCredentialDefinitionClaimsServiceCommand
import com.sphereon.data.credential.definition.command.SetCredentialDefinitionLifecycleArgs
import com.sphereon.data.credential.definition.command.SetCredentialDefinitionLifecycleServiceCommand
import com.sphereon.data.credential.definition.command.SnapshotCredentialDefinitionVersionArgs
import com.sphereon.data.credential.definition.command.SnapshotCredentialDefinitionVersionServiceCommand
import com.sphereon.data.credential.definition.command.UpdateCredentialDefinitionArgs
import com.sphereon.data.credential.definition.command.UpdateCredentialDefinitionClaimArgs
import com.sphereon.data.credential.definition.command.UpdateCredentialDefinitionClaimServiceCommand
import com.sphereon.data.credential.definition.command.UpdateCredentialDefinitionServiceCommand
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
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val PARAM_DEFINITION_ID = "definitionId"
private const val PARAM_CLAIM_PATH = "claimPath"

private fun GenericHttpRequest.requireDefinitionId(): IdkResult<Uuid, IdkError> {
    val raw = requirePathParam(PARAM_DEFINITION_ID).getOrElse { return Err(it) }
    val parsed =
        runCatching { Uuid.parse(raw) }.getOrNull()
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid definition id: '$raw'"))
    return Ok(parsed)
}

@Serializable
private data class CreateCredentialDefinitionBody(
    val name: String,
    val credentialTypeBindingRef: CredentialTypeBindingRef,
    val description: String? = null,
    val claims: List<CredentialClaim> = emptyList(),
)

@Serializable
private data class UpdateCredentialDefinitionBody(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
private data class SetCredentialDefinitionClaimsBody(
    val claims: List<CredentialClaim>,
)

@Serializable
private data class AddCredentialDefinitionClaimBody(
    val claim: CredentialClaim,
)

@Serializable
private data class UpdateCredentialDefinitionClaimBody(
    val claim: CredentialClaim,
)

@Serializable
private data class SetCredentialDefinitionLifecycleBody(
    val lifecycleStatus: CredentialDefinitionLifecycleStatus,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(CreateCredentialDefinitionEndpointCommand.COMMAND_ID)
class CreateCredentialDefinitionEndpointCommandImpl(
    execution: SessionExecution,
    private val service: CreateCredentialDefinitionServiceCommand,
) : HttpEndpointCommandAdapter(
        id = CreateCredentialDefinitionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = CreateCredentialDefinitionEndpointCommand.ENDPOINT,
    ),
    CreateCredentialDefinitionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val body = request.requireJsonBody<CreateCredentialDefinitionBody>(ResponseBuilder.json).getOrElse { return Err(it) }
        return service
            .execute(
                CreateCredentialDefinitionArgs(
                    name = body.name,
                    credentialTypeBindingRef = body.credentialTypeBindingRef,
                    description = body.description,
                    claims = body.claims,
                ),
            ).map { ResponseBuilder.createdWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(GetCredentialDefinitionEndpointCommand.COMMAND_ID)
class GetCredentialDefinitionEndpointCommandImpl(
    execution: SessionExecution,
    private val service: GetCredentialDefinitionServiceCommand,
) : HttpEndpointCommandAdapter(
        id = GetCredentialDefinitionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetCredentialDefinitionEndpointCommand.ENDPOINT,
    ),
    GetCredentialDefinitionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        return service
            .execute(GetCredentialDefinitionArgs(definitionId))
            .map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(ListCredentialDefinitionsEndpointCommand.COMMAND_ID)
class ListCredentialDefinitionsEndpointCommandImpl(
    execution: SessionExecution,
    private val service: ListCredentialDefinitionsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = ListCredentialDefinitionsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ListCredentialDefinitionsEndpointCommand.ENDPOINT,
    ),
    ListCredentialDefinitionsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        @Suppress("UNUSED_VARIABLE")
        val request = applyDuring(args)
        return service
            .execute(ListCredentialDefinitionsArgs())
            .map { result: ListCredentialDefinitionsResult -> ResponseBuilder.list<CredentialDefinition>(result.definitions) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(UpdateCredentialDefinitionEndpointCommand.COMMAND_ID)
class UpdateCredentialDefinitionEndpointCommandImpl(
    execution: SessionExecution,
    private val service: UpdateCredentialDefinitionServiceCommand,
) : HttpEndpointCommandAdapter(
        id = UpdateCredentialDefinitionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = UpdateCredentialDefinitionEndpointCommand.ENDPOINT,
    ),
    UpdateCredentialDefinitionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        val body = request.requireJsonBody<UpdateCredentialDefinitionBody>(ResponseBuilder.json).getOrElse { return Err(it) }
        return service
            .execute(
                UpdateCredentialDefinitionArgs(
                    definitionId = definitionId,
                    name = body.name,
                    description = body.description,
                ),
            ).map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(DeleteCredentialDefinitionEndpointCommand.COMMAND_ID)
class DeleteCredentialDefinitionEndpointCommandImpl(
    execution: SessionExecution,
    private val service: DeleteCredentialDefinitionServiceCommand,
) : HttpEndpointCommandAdapter(
        id = DeleteCredentialDefinitionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = DeleteCredentialDefinitionEndpointCommand.ENDPOINT,
    ),
    DeleteCredentialDefinitionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        return service
            .execute(DeleteCredentialDefinitionArgs(definitionId))
            .map { result: DeleteCredentialDefinitionResult -> ResponseBuilder.okWithData<DeleteCredentialDefinitionResult>(result) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(SetCredentialDefinitionClaimsEndpointCommand.COMMAND_ID)
class SetCredentialDefinitionClaimsEndpointCommandImpl(
    execution: SessionExecution,
    private val service: SetCredentialDefinitionClaimsServiceCommand,
) : HttpEndpointCommandAdapter(
        id = SetCredentialDefinitionClaimsEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SetCredentialDefinitionClaimsEndpointCommand.ENDPOINT,
    ),
    SetCredentialDefinitionClaimsEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        val body = request.requireJsonBody<SetCredentialDefinitionClaimsBody>(ResponseBuilder.json).getOrElse { return Err(it) }
        return service
            .execute(
                SetCredentialDefinitionClaimsArgs(definitionId = definitionId, claims = body.claims),
            ).map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(AddCredentialDefinitionClaimEndpointCommand.COMMAND_ID)
class AddCredentialDefinitionClaimEndpointCommandImpl(
    execution: SessionExecution,
    private val service: AddCredentialDefinitionClaimServiceCommand,
) : HttpEndpointCommandAdapter(
        id = AddCredentialDefinitionClaimEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AddCredentialDefinitionClaimEndpointCommand.ENDPOINT,
    ),
    AddCredentialDefinitionClaimEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        val body = request.requireJsonBody<AddCredentialDefinitionClaimBody>(ResponseBuilder.json).getOrElse { return Err(it) }
        return service
            .execute(
                AddCredentialDefinitionClaimArgs(definitionId = definitionId, claim = body.claim),
            ).map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(UpdateCredentialDefinitionClaimEndpointCommand.COMMAND_ID)
class UpdateCredentialDefinitionClaimEndpointCommandImpl(
    execution: SessionExecution,
    private val service: UpdateCredentialDefinitionClaimServiceCommand,
) : HttpEndpointCommandAdapter(
        id = UpdateCredentialDefinitionClaimEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = UpdateCredentialDefinitionClaimEndpointCommand.ENDPOINT,
    ),
    UpdateCredentialDefinitionClaimEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        val claimPath = request.requirePathParam(PARAM_CLAIM_PATH).getOrElse { return Err(it) }
        val body = request.requireJsonBody<UpdateCredentialDefinitionClaimBody>(ResponseBuilder.json).getOrElse { return Err(it) }
        return service
            .execute(
                UpdateCredentialDefinitionClaimArgs(definitionId = definitionId, claimPath = claimPath, claim = body.claim),
            ).map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(RemoveCredentialDefinitionClaimEndpointCommand.COMMAND_ID)
class RemoveCredentialDefinitionClaimEndpointCommandImpl(
    execution: SessionExecution,
    private val service: RemoveCredentialDefinitionClaimServiceCommand,
) : HttpEndpointCommandAdapter(
        id = RemoveCredentialDefinitionClaimEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = RemoveCredentialDefinitionClaimEndpointCommand.ENDPOINT,
    ),
    RemoveCredentialDefinitionClaimEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        val claimPath = request.requirePathParam(PARAM_CLAIM_PATH).getOrElse { return Err(it) }
        return service
            .execute(
                RemoveCredentialDefinitionClaimArgs(definitionId = definitionId, claimPath = claimPath),
            ).map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(SnapshotCredentialDefinitionVersionEndpointCommand.COMMAND_ID)
class SnapshotCredentialDefinitionVersionEndpointCommandImpl(
    execution: SessionExecution,
    private val service: SnapshotCredentialDefinitionVersionServiceCommand,
) : HttpEndpointCommandAdapter(
        id = SnapshotCredentialDefinitionVersionEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SnapshotCredentialDefinitionVersionEndpointCommand.ENDPOINT,
    ),
    SnapshotCredentialDefinitionVersionEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        return service
            .execute(SnapshotCredentialDefinitionVersionArgs(definitionId))
            .map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(SetCredentialDefinitionLifecycleEndpointCommand.COMMAND_ID)
class SetCredentialDefinitionLifecycleEndpointCommandImpl(
    execution: SessionExecution,
    private val service: SetCredentialDefinitionLifecycleServiceCommand,
) : HttpEndpointCommandAdapter(
        id = SetCredentialDefinitionLifecycleEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = SetCredentialDefinitionLifecycleEndpointCommand.ENDPOINT,
    ),
    SetCredentialDefinitionLifecycleEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val definitionId = request.requireDefinitionId().getOrElse { return Err(it) }
        val body = request.requireJsonBody<SetCredentialDefinitionLifecycleBody>(ResponseBuilder.json).getOrElse { return Err(it) }
        return service
            .execute(
                SetCredentialDefinitionLifecycleArgs(definitionId = definitionId, lifecycleStatus = body.lifecycleStatus),
            ).map { ResponseBuilder.okWithData<CredentialDefinition>(it) }
    }
}
