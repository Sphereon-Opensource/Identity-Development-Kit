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
 */

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwtHeader
import com.sphereon.crypto.core.jose.JwtPayload
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat

import kotlin.native.ObjCName

// ============================================================================
// Request Types
// ============================================================================

/**
 * Options for JWS creation behavior (structure and headers)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsOpts", exact = true)
@JsExportCompat
@Serializable
data class CreateJwsOpts(
    val noIssPayloadUpdate: Boolean = false,
    val noIdentifierInHeader: Boolean = false,
    val protectedHeader: JsonObject? = null,
    val unprotectedHeader: JsonObject? = null
)

/**
 * Base arguments for creating a JWS (compact serialization)
 * Uses ManagedIdentifierOptsOrResult for signing
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsArgs", exact = true)
@JsExportCompat
@Serializable
data class CreateJwsArgs(
    // Managed identifier for signing (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val issuer: ManagedIdentifierOptsOrResult? = null,
    // Payload can be a JSON object, string, or ByteArray (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val payload: Any? = null,
    // How to resolve and use the issuer identifier
    val mode: JwsIdentifierMode = JwsIdentifierMode.AUTO,
    val opts: CreateJwsOpts = CreateJwsOpts()
)

/**
 * Arguments for creating a general or flattened JSON JWS
 * Uses ManagedIdentifierOptsOrResult for signing
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonArgs", exact = true)
@JsExportCompat
@Serializable
data class CreateJwsJsonArgs(
    // Managed identifier for signing (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val issuer: ManagedIdentifierOptsOrResult? = null,
    // Payload can be a JSON object, string, or ByteArray (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val payload: Any? = null,
    // How to resolve and use the issuer identifier
    val mode: JwsIdentifierMode = JwsIdentifierMode.AUTO,
    val opts: CreateJwsOpts = CreateJwsOpts(),
    // For adding to existing signatures in general JSON format
    val existingSignatures: List<JwsJsonSignature>? = null
)

/**
 * Arguments for verifying a JWS
 * Can use any IdentifierOptsOrResult (external identifiers with public keys)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyJwsArgs", exact = true)
@JsExportCompat
@Serializable
data class VerifyJwsArgs(
    // JWS to verify (required, but transient so needs default)
    @kotlinx.serialization.Transient
    val jws: Jws? = null,
    // Optional: provide resolved identifier for verification
    // If not provided, will be extracted from JWS header (kid, x5c, jwk, etc.)
    @kotlinx.serialization.Transient
    val identifier: IdentifierOptsOrResult? = null
)

// ============================================================================
// Command Interfaces
// ============================================================================

/**
 * Command for preparing a JWS object (common functionality for all JWS creation)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJwsCommand", exact = true)
@JsExportCompat
interface PrepareJwsCommand :
    ServiceCommand<CreateJwsJsonArgs, PreparedJwsObject> {

    companion object {
        const val COMMAND_ID = "crypto.jws.prepare"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Command service interface for preparing JWS
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJwsCommandService", exact = true)
interface PrepareJwsCommandService {
    suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError>
}

/**
 * Command for creating a compact JWS
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsCompactCommand", exact = true)
@JsExportCompat
interface CreateJwsCompactCommand :
    ServiceCommand<CreateJwsArgs, JwtCompactResult> {

    companion object {
        const val COMMAND_ID = "crypto.jws.compact"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Command service interface for compact JWS
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsCompactCommandService", exact = true)
interface CreateJwsCompactCommandService {
    suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError>
}

/**
 * Command for creating a flattened JSON JWS
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonFlattenedCommand", exact = true)
@JsExportCompat
interface CreateJwsJsonFlattenedCommand :
    ServiceCommand<CreateJwsJsonArgs, JwsJsonFlattened> {

    companion object {
        const val COMMAND_ID = "crypto.jws.flattened"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Command service interface for flattened JSON JWS
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonFlattenedCommandService", exact = true)
interface CreateJwsJsonFlattenedCommandService {
    suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError>
}

/**
 * Command for creating a general JSON JWS
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonGeneralCommand", exact = true)
@JsExportCompat
interface CreateJwsJsonGeneralCommand :
    ServiceCommand<CreateJwsJsonArgs, JwsJsonGeneral> {

    companion object {
        const val COMMAND_ID = "crypto.jws.general"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Command service interface for general JSON JWS
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJwsJsonGeneralCommandService", exact = true)
interface CreateJwsJsonGeneralCommandService {
    suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError>
}

/**
 * Command for verifying a JWS signature
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyJwsCommand", exact = true)
@JsExportCompat
interface VerifyJwsCommand :
    ServiceCommand<VerifyJwsArgs, JwsValidationResult> {

    companion object {
        const val COMMAND_ID = "crypto.jws.verify"
    }

    override val commandId: String get() = COMMAND_ID
}

/**
 * Command service interface for JWS verification
 * Not exported to JS as it has suspend functions
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyJwsCommandService", exact = true)
interface VerifyJwsCommandService {
    suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError>
}
