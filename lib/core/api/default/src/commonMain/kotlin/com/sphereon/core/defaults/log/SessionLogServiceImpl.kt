/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.core.defaults.log

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionLogService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionLogServiceImpl", exact = true)
class SessionLogServiceImpl(
    override val logManager: SessionLogManager,
    override val sessionContext: SessionContext,
) : SessionLogService {
    val delegate = logManager.withTagAsync(sessionContext.sessionId)

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val id = SERVICE_ID
    override val isEnabled: Boolean
        get() = delegate.isEnabled

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> {
        logScope.launch {
            delegate.execute(message)
        }
        return Unit.asOkResult()
    }

    override suspend fun setConfig(config: LoggerConfig): SessionLogService =
        apply {
            delegate.setConfig(config)
        }

    fun cancel() {
        logScope.cancel()
    }

    override fun toAsync(): AsyncLogService = delegate

    companion object {
        const val SERVICE_ID = "ServiceLogService"
    }
}
