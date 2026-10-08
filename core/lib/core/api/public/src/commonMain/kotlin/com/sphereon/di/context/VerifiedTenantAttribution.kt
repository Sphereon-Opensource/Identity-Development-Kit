/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.di.context

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A command result that establishes a tenant through cryptographic verification, such as the
 * claims of a verified access token.
 *
 * Token verification runs before a caller exists, often in a session without a tenant. Once the
 * token has verified, its tenant claim is trustworthy and can attribute records about that
 * verification (for example audit events). It never selects the tenant used to verify the token.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifiedTenantAttribution", exact = true)
interface VerifiedTenantAttribution {
    /** The tenant carried by the verified material, or null when it carries none. */
    val verifiedTenantId: String?
}
