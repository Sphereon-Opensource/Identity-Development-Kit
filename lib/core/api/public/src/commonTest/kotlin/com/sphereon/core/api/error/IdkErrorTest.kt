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
 *
 */

package com.sphereon.core.api.error

import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdkErrorBasicTest {
    @Test
    fun idkErrorHasCorrectCode() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertEquals("TEST_ERROR", error.code)
    }

    @Test
    fun idkErrorHasCorrectMessage() {
        val message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message")
        val error = IdkError(code = "TEST_ERROR", message = message)
        assertEquals(message, error.message)
        assertEquals("Test message", error.message.defaultMessage)
        assertEquals("test.key", error.message.i18nKey)
    }

    @Test
    fun idkErrorDefaultSeverityIsError() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertEquals(IdkError.Severity.ERROR, error.severity)
    }

    @Test
    fun idkErrorHasCustomSeverity() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                severity = IdkError.Severity.WARNING,
            )
        assertEquals(IdkError.Severity.WARNING, error.severity)
    }

    @Test
    fun idkErrorHasEmptyCausesByDefault() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertTrue(error.causes.isEmpty())
    }

    @Test
    fun idkErrorHasEmptyMetaByDefault() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertTrue(error.meta.isEmpty())
    }

    @Test
    fun idkErrorHasNullExceptionByDefault() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertNull(error.exception)
    }

    @Test
    fun idkErrorToStringContainsCode() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertTrue(error.toString().contains("TEST_ERROR"))
    }

    @Test
    fun idkErrorToStringContainsSeverity() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                severity = IdkError.Severity.FATAL,
            )
        assertTrue(error.toString().contains("FATAL"))
    }
}

class IdkErrorHelperMethodsTest {
    @Test
    fun hasExceptionReturnsFalseWhenNoException() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertFalse(error.hasException())
    }

    @Test
    fun hasExceptionReturnsTrueWhenExceptionPresent() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                exception = RuntimeException("test"),
            )
        assertTrue(error.hasException())
    }

    @Test
    fun hasCausesReturnsFalseWhenEmpty() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertFalse(error.hasCauses())
    }

    @Test
    fun hasCausesReturnsTrueWhenNotEmpty() {
        val cause =
            IdkError(
                code = "CAUSE_ERROR",
                message = IdkError.Message(i18nKey = "cause.key", defaultMessage = "Cause message"),
            )
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                causes = mutableListOf(cause),
            )
        assertTrue(error.hasCauses())
    }

    @Test
    fun hasMetaReturnsFalseWhenEmpty() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
            )
        assertFalse(error.hasMeta())
    }

    @Test
    fun hasMetaReturnsTrueWhenNotEmpty() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                meta = mutableMapOf("key" to "value"),
            )
        assertTrue(error.hasMeta())
    }

    @Test
    fun addCauseAddsCauseToList() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                causes = mutableListOf(),
            )
        val cause =
            IdkError(
                code = "CAUSE_ERROR",
                message = IdkError.Message(i18nKey = "cause.key", defaultMessage = "Cause message"),
            )
        error.addCause(cause)
        assertTrue(error.hasCauses())
        assertEquals(1, error.causes.size)
    }

    @Test
    fun addCauseReturnsSameError() {
        val error =
            IdkError(
                code = "TEST_ERROR",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                causes = mutableListOf(),
            )
        val cause =
            IdkError(
                code = "CAUSE_ERROR",
                message = IdkError.Message(i18nKey = "cause.key", defaultMessage = "Cause message"),
            )
        val returned = error.addCause(cause)
        assertEquals(error, returned)
    }
}

class IdkErrorSeverityTest {
    @Test
    fun severityInfoHasValue10() {
        assertEquals(10, IdkError.Severity.INFO.value)
    }

    @Test
    fun severityWarningHasValue20() {
        assertEquals(20, IdkError.Severity.WARNING.value)
    }

    @Test
    fun severityErrorHasValue30() {
        assertEquals(30, IdkError.Severity.ERROR.value)
    }

    @Test
    fun severityFatalHasValue40() {
        assertEquals(40, IdkError.Severity.FATAL.value)
    }

