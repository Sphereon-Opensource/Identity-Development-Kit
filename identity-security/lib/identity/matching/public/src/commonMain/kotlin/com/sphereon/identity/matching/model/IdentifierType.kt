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

package com.sphereon.identity.matching.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class IdentifierType(
    val value: String,
) {
    companion object {
        val KEY = IdentifierType("KEY")
        val DID = IdentifierType("DID")
        val EMAIL = IdentifierType("EMAIL")
        val SUBJECT_ID = IdentifierType("SUBJECT_ID")
        val CLAIM_TUPLE = IdentifierType("CLAIM_TUPLE")
    }
}
