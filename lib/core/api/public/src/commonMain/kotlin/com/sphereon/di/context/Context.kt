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

package com.sphereon.di.context

import com.sphereon.di.session.SessionContext
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContext", exact = true)
interface UserContext :
    TenantAware,
    PrincipalAware {
    val id: String
    val secureDetails: SecuredTenantContextDetails?

    // Constants for special context types
    companion object {
        const val BACKGROUND_SERVICE = IdentityConstants.ANONYMOUS_ID + ":" + IdentityConstants.ANONYMOUS_ID + ":background-service"

        const val ANONYMOUS = IdentityConstants.ANONYMOUS_ID + ":" + IdentityConstants.ANONYMOUS_ID + ":default"
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("SecuredTenantContextDetails", exact = true)
interface SecuredTenantContextDetails {
    val validFrom: Long // TODO: Does this make sense at all? We are using this in a zero-trust env and why would we trust this info without a sig?
    val validUntil: Long // TODO: Does this make sense at all? We are using this in a zero-trust env and why would we trust this info without a sig?
    val iss: String
    val jwt: String
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("UserSecuredContext", exact = true)
interface UserSecuredContext : UserContext {
    override val secureDetails: SecuredTenantContextDetails
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ContextAware", exact = true)
interface ContextAware {
    val context: UserContext
}

/**
 * Shared constants for anonymous identity values.
 *
 * Use these instead of hardcoded "<anonymous>" strings in new code.
 */
object IdentityConstants {
    const val ANONYMOUS_ID = "<anonymous>"
    const val ANONYMOUS_TENANT_ID = ANONYMOUS_ID
    const val ANONYMOUS_PRINCIPAL_ID = ANONYMOUS_ID
    const val ANONYMOUS_SESSION_ID = ANONYMOUS_ID
}

object AnonymousPrincipal : PrincipalAware {
    override val principal = IdentityConstants.ANONYMOUS_ID

    override fun toString(): String = "AnonymousPrincipal(principal='$principal')"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is PrincipalAware) {
            return false
        }
        if (principal != other.principal) {
            return false
        }
        return true
    }

    override fun hashCode(): Int = principal.hashCode()
}

object AnonymousContext : UserContext {
    override val id: String = "${IdentityConstants.ANONYMOUS_ID}:${IdentityConstants.ANONYMOUS_ID}:default"
    override val tenant: TenantContextData =
        object : TenantContextData {
            override val tenantId = IdentityConstants.ANONYMOUS_TENANT_ID
        }
    override val principal: Any? = IdentityConstants.ANONYMOUS_PRINCIPAL_ID
    override val secureDetails: SecuredTenantContextDetails?
        get() = null

    override fun toString(): String = "AnonymousUserContext(id='$id', tenant=$tenant, principal=$principal"
}

fun createAnonymousSessionContext(
    sessionId: String,
    correlationId: String,
): SessionContext =
    object : SessionContext {
        override val context: UserContext = AnonymousContext
        override val sessionId: String = sessionId
        override val correlationId: String = correlationId

        override fun toString(): String = "AnonymousSessionContext(sessionId='$sessionId', userContext=$context, correlationId='$correlationId')"

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
            var result = sessionId.hashCode()
            result = 31 * result + context.hashCode()
            return result
        }
    }

/**
 * Only use this function from a user context scope. From a session scope a full Session Context is always injectable
 *
 * Mainly useful for logging, which depends on a SessionContext in appScope (NoOpSessionContext), userScope (this function) and session scope (injection)
 */
fun UserContext.toSessionContext(
    sessionId: String = "_from_user_context",
    correlationId: String,
): SessionContext {
    val capturedCorrelationId = correlationId
    return object : SessionContext {
        override val context: UserContext = this@toSessionContext
        override val sessionId: String = sessionId
        override val correlationId: String = capturedCorrelationId

        override fun toString(): String = "UserToSessionContext(sessionId='$sessionId', userContext=$context, correlationId='$correlationId')"

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
            var result = sessionId.hashCode()
            result = 31 * result + context.hashCode()
            return result
        }
    }
}

fun UserContextInstance.toSessionContext(
    sessionId: String = "_from_user_context",
    correlationId: String,
) = context.toSessionContext(sessionId, correlationId)

object NoOpSessionContext : SessionContext by createAnonymousSessionContext(
    sessionId = IdentityConstants.ANONYMOUS_SESSION_ID,
    correlationId = IdentityConstants.ANONYMOUS_ID,
) {
    override fun toString(): String = "AnonymousSessionContext(sessionId='$sessionId', context=$context, correlationId='$correlationId')"
}