    @Test
    fun isAtLeastReturnsTrueForHigherSeverity() {
        assertTrue(IdkError.Severity.FATAL.isAtLeast(IdkError.Severity.ERROR))
        assertTrue(IdkError.Severity.ERROR.isAtLeast(IdkError.Severity.WARNING))
        assertTrue(IdkError.Severity.WARNING.isAtLeast(IdkError.Severity.INFO))
    }

    @Test
    fun isAtLeastReturnsTrueForEqualSeverity() {
        assertTrue(IdkError.Severity.ERROR.isAtLeast(IdkError.Severity.ERROR))
    }

    @Test
    fun isAtLeastReturnsFalseForLowerSeverity() {
        assertFalse(IdkError.Severity.INFO.isAtLeast(IdkError.Severity.ERROR))
        assertFalse(IdkError.Severity.WARNING.isAtLeast(IdkError.Severity.FATAL))
    }

    @Test
    fun isAtMostReturnsTrueForLowerSeverity() {
        assertTrue(IdkError.Severity.INFO.isAtMost(IdkError.Severity.ERROR))
        assertTrue(IdkError.Severity.WARNING.isAtMost(IdkError.Severity.FATAL))
    }

    @Test
    fun isAtMostReturnsTrueForEqualSeverity() {
        assertTrue(IdkError.Severity.ERROR.isAtMost(IdkError.Severity.ERROR))
    }

    @Test
    fun isAtMostReturnsFalseForHigherSeverity() {
        assertFalse(IdkError.Severity.FATAL.isAtMost(IdkError.Severity.ERROR))
        assertFalse(IdkError.Severity.ERROR.isAtMost(IdkError.Severity.WARNING))
    }
}

class IdkErrorMessageTest {
    @Test
    fun messageHasI18nKey() {
        val message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test")
        assertEquals("test.key", message.i18nKey)
    }

    @Test
    fun messageHasDefaultMessage() {
        val message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message")
        assertEquals("Test message", message.defaultMessage)
    }

    @Test
    fun messageHasEmptyI18nParamsByDefault() {
        val message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test")
        assertTrue(message.i18nParams.isEmpty())
    }

    @Test
    fun messageHasI18nParams() {
        val params = mapOf("name" to "John", "count" to 5)
        val message = IdkError.Message(i18nKey = "test.key", i18nParams = params, defaultMessage = "Test")
        assertEquals(2, message.i18nParams.size)
        assertEquals("John", message.i18nParams["name"])
        assertEquals(5, message.i18nParams["count"])
    }
}

class IdkErrorFactoryMethodsTest {
    @Test
    fun unknownErrorHasCorrectCode() {
        val error = IdkError.UNKNOWN_ERROR()
        assertEquals("UNKNOWN_ERROR", error.code)
    }

    @Test
    fun unknownErrorHasDefaultMessage() {
        val error = IdkError.UNKNOWN_ERROR()
        assertEquals("An unknown error occurred", error.message.defaultMessage)
    }

    @Test
    fun unknownErrorHasCustomMessage() {
        val error = IdkError.UNKNOWN_ERROR(message = "Custom error message")
        assertEquals("Custom error message", error.message.defaultMessage)
    }

    @Test
    fun unknownErrorHasException() {
        val exception = RuntimeException("test")
        val error = IdkError.UNKNOWN_ERROR(exception = exception)
        assertEquals(exception, error.exception)
    }

    @Test
    fun illegalArgumentErrorHasCorrectCode() {
        val error = IdkError.ILLEGAL_ARGUMENT_ERROR()
        assertEquals("ILLEGAL_ARGUMENT_ERROR", error.code)
    }

    @Test
    fun illegalArgumentErrorIncludesArgInMessage() {
        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(arg = "badValue")
        assertTrue(error.message.defaultMessage.contains("badValue"))
    }

    @Test
    fun illegalArgumentErrorHasCustomMessage() {
        val error = IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Custom message")
        assertEquals("Custom message", error.message.defaultMessage)
    }

