/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.auth.impl.service

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.resolution.command.ResolveIdentityCommand
import com.sphereon.identity.resolution.model.ResolveIdentityArgs
import com.sphereon.openid.oid4vp.auth.input.CreateUserInput
import com.sphereon.openid.oid4vp.auth.model.ResolvedUser
import com.sphereon.openid.oid4vp.auth.model.UserType
import com.sphereon.openid.oid4vp.auth.service.UserService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * UserService implementation that delegates user lookup to the IDK Identity Resolution module.
 *
 * This bridges wallet authentication flows into the Matching → Resolution pipeline:
 * when the auth bridge extracts a user identifier from verifiable credentials, this
 * service uses [ResolveIdentityCommand] to find the internal identity via HMAC-based
 * matching in the identity match store.
 *
 * For deployments using the identity Matching/Reconciliation modules, this replaces
 * the HTTP-based [UserServiceImpl]. Only one should be on the classpath — selection
 * is classpath-based (no `replaces` needed).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UserService>())
class IdentityResolutionUserServiceImpl(
    private val resolveIdentityCommand: ResolveIdentityCommand,
    private val execution: SessionExecution
) : UserService {

    override suspend fun lookupUser(identifier: String): IdkResult<ResolvedUser?, IdkError> {
        val tenantId = execution.sessionContext.context.tenant.tenantId

        val result = resolveIdentityCommand.execute(
            ResolveIdentityArgs(identifier = identifier, tenantId = tenantId)
        )

        return result.fold(
            success = { resolution ->
                if (resolution.resolved && resolution.internalIdentityId != null) {
                    Ok(
                        ResolvedUser(
                            partyId = resolution.internalIdentityId!!,
                            username = identifier,
                            userType = UserType.EXTERNAL
                        )
                    ).asResult()
                } else {
                    Ok(null).asResult()
                }
            },
            failure = { error ->
                Err(error).asResult()
            }
        )
    }

    override suspend fun createUser(input: CreateUserInput): IdkResult<ResolvedUser, IdkError> {
        // Identity creation happens through the reconciliation flow, not direct user creation.
        // Callers should use identity reconciliation to create identity mappings.
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "User creation not supported via identity resolution. Use identity reconciliation to create identity mappings."
            )
        ).asResult()
    }
}
