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
 *
 */

package com.sphereon.core.defaults.session

import com.sphereon.core.defaults.context.UserContextImpl
import com.sphereon.di.context.SecuredTenantContextDetails
import com.sphereon.di.context.UserContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default [SessionContext] implementation.
 *
 * [UserContext] is inherited from UserScope via Metro's graph extension — the
 * `UserContextGraph.Factory` provides it as `@Provides userContext: UserContext`,
 * and `SessionGraph` extends UserScope, making it injectable here.
 *
 * [sessionId], [correlationId] and the optional per-session [SecuredTenantContextDetails]
 * are provided by `SessionGraph.Factory.createSessionGraph(...)`. When the session
 * carries validated transport credentials (a bearer JWT the REST layer validated),
 * the exposed [context] is the UserScope context enriched with those details — the
 * cached UserScope instance itself stays credential-free because it is shared across
 * requests for the same tenant+principal.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextImpl", exact = true)
class SessionContextImpl(
    context: UserContext,
    @Named("sessionId") override val sessionId: String,
    @Named("correlationId") override val correlationId: String,
    secureDetails: SecuredTenantContextDetails? = null,
) : SessionContext {
    override val context: UserContext =
        if (secureDetails == null || context.secureDetails != null) {
            context
        } else {
            UserContextImpl(
                tenant = context.tenant,
                principal = context.principal,
                secureDetails = secureDetails,
                id = context.id,
            )
        }

    override fun toString(): String = "SessionContext(sessionId='$sessionId', context=$context, correlationId='$correlationId')"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is SessionContext) {
            return false
        }
        if (sessionId != other.sessionId) {
            return false
        }
        if (context != other.context) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + sessionId.hashCode()
        return result
    }
}
