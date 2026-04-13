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

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.log.AbstractLogManager
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.toSessionContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<UserContextLogManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextLogManagerImpl", exact = true)
class UserContextLogManagerImpl(
    loggers: Set<LogService>,
    userContextInstance: UserContextInstance,
) : AbstractLogManager(scope = IdkScope.USER, loggers = loggers, runtimeSessionContext = userContextInstance.toSessionContext()),
    UserContextLogManager
