/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.Serializable

/** Neutral, version-pinned local process references. Input is a claim until admitted by the host. */
@Serializable
data class WalletInteractionProcessBinding(
    val tenantId: String,
    val modelId: String,
    val caseType: String,
    val caseId: String,
    val executionId: String,
    val policyBindingRef: String,
    val policyRevision: Long,
    val action: String,
    val role: String,
    /** Exact semantic model and package version of the governed policy binding. */
    val policyModelId: String = modelId,
    val policyVersion: String = "",
) {
    init {
        require(listOf(tenantId, modelId, caseType, caseId, executionId, policyBindingRef, action, role).all { it.isNotBlank() })
        require(policyRevision > 0)
    }
}

/**
 * Product-contributed launch admission. Implementations derive mandatory business binding from
 * server policy and verify the actual caller/wallet against persisted local process authority.
 * A null successful result explicitly permits an ordinary, unbound interaction. There is no
 * implicit local/ordinary authority: absent or ambiguous contributions must reject admission.
 */
fun interface WalletInteractionLaunchAuthority {
    suspend fun authorize(input: WalletInteractionInput): IdkResult<WalletInteractionProcessBinding?, IdkError>
}
