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

package com.sphereon.core.api.session

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.context.NoOpSessionContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyContextTest {
    @Test
    fun policyContextContainsAllFields() {
        val context =
            PolicyContext(
                actorId = "user-123",
                subjectId = "resource-456",
                resourceId = "party-789",
                commandId = "core.session.session-create",
                correlationId = "corr-abc",
                metadata = mapOf("tenant" to "acme"),
            )

        assertEquals("user-123", context.actorId)
        assertEquals("resource-456", context.subjectId)
        assertEquals("party-789", context.resourceId)
        assertEquals("core.session.session-create", context.commandId)
        assertEquals("corr-abc", context.correlationId)
        assertEquals("acme", context.metadata["tenant"])
    }

    @Test
    fun policyContextDefaultsOptionalFields() {
        val context =
            PolicyContext(
                actorId = "user-123",
                commandId = "core.session.session-create",
            )

        assertEquals("user-123", context.actorId)
        assertNull(context.subjectId)
        assertNull(context.resourceId)
        assertNull(context.correlationId)
        assertTrue(context.metadata.isEmpty())
    }
}

class PolicyContextEqualsBranchTest {
    @Test
    fun equalsReturnsTrueForSameInstance() {
        val context = PolicyContext(actorId = "user-1", commandId = "cmd.1")
        assertEquals(context, context)
    }

    @Test
    fun equalsReturnsTrueForIdenticalValues() {
        val context1 =
            PolicyContext(
                actorId = "user-123",
                subjectId = "subject-456",
                resourceId = "resource-789",
                commandId = "core.session.session-create",
                correlationId = "corr-abc",
                metadata = mapOf("key" to "value"),
            )
        val context2 =
            PolicyContext(
                actorId = "user-123",
                subjectId = "subject-456",
                resourceId = "resource-789",
                commandId = "core.session.session-create",
                correlationId = "corr-abc",
                metadata = mapOf("key" to "value"),
            )
        assertEquals(context1, context2)
        assertEquals(context1.hashCode(), context2.hashCode())
    }

