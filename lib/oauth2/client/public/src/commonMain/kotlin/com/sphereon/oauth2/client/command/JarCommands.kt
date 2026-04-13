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

package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.StringResult
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.oauth2.common.model.AuthorizationRequest

/**
 * Arguments for creating a signed JAR (JWT-secured Authorization Request)
 *
 * Per RFC 9101, authorization requests can be passed as signed JWTs to provide
 * integrity protection and authentication of the client making the request.
 *
 * @property authorizationRequest The authorization request to be signed
 * @property signingKey The client's private key used to sign the JWT
 * @property issuer The client_id (acts as JWT issuer)
 * @property audience The authorization server's issuer URL (acts as JWT audience)
 * @property expirationSeconds Optional expiration time in seconds (default: 300 = 5 minutes)
 */
data class CreateSignedJarArgs(
    val authorizationRequest: AuthorizationRequest,
    val signingKey: KeyInfoType<*>,
    val issuer: String, // client_id
    val audience: String, // authorization server issuer
    val expirationSeconds: Long = 300,
    /** Explicit kid for the JWT header (e.g., DID verification method ID). */
    val kid: String? = null,
    /**
     * X.509 certificate chain for the `x5c` JOSE header per RFC 7515 §4.1.6
     * (base64-encoded DER, leaf first). Mutually exclusive with [kid] in practice;
     * callers should set exactly one.
     */
    val x5c: List<String>? = null,
    /**
     * Whether to emit an `iss` claim in the payload. Defaults to `true` — RFC 9101 §3 lists
     * `iss` as SHOULD and defines it as the client_id. Callers who intentionally omit `iss`
     * (e.g. when their client-id encoding would make `iss = client_id` unhelpful to
     * downstream validators) can set this to `false`. When true, `iss` is set to [issuer].
     */
    val includeIss: Boolean = true,
)

/**
 * Arguments for creating an encrypted JAR (nested JWT)
 *
 * Per RFC 9101, signed JARs can be encrypted to provide confidentiality.
 * This creates a nested JWT structure: JWS(claims) → JWE(JWS)
 *
 * @property signedJar The signed JAR JWT string to be encrypted
 * @property recipientPublicKey The authorization server's public key for encryption
 * @property keyEncryptionAlgorithm The algorithm for key encryption (e.g., "RSA-OAEP-256")
 * @property contentEncryptionAlgorithm The algorithm for content encryption (e.g., "A256GCM")
 */
data class CreateEncryptedJarArgs(
    val signedJar: String,
    val recipientPublicKey: KeyInfoType<*>,
    val keyEncryptionAlgorithm: String = "RSA-OAEP-256",
    val contentEncryptionAlgorithm: String = "A256GCM",
)

/**
 * Arguments for parsing and validating a JAR
 *
 * @property jarToken The signed or encrypted JAR token string
 * @property issuer Expected issuer (client_id)
 * @property audience Expected audience (authorization server)
 * @property verificationKey Public key for signature verification (if signed)
 * @property decryptionKey Private key for decryption (if encrypted)
 */
data class ParseJarArgs(
    val jarToken: String,
    val issuer: String,
    val audience: String,
    val verificationKey: KeyInfoType<*>? = null,
    val decryptionKey: KeyInfoType<*>? = null,
)

/**
 * Result of parsing a JAR token
 *
 * @property authorizationRequest The parsed authorization request
 * @property isEncrypted Whether the JAR was encrypted
 * @property claims Additional JWT claims from the JAR
 */
data class ParsedJarResult(
    val authorizationRequest: AuthorizationRequest,
    val isEncrypted: Boolean,
    val claims: Map<String, Any?>,
)

/**
 * Command for creating a signed JAR (JWT-secured Authorization Request)
 *
 * Per RFC 9101, this command:
 * 1. Converts authorization request parameters to JWT claims
 * 2. Adds standard JWT claims (iss, aud, exp, iat, jti)
 * 3. Signs the JWT using the client's private key
 * 4. Returns the compact JWS serialization
 */
interface CreateSignedJarCommand : ServiceCommand<CreateSignedJarArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jar.sign"
    }
}

/**
 * Command for creating an encrypted JAR (nested JWT)
 *
 * Per RFC 9101, this command:
 * 1. Takes a signed JAR (JWS)
 * 2. Encrypts it using the authorization server's public key
 * 3. Returns the compact JWE serialization
 *
 * The result is a nested JWT: JWE(JWS(authorization_request))
 */
interface CreateEncryptedJarCommand : ServiceCommand<CreateEncryptedJarArgs, StringResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jar.encrypt"
    }
}

/**
 * Command for parsing and validating a JAR
 *
 * This command:
 * 1. Detects if the JAR is encrypted (JWE) or just signed (JWS)
 * 2. If encrypted, decrypts using the provided key
 * 3. Verifies the signature
 * 4. Validates JWT claims (iss, aud, exp)
 * 5. Extracts authorization request parameters
 */
interface ParseJarCommand : ServiceCommand<ParseJarArgs, ParsedJarResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jar.parse"
    }
}
