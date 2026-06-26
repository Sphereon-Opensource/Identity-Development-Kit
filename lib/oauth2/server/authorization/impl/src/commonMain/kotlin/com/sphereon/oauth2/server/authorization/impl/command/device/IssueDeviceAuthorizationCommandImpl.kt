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

package com.sphereon.oauth2.server.authorization.impl.command.device

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.core.api.random.SecureRandom
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationArgs
import com.sphereon.oauth2.server.authorization.command.device.IssueDeviceAuthorizationCommand
import com.sphereon.oauth2.server.authorization.command.device.IssuedDeviceAuthorization
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationRecord
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationState
import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Issue an RFC 8628 device-authorization record at `/device_authorization`.
 *
 * Validation order:
 * 1. Resolve the client from the registry. Unknown -> `invalid_client`.
 * 2. Verify the client's registration includes the `device_code` grant type. Missing ->
 *    `unauthorized_client`.
 * 3. Generate a 32-byte base64url `device_code` and an 8-character `user_code` from the
 *    unambiguous alphabet, formatted as `XXXX-XXXX`. The `user_code` is regenerated up to
 *    [USER_CODE_MAX_RETRIES] times on `userCode` collision against the storage; exhausting the
 *    budget signals a CSPRNG fault and yields `server_error`.
 * 4. Resolve `expires_in` and the polling baseline from the AS config.
 * 5. Construct `verification_uri` (`{baseUrl}/device`) and `verification_uri_complete`
 *    (`{baseUrl}/device?user_code=<percent-encoded>`). The `baseUrl` comes from
 *    `serverConfig.issuer` when set, else from [IssueDeviceAuthorizationArgs.baseUrlOverride]
 *    so deployments behind a TLS-terminating proxy emit URLs matching what discovery advertises.
 * 6. Persist a PENDING [DeviceAuthorizationRecord] with the lookup keys and the polling cadence.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<IssueDeviceAuthorizationCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssueDeviceAuthorizationCommandImpl", exact = true)
