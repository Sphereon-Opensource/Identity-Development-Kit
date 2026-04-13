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

import kotlinx.datetime.Clock

/**
 * Simple [SecuredTenantContextDetails] implementation usable from commonMain.
 *
 * This replaces the need for platform-specific `TransportSecuredDetails` when
 * creating a base for [EnrichedSecureDetailsFactory.create()].
 */
data class BasicSecuredDetails(
    override val jwt: String,
    override val iss: String = "transport",
    override val validFrom: Long = Clock.System.now().toEpochMilliseconds(),
    override val validUntil: Long = Clock.System.now().toEpochMilliseconds() + 3_600_000
) : SecuredTenantContextDetails
