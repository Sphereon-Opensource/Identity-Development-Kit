/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.lifecycle

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciCompletenessLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciCompletenessLifecycleResult
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciIssuanceLifecycleHook
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciOfferLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciOfferLifecycleResult
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciPhaseLifecycleArgs
import com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciPhaseLifecycleResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuanceLifecycleHook>())
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuanceLifecycleHook?>())
class NoOpOid4vciIssuanceLifecycleHook : Oid4vciIssuanceLifecycleHook {
    override suspend fun initializeOffer(args: Oid4vciOfferLifecycleArgs): IdkResult<Oid4vciOfferLifecycleResult, IdkError> = Ok(Oid4vciOfferLifecycleResult())

    override suspend fun recordPhase(args: Oid4vciPhaseLifecycleArgs): IdkResult<Oid4vciPhaseLifecycleResult, IdkError> = Ok(Oid4vciPhaseLifecycleResult())

    override suspend fun evaluateCompleteness(args: Oid4vciCompletenessLifecycleArgs): IdkResult<Oid4vciCompletenessLifecycleResult, IdkError> = Ok(Oid4vciCompletenessLifecycleResult())
}
