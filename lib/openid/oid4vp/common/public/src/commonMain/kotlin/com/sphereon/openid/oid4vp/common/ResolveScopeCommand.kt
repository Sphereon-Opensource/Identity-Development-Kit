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

package com.sphereon.openid.oid4vp.common

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Arguments for resolving scope values to DCQL queries.
 *
 * @property scopeString Space-separated scope values (e.g., "openid com.example.identity")
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveScopeArgs", exact = true)
@JsExportCompat
data class ResolveScopeArgs(
    val scopeString: String?,
)

/**
 * Command for resolving scope values to DCQL queries.
 *
 * OpenID4VP 1.0 Final Section 5.5:
 * "Wallets MAY support requesting Presentations using OAuth 2.0 scope values.
 * Such a scope parameter value MUST be an alias for a well-defined DCQL query."
 *
 * This command is optional - only needed if the wallet supports scope-based
 * credential requests as an alternative to explicit DCQL queries.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveScopeCommand", exact = true)
@JsExportCompat
interface ResolveScopeCommand : ServiceCommand<ResolveScopeArgs, ScopeResolutionResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.scope.resolve"
    }
}

/**
 * Command service interface for resolving scope values.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveScopeCommandService", exact = true)
interface ResolveScopeCommandService {
    suspend fun resolveScope(scopeString: String?): IdkResult<ScopeResolutionResult, IdkError>
}
