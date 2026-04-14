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

package com.sphereon.did.resolver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationPurpose
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

// ============================================================================
// DID Resolution Command Interfaces
// ============================================================================

/**
 * Command for resolving a DID to its DID Document.
 *
 * This command resolves a DID using registered method-specific resolvers
 * and optionally caches results for methods that support caching.
 *
 * @see ResolveDidArgs
 * @see DidResolutionResult
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveDidCommand", exact = true)
@JsExportCompat
interface ResolveDidCommand : ServiceCommand<ResolveDidArgs, DidResolutionResult> {
    companion object {
        const val COMMAND_ID = "did.resolver.resolve"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Service interface for resolving DIDs.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveDidCommandService", exact = true)
@JsExportCompat
interface ResolveDidCommandService {
    suspend fun resolve(args: ResolveDidArgs): IdkResult<DidResolutionResult, IdkError>
}

/**
 * Command for dereferencing a DID URL to a specific resource.
 *
 * Supports:
 * - Kid resolution: `did:example:123#key-1`
 * - Service resolution: `did:example:123?service=hub`
 * - Full document: `did:example:123`
 *
 * @see DereferenceDidArgs
 * @see DidDereferenceResult
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DereferenceDidCommand", exact = true)
@JsExportCompat
interface DereferenceDidCommand : ServiceCommand<DereferenceDidArgs, DidDereferenceResult> {
    companion object {
        const val COMMAND_ID = "did.resolver.dereference"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Service interface for dereferencing DID URLs.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DereferenceDidCommandService", exact = true)
@JsExportCompat
interface DereferenceDidCommandService {
    suspend fun dereference(args: DereferenceDidArgs): IdkResult<DidDereferenceResult, IdkError>
}

/**
 * Command for resolving a specific verification method by kid.
 *
 * Convenience command for common kid lookup use case.
 *
 * @see ResolveVerificationMethodArgs
 * @see VerificationMethod
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveVerificationMethodCommand", exact = true)
@JsExportCompat
interface ResolveVerificationMethodCommand : ServiceCommand<ResolveVerificationMethodArgs, VerificationMethod> {
    companion object {
        const val COMMAND_ID = "did.resolver.verifymethod"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Service interface for resolving verification methods.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveVerificationMethodCommandService", exact = true)
@JsExportCompat
interface ResolveVerificationMethodCommandService {
    suspend fun resolveVerificationMethod(args: ResolveVerificationMethodArgs): IdkResult<VerificationMethod, IdkError>
}

/**
 * Command for resolving all verification methods for a specific purpose.
 *
 * @see ResolveVerificationMethodsByPurposeArgs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveVerificationMethodsByPurposeCommand", exact = true)
@JsExportCompat
interface ResolveVerificationMethodsByPurposeCommand : ServiceCommand<ResolveVerificationMethodsByPurposeArgs, List<VerificationMethod>> {
    companion object {
        const val COMMAND_ID = "did.resolver.verifypurpose"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Service interface for resolving verification methods by purpose.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveVerificationMethodsByPurposeCommandService", exact = true)
@JsExportCompat
interface ResolveVerificationMethodsByPurposeCommandService {
    suspend fun resolveVerificationMethodsByPurpose(args: ResolveVerificationMethodsByPurposeArgs): IdkResult<List<VerificationMethod>, IdkError>
}
