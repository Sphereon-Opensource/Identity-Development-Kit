@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.data.credential.definition.CredentialClaim
import com.sphereon.data.credential.definition.CredentialDefinition
import com.sphereon.data.credential.definition.CredentialDefinitionApiConstants.CommandIds
import com.sphereon.data.credential.definition.CredentialDefinitionLifecycleStatus
import com.sphereon.data.credential.definition.CredentialTypeBindingRef
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Create a new draft free-form credential definition for the calling tenant.
 *
 * @property name human-readable definition name.
 * @property credentialTypeBindingRef the credential wire-format identity this definition maps to.
 * @property description optional free-form description.
 * @property claims the definition's initial claim list; defaults to empty.
 */
@Serializable
data class CreateCredentialDefinitionArgs(
    val name: String,
    val credentialTypeBindingRef: CredentialTypeBindingRef,
    val description: String? = null,
    val claims: List<CredentialClaim> = emptyList(),
)

interface CreateCredentialDefinitionServiceCommand : ServiceCommand<CreateCredentialDefinitionArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_CREATE
    }
}

/**
 * Fetch a single free-form definition by id within the calling tenant.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 */
@Serializable
data class GetCredentialDefinitionArgs(
    val definitionId: Uuid,
)

interface GetCredentialDefinitionServiceCommand : ServiceCommand<GetCredentialDefinitionArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_GET
    }
}

/** List every free-form definition in the calling tenant. */
@Serializable
class ListCredentialDefinitionsArgs

@Serializable
data class ListCredentialDefinitionsResult(
    val definitions: List<CredentialDefinition>,
)

interface ListCredentialDefinitionsServiceCommand : ServiceCommand<ListCredentialDefinitionsArgs, ListCredentialDefinitionsResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_LIST
    }
}

/**
 * Update the mutable metadata of a definition: name and description. The claims are mutated via the
 * dedicated claim commands; lifecycle and version have their own commands.
 *
 * Each field is optional; a null leaves the corresponding value unchanged. In particular a null
 * [description] leaves the existing description unchanged; clearing a previously-set description back
 * to null is not supported via this PATCH.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 */
@Serializable
data class UpdateCredentialDefinitionArgs(
    val definitionId: Uuid,
    val name: String? = null,
    val description: String? = null,
)

interface UpdateCredentialDefinitionServiceCommand : ServiceCommand<UpdateCredentialDefinitionArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_UPDATE
    }
}

/**
 * Delete a definition by id within the calling tenant.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 */
@Serializable
data class DeleteCredentialDefinitionArgs(
    val definitionId: Uuid,
)

@Serializable
data class DeleteCredentialDefinitionResult(
    val deleted: Boolean,
)

interface DeleteCredentialDefinitionServiceCommand : ServiceCommand<DeleteCredentialDefinitionArgs, DeleteCredentialDefinitionResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_DELETE
    }
}

/**
 * Replace the entire claims list of a definition. Claim paths must be unique (enforced by
 * [CredentialDefinition]).
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 * @property claims the replacement claims list.
 */
@Serializable
data class SetCredentialDefinitionClaimsArgs(
    val definitionId: Uuid,
    val claims: List<CredentialClaim>,
)

interface SetCredentialDefinitionClaimsServiceCommand : ServiceCommand<SetCredentialDefinitionClaimsArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_SET_CLAIMS
    }
}

/**
 * Add a single claim to a definition. The claim's path must not already exist in the definition.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 * @property claim the claim to add.
 */
@Serializable
data class AddCredentialDefinitionClaimArgs(
    val definitionId: Uuid,
    val claim: CredentialClaim,
)

interface AddCredentialDefinitionClaimServiceCommand : ServiceCommand<AddCredentialDefinitionClaimArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_ADD_CLAIM
    }
}

/**
 * Replace the claim at [claimPath] with [claim]. The replacement's path must equal [claimPath] so
 * the claim identity is stable.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 * @property claimPath the path of the claim to replace; carried by the URL on the REST surface.
 * @property claim the replacement claim.
 */
@Serializable
data class UpdateCredentialDefinitionClaimArgs(
    val definitionId: Uuid,
    val claimPath: String,
    val claim: CredentialClaim,
)

interface UpdateCredentialDefinitionClaimServiceCommand : ServiceCommand<UpdateCredentialDefinitionClaimArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_UPDATE_CLAIM
    }
}

/**
 * Remove the claim at [claimPath] from a definition.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 * @property claimPath the path of the claim to remove; carried by the URL on the REST surface.
 */
@Serializable
data class RemoveCredentialDefinitionClaimArgs(
    val definitionId: Uuid,
    val claimPath: String,
)

interface RemoveCredentialDefinitionClaimServiceCommand : ServiceCommand<RemoveCredentialDefinitionClaimArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_REMOVE_CLAIM
    }
}

/**
 * Bump the definition's monotonic snapshot [CredentialDefinition.version] by one.
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 */
@Serializable
data class SnapshotCredentialDefinitionVersionArgs(
    val definitionId: Uuid,
)

interface SnapshotCredentialDefinitionVersionServiceCommand : ServiceCommand<SnapshotCredentialDefinitionVersionArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_SNAPSHOT_VERSION
    }
}

/**
 * Toggle the definition's two-state [CredentialDefinition.lifecycleStatus] (DRAFT <-> PUBLISHED).
 *
 * @property definitionId the definition identifier; carried by the URL on the REST surface.
 * @property lifecycleStatus the target lifecycle status.
 */
@Serializable
data class SetCredentialDefinitionLifecycleArgs(
    val definitionId: Uuid,
    val lifecycleStatus: CredentialDefinitionLifecycleStatus,
)

interface SetCredentialDefinitionLifecycleServiceCommand : ServiceCommand<SetCredentialDefinitionLifecycleArgs, CredentialDefinition, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = CommandIds.SERVICE_SET_LIFECYCLE
    }
}
