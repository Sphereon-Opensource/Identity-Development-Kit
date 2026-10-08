@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.credential.definition

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The free-form (lightweight) credential-definition tier of the enterprise semantic model. This is
 * the role-neutral, open-core (IDK) mode: it carries pure claim data with no link to an attribute
 * profile, so it is ungated and usable by both issuer and verifier flows.
 *
 * A profile-bound (semantic, gated) definition lives in the EDK layer and resolves DOWN to this same
 * free-form representation, so every downstream converter consumes one claim shape regardless of how
 * the definition was authored.
 *
 * @property id stable definition identifier.
 * @property tenantId the tenant that owns this definition.
 * @property name human-readable definition name.
 * @property version monotonic snapshot version, starting at 1.
 * @property lifecycleStatus draft vs published state.
 * @property credentialTypeBindingRef the credential wire-format identity this definition maps to.
 * @property claims the role-neutral claims this definition exposes; each claim path is unique.
 * @property description optional free-form description.
 * @property createdAt creation timestamp.
 * @property updatedAt last-modification timestamp.
 */
@Serializable
data class CredentialDefinition(
    val id: Uuid,
    val tenantId: String,
    val name: String,
    val version: Long,
    val lifecycleStatus: CredentialDefinitionLifecycleStatus,
    val credentialTypeBindingRef: CredentialTypeBindingRef,
    val claims: List<CredentialClaim>,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "A CredentialDefinition.name must not be blank" }
        require(tenantId.isNotBlank()) { "A CredentialDefinition.tenantId must not be blank" }
        require(version >= 1) {
            "A CredentialDefinition.version must be >= 1 (versions are monotonic, starting at 1); " +
                "got: $version"
        }

        val paths = claims.map { it.path.value }
        require(paths.size == paths.toSet().size) {
            "CredentialClaim paths must be unique within a definition; duplicates among: " +
                paths.joinToString()
        }
    }
}
