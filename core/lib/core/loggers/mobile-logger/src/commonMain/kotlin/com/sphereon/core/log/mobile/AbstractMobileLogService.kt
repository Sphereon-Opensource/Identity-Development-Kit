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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AbstractLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogMessageFormatter
import com.sphereon.core.api.log.LogService
import com.sphereon.di.session.SessionContext

abstract class AbstractMobileSessionLogService(
    runtimeSessionContext: SessionContext,
    id: String,
) : AbstractLogService(id = id, sessionContext = runtimeSessionContext),
    LogService

/**
 * Abstract base class for mobile log services - optimized for performance
 */
abstract class AbstractMobileLogService(
    runtimeSessionContext: SessionContext,
    id: String,
    private val repository: MobileLogRepository,
) : AbstractMobileSessionLogService(runtimeSessionContext, id) {
    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage,
    ): IdkResult<Unit, IdkErrorType> {
        val processedMessage = applyDuring(args)
        val config = getConfig()

        if (!config.isEnabled(processedMessage.level)) {
            return IdkResult.ok(Unit)
        }

        val runtimeSessionContextId = sessionContext.toString()

        // Create log entry and add to repository
        val logEntry = MobileLogEntry.fromLogMessage(processedMessage, runtimeSessionContextId)
        repository.addLog(logEntry)

        // Use LogMessageFormatter for consistent output across all loggers
        val consoleMessage = LogMessageFormatter.format(processedMessage, config)
        println(consoleMessage)

        return IdkResult.ok(Unit)
    }
}
