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
import com.sphereon.di.context.NoOpSessionContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * App-scoped mobile log service
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LogService>())
@ContributesIntoSet(AppScope::class, binding = binding<Logger>())
class AppMobileLogService(
    private val repository: MobileLogRepository,
) : AbstractMobileLogService(NoOpSessionContext, SERVICE_ID, repository) {
    override val scope = IdkScope.APP

    companion object {
        const val SERVICE_ID = "AppMobileLogger"
    }
}