    @Test
    fun notFoundErrorHasCorrectCode() {
        val error = IdkError.NOT_FOUND_ERROR()
        assertEquals("NOT_FOUND_ERROR", error.code)
    }

    @Test
    fun notFoundErrorIncludesResourceInMessage() {
        val error = IdkError.NOT_FOUND_ERROR(resource = "User:123")
        assertTrue(error.message.defaultMessage.contains("User:123"))
    }

    @Test
    fun notFoundErrorHasDefaultMessageWithoutResource() {
        val error = IdkError.NOT_FOUND_ERROR()
        assertEquals("Not found", error.message.defaultMessage)
    }

    @Test
    fun commandDisabledErrorHasCorrectCode() {
        val error = IdkError.COMMAND_DISABLED_ERROR(commandId = "testCommand")
        assertEquals("COMMAND_DISABLED", error.code)
    }

    @Test
    fun commandDisabledErrorIncludesCommandIdInMessage() {
        val error = IdkError.COMMAND_DISABLED_ERROR(commandId = "testCommand")
        assertTrue(error.message.defaultMessage.contains("testCommand"))
    }

    @Test
    fun commandDisabledErrorIncludesCommandIdAndMessage() {
        val error = IdkError.COMMAND_DISABLED_ERROR(commandId = "testCommand")
        assertEquals("COMMAND_DISABLED", error.code)
        assertTrue(error.message.defaultMessage.contains("testCommand"))
    }

    @Test
    fun commandNotAuthorizedErrorHasCorrectCode() {
        val error = IdkError.COMMAND_NOT_AUTHORIZED_ERROR(commandId = "testCommand", reason = "No permission")
        assertEquals("COMMAND_NOT_AUTHORIZED", error.code)
    }

    @Test
    fun commandNotAuthorizedErrorIncludesReasonInMessage() {
        val error = IdkError.COMMAND_NOT_AUTHORIZED_ERROR(commandId = "testCommand", reason = "No permission")
        assertTrue(error.message.defaultMessage.contains("No permission"))
    }

    @Test
    fun commandNotAuthorizedErrorHasMeta() {
        val error = IdkError.COMMAND_NOT_AUTHORIZED_ERROR(commandId = "testCommand", reason = "No permission")
        assertEquals("testCommand", error.meta["commandId"])
        assertEquals("No permission", error.meta["reason"])
    }

    @Test
    fun forbiddenErrorHasCorrectCode() {
        val error = IdkError.FORBIDDEN_ERROR()
        assertEquals("FORBIDDEN", error.code)
    }

    @Test
    fun forbiddenErrorHasDefaultMessage() {
        val error = IdkError.FORBIDDEN_ERROR()
        assertEquals("Access forbidden", error.message.defaultMessage)
    }

    @Test
    fun forbiddenErrorHasCustomMessage() {
        val error = IdkError.FORBIDDEN_ERROR(message = "Custom forbidden message")
        assertEquals("Custom forbidden message", error.message.defaultMessage)
    }

    @Test
    fun commandArgNotSupportedErrorHasCorrectCode() {
        val error = IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR()
        assertEquals("COMMAND_ARG_NOT_SUPPORTED_ERROR", error.code)
    }

    @Test
    fun commandArgNotSupportedErrorIncludesOnlyArgTypeInMessage() {
        val error = IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(arg = "unsupportedArg")
        assertTrue(error.message.defaultMessage.contains("String"))
        assertFalse(error.message.defaultMessage.contains("unsupportedArg"))
    }

    @Test
    fun commandArgNotSupportedErrorDoesNotReflectHttpRequestSecrets() {
        val error =
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(
                arg =
                    GenericHttpRequest(
                        method = "POST",
                        path = "/api/platform/config/v1/tenants/tenant-a/kms/providers",
                        headers = mapOf("Authorization" to "Bearer secret-token"),
                        bodyContent = GenericHttpBody.Text("""{"clientSecret":"secret-body"}"""),
                    ),
            )

        assertTrue(error.message.defaultMessage.contains("GenericHttpRequest"))
        assertFalse(error.message.defaultMessage.contains("secret-token"))
        assertFalse(error.message.defaultMessage.contains("secret-body"))
        assertFalse(error.message.defaultMessage.contains("/api/platform/config"))
    }
}

