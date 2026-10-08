/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.extension

import com.sphereon.oauth2.server.authorization.extension.AuthorizeRequestExtension
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Declares the [AuthorizeRequestExtension] multibinding as allow-empty so the IDK
 * default (zero extensions) compiles. EDK modules contributing extensions via
 * `@ContributesIntoSet` populate the set; pure-IDK deployments leave it empty and
 * the AS verifier runs no additional checks beyond the per-flag legacy ones.
 */
@ContributesTo(AppScope::class)
interface AuthorizeExtensionMultibindings {
    @Multibinds(allowEmpty = true)
    fun authorizeRequestExtensions(): Set<AuthorizeRequestExtension>
}
