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

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.Err
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

// Shared helpers used by every GrantHandler implementation. Package-internal because the same
// shape recurs in each handler (DPoP-binding error shape, generic AS-error wrapping, token-type
// derivation): centralising keeps every handler uniform without forcing a deep base class.

/**
 * RFC 9449 §4: tokens bound to a DPoP proof advertise `token_type: DPoP`, otherwise `Bearer`.
 */
internal fun tokenTypeFor(boundJkt: String?): String = if (boundJkt != null) "DPoP" else "Bearer"

/**
 * Wrap an [AuthorizationServerError] DTO as an [IdkError] in an [Err] for early-return convenience.
 */
internal fun errOf(dto: AuthorizationServerError): Err<IdkError> = Err(IdkError.fromDTO(dto))

/**
 * RFC 9449 §10.1: shorthand for the recurring `invalid_dpop_proof` rejection.
 */
internal fun invalidDpopProof(details: String): Err<IdkError> = errOf(AuthorizationServerError.InvalidDpopProof(details = details))
