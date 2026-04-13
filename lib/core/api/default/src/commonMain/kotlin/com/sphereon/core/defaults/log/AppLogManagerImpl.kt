package com.sphereon.core.defaults.log

import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.log.AbstractLogManager
import com.sphereon.core.api.log.Log
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AppLogManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppLogManagerImpl", exact = true)
class AppLogManagerImpl(loggers: Set<LogService>) : AbstractLogManager(scope = IdkScope.APP, loggers), AppLogManager {
    init {
        Log.register(scope = IdkScope.APP, this)
    }
}