class IssueDeviceAuthorizationCommandImpl(
    execution: SessionExecution,
    private val deviceAuthorizationStorage: DeviceAuthorizationStorage,
    private val clientRegistry: com.sphereon.oauth2.server.authorization.storage.ClientRegistry,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
    private val secureRandom: SecureRandom,
    private val clock: Clock,
) : TypedServiceCommandAdapter<IssueDeviceAuthorizationArgs, IssuedDeviceAuthorization, IdkError>(
        commandId = IssueDeviceAuthorizationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<IssueDeviceAuthorizationArgs>(),
        outputTypeToken = typeToken<IssuedDeviceAuthorization>(),
    ),
    IssueDeviceAuthorizationCommand {
    override val commandId: String get() = IssueDeviceAuthorizationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IssueDeviceAuthorizationArgs

    override suspend fun doExecute(
        args: IssueDeviceAuthorizationArgs,
        applyDuring: (IssueDeviceAuthorizationArgs) -> IssueDeviceAuthorizationArgs,
    ): IdkResult<IssuedDeviceAuthorization, IdkError> = executeInternal(applyDuring(args)).mapError { IdkError.fromDTO(it) }

    private suspend fun executeInternal(args: IssueDeviceAuthorizationArgs): IdkResult<IssuedDeviceAuthorization, AuthorizationServerError> {
        val client =
            clientRegistry
                .getClient(args.clientId)
                .getOrElse { error ->
                    return Err(
                        AuthorizationServerError.ServerError(
                            details = "Failed to resolve client '${args.clientId}': ${error.details}",
                            exception = null,
                        ),
                    )
                }
                ?: return Err(AuthorizationServerError.InvalidClient(details = "Unknown client_id: ${args.clientId}"))

        if (GrantType.DEVICE_CODE !in client.grantTypes) {
            return Err(AuthorizationServerError.UnauthorizedClient(clientId = args.clientId))
        }

        val serverConfig = serversConfigProvider.serverConfig
        val expiresInSeconds = serverConfig.deviceCodeLifetimeSeconds
        val intervalSeconds = serverConfig.devicePollIntervalSeconds

        val baseUrl =
            (args.baseUrlOverride?.takeIf { it.isNotBlank() } ?: serverConfig.issuer)
                ?: return Err(
                    AuthorizationServerError.ServerError(
                        details = "Cannot resolve base URL for verification_uri: neither serverConfig.issuer nor baseUrlOverride is set",
                        exception = null,
                    ),
                )

        val deviceCode = secureRandom.newToken(lengthBytes = DEVICE_CODE_BYTES)
        val userCode =
            generateUniqueUserCode()
                ?: return Err(
                    AuthorizationServerError.ServerError(
                        details = "Failed to generate a unique user_code after $USER_CODE_MAX_RETRIES attempts",
                        exception = null,
                    ),
                )

        val now = clock.now()
        val record =
            DeviceAuthorizationRecord(
                deviceCode = deviceCode,
                userCode = userCode,
                clientId = args.clientId,
                scope = args.scope,
                resource = args.resource,
                audience = args.audience,
                state = DeviceAuthorizationState.PENDING,
                createdAt = now,
                expiresAt = now + expiresInSeconds.seconds,
                intervalSeconds = intervalSeconds,
            )

        deviceAuthorizationStorage
            .create(record)
            .getOrElse { error ->
                return Err(
                    AuthorizationServerError.ServerError(
                        details = "Failed to persist device-authorization record: ${error.details}",
                        exception = null,
                    ),
                )
            }

        val verificationUri = "${baseUrl.trimEnd('/')}/device"
        val verificationUriComplete = "$verificationUri?user_code=${percentEncodeQueryComponent(userCode)}"

        return Ok(
            IssuedDeviceAuthorization(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = verificationUri,
                verificationUriComplete = verificationUriComplete,
                expiresIn = expiresInSeconds,
                intervalSeconds = intervalSeconds,
            ),
        )
    }

    /**
     * Generate a `user_code` that does not collide with an existing record's user code. Returns
     * `null` after [USER_CODE_MAX_RETRIES] consecutive collisions: the unambiguous alphabet has
     * `32^8 = ~10^12` codewords so collisions on a healthy CSPRNG are vanishingly unlikely; a
     * hit on every retry signals either a faulty randomness source or a wedged storage, both of
     * which the operator wants surfaced as `server_error` rather than swallowed.
     */
    private suspend fun generateUniqueUserCode(): String? {
        repeat(USER_CODE_MAX_RETRIES) {
            val candidate = generateUserCode()
            val existing =
                deviceAuthorizationStorage
                    .findByUserCode(candidate)
                    .getOrElse { return null }
            if (existing == null) {
                return candidate
            }
        }
        return null
    }

    /**
     * Sample 8 characters from [USER_CODE_ALPHABET] (`2-9` plus `A-Z` minus `I`/`O`) and format
     * as `XXXX-XXXX`. Bytes are drawn from the CSPRNG and modulo-reduced to alphabet length;
     * the alphabet length (32) divides 256 so the modulo introduces no bias.
     */
    private suspend fun generateUserCode(): String {
        val raw = secureRandom.randomBytes(USER_CODE_LENGTH)
        val sb = StringBuilder(USER_CODE_LENGTH + 1)
        for ((index, byte) in raw.withIndex()) {
            if (index == USER_CODE_GROUP_SIZE) {
                sb.append('-')
            }
            val unsigned = byte.toInt() and 0xFF
            sb.append(USER_CODE_ALPHABET[unsigned % USER_CODE_ALPHABET.length])
        }
        return sb.toString()
    }

    private companion object {
        /**
         * 32 bytes of CSPRNG output, base64url-encoded, gives a ~256-bit opaque device code per
         * RFC 8628 §6.1 entropy guidance.
         */
        private const val DEVICE_CODE_BYTES = 32

        /**
         * RFC 8628 §6.1 unambiguous alphabet: digits 2-9 plus uppercase A-Z without `0`/`O` /
         * `1`/`I`. 32 codewords keep modulo selection unbiased against an 8-bit byte space.
         */
        private const val USER_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        /** Total characters in the user code, before the separator is inserted. */
        private const val USER_CODE_LENGTH = 8

        /** Group size for the `XXXX-XXXX` separator placement. */
        private const val USER_CODE_GROUP_SIZE = 4

        /**
         * Cap on consecutive `user_code` collisions before yielding `server_error`. With 32^8
         * codewords and a healthy CSPRNG, 5 consecutive collisions across a non-empty storage is
         * already astronomically unlikely; it is far more likely to be a faulty randomness
         * source or a wedged storage backing.
         */
        private const val USER_CODE_MAX_RETRIES = 5
    }
}
