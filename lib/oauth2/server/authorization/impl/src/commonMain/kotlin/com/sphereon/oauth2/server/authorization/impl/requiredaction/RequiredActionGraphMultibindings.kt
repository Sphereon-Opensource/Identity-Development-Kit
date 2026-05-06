/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.requiredaction

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionGraphProvider
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Declares the [RequiredActionGraphProvider] multibinding as allow-empty so the IDK
 * default (zero providers = no actions can be fulfilled by the orchestrator) compiles.
 * EDK modules contributing providers via `@ContributesIntoSet(SessionScope::class, ...)`
 * populate the set; pure-IDK deployments without any IDV method modules leave it
 * empty and the orchestrator falls back to `interaction_required` JSON.
 *
 * SessionScope mirrors the evaluator multibinding: providers can read tenant config
 * directly via injected `TenantConfigService` to swap their target `methodId`
 * per tenant.
 */
@ContributesTo(SessionScope::class)
interface RequiredActionGraphMultibindings {
    @Multibinds(allowEmpty = true)
    fun requiredActionGraphProviders(): Set<RequiredActionGraphProvider>
}
