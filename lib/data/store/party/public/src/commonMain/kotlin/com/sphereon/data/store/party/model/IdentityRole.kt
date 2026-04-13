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

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The role of an identity in the credential ecosystem.
 * One party can have multiple identities with different roles.
 *
 * This is an extensible type - core SSI roles (ISSUER, VERIFIER, HOLDER) are predefined,
 * while downstream projects can add additional roles for their specific use cases.
 *
 * Usage:
 * ```kotlin
 * // Use predefined constants
 * val role = IdentityRole.ISSUER
 *
 * // Create custom roles
 * val customRole = IdentityRole("general")
 *
 * // Compare
 * if (role == IdentityRole.VERIFIER) { ... }
 * ```
 */
@Serializable
@JvmInline
value class IdentityRole(
    val value: String,
) {
    companion object {
        /** Identity used for issuing credentials (OID4VCI) */
        val ISSUER = IdentityRole("issuer")

        /** Identity used for verifying/requesting credentials (OID4VP) */
        val VERIFIER = IdentityRole("verifier")

        /** Identity used for holding/presenting credentials */
        val HOLDER = IdentityRole("holder")
    }

    override fun toString(): String = value
}
