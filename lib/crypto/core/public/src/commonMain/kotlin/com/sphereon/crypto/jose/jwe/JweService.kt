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

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Main service interface for JWE (JSON Web Encryption) operations
 *
 * This service provides a unified interface for creating and decrypting JSON Web Encryption (JWE)
 * in various formats (compact, flattened JSON, general JSON). It internally uses command objects
 * for each operation, providing both command-based and service-based access patterns.
 *
 * JWE provides confidentiality by encrypting the payload content, unlike JWS which only provides
 * integrity and authenticity through signatures.
 *
 * Supported formats:
 * - Compact serialization: Single recipient, minimal overhead (base64url encoded)
 * - JSON flattened: Single recipient, JSON format with optional unprotected headers
 * - JSON general: Multiple recipients, JSON format for multi-recipient scenarios
 *
 * Not exported to JS as it has suspend functions and nested interfaces
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweService", exact = true)
interface JweService :
    PrepareJweCommandService,
    CreateJweCompactCommandService,
    CreateJweJsonFlattenedCommandService,
    CreateJweJsonGeneralCommandService,
    DecryptJweCommandService {
    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all JWE commands
     */
    interface Commands {
        val prepareJwe: PrepareJweCommand
        val createJweCompact: CreateJweCompactCommand
        val createJweJsonFlattened: CreateJweJsonFlattenedCommand
        val createJweJsonGeneral: CreateJweJsonGeneralCommand
        val decryptJwe: DecryptJweCommand
    }
}
