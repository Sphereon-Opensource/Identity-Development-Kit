/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.sdjwt.vc.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.sdjwt.vc.IssuerMetadataResolutionResult
import com.sphereon.sdjwt.vc.ResolveIssuerMetadataArgs
import com.sphereon.sdjwt.vc.ResolveTypeMetadataArgs
import com.sphereon.sdjwt.vc.TypeMetadataResolutionResult
import com.sphereon.sdjwt.vc.UrlTypeMetadataResolver
import com.sphereon.sdjwt.vc.WellKnownIssuerMetadataResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveTypeMetadataCommandImpl", exact = true)
class ResolveTypeMetadataCommandImpl(
    private val httpClientFactory: HttpClientFactory,
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ResolveTypeMetadataArgs, TypeMetadataResolutionResult, IdkError>(
        commandId = ResolveTypeMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveTypeMetadataArgs>(),
        outputTypeToken = typeToken<TypeMetadataResolutionResult>(),
    ),
    ResolveTypeMetadataCommand {
    override val commandId: String get() = ResolveTypeMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveTypeMetadataArgs

    override suspend fun doExecute(
        args: ResolveTypeMetadataArgs,
        applyDuring: (ResolveTypeMetadataArgs) -> ResolveTypeMetadataArgs,
    ): IdkResult<TypeMetadataResolutionResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val resolver = appliedArgs.resolver ?: UrlTypeMetadataResolver(httpClientFactory.createClient(HttpClientOptions.createDefault()))
        return Ok(resolver.resolve(appliedArgs.vct))
    }
}

@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveIssuerMetadataCommandImpl", exact = true)
class ResolveIssuerMetadataCommandImpl(
    private val httpClientFactory: HttpClientFactory,
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ResolveIssuerMetadataArgs, IssuerMetadataResolutionResult, IdkError>(
        commandId = ResolveIssuerMetadataCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveIssuerMetadataArgs>(),
        outputTypeToken = typeToken<IssuerMetadataResolutionResult>(),
    ),
    ResolveIssuerMetadataCommand {
    override val commandId: String get() = ResolveIssuerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveIssuerMetadataArgs

    override suspend fun doExecute(
        args: ResolveIssuerMetadataArgs,
        applyDuring: (ResolveIssuerMetadataArgs) -> ResolveIssuerMetadataArgs,
    ): IdkResult<IssuerMetadataResolutionResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val resolver = appliedArgs.resolver ?: WellKnownIssuerMetadataResolver(httpClientFactory.createClient(HttpClientOptions.createDefault()))
        return Ok(resolver.resolve(appliedArgs.issuer))
    }
}
