/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.core.api.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogLevelValuesTest {

    @Test
    fun traceHasLowestValue() {
        assertEquals(0, LogLevel.TRACE.value)
    }

    @Test
    fun debugHasCorrectValue() {
        assertEquals(10, LogLevel.DEBUG.value)
    }

    @Test
    fun infoHasCorrectValue() {
        assertEquals(20, LogLevel.INFO.value)
    }

    @Test
    fun warnHasCorrectValue() {
        assertEquals(30, LogLevel.WARN.value)
    }

    @Test
    fun errorHasCorrectValue() {
        assertEquals(40, LogLevel.ERROR.value)
    }

    @Test
    fun offHasHighestValue() {
        assertEquals(100, LogLevel.OFF.value)
    }

    @Test
    fun toStringReturnsName() {
        assertEquals("TRACE", LogLevel.TRACE.toString())
        assertEquals("DEBUG", LogLevel.DEBUG.toString())
        assertEquals("INFO", LogLevel.INFO.toString())
        assertEquals("WARN", LogLevel.WARN.toString())
        assertEquals("ERROR", LogLevel.ERROR.toString())
        assertEquals("OFF", LogLevel.OFF.toString())
    }

    @Test
    fun levelOrderIsCorrect() {
        assertTrue(LogLevel.TRACE.value < LogLevel.DEBUG.value)
        assertTrue(LogLevel.DEBUG.value < LogLevel.INFO.value)
        assertTrue(LogLevel.INFO.value < LogLevel.WARN.value)
        assertTrue(LogLevel.WARN.value < LogLevel.ERROR.value)
        assertTrue(LogLevel.ERROR.value < LogLevel.OFF.value)
    }

    @Test
    fun enumHasSixValues() {
        assertEquals(6, LogLevel.entries.size)
    }
}

class LoggerConfigTest {

    @Test
    fun defaultConfigUsesDebugLevel() {
        val config = LoggerConfig.Default
        assertEquals(LogLevel.DEBUG, config.minLevel)
    }

    @Test
    fun defaultConfigHasSphereonTag() {
        val config = LoggerConfig.Default
        assertEquals("sphereon", config.tag)
    }

    @Test
    fun debugConfigUsesDebugLevel() {
        val config = LoggerConfig.Debug
        assertEquals(LogLevel.DEBUG, config.minLevel)
    }

    @Test
    fun disabledConfigUsesOffLevel() {
        val config = LoggerConfig.Disabled
        assertEquals(LogLevel.OFF, config.minLevel)
    }

    @Test
    fun isEnabledReturnsTrueForHigherLevels() {
        val config = LoggerConfig(minLevel = LogLevel.INFO)
        assertTrue(config.isEnabled(LogLevel.INFO))
        assertTrue(config.isEnabled(LogLevel.WARN))
        assertTrue(config.isEnabled(LogLevel.ERROR))
    }

    @Test
    fun isEnabledReturnsFalseForLowerLevels() {
        val config = LoggerConfig(minLevel = LogLevel.INFO)
        assertEquals(false, config.isEnabled(LogLevel.DEBUG))
        assertEquals(false, config.isEnabled(LogLevel.TRACE))
    }

    @Test
    fun disableReturnsConfigWithOffLevel() {
        val config = LoggerConfig(minLevel = LogLevel.DEBUG)
        val disabled = config.disable()
        assertEquals(LogLevel.OFF, disabled.minLevel)
    }

    @Test
    fun disablePreservesTag() {
        val config = LoggerConfig(minLevel = LogLevel.DEBUG, tag = "custom-tag")
        val disabled = config.disable()
        assertEquals("custom-tag", disabled.tag)
    }

    @Test
    fun copyCreatesNewInstance() {
        val original = LoggerConfig(minLevel = LogLevel.DEBUG, tag = "original")
        val copy = original.copy(minLevel = LogLevel.INFO)
        assertEquals(LogLevel.DEBUG, original.minLevel)
        assertEquals(LogLevel.INFO, copy.minLevel)
        assertEquals("original", copy.tag)
    }

    @Test
    fun customConfigWithDifferentValues() {
        val config = LoggerConfig(minLevel = LogLevel.WARN, tag = "my-app")
        assertEquals(LogLevel.WARN, config.minLevel)
        assertEquals("my-app", config.tag)
    }
}

class LogMessageTest {

    @Test
    fun defaultLevelIsDebug() {
        val message = LogMessage(message = "test message")
        assertEquals(LogLevel.DEBUG, message.level)
    }

    @Test
    fun messageIsStoredCorrectly() {
        val message = LogMessage(message = "test message")
        assertEquals("test message", message.message)
    }

    @Test
    fun tagDefaultsToNull() {
        val message = LogMessage(message = "test")
        assertEquals(null, message.tag)
    }

    @Test
    fun tagCanBeSet() {
        val message = LogMessage(message = "test", tag = "my-tag")
        assertEquals("my-tag", message.tag)
    }

    @Test
    fun exceptionDefaultsToNull() {
        val message = LogMessage(message = "test")
        assertEquals(null, message.exception)
    }

    @Test
    fun exceptionCanBeSet() {
        val exception = RuntimeException("test error")
        val message = LogMessage(message = "test", exception = exception)
        assertEquals(exception, message.exception)
    }

