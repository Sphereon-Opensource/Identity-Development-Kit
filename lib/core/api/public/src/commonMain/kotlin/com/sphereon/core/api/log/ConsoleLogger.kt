/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.core.api.log

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.toSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

abstract class AbstractSessionBoundConsoleLogService(
    runtimeSessionContext: SessionContext = NoOpSessionContext,
    id: String,
    config: LoggerConfig = LoggerConfig.Default,
) : AbstractLogService(id = id, sessionContext = runtimeSessionContext, config = config),
    LogService

abstract class AbstractConsoleLogService(
    runtimeSessionContext: SessionContext = NoOpSessionContext,
    id: String,
    config: LoggerConfig = LoggerConfig.Default,
) : AbstractSessionBoundConsoleLogService(runtimeSessionContext, id, config) {
    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage,
    ): IdkResult<Unit, IdkErrorType> {
        val message = applyDuring(args)
        val config = getConfig()
        val formattedMessage = LogMessageFormatter.format(message, config)
        return println(formattedMessage).asOkResult()
    }
}

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LogService>())
@ContributesIntoSet(AppScope::class, binding = binding<Logger>())
class AppConsoleLogServiceImpl : AbstractConsoleLogService(id = SERVICE_ID) {
    override val scope: IdkScope = IdkScope.APP

    companion object {
        const val SERVICE_ID = "AppConsoleLogger"
    }
}

@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<LogService>())
@ContributesIntoSet(UserScope::class, binding = binding<Logger>())
class UserContextConsoleLogServiceImpl(
    userContextInstance: UserContextInstance,
) : AbstractConsoleLogService(userContextInstance.toSessionContext(), SERVICE_ID),
    LogService {
    override val scope: IdkScope = IdkScope.USER

    companion object {
        const val SERVICE_ID = "ContextConsoleLogger"
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<LogService>())
@ContributesIntoSet(SessionScope::class, binding = binding<Logger>())
class SessionConsoleLogServiceImpl(
    runtimeSessionContext: SessionContext,
) : AbstractConsoleLogService(runtimeSessionContext, SERVICE_ID) {
    override val scope: IdkScope = IdkScope.SESSION

    companion object {
        const val SERVICE_ID = "SessionConsoleLogger"
    }
}