class IdkErrorFromDTOTest {
    @Test
    fun fromDTOCreatesErrorWithSameCode() {
        val original =
            IdkError(
                code = "ORIGINAL_CODE",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test"),
                severity = IdkError.Severity.WARNING,
            )
        val copy = IdkError.fromDTO(original)
        assertEquals("ORIGINAL_CODE", copy.code)
    }

    @Test
    fun fromDTOCreatesErrorWithSameSeverity() {
        val original =
            IdkError(
                code = "ORIGINAL_CODE",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test"),
                severity = IdkError.Severity.WARNING,
            )
        val copy = IdkError.fromDTO(original)
        assertEquals(IdkError.Severity.WARNING, copy.severity)
    }

    @Test
    fun fromDTOCreatesErrorWithSameMessage() {
        val original =
            IdkError(
                code = "ORIGINAL_CODE",
                message = IdkError.Message(i18nKey = "test.key", defaultMessage = "Test message"),
                severity = IdkError.Severity.WARNING,
            )
        val copy = IdkError.fromDTO(original)
        assertEquals("Test message", copy.message.defaultMessage)
    }
}

class IdkErrorFromStringTest {
    @Test
    fun fromStringCreatesErrorWithMessage() {
        val error = IdkError.fromString("Test error message")
        assertEquals("Test error message", error.message.defaultMessage)
    }

    @Test
    fun fromStringCreatesErrorWithDefaultCode() {
        val error = IdkError.fromString("Test error message")
        assertEquals("UNKNOWN_ERROR", error.code)
    }

    @Test
    fun fromStringCreatesErrorWithCustomCode() {
        val error = IdkError.fromString("Test error message", code = "CUSTOM_CODE")
        assertEquals("CUSTOM_CODE", error.code)
    }

    @Test
    fun fromStringCreatesErrorWithException() {
        val exception = RuntimeException("test")
        val error = IdkError.fromString("Test error message", exception = exception)
        assertEquals(exception, error.exception)
    }

    @Test
    fun fromStringCreatesErrorWithCustomSeverity() {
        val error = IdkError.fromString("Test error message", severity = IdkError.Severity.FATAL)
        assertEquals(IdkError.Severity.FATAL, error.severity)
    }

    @Test
    fun fromStringCreatesErrorWithI18nKey() {
        val error = IdkError.fromString("Test error message", i18nKey = "custom.i18n.key")
        assertEquals("custom.i18n.key", error.message.i18nKey)
    }
}

class IdkErrorFromDefinitionTest {
    private class TestErrorDefinition : ErrorDefinitionType {
        override val code = "TEST_DEFINITION_CODE"
        override val i18nKey = "test.definition.key"
        override val defaultMessage = "Default definition message"
        override val severity = IdkError.Severity.WARNING
    }

    @Test
    fun fromDefinitionUsesDefinitionCode() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition)
        assertEquals("TEST_DEFINITION_CODE", error.code)
    }

    @Test
    fun fromDefinitionUsesDefinitionI18nKey() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition)
        assertEquals("test.definition.key", error.message.i18nKey)
    }

    @Test
    fun fromDefinitionUsesDefinitionDefaultMessage() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition)
        assertEquals("Default definition message", error.message.defaultMessage)
    }

    @Test
    fun fromDefinitionUsesDefinitionSeverity() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition)
        assertEquals(IdkError.Severity.WARNING, error.severity)
    }

    @Test
    fun fromDefinitionAllowsSeverityOverride() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition, severity = IdkError.Severity.FATAL)
        assertEquals(IdkError.Severity.FATAL, error.severity)
    }

    @Test
    fun fromDefinitionIncludesI18nParams() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition, i18nParams = mapOf("name" to "test"))
        assertEquals("test", error.message.i18nParams["name"])
    }

    @Test
    fun fromDefinitionIncludesMeta() {
        val definition = TestErrorDefinition()
        val error = IdkError.fromDefinition(definition, meta = mapOf("key" to "value"))
        assertEquals("value", error.meta["key"])
    }
}

