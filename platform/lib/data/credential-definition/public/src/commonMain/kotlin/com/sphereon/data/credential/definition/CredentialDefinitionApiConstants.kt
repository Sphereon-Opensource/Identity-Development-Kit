package com.sphereon.data.credential.definition

/**
 * Paths, command-id constants and OpenAPI tags for the free-form (lightweight) credential-definition
 * authoring surface.
 *
 * This is the role-neutral, open-core (IDK) tier: pure claim data with no attribute-profile link, so
 * it is UNGATED (no license feature is required to author it). The profile-bound (gated) tier lives
 * in the EDK layer and carries its own constants and license gate.
 */
object CredentialDefinitionApiConstants {
    /** Adapter mount; every free-form credential-definition endpoint is rooted here. */
    const val BASE_PATH = "/api/v1"

    /** REST endpoint paths, relative to [BASE_PATH]. No hyphens in path segments. */
    object Paths {
        const val DEFINITIONS = "/credential/definitions/freeform"
        const val DEFINITION_BY_ID = "/credential/definitions/freeform/{definitionId}"
        const val DEFINITION_VERSION = "/credential/definitions/freeform/{definitionId}/version"
        const val DEFINITION_LIFECYCLE = "/credential/definitions/freeform/{definitionId}/lifecycle"
        const val DEFINITION_CLAIMS = "/credential/definitions/freeform/{definitionId}/claims"
        const val DEFINITION_CLAIM_BY_PATH = "/credential/definitions/freeform/{definitionId}/claims/{claimPath}"
    }

    object Tags {
        const val CREDENTIAL_DEFINITION = "Free-form Credential Definition"
    }

    /**
     * Three-part command ids (`module.service.command`), lowercase with hyphens between words. The
     * `credential-definition.freeform` `module.service` segment is reused across every free-form
     * command; disambiguation is by the command name (third segment) only.
     *
     * A service command and the HTTP endpoint command that fronts it are two distinct commands and
     * therefore carry two distinct ids. They share the `credential-definition.freeform`
     * `module.service` segment but the HTTP command's command-name carries an `-endpoint` suffix
     * (e.g. service `credential-definition.freeform.create` vs HTTP
     * `credential-definition.freeform.create-endpoint`). The suffix keeps each id 3-part (no fourth
     * `.http.` segment) and globally unique.
     */
    object CommandIds {
        const val SERVICE_CREATE = "credential-definition.freeform.create"
        const val SERVICE_GET = "credential-definition.freeform.get"
        const val SERVICE_LIST = "credential-definition.freeform.list"
        const val SERVICE_UPDATE = "credential-definition.freeform.update"
        const val SERVICE_DELETE = "credential-definition.freeform.delete"
        const val SERVICE_SET_CLAIMS = "credential-definition.freeform.set-claims"
        const val SERVICE_ADD_CLAIM = "credential-definition.freeform.add-claim"
        const val SERVICE_UPDATE_CLAIM = "credential-definition.freeform.update-claim"
        const val SERVICE_REMOVE_CLAIM = "credential-definition.freeform.remove-claim"
        const val SERVICE_SNAPSHOT_VERSION = "credential-definition.freeform.snapshot-version"
        const val SERVICE_SET_LIFECYCLE = "credential-definition.freeform.set-lifecycle"

        const val HTTP_CREATE = "credential-definition.freeform.create-endpoint"
        const val HTTP_GET = "credential-definition.freeform.get-endpoint"
        const val HTTP_LIST = "credential-definition.freeform.list-endpoint"
        const val HTTP_UPDATE = "credential-definition.freeform.update-endpoint"
        const val HTTP_DELETE = "credential-definition.freeform.delete-endpoint"
        const val HTTP_SET_CLAIMS = "credential-definition.freeform.set-claims-endpoint"
        const val HTTP_ADD_CLAIM = "credential-definition.freeform.add-claim-endpoint"
        const val HTTP_UPDATE_CLAIM = "credential-definition.freeform.update-claim-endpoint"
        const val HTTP_REMOVE_CLAIM = "credential-definition.freeform.remove-claim-endpoint"
        const val HTTP_SNAPSHOT_VERSION = "credential-definition.freeform.snapshot-version-endpoint"
        const val HTTP_SET_LIFECYCLE = "credential-definition.freeform.set-lifecycle-endpoint"
    }
}
