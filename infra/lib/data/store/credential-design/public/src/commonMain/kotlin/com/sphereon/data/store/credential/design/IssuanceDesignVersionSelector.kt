/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.credential.design

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The design, and the version of it, that issuance of one credential configuration uses. */
data class IssuanceDesignVersion(
    val designId: Uuid,
    val version: Int,
)

/**
 * Selects the credential design version that issuance uses for a credential configuration.
 *
 * A design store that keeps a single, unversioned design binds none, and issuance resolves the design bound to the
 * configuration as stored. A store that versions designs and publishes one of them returns the version issuance must
 * use whoever the caller is, `null` when no design is bound to the configuration, and an error when the bound design has
 * no version issuance may use, so a credential is never issued from an unpublished design.
 */
fun interface IssuanceDesignVersionSelector {
    suspend fun issuanceVersion(tenantId: String, credentialConfigurationId: String): IdkResult<IssuanceDesignVersion?, IdkError>
}

/** Without a versioning design store, issuance resolves the design as stored. */
@ContributesTo(SessionScope::class)
interface IssuanceDesignVersionSelectorOptionalProvider {
    @OptionalBinding
    val optionalIssuanceDesignVersionSelector: IssuanceDesignVersionSelector? get() = null
}
