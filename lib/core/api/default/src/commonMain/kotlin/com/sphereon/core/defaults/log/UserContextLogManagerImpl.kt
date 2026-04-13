package com.sphereon.core.defaults.log

import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.log.AbstractLogManager
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.toSessionContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<UserContextLogManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextLogManagerImpl", exact = true)
class UserContextLogManagerImpl(loggers: Set<LogService>, userContextInstance: UserContextInstance) :
    AbstractLogManager(scope = IdkScope.USER, loggers = loggers, runtimeSessionContext = userContextInstance.toSessionContext()), UserContextLogManager {
}
