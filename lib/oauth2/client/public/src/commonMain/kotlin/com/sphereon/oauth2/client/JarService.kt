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

package com.sphereon.oauth2.client

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreateEncryptedJarArgs
import com.sphereon.oauth2.client.command.CreateEncryptedJarCommand
import com.sphereon.oauth2.client.command.CreateSignedJarArgs
import com.sphereon.oauth2.client.command.CreateSignedJarCommand
import com.sphereon.oauth2.client.command.MergeRequestObjectArgs
import com.sphereon.oauth2.client.command.MergeRequestObjectCommand
import com.sphereon.oauth2.client.command.MergedRequestObjectResult
import com.sphereon.oauth2.client.command.ParseJarArgs
import com.sphereon.oauth2.client.command.ParseJarCommand
import com.sphereon.oauth2.client.command.ParsedJarResult
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Main service interface for JAR (JWT-secured Authorization Request) operations
 *
 * This service provides a unified interface for creating and parsing JWT-secured
 * authorization requests as defined in RFC 9101. It supports both signed JARs (JWS)
 * and encrypted JARs (nested JWT: JWE(JWS)).
 *
 * JAR enables clients to:
 * - Ensure integrity and authenticity of authorization requests
 * - Protect authorization request confidentiality through encryption
 * - Support complex authorization scenarios requiring signed/encrypted requests
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JarService", exact = true)
@JsExportCompat
interface JarService {
    /**
     * Create a signed JAR from an authorization request
     *
     * This operation:
     * 1. Converts authorization request parameters to JWT claims
     * 2. Adds standard JWT claims (iss, aud, exp, iat, jti)
     * 3. Signs the JWT using the client's private key
     * 4. Returns the compact JWS serialization
     *
     * @param args Arguments including authorization request and signing key
     * @return IdkResult containing the signed JAR as a compact JWS string
     */
    suspend fun createSignedJar(args: CreateSignedJarArgs): IdkResult<StringResult, IdkError>

    /**
     * Encrypt a signed JAR to create a nested JWT
     *
     * This operation:
     * 1. Takes a signed JAR (JWS)
     * 2. Encrypts it using the authorization server's public key
     * 3. Returns the compact JWE serialization
     *
     * The result is a nested JWT: JWE(JWS(authorization_request))
     *
     * @param args Arguments including the signed JAR and recipient public key
     * @return IdkResult containing the encrypted JAR as a compact JWE string
     */
    suspend fun createEncryptedJar(args: CreateEncryptedJarArgs): IdkResult<StringResult, IdkError>

    /**
     * Parse and validate a JAR token
     *
     * This operation:
     * 1. Detects if the JAR is encrypted (JWE) or just signed (JWS)
     * 2. If encrypted, decrypts using the provided key
     * 3. Verifies the signature
     * 4. Validates JWT claims (iss, aud, exp)
     * 5. Extracts authorization request parameters
     *
     * @param args Arguments including the JAR token and verification/decryption keys
     * @return IdkResult containing the parsed authorization request and metadata
     */
    suspend fun parseJar(args: ParseJarArgs): IdkResult<ParsedJarResult, IdkError>

    /**
     * Merge JWT request object parameters with query parameters
     *
     * Per RFC 9101 Section 3.2, this operation:
     * 1. Parses and validates the JWT request object
     * 2. Extracts authorization request parameters from JWT claims
     * 3. Merges with query parameters following RFC 9101 rules
     * 4. Returns merged parameters as Ktor Parameters
     *
     * @param args Arguments including the JWT and query parameters
     * @return IdkResult containing merged parameters or error
     */
    suspend fun mergeRequestObject(args: MergeRequestObjectArgs): IdkResult<MergedRequestObjectResult, IdkError>

    /**
     * Provides access to the underlying commands for advanced usage scenarios
     */
    val commands: Commands

    /**
     * Container for all JAR commands
     */
    @JsExportIgnoreCompat
    interface Commands {
        val createSignedJar: CreateSignedJarCommand
        val createEncryptedJar: CreateEncryptedJarCommand
        val parseJar: ParseJarCommand
        val mergeRequestObject: MergeRequestObjectCommand
    }
}

/**
 * Implementation of JarService that delegates to command implementations
 *
 * This implementation aggregates all JAR command implementations and provides
 * a unified service interface. It uses dependency injection to obtain command
 * instances and delegates all operations to them.
 *
 * The service is scoped to the session, meaning a single instance is shared
 * within a session context.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<JarService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("JarServiceImpl", exact = true)
class JarServiceImpl(
    private val createSignedJarCommand: CreateSignedJarCommand,
    private val createEncryptedJarCommand: CreateEncryptedJarCommand,
    private val parseJarCommand: ParseJarCommand,
    private val mergeRequestObjectCommand: MergeRequestObjectCommand,
) : JarService {
    /**
     * Graph interface for DI graph integration
     * Allows other components to access the JarService
     */
    @ContributesTo(SessionScope::class)
    interface Graph {
        val jarService: JarService
    }

    /**
     * Implementation of Commands that exposes the injected command instances
     */
    inner class CommandsImpl : JarService.Commands {
        override val createSignedJar: CreateSignedJarCommand = this@JarServiceImpl.createSignedJarCommand
        override val createEncryptedJar: CreateEncryptedJarCommand = this@JarServiceImpl.createEncryptedJarCommand
        override val parseJar: ParseJarCommand = this@JarServiceImpl.parseJarCommand
        override val mergeRequestObject: MergeRequestObjectCommand = this@JarServiceImpl.mergeRequestObjectCommand
    }

    override val commands: JarService.Commands = CommandsImpl()

    // Delegate service methods to commands

    override suspend fun createSignedJar(args: CreateSignedJarArgs): IdkResult<StringResult, IdkError> = createSignedJarCommand.execute(args)

    override suspend fun createEncryptedJar(args: CreateEncryptedJarArgs): IdkResult<StringResult, IdkError> = createEncryptedJarCommand.execute(args)

    override suspend fun parseJar(args: ParseJarArgs): IdkResult<ParsedJarResult, IdkError> = parseJarCommand.execute(args)

    override suspend fun mergeRequestObject(args: MergeRequestObjectArgs): IdkResult<MergedRequestObjectResult, IdkError> = mergeRequestObjectCommand.execute(args)
}
