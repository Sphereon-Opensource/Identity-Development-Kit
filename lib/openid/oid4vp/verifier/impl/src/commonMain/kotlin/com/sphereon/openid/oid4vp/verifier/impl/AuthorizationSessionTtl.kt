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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError

internal const val AUTHORIZATION_SESSION_MILLIS_PER_SECOND: Long = 1_000L

internal fun authorizationSessionExpiresAt(
    nowEpochMillis: Long,
    ttlSeconds: Long,
): IdkResult<Long, IdkError> {
    if (ttlSeconds <= 0L) {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "ttl_seconds must be greater than zero"))
    }
    if (nowEpochMillis < 0L || ttlSeconds > (Long.MAX_VALUE - nowEpochMillis) / AUTHORIZATION_SESSION_MILLIS_PER_SECOND) {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "ttl_seconds is too large"))
    }
    return Ok(nowEpochMillis + ttlSeconds * AUTHORIZATION_SESSION_MILLIS_PER_SECOND)
}
