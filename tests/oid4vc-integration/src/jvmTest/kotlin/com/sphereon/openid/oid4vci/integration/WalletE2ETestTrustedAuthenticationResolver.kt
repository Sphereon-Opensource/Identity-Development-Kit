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
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.spi.VerifierTrustedAuthenticationRequest
import com.sphereon.openid.oid4vp.verifier.spi.VerifierTrustedAuthenticationResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Test-only authoritative verifier configuration seam; disabled unless a test opts in. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerifierTrustedAuthenticationResolver>())
class WalletE2ETestTrustedAuthenticationResolver : VerifierTrustedAuthenticationResolver {
    override suspend fun resolveTrustedAuthentications(
        request: VerifierTrustedAuthenticationRequest,
    ): IdkResult<List<TrustedAuthenticationResolution>, IdkError> = Ok(configuredSources)

    companion object {
        @Volatile
        private var configuredSources: List<TrustedAuthenticationResolution> = emptyList()

        fun enable(sources: List<TrustedAuthenticationResolution>) {
            configuredSources = sources.toList()
        }

        fun disable() {
            configuredSources = emptyList()
        }
    }
}
