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

package com.sphereon.crypto.jose.jws

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommandService
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonFlattenedCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonFlattenedCommandService
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonGeneralCommandService
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommand
import com.sphereon.crypto.jose.jws.command.PrepareJwsCommandService
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommandService
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Main service interface for JWS/JWT operations
 *
 * This service provides a unified interface for creating and verifying JSON Web Signatures (JWS)
 * in various formats (compact, flattened JSON, general JSON). It internally uses command objects
 * for each operation, providing both command-based and service-based access patterns.
 *
 * Not exported to JS as it has suspend functions and nested interfaces
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JwtService", exact = true)
interface JwtService :
    PrepareJwsCommandService,
    CreateJwsCompactCommandService,
    CreateJwsJsonFlattenedCommandService,
    CreateJwsJsonGeneralCommandService,
    VerifyJwsCommandService {
    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /** Assemble JWS JSON General from prepared state + external signature (two-step signing) */
    fun assembleJwsGeneral(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ): JwsJsonGeneral

    /** Assemble JWS JSON Flattened from prepared state + external signature (two-step signing) */
    fun assembleJwsFlattened(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ): JwsJsonFlattened

    /** Assemble compact JWS (JWT) from prepared state + external signature (two-step signing) */
    fun assembleJwsCompact(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ): JwtCompactResult

    /**
     * Container for all JWS/JWT commands
     */
    interface Commands {
        val prepareJws: PrepareJwsCommand
        val createJwsCompact: CreateJwsCompactCommand
        val createJwsJsonFlattened: CreateJwsJsonFlattenedCommand
        val createJwsJsonGeneral: CreateJwsJsonGeneralCommand
        val verifyJws: VerifyJwsCommand
    }
}
