package com.sphereon.data.credential.definition

import kotlinx.serialization.Serializable

/**
 * Draft vs published state of a [CredentialDefinition]. A DRAFT is freely editable; a PUBLISHED
 * definition is frozen as a snapshot at its [CredentialDefinition.version].
 */
@Serializable
enum class CredentialDefinitionLifecycleStatus {
    DRAFT,
    PUBLISHED,
}
