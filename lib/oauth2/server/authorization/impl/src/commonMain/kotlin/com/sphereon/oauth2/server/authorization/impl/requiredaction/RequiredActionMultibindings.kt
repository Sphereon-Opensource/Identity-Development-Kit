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
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionEvaluator
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Declares the [RequiredActionEvaluator] multibinding as allow-empty so the IDK
 * default (zero evaluators = no required actions) compiles. EDK modules
 * contributing evaluators via `@ContributesIntoSet(SessionScope::class, ...)`
 * populate the set; pure-IDK deployments leave it empty and the AS skips the gate.
 *
 * SessionScope (not AppScope) so evaluators can inject session-scoped services
 * (`TenantConfigService`, `SessionExecution`) directly and read tenant config via
 * the natural parent-cascade rather than a manual `tenants.<tenantId>.<key>` prefix.
 */
@ContributesTo(SessionScope::class)
interface RequiredActionMultibindings {
    @Multibinds(allowEmpty = true)
    fun requiredActionEvaluators(): Set<RequiredActionEvaluator>
}
