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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Arguments for the UserInfo endpoint
 */
data class GetUserInfoArgs(
    val accessToken: String,
)

/**
 * UserInfo endpoint response, per OpenID Connect Core 1.0 §5.3.2.
 *
 * The wire shape MUST be a flat JSON object with claims as top-level keys:
 *
 * ```json
 * { "sub": "248289761001", "email": "jdoe@example.com", "email_verified": true, "given_name": "Jane" }
 * ```
 *
 * Earlier revisions wrapped claims in a `{"sub": ..., "claims": {...}}` envelope which is
 * not OIDC-compatible. [UserInfoResponseSerializer] ensures serialization emits the flat
 * form regardless of DTO shape.
 */
@Serializable(with = UserInfoResponseSerializer::class)
data class UserInfoResponse(
    val claims: JsonObject,
) {
    /**
     * The `sub` claim — required per OIDC §5.3.2. Throws if absent, since a UserInfo
     * response without `sub` is malformed and callers cannot proceed.
     */
    val sub: String
        get() =
            claims["sub"]?.jsonPrimitive?.content
                ?: error("UserInfoResponse is missing required 'sub' claim")
}

/**
 * Serializes [UserInfoResponse] as its inner [JsonObject] (flat wire shape),
 * not as an envelope.
 */
object UserInfoResponseSerializer : KSerializer<UserInfoResponse> {
    private val delegate = JsonObject.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: UserInfoResponse,
    ) {
        encoder.encodeSerializableValue(delegate, value.claims)
    }

    override fun deserialize(decoder: Decoder): UserInfoResponse = UserInfoResponse(decoder.decodeSerializableValue(delegate))
}

/**
 * Get UserInfo command
 *
 * OpenID Connect Core 1.0 §5.3: UserInfo Endpoint.
 *
 * Returns claims about the authenticated End-User filtered by the scopes granted in the
 * access token.
 */
interface GetUserInfoCommand : ServiceCommand<GetUserInfoArgs, UserInfoResponse, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.userinfo.get"
    }
}
