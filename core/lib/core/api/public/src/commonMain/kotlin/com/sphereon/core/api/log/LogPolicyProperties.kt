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
 */

package com.sphereon.core.api.log

import com.sphereon.core.api.conf.CommandConfigScope
import com.sphereon.core.api.conf.CommandScopedConfigBinder
import com.sphereon.core.api.conf.getConfig
import com.sphereon.core.api.conf.toCommandScopedBinder
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.context.UserScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Serializable log policy configuration that can be bound from properties.
 *
 * Property layout (all lowercase):
 * ```properties
 * # Global log policy (bare keys)
 * logging.policy.min.level=INFO
 * logging.policy.by.scope.app=INFO
 * logging.policy.by.scope.session=DEBUG
 * logging.policy.by.module.kms=TRACE
 *
 * # Per-command log levels via cmd.* scoping (preferred for dotted patterns):
 * cmd.kms.keys.get.logging.policy.min.level=TRACE
 * cmd.oid4vp.default.default.logging.policy.min.level=DEBUG
 * ```
 *
 * For command-level log overrides, prefer the `cmd.*` scoping mechanism over
 * `by.command` maps, since command IDs contain dots that conflict with property key
 * separators. The `by.module` map works well for simple single-segment keys.
 */
@JsExportCompat
@Serializable
data class LogPolicyProperties(
    val minLevel: LogLevel? = null,
    val by: LogPolicyByProperties? = null,
    val disabled: LogPolicyDisabledProperties? = null,
) {
    companion object {
        const val CONFIG_SUFFIX = "logging.policy"
    }
}

@JsExportCompat
@Serializable
data class LogPolicyByProperties(
    val scope: Map<String, String> = emptyMap(),
    val module: Map<String, String> = emptyMap(),
    val service: Map<String, String> = emptyMap(),
    val command: Map<String, String> = emptyMap(),
)

@JsExportCompat
@Serializable
data class LogPolicyDisabledProperties(
    val scopes: List<String> = emptyList(),
    val servicePatterns: List<String> = emptyList(),
    val commandPatterns: List<String> = emptyList(),
)

/**
 * Convert config-driven [LogPolicyProperties] to the runtime [LogPolicy].
 *
 * - `by.scope` entries map to [LogPolicy.minLevelByScope]
 * - `by.module` entries become service patterns with `module.*` wildcard
 * - `by.service` entries map to [LogPolicy.minLevelByServicePattern]
 * - `by.command` entries map to [LogPolicy.minLevelByCommandPattern]
 */
fun LogPolicyProperties.toLogPolicy(): LogPolicy {
    val byProps = by ?: LogPolicyByProperties()
    val disabledProps = disabled ?: LogPolicyDisabledProperties()

    val minLevelByScope =
        byProps.scope
            .mapNotNull { (scopeStr, levelStr) ->
                val scope = IdkScope.entries.find { it.name.equals(scopeStr, ignoreCase = true) }
                val level = parseLogLevel(levelStr)
                if (scope != null && level != null) {
                    scope to level
                } else {
                    null
                }
            }.toMap()

    val minLevelByServicePattern =
        buildMap<String, LogLevel> {
            byProps.module.forEach { (module, levelStr) ->
                val level = parseLogLevel(levelStr)
                if (level != null) {
                    put("$module.*", level)
                }
            }
            byProps.service.forEach { (pattern, levelStr) ->
                val level = parseLogLevel(levelStr)
                if (level != null) {
                    put(pattern, level)
                }
            }
        }

    val minLevelByCommandPattern =
        byProps.command
            .mapNotNull { (pattern, levelStr) ->
                val level = parseLogLevel(levelStr)
                if (level != null) {
                    pattern to level
                } else {
                    null
                }
            }.toMap()

    val disabledScopes =
        disabledProps.scopes
            .mapNotNull { scopeStr ->
                IdkScope.entries.find { it.name.equals(scopeStr, ignoreCase = true) }
            }.toSet()

    return LogPolicy(
        minLevelByScope = minLevelByScope,
        minLevelByServicePattern = minLevelByServicePattern,
        minLevelByCommandPattern = minLevelByCommandPattern,
        disabledScopes = disabledScopes,
        disabledServicePatterns = disabledProps.servicePatterns.toSet(),
        disabledCommandPatterns = disabledProps.commandPatterns.toSet(),
    )
}

private fun parseLogLevel(str: String): LogLevel? = LogLevel.entries.find { it.name.equals(str, ignoreCase = true) }

/**
 * Resolves [LogPolicy] from configuration with optional command-scope overlay.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("LogPolicyConfigResolver", exact = true)
interface LogPolicyConfigResolver {
    fun resolve(): LogPolicy

    fun resolve(scope: CommandConfigScope): LogPolicy
}

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class LogPolicyConfigResolverImpl(
    private val contextConfig: ContextConfig,
) : LogPolicyConfigResolver {
    override fun resolve(): LogPolicy {
        val binder = contextConfig.toCommandScopedBinder(CommandConfigScope.GLOBAL)
        val props =
            binder.getConfig<LogPolicyProperties>(LogPolicyProperties.CONFIG_SUFFIX)
                ?: return LogPolicy.AllowAll
        return props.toLogPolicy()
    }

    override fun resolve(scope: CommandConfigScope): LogPolicy {
        val binder = contextConfig.toCommandScopedBinder(scope)
        val props =
            binder.getConfig<LogPolicyProperties>(LogPolicyProperties.CONFIG_SUFFIX)
                ?: return LogPolicy.AllowAll
        return props.toLogPolicy()
    }
}
