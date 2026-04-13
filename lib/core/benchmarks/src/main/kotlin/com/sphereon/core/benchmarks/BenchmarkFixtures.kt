package com.sphereon.core.benchmarks

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.PropertySourcesPropertyResolver
import com.sphereon.core.api.conf.ScopedPropertySourceWrapper
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AbstractLogService
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.di.SortOrder

data class ResolverFixture(
    val resolver: PropertySourcesPropertyResolver,
    val directHitKey: String,
    val fallbackHitKey: String,
    val missKey: String
)

object BenchmarkFixtures {
    @JvmStatic
    fun disabledDebugLogService(): LogService = DisabledDebugLogService()

    @JvmStatic
    fun propertyResolverFixture(sizePerScope: Int = 128): ResolverFixture {
        val directHitKey = "bench.direct.hit"
        val fallbackHitKey = "bench.fallback.hit"
        val missKey = "bench.miss.path"

        val principalMap = LinkedHashMap<String, Any>(sizePerScope + 1)
        val tenantMap = LinkedHashMap<String, Any>(sizePerScope)
        val appMap = LinkedHashMap<String, Any>(sizePerScope + 1)

        repeat(sizePerScope) { idx ->
            principalMap["principal.setting.$idx"] = "principal-$idx"
            tenantMap["tenant.setting.$idx"] = "tenant-$idx"
            appMap["app.setting.$idx"] = "app-$idx"
        }

        principalMap[directHitKey] = "value-direct"
        appMap[fallbackHitKey] = "value-fallback"

        val propertySources = DefaultPropertySources(
            sources = mutableListOf(
                ScopedPropertySourceWrapper(
                    delegate = MapPropertySource(name = "bench-principal", source = principalMap, order = 10),
                    configLevel = ConfigLevel.PRINCIPAL
                ),
                ScopedPropertySourceWrapper(
                    delegate = MapPropertySource(name = "bench-tenant", source = tenantMap, order = 20),
                    configLevel = ConfigLevel.TENANT
                ),
                ScopedPropertySourceWrapper(
                    delegate = MapPropertySource(name = "bench-app", source = appMap, order = 30),
                    configLevel = ConfigLevel.APP
                )
            ),
            sorting = SortOrder.ASC
        )

        return ResolverFixture(
            resolver = PropertySourcesPropertyResolver(propertySources = propertySources),
            directHitKey = directHitKey,
            fallbackHitKey = fallbackHitKey,
            missKey = missKey
        )
    }
}

private class DisabledDebugLogService : AbstractLogService(
    id = "benchmark-disabled-debug-log-service",
    isEnabled = true,
    config = LoggerConfig(minLevel = LogLevel.ERROR)
), LogService {
    override val scope: IdkScope = IdkScope.APP

    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage
    ): IdkResult<Unit, IdkErrorType> = Unit.asOkResult()
}
