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

package com.sphereon.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdkBuilderTest {
    private class TestBuilder(
        private val value: String = "test",
        private val errors: List<String> = emptyList(),
    ) : IdkBuilder<String> {
        override fun validate(): List<String> = errors

        override fun buildInternal(): String = value
    }

    @Test
    fun buildSucceedsWhenValidationPasses() {
        val builder = TestBuilder(value = "success")
        assertEquals("success", builder.build())
    }

    @Test
    fun buildThrowsWhenValidationFails() {
        val builder = TestBuilder(errors = listOf("error1", "error2"))
        val exception =
            assertFailsWith<IllegalStateException> {
                builder.build()
            }
        assertTrue(exception.message!!.contains("error1"))
        assertTrue(exception.message!!.contains("error2"))
    }
}

class FluentBuilderTest {
    private class TestFluentBuilder : FluentBuilder<String, TestFluentBuilder> {
        var value: String = ""

        override fun validate(): List<String> = if (value.isEmpty()) listOf("value required") else emptyList()

        override fun buildInternal(): String = value

        fun withValue(v: String): TestFluentBuilder {
            value = v
            return self()
        }
    }

    @Test
    fun selfReturnsSameInstance() {
        val builder = TestFluentBuilder()
        val self = builder.self()
        assertEquals(builder, self)
    }

    @Test
    fun fluentChainingWorks() {
        val result =
            TestFluentBuilder()
                .withValue("fluent")
                .build()
        assertEquals("fluent", result)
    }
}

class ConfigBuilderTest {
    private class TestConfigBuilder : ConfigBuilder<String>() {
        var value: String = "default"

        override fun buildInternal(): String = value
    }

    @Test
    fun configureAppliesBlock() {
        val builder = TestConfigBuilder()
        builder.configure {
            (this as TestConfigBuilder).value = "configured"
        }
        assertEquals("configured", builder.build())
    }

    @Test
    fun configureReturnsBuilder() {
        val builder = TestConfigBuilder()
        val result = builder.configure { }
        assertEquals(builder, result)
    }

    @Test
    fun defaultValidationReturnsEmpty() {
        val builder = TestConfigBuilder()
        assertTrue(builder.validate().isEmpty())
    }
}

class BuilderValidationResultTest {
    @Test
    fun validResultIsValid() {
        val result = BuilderValidationResult.Valid
        assertTrue(result.isValid)
        assertFalse(result.isInvalid)
    }

    @Test
    fun invalidResultIsInvalid() {
        val result = BuilderValidationResult.Invalid(listOf("error"))
        assertFalse(result.isValid)
        assertTrue(result.isInvalid)
    }

    @Test
    fun invalidResultContainsErrors() {
        val result = BuilderValidationResult.Invalid(listOf("error1", "error2"))
        assertEquals(listOf("error1", "error2"), (result as BuilderValidationResult.Invalid).errors)
    }
}

class ToValidationResultTest {
    @Test
    fun emptyListReturnsValid() {
        val result = emptyList<String>().toValidationResult()
        assertTrue(result.isValid)
    }

    @Test
    fun nonEmptyListReturnsInvalid() {
        val result = listOf("error").toValidationResult()
        assertTrue(result.isInvalid)
    }
}

class BuildSafelyTest {
    private class SuccessBuilder : IdkBuilder<String> {
        override fun validate(): List<String> = emptyList()

        override fun buildInternal(): String = "success"
    }

    private class FailingValidationBuilder : IdkBuilder<String> {
        override fun validate(): List<String> = listOf("validation error")

        override fun buildInternal(): String = throw AssertionError("Should not be called")
    }

    private class ExceptionBuilder : IdkBuilder<String> {
        override fun validate(): List<String> = emptyList()

        override fun buildInternal(): String = throw RuntimeException("build failed")
    }

    @Test
    fun buildSafelyReturnsOkOnSuccess() {
        val builder = SuccessBuilder()
        val result = builder.buildSafely()
        assertTrue(result.isOk)
        assertEquals("success", result.getOrNull())
    }

    @Test
    fun buildSafelyReturnsErrOnValidationFailure() {
        val builder = FailingValidationBuilder()
        val result = builder.buildSafely()
        assertTrue(result.isErr)
    }

    @Test
    fun buildSafelyReturnsErrOnException() {
        val builder = ExceptionBuilder()
        val result = builder.buildSafely()
        assertTrue(result.isErr)
    }
}

class ApplyConfigTest {
    data class ConfigObject(
        var value: String = "",
    )

    @Test
    fun applyConfigModifiesObject() {
        val obj = ConfigObject()
        obj.applyConfig { value = "modified" }
        assertEquals("modified", obj.value)
    }

    @Test
    fun applyConfigReturnsSameObject() {
        val obj = ConfigObject()
        val result = obj.applyConfig { value = "modified" }
        assertEquals(obj, result)
    }
}

class ApplyConfigIfTest {
    data class ConfigObject(
        var value: String = "default",
    )

    @Test
    fun applyConfigIfAppliesWhenConditionIsTrue() {
        val obj = ConfigObject()
        obj.applyConfigIf(true) { value = "applied" }
        assertEquals("applied", obj.value)
    }

    @Test
    fun applyConfigIfSkipsWhenConditionIsFalse() {
        val obj = ConfigObject()
        obj.applyConfigIf(false) { value = "applied" }
        assertEquals("default", obj.value)
    }

    @Test
    fun applyConfigIfPredicateAppliesWhenPredicateIsTrue() {
        val obj = ConfigObject(value = "initial")
        obj.applyConfigIf({ it.value == "initial" }) { value = "updated" }
        assertEquals("updated", obj.value)
    }

    @Test
    fun applyConfigIfPredicateSkipsWhenPredicateIsFalse() {
        val obj = ConfigObject(value = "other")
        obj.applyConfigIf({ it.value == "initial" }) { value = "updated" }
        assertEquals("other", obj.value)
    }
}
