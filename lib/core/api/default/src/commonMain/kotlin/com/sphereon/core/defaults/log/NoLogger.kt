package com.sphereon.core.defaults.log

import com.sphereon.core.api.log.AbstractLogService
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType

import com.sphereon.di.context.UserScope
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LogService>())
class AppNoLogService() : AbstractNoLogService(), LogService {
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.APP
}

@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<LogService>())
class UserContextNoLogService() : AbstractNoLogService(), LogService {
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.USER
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<LogService>())
class SessionNoLogService() : AbstractNoLogService(), LogService {
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
}


abstract class AbstractNoLogService() : AbstractLogService(id = SERVICE_ID), LogService {
    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage,
    ): IdkResult<Unit, IdkErrorType> {
        return Unit.asOkResult()
    }

    companion object {
        const val SERVICE_ID = "NoLogger"
    }
}
