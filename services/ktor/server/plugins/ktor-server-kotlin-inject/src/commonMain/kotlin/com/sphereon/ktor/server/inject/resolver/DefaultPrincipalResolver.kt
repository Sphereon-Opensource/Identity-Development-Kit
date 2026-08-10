/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.ktor.server.inject.resolver

import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.PrincipalInput
import com.sphereon.ktor.server.inject.ValidatedJwtClaimsAttribute
import io.ktor.server.application.ApplicationCall

/**
 * Resolves authenticated principals only from the platform's validated JWT
 * authentication. Requests without a validated JWT remain explicitly
 * anonymous so public protocol routes can create request-scoped context; no
 * request header can promote that anonymous context to an authenticated user.
 */
class DefaultPrincipalResolver : PrincipalResolver {
    override fun resolve(call: ApplicationCall): PrincipalInput =
        call.attributes.getOrNull(ValidatedJwtClaimsAttribute)
            ?.claimsInput
            ?: DefaultPrincipalInputString(IdentityConstants.ANONYMOUS_PRINCIPAL_ID)
}
