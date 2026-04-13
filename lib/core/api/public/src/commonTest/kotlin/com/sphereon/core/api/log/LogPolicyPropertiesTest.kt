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
import com.sphereon.core.api.conf.DefaultPropertySources
import com.sphereon.core.api.conf.MutableMapPropertySource
import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySourcesPropertyResolver
import com.sphereon.core.api.conf.getConfig
import com.sphereon.core.api.context.IdkScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogPolicyPropertiesToLogPolicyTest {
    @Test
    fun convertsMinLevelByScope() {
        // Map keys come from config binding — uppercase values need brackets
        // to survive normalization, but toLogPolicy strips them
        val props =
            LogPolicyProperties(
                by =
                    LogPolicyByProperties(
                        scope = mapOf("app" to "INFO", "session" to "DEBUG"),
                    ),
            )

        val policy = props.toLogPolicy()

        assertEquals(LogLevel.INFO, policy.minLevelByScope[IdkScope.APP])
        assertEquals(LogLevel.DEBUG, policy.minLevelByScope[IdkScope.SESSION])
    }

    @Test
    fun convertsModuleToServicePatternWithWildcard() {
        val props =
            LogPolicyProperties(
                by =
                    LogPolicyByProperties(
                        module = mapOf("kms" to "TRACE", "party" to "WARN"),
                    ),
            )

        val policy = props.toLogPolicy()

        assertEquals(LogLevel.TRACE, policy.minLevelByServicePattern["kms.*"])
        assertEquals(LogLevel.WARN, policy.minLevelByServicePattern["party.*"])
    }

    @Test
    fun convertsServicePatterns() {
        val props =
            LogPolicyProperties(
                by =
                    LogPolicyByProperties(
                        service = mapOf("kms.keys.*" to "TRACE"),
                    ),
            )

        val policy = props.toLogPolicy()

        assertEquals(LogLevel.TRACE, policy.minLevelByServicePattern["kms.keys.*"])
    }

    @Test
    fun convertsCommandPatterns() {
        val props =
            LogPolicyProperties(
                by =
                    LogPolicyByProperties(
                        command = mapOf("kms.keys.get" to "TRACE", "party.parties.create" to "DEBUG"),
                    ),
            )

        val policy = props.toLogPolicy()

        assertEquals(LogLevel.TRACE, policy.minLevelByCommandPattern["kms.keys.get"])
        assertEquals(LogLevel.DEBUG, policy.minLevelByCommandPattern["party.parties.create"])
    }

    @Test
    fun convertsDisabledScopes() {
        val props =
            LogPolicyProperties(
                disabled =
                    LogPolicyDisabledProperties(
                        scopes = listOf("APP"),
                        servicePatterns = listOf("internal.*"),
                        commandPatterns = listOf("health.*"),
                    ),
            )

        val policy = props.toLogPolicy()

        assertTrue(IdkScope.APP in policy.disabledScopes)
        assertTrue("internal.*" in policy.disabledServicePatterns)
        assertTrue("health.*" in policy.disabledCommandPatterns)
    }

    @Test
    fun emptyPropertiesProducesAllowAllEquivalent() {
        val props = LogPolicyProperties()
        val policy = props.toLogPolicy()

        assertTrue(policy.minLevelByScope.isEmpty())
        assertTrue(policy.minLevelByServicePattern.isEmpty())
        assertTrue(policy.minLevelByCommandPattern.isEmpty())
        assertTrue(policy.disabledScopes.isEmpty())
    }

    @Test
    fun caseInsensitiveParsing() {
        val props =
            LogPolicyProperties(
                by =
                    LogPolicyByProperties(
                        scope = mapOf("app" to "info", "session" to "debug"),
                        // For command patterns with dots, prefer cmd.* scoping in config.
                        // When using the map directly, the key is used as-is.
                        module = mapOf("kms" to "trace"),
                    ),
            )

        val policy = props.toLogPolicy()

        assertEquals(LogLevel.INFO, policy.minLevelByScope[IdkScope.APP])
        assertEquals(LogLevel.DEBUG, policy.minLevelByScope[IdkScope.SESSION])
        assertEquals(LogLevel.TRACE, policy.minLevelByServicePattern["kms.*"])
    }
}

class LogPolicyPropertiesConfigBindingTest {
    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun bindsMinLevelOnly() {
        val resolver =
            createResolver(
                "logging.policy.min.level" to "INFO",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.GLOBAL,
            )
        val config = binder.getConfig<LogPolicyProperties>(LogPolicyProperties.CONFIG_SUFFIX)
        kotlin.test.assertNotNull(config, "Config should not be null with just minLevel")
        assertEquals(LogLevel.INFO, config!!.minLevel)
    }

    @Test
    fun bindsFromGlobalProperties() {
        val resolver =
            createResolver(
                "logging.policy.min.level" to "INFO",
                "logging.policy.by.module.kms" to "TRACE",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.GLOBAL,
            )

        val config = binder.getConfig<LogPolicyProperties>(LogPolicyProperties.CONFIG_SUFFIX)
        kotlin.test.assertNotNull(config, "Config should bind from logging.policy prefix")
        assertEquals(LogLevel.INFO, config!!.minLevel)
        assertEquals("TRACE", config.by?.module?.get("kms"))
    }

    @Test
    fun moduleOverridesGlobalPolicy() {
        val resolver =
            createResolver(
                "logging.policy.min.level" to "INFO",
                "cmd.oid4vp.default.default.logging.policy.min.level" to "DEBUG",
            )
        val binder =
            CommandScopedConfigBinder(
                resolver = resolver,
                scope = CommandConfigScope.fromCommandId("oid4vp.verifier.request"),
            )

        val config = binder.getConfig<LogPolicyProperties>(LogPolicyProperties.CONFIG_SUFFIX)
        kotlin.test.assertNotNull(config)
        assertEquals(LogLevel.DEBUG, config!!.minLevel, "Module override wins")
    }
}