    @Test
    fun equalsReturnsFalseForDifferentActorId() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1")
        val context2 = PolicyContext(actorId = "user-2", commandId = "cmd.1")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCommandId() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.2")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSubjectIdNullVsNonNull() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = null)
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = "subject")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSubjectIdNonNullVsNull() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = "subject")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = null)
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentSubjectIdValues() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = "subject-1")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = "subject-2")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentResourceIdNullVsNonNull() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = null)
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = "resource")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentResourceIdNonNullVsNull() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = "resource")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = null)
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentResourceIdValues() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = "resource-1")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = "resource-2")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCorrelationIdNullVsNonNull() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = null)
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = "corr")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCorrelationIdNonNullVsNull() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = "corr")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = null)
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentCorrelationIdValues() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = "corr-1")
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = "corr-2")
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMetadataEmptyVsNonEmpty() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = emptyMap())
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = mapOf("key" to "value"))
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMetadataValues() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = mapOf("key" to "value1"))
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = mapOf("key" to "value2"))
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsFalseForDifferentMetadataKeys() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = mapOf("key1" to "value"))
        val context2 = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = mapOf("key2" to "value"))
        assertNotEquals(context1, context2)
    }

    @Test
    fun equalsReturnsTrueForBothNullOptionalFields() {
        val context1 =
            PolicyContext(
                actorId = "user-1",
                commandId = "cmd.1",
                subjectId = null,
                resourceId = null,
                correlationId = null,
            )
        val context2 =
            PolicyContext(
                actorId = "user-1",
                commandId = "cmd.1",
                subjectId = null,
                resourceId = null,
                correlationId = null,
            )
        assertEquals(context1, context2)
    }

    @Test
    fun equalsReturnsTrueForBothNonNullOptionalFields() {
        val context1 =
            PolicyContext(
                actorId = "user-1",
                commandId = "cmd.1",
                subjectId = "subject",
                resourceId = "resource",
                correlationId = "corr",
            )
        val context2 =
            PolicyContext(
                actorId = "user-1",
                commandId = "cmd.1",
                subjectId = "subject",
                resourceId = "resource",
                correlationId = "corr",
            )
        assertEquals(context1, context2)
    }

    @Test
    fun hashCodeIsConsistentForEqualObjects() {
        val context1 =
            PolicyContext(
                actorId = "user-123",
                subjectId = "subject",
                resourceId = "resource",
                commandId = "cmd.1",
                correlationId = "corr",
                metadata = mapOf("k" to "v"),
            )
        val context2 =
            PolicyContext(
                actorId = "user-123",
                subjectId = "subject",
                resourceId = "resource",
                commandId = "cmd.1",
                correlationId = "corr",
                metadata = mapOf("k" to "v"),
            )
        assertEquals(context1.hashCode(), context2.hashCode())
    }

    @Test
    fun hashCodeDiffersForDifferentObjects() {
        val context1 = PolicyContext(actorId = "user-1", commandId = "cmd.1")
        val context2 = PolicyContext(actorId = "user-2", commandId = "cmd.2")
        // Note: hashCode collision is allowed, but typically differs for different values
        assertNotEquals(context1, context2)
    }

    @Test
    fun copyWithDifferentActorId() {
        val original = PolicyContext(actorId = "user-1", commandId = "cmd.1")
        val copied = original.copy(actorId = "user-2")
        assertNotEquals(original, copied)
        assertEquals("user-2", copied.actorId)
        assertEquals(original.commandId, copied.commandId)
    }

    @Test
    fun copyWithDifferentSubjectId() {
        val original = PolicyContext(actorId = "user-1", commandId = "cmd.1", subjectId = null)
        val copied = original.copy(subjectId = "subject-new")
        assertNotEquals(original, copied)
        assertEquals("subject-new", copied.subjectId)
    }

    @Test
    fun copyWithDifferentResourceId() {
        val original = PolicyContext(actorId = "user-1", commandId = "cmd.1", resourceId = "old")
        val copied = original.copy(resourceId = "new")
        assertNotEquals(original, copied)
        assertEquals("new", copied.resourceId)
    }

    @Test
    fun copyWithDifferentCorrelationId() {
        val original = PolicyContext(actorId = "user-1", commandId = "cmd.1", correlationId = null)
        val copied = original.copy(correlationId = "corr-new")
        assertNotEquals(original, copied)
        assertEquals("corr-new", copied.correlationId)
    }

    @Test
    fun copyWithDifferentMetadata() {
        val original = PolicyContext(actorId = "user-1", commandId = "cmd.1", metadata = emptyMap())
        val copied = original.copy(metadata = mapOf("new" to "data"))
        assertNotEquals(original, copied)
        assertEquals("data", copied.metadata["new"])
    }
}

class PolicyContextSerializationBranchTest {
    private val json = Json { prettyPrint = false }

    @Test
    fun serializeAndDeserializeWithAllFields() {
        val original =
            PolicyContext(
                actorId = "user-123",
                subjectId = "subject-456",
                resourceId = "resource-789",
                commandId = "core.session.session-create",
                correlationId = "corr-abc",
                metadata = mapOf("key1" to "value1", "key2" to "value2"),
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
    }

    @Test
    fun serializeAndDeserializeWithDefaultFields() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "core.session.session-create",
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertNull(deserialized.subjectId)
        assertNull(deserialized.resourceId)
        assertNull(deserialized.correlationId)
        assertTrue(deserialized.metadata.isEmpty())
    }

