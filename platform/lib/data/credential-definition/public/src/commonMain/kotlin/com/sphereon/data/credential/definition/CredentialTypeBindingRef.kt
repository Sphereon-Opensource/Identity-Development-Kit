package com.sphereon.data.credential.definition

import com.sphereon.data.store.credential.type.binding.CredentialTypeBinding
import kotlinx.serialization.Serializable

/**
 * A reference to a [CredentialTypeBinding] by its stable id. A credential definition references the
 * binding rather than embedding it, so the role-neutral binding registry stays the single source of
 * truth for credential wire-format identity (format + vct/doctype).
 *
 * @property bindingId the referenced [CredentialTypeBinding.id].
 */
@Serializable
data class CredentialTypeBindingRef(
    val bindingId: String
) {
    init {
        require(bindingId.isNotBlank()) { "A CredentialTypeBindingRef.bindingId must not be blank" }
    }
}
