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

package com.sphereon.di.session

import com.sphereon.di.context.ContextAware
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.PrincipalType
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionContext", exact = true)
interface SessionContext : ContextAware {
    val sessionId: String

    /**
     * Optional business / trace key for the operation this session belongs
     * to. Callers may set it explicitly (HTTP `X-Correlation-Id`, parent
     * execution, durable-row replay, caller-chosen identifier); when not
     * set it defaults to [sessionId] so every session always has a
     * non-null correlation key. Constant for the session's lifetime;
     * nested command invocations within the session inherit it
     * automatically, and every call-site that receives a correlationId
     * should propagate it onto downstream sessions.
     */
    val correlationId: String
        get() = sessionId

    fun isAnonymous(): Boolean =
        context.principalType == PrincipalType.ANONYMOUS ||
        (
            this.sessionId == IdentityConstants.ANONYMOUS_SESSION_ID && this.context.tenant.tenantId == IdentityConstants.ANONYMOUS_TENANT_ID &&
                this.context.principal == IdentityConstants.ANONYMOUS_PRINCIPAL_ID
        ) ||
            this == NoOpSessionContext
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ISessionContextAware", exact = true)
interface ISessionContextAware {
    val sessionContext: SessionContext
}
