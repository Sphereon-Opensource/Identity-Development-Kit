/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContribution
import com.sphereon.openid.oid4vci.issuer.attribute.CredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.impl.attribute.NoOpCredentialAttributeContributor
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Test-only authoritative issuance projection.
 *
 * It replaces the IDK no-op contributor but remains a no-op unless a test explicitly installs a
 * contribution for a credential configuration. This lets protocol tests prove that semantic VCDM
 * credential and subject identities survive the complete OID4VCI pipeline without deriving either
 * identity from the access-token subject, proof key, issuer, or holder.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<CredentialAttributeContributor>(),
    replaces = [NoOpCredentialAttributeContributor::class],
)
class WalletE2ETestCredentialAttributeContributor : CredentialAttributeContributor {
    override suspend fun contribute(
        session: IssuanceSession,
        tokenContext: ValidatedTokenContext,
        credentialConfigurationId: String,
    ): IdkResult<CredentialAttributeContribution, IdkError> =
        Ok(configuredContributions[credentialConfigurationId] ?: CredentialAttributeContribution(emptyMap()))

    companion object {
        @Volatile
        private var configuredContributions: Map<String, CredentialAttributeContribution> = emptyMap()

        fun enable(contributions: Map<String, CredentialAttributeContribution>) {
            configuredContributions = contributions.toMap()
        }

        fun disable() {
            configuredContributions = emptyMap()
        }
    }
}
