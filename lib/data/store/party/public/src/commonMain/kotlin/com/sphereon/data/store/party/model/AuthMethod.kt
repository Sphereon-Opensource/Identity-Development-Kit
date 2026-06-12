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
 *
 */

package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * An authentication method an application's login surface accepts for an identity.
 *
 * This is an extensible type - core methods are predefined,
 * while downstream projects can add additional methods for their specific use cases.
 *
 * Usage:
 * ```kotlin
 * // Use predefined constants
 * val method = AuthMethod.PASSWORD
 *
 * // Create custom methods
 * val customMethod = AuthMethod("sms_otp")
 *
 * // Compare
 * if (method == AuthMethod.PASSWORD) { ... }
 * ```
 */
@Serializable
@JvmInline
value class AuthMethod(
    val value: String,
) {
    companion object {
        /** Username/identifier plus password. */
        val PASSWORD = AuthMethod("password")

        /** One-time passcode delivered by email. */
        val EMAIL_OTP = AuthMethod("email_otp")

        /** Single-use magic link delivered out of band. */
        val MAGIC_LINK = AuthMethod("magic_link")

        /** Time-based one-time password (authenticator app). */
        val TOTP = AuthMethod("totp")

        /** Federated login via an external OIDC identity provider. */
        val FEDERATED_OIDC = AuthMethod("federated_oidc")

        /** Wallet-based authentication (verifiable presentation). */
        val WALLET = AuthMethod("wallet")
    }

    override fun toString(): String = value
}
