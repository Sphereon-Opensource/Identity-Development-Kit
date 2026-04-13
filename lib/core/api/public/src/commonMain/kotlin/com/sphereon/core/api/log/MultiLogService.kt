package com.sphereon.core.api.log

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType

import com.sphereon.core.api.session.MultiCommandAdapter
import com.sphereon.core.api.session.MultiService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

//@MultiService(id = MultiLogService.SERVICE_ID, plugin = "")
class MultiLogService(
    loggers: Set<LogService>,
    isEnabled: Boolean = true,
    override val scope: IdkScope,
    private val tag: String = "",
    private var config: LoggerConfig = LoggerConfig.Companion.Default,
    private var logPolicy: LogPolicy = LogPolicy.AllowAll,
    override val sessionContext: SessionContext = NoOpSessionContext,
) :
    MultiCommandAdapter<LogMessage, Unit, IdkErrorType>(
        id = SERVICE_ID,
        commands = loggers.map { it.toAsync() }.toMutableList(),
        isEnabled = isEnabled
    ), LogService, MultiService<LogMessage, Unit, IdkErrorType> {

    companion object {
        const val SERVICE_ID = "LogService"
    }

    private fun commandOrTag(message: LogMessage, fallbackTag: String? = null): String? =
        message.metadata?.get("commandId")
            ?: message.metadata?.get("command.id")
            ?: message.metadata?.get("command_id")
            ?: message.metadata?.get("tag")
            ?: message.tag
            ?: fallbackTag

    private fun isEnabledForService(serviceId: String, level: LogLevel, commandOrTag: String?): Boolean =
        logPolicy.isEnabled(
            level = level,
            defaultMinLevel = config.minLevel,
            scope = scope,
            serviceId = serviceId,
            commandOrTag = commandOrTag
        )

    override suspend fun execute(args: LogMessage): IdkResult<Unit, IdkErrorType> {
        if (!isEnabled) {
            // The superclass adapter throws an error in case it is not enabled, since logging can be disabled altogether let's not create errors for it.
            return LogError.ServiceDisabled.asResult(severity = IdkError.Severity.INFO)
        } else if (commands.isEmpty()) {
            return LogError.NoLoggersConfigured.asError(
                severity = IdkError.Severity.ERROR,
                causes = listOf(
                    IdkError(
                        code = "ORIGINAL_LOG_MESSAGE",
                        message = IdkError.Message(i18nKey = args.message, defaultMessage = args.message),
                        severity = IdkError.Severity.INFO
                    )
                )
            ).asErrorResult()
        }
        // Evaluate enablement using the service-owned runtime session context.
        val commandOrTag = commandOrTag(args, fallbackTag = this.tag)
        if (!isEnabled(level = args.level, tag = commandOrTag, context = this.sessionContext)) {
            return Unit.asOkResult()
        }
        return super.execute(args.copy(tag = args.tag ?: this.tag))
    }

    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage,
    ): IdkResult<Unit, IdkErrorType> {
        // TODO: Make this generic and put it in the template multi session execute or introduce a super doExecute
        val message = applyDuring(args)
        val commandOrTag = commandOrTag(message, fallbackTag = this.tag)
        val eligibleCommands = commands.filter { command ->
            isEnabledForService(
                serviceId = command.id,
                level = message.level,
                commandOrTag = commandOrTag
            )
        }
        if (eligibleCommands.isEmpty()) {
            return Unit.asOkResult()
        }

        val results = eligibleCommands.map {
            runCatching { it.execute(message) }.getOrElse {
                IdkError.UNKNOWN_ERROR(exception = it).asErrorResult()
            }
        }
        val errors = results.filter { it.isErr }.map { it.error }.toList()
        return if (errors.isNotEmpty()) {
            IdkError.UNKNOWN_ERROR(causes = errors).asErrorResult()
        } else {
            Unit.asOkResult()
        }
    }

    override suspend fun setConfig(config: LoggerConfig) = apply {
        this.config = config
    }
    override val policy: LogPolicy
        get() = logPolicy
    override suspend fun getPolicy(): LogPolicy = logPolicy
    override suspend fun setPolicy(policy: LogPolicy) = apply {
        this.logPolicy = policy
    }
    override fun isEnabled(level: LogLevel, tag: String?, context: SessionContext?): Boolean = isEnabled &&
        commands.any { command ->
            isEnabledForService(
                serviceId = command.id,
                level = level,
                commandOrTag = tag
            )
        }

    override fun executeAsync(
        message: LogMessage
    ): IdkResult<Unit, IdkErrorType> {
        coroutineScope.launch {
            execute(message)
        }
        return Unit.asOkResult()
    }

    override fun toAsync(): AsyncLogService = _async

    override suspend fun getConfig() = config

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val _async: AsyncLogService = object : AsyncLogService {
        override val sessionContext = this@MultiLogService.sessionContext
        override val scope: IdkScope = this@MultiLogService.scope
        override val id: String = this@MultiLogService.id
        override val isEnabled: Boolean = this@MultiLogService.isEnabled
        override val policy: LogPolicy
            get() = this@MultiLogService.policy
        override fun isEnabled(level: LogLevel, tag: String?, context: SessionContext?): Boolean =
            this@MultiLogService.isEnabled(level = level, tag = tag, context = context)

        override suspend fun setConfig(config: LoggerConfig) = apply { this@MultiLogService.setConfig(config) }
        override suspend fun getPolicy(): LogPolicy = this@MultiLogService.getPolicy()
        override suspend fun setPolicy(policy: LogPolicy) = apply { this@MultiLogService.setPolicy(policy) }

        override suspend fun execute(args: LogMessage): IdkResult<Unit, IdkErrorType> {
            return this@MultiLogService.execute(args)
        }

        override fun toSync(): LogService {
            return this@MultiLogService
        }
    }
}