    @Test
    fun errorResultDefaultsToNull() {
        val message = LogMessage(message = "test")
        assertEquals(null, message.errorResult)
    }

    @Test
    fun allParametersCanBeSet() {
        val exception = RuntimeException("error")
        val message = LogMessage(
            level = LogLevel.ERROR,
            message = "error message",
            tag = "error-tag",
            exception = exception
        )
        assertEquals(LogLevel.ERROR, message.level)
        assertEquals("error message", message.message)
        assertEquals("error-tag", message.tag)
        assertEquals(exception, message.exception)
    }

    @Test
    fun copyCreatesNewMessage() {
        val original = LogMessage(message = "original")
        val copy = original.copy(message = "copied")
        assertEquals("original", original.message)
        assertEquals("copied", copy.message)
    }

    @Test
    fun copyPreservesOtherFields() {
        val original = LogMessage(level = LogLevel.WARN, message = "original", tag = "test-tag")
        val copy = original.copy(message = "copied")
        assertEquals(LogLevel.WARN, copy.level)
        assertEquals("test-tag", copy.tag)
    }
}

class LogErrorTest {

    @Test
    fun serviceDisabledHasCorrectCode() {
        assertEquals("SERVICE_DISABLED", LogError.ServiceDisabled.code)
    }

    @Test
    fun serviceDisabledHasCorrectDefaultMessage() {
        assertEquals("The logging session is disabled", LogError.ServiceDisabled.defaultMessage)
    }

    @Test
    fun serviceDisabledHasCorrectI18nKey() {
        assertEquals("errors.com.sphereon.core.log.session-disabled", LogError.ServiceDisabled.i18nKey)
    }

    @Test
    fun noLoggersConfiguredHasCorrectCode() {
        assertEquals("NO_LOGGERS_CONFIGURED", LogError.NoLoggersConfigured.code)
    }

    @Test
    fun noLoggersConfiguredHasCorrectDefaultMessage() {
        assertEquals("No loggers configured", LogError.NoLoggersConfigured.defaultMessage)
    }

    @Test
    fun noLoggersConfiguredHasCorrectI18nKey() {
        assertEquals("errors.com.sphereon.core.log.no-loggers-configured", LogError.NoLoggersConfigured.i18nKey)
    }

    @Test
    fun notFoundHasCorrectCode() {
        val notFound = LogError.NotFound("custom message")
        assertEquals("NOT_FOUND", notFound.code)
    }

    @Test
    fun notFoundHasCorrectI18nKey() {
        val notFound = LogError.NotFound("custom message")
        assertEquals("errors.com.sphereon.core.log.not-found", notFound.i18nKey)
    }

    @Test
    fun notFoundUsesCustomMessage() {
        val notFound = LogError.NotFound("custom message")
        assertEquals("custom message", notFound.defaultMessage)
    }

    @Test
    fun notFoundWithNullMessageUsesDefault() {
        val notFound = LogError.NotFound(null)
        assertEquals("Not found", notFound.defaultMessage)
    }

    @Test
    fun getByCodeFindsServiceDisabled() {
        val found = LogError.ServiceDisabled.getByCode("SERVICE_DISABLED")
        assertEquals(LogError.ServiceDisabled, found)
    }

    @Test
    fun getByCodeFindsNoLoggersConfigured() {
        val found = LogError.ServiceDisabled.getByCode("NO_LOGGERS_CONFIGURED")
        assertEquals(LogError.NoLoggersConfigured, found)
    }

    @Test
    fun getByCodeReturnsNotFoundForUnknownCode() {
        val found = LogError.ServiceDisabled.getByCode("UNKNOWN_CODE")
        assertTrue(found is LogError.NotFound)
    }

    @Test
    fun getByI18nKeyFindsServiceDisabled() {
        val found = LogError.ServiceDisabled.getByI18nKey("errors.com.sphereon.core.log.session-disabled")
        assertEquals(LogError.ServiceDisabled, found)
    }

    @Test
    fun getByI18nKeyFindsNoLoggersConfigured() {
        val found = LogError.ServiceDisabled.getByI18nKey("errors.com.sphereon.core.log.no-loggers-configured")
        assertEquals(LogError.NoLoggersConfigured, found)
    }

    @Test
    fun getByI18nKeyReturnsNotFoundForUnknownKey() {
        val found = LogError.ServiceDisabled.getByI18nKey("unknown.key")
        assertTrue(found is LogError.NotFound)
    }
}

class LogServiceFilterExtensionsTest {

    @Test
    fun filterScopeFiltersCorrectly() {
        val loggers = setOf(AppConsoleLogServiceImpl())
        val filtered = loggers.filterScope(com.sphereon.core.api.context.IdkScope.APP)
        assertEquals(1, filtered.size)
    }

    @Test
    fun filterScopeReturnsEmptyForNonMatchingScope() {
        val loggers = setOf(AppConsoleLogServiceImpl())
        val filtered = loggers.filterScope(com.sphereon.core.api.context.IdkScope.SESSION)
        assertEquals(0, filtered.size)
    }

    @Test
    fun filterEnabledFiltersDisabledLoggers() {
        val loggers = setOf(AppConsoleLogServiceImpl())
        val filtered = loggers.filterEnabled()
        assertEquals(1, filtered.size)
    }
}
