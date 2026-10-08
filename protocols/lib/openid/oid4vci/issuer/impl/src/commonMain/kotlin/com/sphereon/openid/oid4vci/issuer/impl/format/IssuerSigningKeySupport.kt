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

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext

/**
 * The key name a format handler signs this credential under.
 *
 * The name is resolved server side before the issuance reaches a handler
 * ([com.sphereon.openid.oid4vci.issuer.spi.IssuerKeyNameResolver] answers from the deployment's own
 * binding for the active tenant and issuer instance). A handler therefore only consumes it, and
 * refuses when it is absent: there is no credential-configuration id, doctype, or vct to fall back
 * on, because a name derived from a caller-visible identifier would let a credential be signed under
 * whatever key happened to sit at that name, and would mint one in deployments that create keys on
 * first use.
 *
 * Every reason the key is unusable ends at [CREDENTIAL_SIGNING_KEY_UNAVAILABLE], so the refusal
 * carries no information about which credential configurations hold which key material.
 */
internal fun IssuanceContext.requireSigningKeyName(): IdkResult<String, IdkError> =
    signingKeyAlias
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { Ok(it) }
        ?: Err(IdkError.fromString(code = "signing_key_unavailable", message = CREDENTIAL_SIGNING_KEY_UNAVAILABLE))

internal const val CREDENTIAL_SIGNING_KEY_UNAVAILABLE = "The credential signing key is unavailable"
