/*
 * © 2025 Sphereon International B.V.
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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextManager
import dev.zacsweers.metro.Named
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContextImpl", exact = true)
class SessionContextImpl(
    // TODO: We really should inject the UserContext, but since that is an argument to the Factory and likely a bug in kotlin-inject(-anvil) when injecting that from another scope we have to do it like this
    userContextManager: UserContextManager,
    override val context: UserContext = userContextManager.getActive().context,
    @Named("sessionId") override val sessionId: String = "session-${context.tenant.tenantId}",
) : SessionContext {
    override fun toString(): String {
        return "SessionContext(sessionId='$sessionId', context=$context)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionContext) return false
        if (sessionId != other.sessionId) return false
        if (context != other.context) return false
        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + sessionId.hashCode()
        return result
    }


}
