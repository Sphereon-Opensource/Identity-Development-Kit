package com.sphereon.core.defaults.log

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionLogService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionLogServiceImpl", exact = true)
class SessionLogServiceImpl(override val logManager: SessionLogManager, override val sessionContext: SessionContext) :
    SessionLogService {
    val delegate = logManager.withTagAsync(sessionContext.sessionId)

    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> {
        logScope.launch {
            delegate.execute(message)
        }
        return Unit.asOkResult()

    }

    override suspend fun setConfig(config: LoggerConfig): SessionLogService = apply {
        delegate.setConfig(config)
    }

    override val id = SERVICE_ID
    override val isEnabled: Boolean
        get() = delegate.isEnabled


    override fun toAsync(): AsyncLogService {
        return delegate
    }

    companion object {
        const val SERVICE_ID = "ServiceLogService"
    }

}
