/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Non-JVM targets do not get an implicit platform DNS implementation. A
 * target-specific secure transport must replace this binding before use.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<TrustListHttpTransport>())
internal class FailClosedTrustListHttpTransport : TrustListHttpTransport {
    override suspend fun execute(
        uri: String,
        timeoutMs: Long,
    ): TrustListHttpResponse {
        throw TrustListResolutionException(
            "Trust-list transport is unavailable",
            reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_TARGET_REJECTED,
        )
    }
}