class ErrorDefinitionTypeTest {
    private class TestDefinition : ErrorDefinitionType {
        override val code = "TEST_CODE"
        override val i18nKey = "test.key"
        override val defaultMessage = "Test message"
        override val severity = IdkError.Severity.ERROR
    }

    @Test
    fun asErrorCreatesIdkError() {
        val definition = TestDefinition()
        val error = definition.asError()
        assertEquals("TEST_CODE", error.code)
    }

    @Test
    fun asErrorAllowsParameterOverrides() {
        val definition = TestDefinition()
        val error = definition.asError(severity = IdkError.Severity.FATAL)
        assertEquals(IdkError.Severity.FATAL, error.severity)
    }

    @Test
    fun asResultCreatesErrResult() {
        val definition = TestDefinition()
        val result = definition.asResult()
        assertTrue(result.isErr)
    }
}

class InvalidStateErrorTest {
    @Test
    fun invalidStateErrorHasCorrectCode() {
        val error = IdkError.INVALID_STATE()
        assertEquals("INVALID_STATE", error.code)
    }

    @Test
    fun invalidStateErrorHasDefaultMessage() {
        val error = IdkError.INVALID_STATE()
        assertEquals("Invalid state", error.message.defaultMessage)
    }

    @Test
    fun invalidStateErrorHasCustomMessage() {
        val error = IdkError.INVALID_STATE(message = "The state machine is in an invalid state")
        assertEquals("The state machine is in an invalid state", error.message.defaultMessage)
    }

    @Test
    fun invalidStateErrorHasDefaultSeverity() {
        val error = IdkError.INVALID_STATE()
        assertEquals(IdkError.Severity.ERROR, error.severity)
    }

    @Test
    fun invalidStateErrorHasCustomSeverity() {
        val error = IdkError.INVALID_STATE(severity = IdkError.Severity.FATAL)
        assertEquals(IdkError.Severity.FATAL, error.severity)
    }

    @Test
    fun invalidStateErrorHasCauses() {
        val cause = IdkError.UNKNOWN_ERROR(message = "Root cause")
        val error = IdkError.INVALID_STATE(causes = listOf(cause))
        assertEquals(1, error.causes.size)
    }

    @Test
    fun invalidStateErrorHasException() {
        val exception = IllegalStateException("Bad state")
        val error = IdkError.INVALID_STATE(throwable = exception)
        assertEquals(exception, error.exception)
    }

    @Test
    fun invalidStateErrorHasCorrectI18nKey() {
        val error = IdkError.INVALID_STATE()
        assertEquals("com.sphereon.core.error.invalid-state", error.message.i18nKey)
    }
}

class NotFoundExceptionTest {
    @Test
    fun notFoundExceptionHasResource() {
        val exception = NotFoundException(resource = "User:123")
        assertEquals("User:123", exception.resource)
    }

    @Test
    fun notFoundExceptionHasMessage() {
        val exception = NotFoundException(resource = "User:123", message = "User not found")
        assertEquals("User not found", exception.message)
    }

    @Test
    fun notFoundExceptionHasCause() {
        val cause = RuntimeException("original")
        val exception = NotFoundException(resource = "User:123", message = "User not found", cause = cause)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun notFoundExceptionToStringContainsResource() {
        val exception = NotFoundException(resource = "User:123", message = "User not found")
        val str = exception.toString()
        assertTrue(str.contains("User:123"))
        assertTrue(str.contains("NotFoundException"))
    }

    @Test
    fun notFoundExceptionAsErrorCreatesIdkError() {
        val exception = NotFoundException(resource = "User:123", message = "User not found")
        val error = exception.asError()
        assertEquals("NOT_FOUND_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("User not found"))
        assertEquals(exception, error.exception)
    }

    @Test
    fun notFoundExceptionAsErrorWithNullMessage() {
        val exception = NotFoundException(resource = "User:123")
        val error = exception.asError()
        assertEquals("NOT_FOUND_ERROR", error.code)
        assertTrue(error.message.defaultMessage.contains("resource not found"))
    }
}
