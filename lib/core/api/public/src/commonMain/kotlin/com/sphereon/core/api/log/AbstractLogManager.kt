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

package com.sphereon.core.api.log

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionInstance

open class AbstractLogManager(
    private val scope: IdkScope,
    loggers: Set<LogService> = emptySet(),
    private val runtimeSessionContext: SessionContext = NoOpSessionContext,
    private var config: LoggerConfig = LoggerConfig.Default,
    private var policy: LogPolicy = LogPolicy.AllowAll,
) : LogManager {
    val loggers: Set<LogService> = loggers.filterEnabled().filterScope(scope)

    override suspend fun setGlobalConfig(config: LoggerConfig): LogManager =
        apply {
            this.config = config
            loggers.forEach { it.setConfig(config) }
        }

    override suspend fun getGlobalConfig() = config

    override suspend fun setGlobalPolicy(policy: LogPolicy): LogManager =
        apply {
            this.policy = policy
            loggers.forEach { it.setPolicy(policy) }
        }

    override suspend fun getGlobalPolicy(): LogPolicy = policy

    override fun withTagAsync(
        tag: String,
        config: LoggerConfig?,
    ): AsyncLogService = withTag(tag, config).toAsync()

    override fun withTag(
        tag: String,
        config: LoggerConfig?,
    ): LogService =
        MultiLogService(
            config = config ?: this.config,
            loggers = loggers,
            tag = tag,
            scope = scope,
            sessionContext = runtimeSessionContext,
            logPolicy = this.policy,
        )
}

/**
 * This object allows logging without injection. For user and session scope it does expect injected instances.
 */
object Log {
    fun register(
        scope: IdkScope,
        logManager: LogManager,
    ) = Registry.loggers.put(scope, logManager)

    internal object Registry {
        val loggers = mutableMapOf<IdkScope, LogManager>()
    }

    fun app(): LogManager = Registry.loggers[IdkScope.APP] ?: object : AbstractLogManager(scope = IdkScope.APP, loggers = setOf(AppConsoleLogServiceImpl())) {}

    fun sessionInstance(sessionInstance: SessionInstance): LogManager = sessionInstance.sessionExecution.log.logManager

    fun sessionExecution(sessionExecution: SessionExecution): LogManager = sessionExecution.log.logManager

    fun user(userContextInstance: UserContextInstance): LogManager = userContextInstance.graph.logManager
}
