@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package com.sphereon.data.credential.definition.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.data.credential.definition.CredentialDefinition
import com.sphereon.data.credential.definition.CredentialDefinitionLifecycleStatus
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
import com.sphereon.data.credential.definition.persistence.CredentialDefinitionStore
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Load a definition by id within the calling tenant, mapping a missing row to a NOT_FOUND error. */
internal suspend fun CredentialDefinitionStore.require(
    tenantId: String,
    id: Uuid,
): IdkResult<CredentialDefinition, IdkError> {
    val definition =
        get(tenantId, id).getOrElse { return Err(it) }
            ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential definition not found: $id"))
    return Ok(definition)
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CreateCredentialDefinitionServiceCommand>())
class CreateCredentialDefinitionServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<CreateCredentialDefinitionArgs, CredentialDefinition, IdkError>(
        commandId = CreateCredentialDefinitionServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateCredentialDefinitionArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    CreateCredentialDefinitionServiceCommand {
    override val commandId: String get() = CreateCredentialDefinitionServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateCredentialDefinitionArgs

    override suspend fun doExecute(
        args: CreateCredentialDefinitionArgs,
        applyDuring: (CreateCredentialDefinitionArgs) -> CreateCredentialDefinitionArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        if (input.name.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential definition name must not be blank"))
        }
        val now = Clock.System.now()
        val definition =
            CredentialDefinition(
                id = Uuid.random(),
                tenantId = execution.tenantId,
                name = input.name,
                version = 1,
                lifecycleStatus = CredentialDefinitionLifecycleStatus.DRAFT,
                credentialTypeBindingRef = input.credentialTypeBindingRef,
                claims = input.claims,
                description = input.description,
                createdAt = now,
                updatedAt = now,
            )
        return store.save(definition)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetCredentialDefinitionServiceCommand>())
class GetCredentialDefinitionServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<GetCredentialDefinitionArgs, CredentialDefinition, IdkError>(
        commandId = GetCredentialDefinitionServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetCredentialDefinitionArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    GetCredentialDefinitionServiceCommand {
    override val commandId: String get() = GetCredentialDefinitionServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetCredentialDefinitionArgs

    override suspend fun doExecute(
        args: GetCredentialDefinitionArgs,
        applyDuring: (GetCredentialDefinitionArgs) -> GetCredentialDefinitionArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        return store.require(execution.tenantId, input.definitionId)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ListCredentialDefinitionsServiceCommand>())
class ListCredentialDefinitionsServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<ListCredentialDefinitionsArgs, ListCredentialDefinitionsResult, IdkError>(
        commandId = ListCredentialDefinitionsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListCredentialDefinitionsArgs>(),
        outputTypeToken = typeToken<ListCredentialDefinitionsResult>(),
    ),
    ListCredentialDefinitionsServiceCommand {
    override val commandId: String get() = ListCredentialDefinitionsServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ListCredentialDefinitionsArgs

    override suspend fun doExecute(
        args: ListCredentialDefinitionsArgs,
        applyDuring: (ListCredentialDefinitionsArgs) -> ListCredentialDefinitionsArgs,
    ): IdkResult<ListCredentialDefinitionsResult, IdkError> {
        val definitions = store.list(execution.tenantId).getOrElse { return Err(it) }
        return Ok(ListCredentialDefinitionsResult(definitions))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateCredentialDefinitionServiceCommand>())
class UpdateCredentialDefinitionServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<UpdateCredentialDefinitionArgs, CredentialDefinition, IdkError>(
        commandId = UpdateCredentialDefinitionServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateCredentialDefinitionArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    UpdateCredentialDefinitionServiceCommand {
    override val commandId: String get() = UpdateCredentialDefinitionServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is UpdateCredentialDefinitionArgs

    override suspend fun doExecute(
        args: UpdateCredentialDefinitionArgs,
        applyDuring: (UpdateCredentialDefinitionArgs) -> UpdateCredentialDefinitionArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        val newName = input.name
        if (newName != null && newName.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Credential definition name must not be blank"))
        }
        // Read-modify-write is not atomic across the store boundary: two concurrent mutations can
        // read the same row and the later save wins, losing the earlier update. A durable overlay
        // must use optimistic locking (version-check CAS on save) to prevent lost updates. The other
        // mutation commands below share this caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        val updated =
            existing.copy(
                name = input.name ?: existing.name,
                description = input.description ?: existing.description,
                updatedAt = Clock.System.now(),
            )
        return store.save(updated)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeleteCredentialDefinitionServiceCommand>())
class DeleteCredentialDefinitionServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<DeleteCredentialDefinitionArgs, DeleteCredentialDefinitionResult, IdkError>(
        commandId = DeleteCredentialDefinitionServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<DeleteCredentialDefinitionArgs>(),
        outputTypeToken = typeToken<DeleteCredentialDefinitionResult>(),
    ),
    DeleteCredentialDefinitionServiceCommand {
    override val commandId: String get() = DeleteCredentialDefinitionServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is DeleteCredentialDefinitionArgs

    override suspend fun doExecute(
        args: DeleteCredentialDefinitionArgs,
        applyDuring: (DeleteCredentialDefinitionArgs) -> DeleteCredentialDefinitionArgs,
    ): IdkResult<DeleteCredentialDefinitionResult, IdkError> {
        val input = applyDuring(args)
        val deleted = store.delete(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        return Ok(DeleteCredentialDefinitionResult(deleted))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SetCredentialDefinitionClaimsServiceCommand>())
class SetCredentialDefinitionClaimsServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<SetCredentialDefinitionClaimsArgs, CredentialDefinition, IdkError>(
        commandId = SetCredentialDefinitionClaimsServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SetCredentialDefinitionClaimsArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    SetCredentialDefinitionClaimsServiceCommand {
    override val commandId: String get() = SetCredentialDefinitionClaimsServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SetCredentialDefinitionClaimsArgs

    override suspend fun doExecute(
        args: SetCredentialDefinitionClaimsArgs,
        applyDuring: (SetCredentialDefinitionClaimsArgs) -> SetCredentialDefinitionClaimsArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        // Non-atomic read-modify-write; see UpdateCredentialDefinitionServiceCommandImpl for the CAS caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        val updated =
            runCatching {
                existing.copy(claims = input.claims, updatedAt = Clock.System.now())
            }.getOrElse {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = it.message ?: "Invalid claims list"))
            }
        return store.save(updated)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AddCredentialDefinitionClaimServiceCommand>())
class AddCredentialDefinitionClaimServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<AddCredentialDefinitionClaimArgs, CredentialDefinition, IdkError>(
        commandId = AddCredentialDefinitionClaimServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<AddCredentialDefinitionClaimArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    AddCredentialDefinitionClaimServiceCommand {
    override val commandId: String get() = AddCredentialDefinitionClaimServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is AddCredentialDefinitionClaimArgs

    override suspend fun doExecute(
        args: AddCredentialDefinitionClaimArgs,
        applyDuring: (AddCredentialDefinitionClaimArgs) -> AddCredentialDefinitionClaimArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        // Non-atomic read-modify-write; see UpdateCredentialDefinitionServiceCommandImpl for the CAS caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        if (existing.claims.any { it.path == input.claim.path }) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "A claim already exists at path '${input.claim.path.value}'",
                ),
            )
        }
        val updated = existing.copy(claims = existing.claims + input.claim, updatedAt = Clock.System.now())
        return store.save(updated)
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UpdateCredentialDefinitionClaimServiceCommand>())
class UpdateCredentialDefinitionClaimServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<UpdateCredentialDefinitionClaimArgs, CredentialDefinition, IdkError>(
        commandId = UpdateCredentialDefinitionClaimServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateCredentialDefinitionClaimArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    UpdateCredentialDefinitionClaimServiceCommand {
    override val commandId: String get() = UpdateCredentialDefinitionClaimServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is UpdateCredentialDefinitionClaimArgs

    override suspend fun doExecute(
        args: UpdateCredentialDefinitionClaimArgs,
        applyDuring: (UpdateCredentialDefinitionClaimArgs) -> UpdateCredentialDefinitionClaimArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        if (input.claim.path.value != input.claimPath) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message =
                        "The replacement claim path '${input.claim.path.value}' must equal the " +
                            "target path '${input.claimPath}'",
                ),
            )
        }
        // Non-atomic read-modify-write; see UpdateCredentialDefinitionServiceCommandImpl for the CAS caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        if (existing.claims.none { it.path.value == input.claimPath }) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "No claim at path '${input.claimPath}'"))
        }
        val newClaims = existing.claims.map { if (it.path.value == input.claimPath) input.claim else it }
        return store.save(existing.copy(claims = newClaims, updatedAt = Clock.System.now()))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RemoveCredentialDefinitionClaimServiceCommand>())
class RemoveCredentialDefinitionClaimServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<RemoveCredentialDefinitionClaimArgs, CredentialDefinition, IdkError>(
        commandId = RemoveCredentialDefinitionClaimServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RemoveCredentialDefinitionClaimArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    RemoveCredentialDefinitionClaimServiceCommand {
    override val commandId: String get() = RemoveCredentialDefinitionClaimServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RemoveCredentialDefinitionClaimArgs

    override suspend fun doExecute(
        args: RemoveCredentialDefinitionClaimArgs,
        applyDuring: (RemoveCredentialDefinitionClaimArgs) -> RemoveCredentialDefinitionClaimArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        // Non-atomic read-modify-write; see UpdateCredentialDefinitionServiceCommandImpl for the CAS caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        if (existing.claims.none { it.path.value == input.claimPath }) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "No claim at path '${input.claimPath}'"))
        }
        val newClaims = existing.claims.filterNot { it.path.value == input.claimPath }
        return store.save(existing.copy(claims = newClaims, updatedAt = Clock.System.now()))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SnapshotCredentialDefinitionVersionServiceCommand>())
class SnapshotCredentialDefinitionVersionServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<SnapshotCredentialDefinitionVersionArgs, CredentialDefinition, IdkError>(
        commandId = SnapshotCredentialDefinitionVersionServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SnapshotCredentialDefinitionVersionArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    SnapshotCredentialDefinitionVersionServiceCommand {
    override val commandId: String get() = SnapshotCredentialDefinitionVersionServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SnapshotCredentialDefinitionVersionArgs

    override suspend fun doExecute(
        args: SnapshotCredentialDefinitionVersionArgs,
        applyDuring: (SnapshotCredentialDefinitionVersionArgs) -> SnapshotCredentialDefinitionVersionArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        // Non-atomic read-modify-write; see UpdateCredentialDefinitionServiceCommandImpl for the CAS caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        return store.save(existing.copy(version = existing.version + 1, updatedAt = Clock.System.now()))
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SetCredentialDefinitionLifecycleServiceCommand>())
class SetCredentialDefinitionLifecycleServiceCommandImpl(
    execution: SessionExecution,
    private val store: CredentialDefinitionStore,
) : TypedServiceCommandAdapter<SetCredentialDefinitionLifecycleArgs, CredentialDefinition, IdkError>(
        commandId = SetCredentialDefinitionLifecycleServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SetCredentialDefinitionLifecycleArgs>(),
        outputTypeToken = typeToken<CredentialDefinition>(),
    ),
    SetCredentialDefinitionLifecycleServiceCommand {
    override val commandId: String get() = SetCredentialDefinitionLifecycleServiceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SetCredentialDefinitionLifecycleArgs

    override suspend fun doExecute(
        args: SetCredentialDefinitionLifecycleArgs,
        applyDuring: (SetCredentialDefinitionLifecycleArgs) -> SetCredentialDefinitionLifecycleArgs,
    ): IdkResult<CredentialDefinition, IdkError> {
        val input = applyDuring(args)
        // Non-atomic read-modify-write; see UpdateCredentialDefinitionServiceCommandImpl for the CAS caveat.
        val existing = store.require(execution.tenantId, input.definitionId).getOrElse { return Err(it) }
        if (existing.lifecycleStatus == input.lifecycleStatus) {
            return Ok(existing)
        }
        return store.save(existing.copy(lifecycleStatus = input.lifecycleStatus, updatedAt = Clock.System.now()))
    }
}
