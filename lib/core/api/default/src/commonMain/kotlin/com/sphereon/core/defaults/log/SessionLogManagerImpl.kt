package com.sphereon.core.defaults.log

import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.log.AbstractLogManager
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SessionLogManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionLogManagerImpl", exact = true)
class SessionLogManagerImpl(runtimeSessionContext: SessionContext, loggers: Set<LogService>) :
    AbstractLogManager(IdkScope.SESSION, loggers, runtimeSessionContext), SessionLogManager {

}
