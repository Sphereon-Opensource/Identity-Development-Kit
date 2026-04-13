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

package com.sphereon.core.log.mobile

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.Logger
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.toSessionContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Context-scoped mobile log service
 */
@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<LogService>())
@ContributesIntoSet(UserScope::class, binding = binding<Logger>())
class UserContextMobileLogService(
    userContextInstance: UserContextInstance,
    private val repository: MobileLogRepository,
) : AbstractMobileLogService(userContextInstance.toSessionContext(), SERVICE_ID, repository) {
    override val scope = IdkScope.USER

    companion object {
        const val SERVICE_ID = "ContextMobileLogger"
    }
}