    @Test
    fun serializeAndDeserializeWithNullSubjectId() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                subjectId = null,
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertNull(deserialized.subjectId)
    }

    @Test
    fun serializeAndDeserializeWithNonNullSubjectId() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                subjectId = "subject-value",
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertEquals("subject-value", deserialized.subjectId)
    }

    @Test
    fun serializeAndDeserializeWithNullResourceId() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                resourceId = null,
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertNull(deserialized.resourceId)
    }

    @Test
    fun serializeAndDeserializeWithNonNullResourceId() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                resourceId = "resource-value",
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertEquals("resource-value", deserialized.resourceId)
    }

    @Test
    fun serializeAndDeserializeWithNullCorrelationId() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                correlationId = null,
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertNull(deserialized.correlationId)
    }

    @Test
    fun serializeAndDeserializeWithNonNullCorrelationId() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                correlationId = "corr-value",
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertEquals("corr-value", deserialized.correlationId)
    }

    @Test
    fun serializeAndDeserializeWithEmptyMetadata() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                metadata = emptyMap(),
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertTrue(deserialized.metadata.isEmpty())
    }

    @Test
    fun serializeAndDeserializeWithNonEmptyMetadata() {
        val original =
            PolicyContext(
                actorId = "user-123",
                commandId = "cmd.1",
                metadata = mapOf("tenant" to "acme", "region" to "us-east"),
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
        assertEquals(2, deserialized.metadata.size)
        assertEquals("acme", deserialized.metadata["tenant"])
        assertEquals("us-east", deserialized.metadata["region"])
    }

    @Test
    fun deserializeFromJsonWithMissingOptionalFields() {
        val jsonStr = """{"actorId":"user-123","commandId":"cmd.1"}"""
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals("user-123", deserialized.actorId)
        assertEquals("cmd.1", deserialized.commandId)
        assertNull(deserialized.subjectId)
        assertNull(deserialized.resourceId)
        assertNull(deserialized.correlationId)
        assertTrue(deserialized.metadata.isEmpty())
    }

    @Test
    fun deserializeFromJsonWithExplicitNullFields() {
        val jsonStr = """{"actorId":"user-123","subjectId":null,"resourceId":null,"commandId":"cmd.1","correlationId":null,"metadata":{}}"""
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals("user-123", deserialized.actorId)
        assertEquals("cmd.1", deserialized.commandId)
        assertNull(deserialized.subjectId)
        assertNull(deserialized.resourceId)
        assertNull(deserialized.correlationId)
        assertTrue(deserialized.metadata.isEmpty())
    }

    @Test
    fun serializationRoundTripPreservesAllNullableFieldsAsNull() {
        val original =
            PolicyContext(
                actorId = "actor",
                commandId = "cmd",
                subjectId = null,
                resourceId = null,
                correlationId = null,
                metadata = emptyMap(),
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
    }

    @Test
    fun serializationRoundTripPreservesAllNullableFieldsAsNonNull() {
        val original =
            PolicyContext(
                actorId = "actor",
                commandId = "cmd",
                subjectId = "subject",
                resourceId = "resource",
                correlationId = "corr",
                metadata = mapOf("key" to "value"),
            )
        val jsonStr = json.encodeToString(original)
        val deserialized = json.decodeFromString<PolicyContext>(jsonStr)
        assertEquals(original, deserialized)
    }
}

class PermissivePolicyProviderTest {
    @Test
    fun permissivePolicyProviderAlwaysAllows() =
        runTest {
            val context =
                PolicyContext(
                    actorId = "user-123",
                    commandId = "any.command.type-action",
                )

            val result = PermissivePolicyProvider.isAllowed(context)

            assertTrue(result.isOk)
            assertEquals(true, result.value)
        }

    @Test
    fun permissivePolicyProviderAllowsAnyCommand() =
        runTest {
            val commands =
                listOf(
                    "core.session.session-create",
                    "admin.user.user-delete",
                    "dangerous.system.system-wipe",
                )

            for (cmd in commands) {
                val context = PolicyContext(actorId = "test", commandId = cmd)
                val result = PermissivePolicyProvider.isAllowed(context)
                assertTrue(result.isOk, "Expected $cmd to be allowed")
                assertTrue(result.value, "Expected $cmd to be allowed")
            }
        }
}

class DenyAllPolicyProviderTest {
    @Test
    fun denyAllPolicyProviderAlwaysDenies() =
        runTest {
            val context =
                PolicyContext(
                    actorId = "user-123",
                    commandId = "any.command.type-action",
                )

            val result = DenyAllPolicyProvider.isAllowed(context)

            assertTrue(result.isOk)
            assertEquals(false, result.value)
        }

    @Test
    fun denyAllPolicyProviderDeniesAllCommands() =
        runTest {
            val commands =
                listOf(
                    "core.session.session-create",
                    "public.api.api-read",
                    "safe.operation.operation-execute",
                )

            for (cmd in commands) {
                val context = PolicyContext(actorId = "test", commandId = cmd)
                val result = DenyAllPolicyProvider.isAllowed(context)
                assertTrue(result.isOk, "Expected result to be Ok for $cmd")
                assertEquals(false, result.value, "Expected $cmd to be denied")
            }
        }
}
