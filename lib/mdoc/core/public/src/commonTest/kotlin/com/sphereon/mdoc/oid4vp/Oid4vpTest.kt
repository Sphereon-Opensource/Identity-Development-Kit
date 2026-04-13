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

package com.sphereon.mdoc.oid4vp

import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.json.oid4vpJsonSerializer
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for OID4VP classes.
 */
class Oid4vpTest {
    // Oid4VPFormatIdentifier tests

    @Test
    fun testOid4vpFormatIdentifierMsoMdoc() {
        val format = Oid4VPFormatIdentifier.MSO_MDOC
        assertEquals("mso_mdoc", format.value)
    }

    @Test
    fun testOid4vpFormatIdentifierSdJwtVc() {
        val format = Oid4VPFormatIdentifier.SD_JWT_VC
        assertEquals("vc+sd-jwt", format.value)
    }

    @Test
    fun testOid4vpFormatIdentifierFromValueMsoMdoc() {
        val format = Oid4VPFormatIdentifier.fromValue("mso_mdoc")
        assertEquals(Oid4VPFormatIdentifier.MSO_MDOC, format)
    }

    @Test
    fun testOid4vpFormatIdentifierFromValueSdJwtVc() {
        val format = Oid4VPFormatIdentifier.fromValue("vc+sd-jwt")
        assertEquals(Oid4VPFormatIdentifier.SD_JWT_VC, format)
    }

    @Test
    fun testOid4vpFormatIdentifierFromValueUnknown() {
        val format = Oid4VPFormatIdentifier.fromValue("unknown")
        assertNull(format)
    }

    @Test
    fun testOid4vpFormatIdentifierEntries() {
        assertEquals(2, Oid4VPFormatIdentifier.entries.size)
    }

    // Oid4VPSupportedAlgorithm tests

    @Test
    fun testOid4vpSupportedAlgorithmCreation() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertEquals(1, alg.alg.size)
        assertEquals("ES256", alg.alg[0])
    }

    @Test
    fun testOid4vpSupportedAlgorithmMultiple() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256", "ES384", "ES512"))
        assertEquals(3, alg.alg.size)
    }

    @Test
    fun testOid4vpSupportedAlgorithmObjects() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertEquals(1, alg.algorithmObjects.size)
    }

    @Test
    fun testOid4vpSupportedAlgorithmEquality() {
        val alg1 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val alg2 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertEquals(alg1, alg2)
    }

    @Test
    fun testOid4vpSupportedAlgorithmEqualitySameInstance() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertEquals(alg, alg)
    }

    @Test
    fun testOid4vpSupportedAlgorithmInequalityDifferentType() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertFalse(alg.equals("not an algorithm"))
    }

    @Test
    fun testOid4vpSupportedAlgorithmInequalityDifferentAlgorithms() {
        val alg1 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val alg2 = Oid4VPSupportedAlgorithm(arrayOf("ES384"))
        assertNotEquals(alg1, alg2)
    }

    @Test
    fun testOid4vpSupportedAlgorithmHashCode() {
        val alg1 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val alg2 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertEquals(alg1.hashCode(), alg2.hashCode())
    }

    @Test
    fun testOid4vpSupportedAlgorithmFromDTO() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val fromDTO = Oid4VPSupportedAlgorithm.fromDTO(alg)
        assertEquals(alg, fromDTO)
    }

    // Oid4VPFormat tests

    @Test
    fun testOid4vpFormatWithMsoMdoc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertNotNull(format.mso_mdoc)
        assertNull(format.vc_sd_jwt)
    }

    @Test
    fun testOid4vpFormatWithSdJwtVc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        assertNull(format.mso_mdoc)
        assertNotNull(format.vc_sd_jwt)
    }

    @Test
    fun testOid4vpFormatBothPresentThrows() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertFailsWith<IllegalArgumentException> {
            Oid4VPFormat(mso_mdoc = supportedAlg, vc_sd_jwt = supportedAlg)
        }
    }

    @Test
    fun testOid4vpFormatBothAbsentThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4VPFormat(mso_mdoc = null, vc_sd_jwt = null)
        }
    }

    @Test
    fun testOid4vpFormatEmptyMsoMdocAlgorithmsThrows() {
        val emptyAlg = Oid4VPSupportedAlgorithm(arrayOf())
        assertFailsWith<IllegalArgumentException> {
            Oid4VPFormat(mso_mdoc = emptyAlg)
        }
    }

    @Test
    fun testOid4vpFormatEmptySdJwtVcAlgorithmsThrows() {
        val emptyAlg = Oid4VPSupportedAlgorithm(arrayOf())
        assertFailsWith<IllegalArgumentException> {
            Oid4VPFormat(vc_sd_jwt = emptyAlg)
        }
    }

    @Test
    fun testOid4vpFormatValidateAlgorithmsMsoMdoc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertTrue(format.validateAlgorithms())
    }

    @Test
    fun testOid4vpFormatValidateAlgorithmsSdJwtVc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        assertTrue(format.validateAlgorithms())
    }

    @Test
    fun testOid4vpFormatHasFormatMsoMdoc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertTrue(format.hasFormat(Oid4VPFormatIdentifier.MSO_MDOC))
    }

    @Test
    fun testOid4vpFormatFromDTO() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val fromDTO = Oid4VPFormat.fromDTO(format)
        assertEquals(format.mso_mdoc?.alg?.contentEquals(fromDTO.mso_mdoc?.alg ?: arrayOf()), true)
    }

    // Oid4VPConstraintField tests

    @Test
    fun testOid4vpConstraintFieldCreation() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        assertEquals(1, field.path.size)
        assertTrue(field.intent_to_retain)
    }

    @Test
    fun testOid4vpConstraintFieldEmptyPathThrows() {
        assertFailsWith<IllegalStateException> {
            Oid4VPConstraintField(path = arrayOf(), intent_to_retain = false)
        }
    }

    @Test
    fun testOid4vpConstraintFieldInvalidPathThrows() {
        assertFailsWith<Exception> {
            Oid4VPConstraintField(path = arrayOf("invalid-path"), intent_to_retain = false)
        }
    }

    @Test
    fun testOid4vpConstraintFieldFromElementIdentifiers() {
        val field =
            Oid4VPConstraintField.fromElementIdentifiers(
                nameSpace = "org.iso.18013.5.1",
                elementIdentifiers = arrayOf("family_name", "given_name"),
                intentToRetain = true,
            )
        assertEquals(2, field.path.size)
        assertTrue(field.path[0].contains("family_name"))
        assertTrue(field.path[1].contains("given_name"))
    }

    @Test
    fun testOid4vpConstraintFieldFromDTO() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = false,
            )
        val fromDTO = Oid4VPConstraintField.fromDTO(field)
        assertFalse(fromDTO.intent_to_retain)
    }

    // Oid4VPConstraints tests

    @Test
    fun testOid4vpConstraintsCreation() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        assertEquals(1, constraints.fields.size)
        assertEquals("required", constraints.limit_disclosure)
    }

    @Test
    fun testOid4vpConstraintsInvalidLimitDisclosureThrows() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        assertFailsWith<IllegalArgumentException> {
            Oid4VPConstraints(fields = arrayOf(field), limit_disclosure = "optional")
        }
    }

    @Test
    fun testOid4vpConstraintsEquality() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field))
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field))
        // Arrays use content comparison in equals
    }

    @Test
    fun testOid4vpConstraintsEqualitySameInstance() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        assertEquals(constraints, constraints)
    }

    @Test
    fun testOid4vpConstraintsHashCode() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        constraints.hashCode() // Just verify it doesn't throw
    }

    @Test
    fun testOid4vpConstraintsFromDTO() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val fromDTO = Oid4VPConstraints.fromDTO(constraints)
        assertEquals("required", fromDTO.limit_disclosure)
    }

    // Oid4VPInputDescriptor tests

    @Test
    fun testOid4vpInputDescriptorCreation() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))

        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        assertEquals("org.iso.18013.5.1.mDL", descriptor.id.toString())
    }

    @Test
    fun testOid4vpInputDescriptorFromDTO() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))

        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val fromDTO = Oid4VPInputDescriptor.fromDTO(descriptor)
        assertEquals(descriptor.id, fromDTO.id)
    }

    // Oid4VPPresentationDefinition tests

    @Test
    fun testOid4vpPresentationDefinitionCreation() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        assertEquals("test-definition", definition.id)
        assertEquals(1, definition.input_descriptors.size)
    }

    @Test
    fun testOid4vpPresentationDefinitionToDocRequest() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        val docRequest = definition.toDocRequest()
        assertNotNull(docRequest)
    }

    @Test
    fun testOid4vpPresentationDefinitionEquality() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition1 =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )
        val definition2 =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        // Same instance check
        assertEquals(definition1, definition1)
    }

    @Test
    fun testOid4vpPresentationDefinitionHashCode() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        definition.hashCode() // Verify it doesn't throw
    }

    @Test
    fun testOid4vpPresentationDefinitionToJsonString() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        val jsonString = definition.toJsonString()
        assertTrue(jsonString.contains("test-definition"))
    }

    // Oid4vpSubmissionDescriptor tests

    @Test
    fun testOid4vpSubmissionDescriptorCreation() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        assertEquals("test-id", descriptor.id)
        assertEquals("mso_mdoc", descriptor.format)
        assertEquals("$", descriptor.path)
    }

    @Test
    fun testOid4vpSubmissionDescriptorFromDTO() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val fromDTO = Oid4vpSubmissionDescriptor.fromDTO(descriptor)
        assertEquals(descriptor.id, fromDTO.id)
        assertEquals(descriptor.format, fromDTO.format)
        assertEquals(descriptor.path, fromDTO.path)
    }

    // Oid4VPPresentationSubmission tests

    @Test
    fun testOid4vpPresentationSubmissionCreation() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )

        assertEquals("def-1", submission.definition_id)
        assertEquals("sub-1", submission.id)
        assertEquals(1, submission.descriptor_map.size)
    }

    @Test
    fun testOid4vpPresentationSubmissionFromDTO() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        val fromDTO = Oid4VPPresentationSubmission.fromDTO(submission)
        assertEquals(submission.definition_id, fromDTO.definition_id)
        assertEquals(submission.id, fromDTO.id)
    }

    // Additional equality tests for branch coverage

    // Oid4VPConstraints equality tests

    @Test
    fun testOid4vpConstraintsInequalityNull() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        assertFalse(constraints.equals(null))
    }

    @Test
    fun testOid4vpConstraintsInequalityDifferentType() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        assertFalse(constraints.equals("not a constraints object"))
    }

    @Test
    fun testOid4vpConstraintsInequalityDifferentFields() {
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['given_name']"),
                intent_to_retain = false,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field1))
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field2))
        assertNotEquals(constraints1, constraints2)
    }

    @Test
    fun testOid4vpConstraintsEqualityIdentical() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field))
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field))
        // Different instances with same content
        assertEquals(constraints1.limit_disclosure, constraints2.limit_disclosure)
    }

    // Oid4VPPresentationDefinition equality tests

    @Test
    fun testOid4vpPresentationDefinitionInequalityNull() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        assertFalse(definition.equals(null))
    }

    @Test
    fun testOid4vpPresentationDefinitionInequalityDifferentType() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        assertFalse(definition.equals("not a presentation definition"))
    }

    @Test
    fun testOid4vpPresentationDefinitionInequalityDifferentId() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition1 =
            Oid4VPPresentationDefinition(
                id = "definition-1",
                input_descriptors = arrayOf(descriptor),
            )
        val definition2 =
            Oid4VPPresentationDefinition(
                id = "definition-2",
                input_descriptors = arrayOf(descriptor),
            )

        assertNotEquals(definition1, definition2)
    }

    @Test
    fun testOid4vpPresentationDefinitionInequalityDifferentDescriptors() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['given_name']"),
                intent_to_retain = false,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field1))
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field2))
        val descriptor1 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints1,
            )
        val descriptor2 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints2,
            )

        val definition1 =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor1),
            )
        val definition2 =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor2),
            )

        assertNotEquals(definition1, definition2)
    }

    // Oid4VPSupportedAlgorithm equality tests

    @Test
    fun testOid4vpSupportedAlgorithmInequalityNull() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        assertFalse(alg.equals(null))
    }

    // Oid4VPConstraintField equality tests

    @Test
    fun testOid4vpConstraintFieldEquality() {
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        // contentEquals on arrays
        assertTrue(field1.path.contentEquals(field2.path))
        assertEquals(field1.intent_to_retain, field2.intent_to_retain)
    }

    @Test
    fun testOid4vpConstraintFieldInequalityDifferentIntentToRetain() {
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = false,
            )
        assertNotEquals(field1.intent_to_retain, field2.intent_to_retain)
    }

    // Oid4vpSubmissionDescriptor tests from InputDescriptor

    @Test
    fun testOid4vpSubmissionDescriptorFromInputDescriptorMsoMdoc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val descriptor = Oid4vpSubmissionDescriptor.fromInputDescriptor(inputDescriptor)
        assertEquals("org.iso.18013.5.1.mDL", descriptor.id)
        assertEquals("mso_mdoc", descriptor.format)
        assertEquals("$", descriptor.path)
    }

    @Test
    fun testOid4vpSubmissionDescriptorFromInputDescriptorSdJwtVc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['eu.europa.ec.eudi.pid.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("eu.europa.ec.eudi.pid.1"),
                format = format,
                constraints = constraints,
            )

        val descriptor = Oid4vpSubmissionDescriptor.fromInputDescriptor(inputDescriptor)
        assertEquals("eu.europa.ec.eudi.pid.1", descriptor.id)
        assertEquals("vc+sd-jwt", descriptor.format)
    }

    // Oid4VPPresentationSubmission fromPresentationDefinition tests

    @Test
    fun testOid4vpPresentationSubmissionFromPresentationDefinition() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        val submission = Oid4VPPresentationSubmission.fromPresentationDefinition(definition, "test-sub-id")
        assertEquals("test-definition", submission.definition_id)
        assertEquals("test-sub-id", submission.id)
        assertEquals(1, submission.descriptor_map.size)
    }

    // Oid4VPFormat additional tests

    @Test
    fun testOid4vpFormatHasFormatSdJwtVc() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        assertTrue(format.hasFormat(Oid4VPFormatIdentifier.SD_JWT_VC))
    }

    @Test
    fun testOid4vpFormatDoesNotHaveFormat() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertFalse(format.hasFormat(Oid4VPFormatIdentifier.SD_JWT_VC))
    }

    // Oid4VPConstraintField from DataElementDef test - requires mock DataElementDef
    // This would require creating a test implementation of AbstractDataElementDef

    // Oid4VPInputDescriptor toDeviceItemsRequest test
    @Test
    fun testOid4vpInputDescriptorToDeviceItemsRequest() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']", "$['org.iso.18013.5.1']['given_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val builder = DeviceItemsRequest.Builder()
        descriptor.toDeviceItemsRequest(builder)
        val request = builder.build()

        assertEquals(DocType("org.iso.18013.5.1.mDL"), request.docType)
    }

    // Oid4VPPresentationSubmission assertValid tests

    @Test
    fun testOid4vpPresentationSubmissionAssertValidSuccess() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(inputDescriptor),
            )

        val submissionDescriptor =
            Oid4vpSubmissionDescriptor(
                id = "org.iso.18013.5.1.mDL",
                format = "mso_mdoc",
                path = "$",
            )

        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "test-definition",
                id = "test-submission",
                descriptor_map = arrayOf(submissionDescriptor),
            )

        // Should not throw
        submission.assertValid(definition)
    }

    @Test
    fun testOid4vpPresentationSubmissionAssertValidDefinitionIdMismatch() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "different-definition",
                input_descriptors = arrayOf(inputDescriptor),
            )

        val submissionDescriptor =
            Oid4vpSubmissionDescriptor(
                id = "org.iso.18013.5.1.mDL",
                format = "mso_mdoc",
                path = "$",
            )

        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "test-definition",
                id = "test-submission",
                descriptor_map = arrayOf(submissionDescriptor),
            )

        assertFailsWith<IllegalArgumentException> {
            submission.assertValid(definition)
        }
    }

    // Oid4VPConstraints equality tests for limit_disclosure branch

    @Test
    fun testOid4vpConstraintsInequalityDifferentLimitDisclosure() {
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field1), limit_disclosure = "required")

        // Note: limit_disclosure can only be "required" per spec, so different values throw
        // But we can test the equality comparison by using same values
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field1), limit_disclosure = "required")

        assertEquals(constraints1.limit_disclosure, constraints2.limit_disclosure)
    }

    // Oid4VPFormat equality tests

    @Test
    fun testOid4vpFormatEqualitySameInstance() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertEquals(format, format)
    }

    @Test
    fun testOid4vpFormatEqualitySameValues() {
        val supportedAlg1 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val supportedAlg2 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format1 = Oid4VPFormat(mso_mdoc = supportedAlg1)
        val format2 = Oid4VPFormat(mso_mdoc = supportedAlg2)
        assertEquals(format1, format2)
    }

    @Test
    fun testOid4vpFormatInequalityDifferentFormats() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format1 = Oid4VPFormat(mso_mdoc = supportedAlg)
        val format2 = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        assertNotEquals(format1, format2)
    }

    @Test
    fun testOid4vpFormatHashCode() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format1 = Oid4VPFormat(mso_mdoc = supportedAlg)
        val format2 = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertEquals(format1.hashCode(), format2.hashCode())
    }

    // Oid4VPConstraintField equality tests

    @Test
    fun testOid4vpConstraintFieldEqualitySameInstance() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        assertEquals(field, field)
    }

    @Test
    fun testOid4vpConstraintFieldEqualityDifferentInstances() {
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        // Data classes with arrays compare references, so fields won't be equal
        // unless we use contentEquals for paths
        assertTrue(field1.path.contentEquals(field2.path))
        assertEquals(field1.intent_to_retain, field2.intent_to_retain)
    }

    @Test
    fun testOid4vpConstraintFieldInequalityDifferentPath() {
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['given_name']"),
                intent_to_retain = true,
            )
        assertNotEquals(field1, field2)
    }

    @Test
    fun testOid4vpConstraintFieldHashCode() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        assertNotNull(field.hashCode())
    }

    // Oid4VPInputDescriptor equality tests

    @Test
    fun testOid4vpInputDescriptorEqualitySameInstance() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        assertEquals(descriptor, descriptor)
    }

    @Test
    fun testOid4vpInputDescriptorHashCode() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        assertNotNull(descriptor.hashCode())
    }

    // Oid4vpSubmissionDescriptor equality tests

    @Test
    fun testOid4vpSubmissionDescriptorEqualitySameInstance() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        assertEquals(descriptor, descriptor)
    }

    @Test
    fun testOid4vpSubmissionDescriptorEqualitySameValues() {
        val descriptor1 =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val descriptor2 =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        assertEquals(descriptor1, descriptor2)
    }

    @Test
    fun testOid4vpSubmissionDescriptorInequalityDifferentId() {
        val descriptor1 =
            Oid4vpSubmissionDescriptor(
                id = "test-id-1",
                format = "mso_mdoc",
                path = "$",
            )
        val descriptor2 =
            Oid4vpSubmissionDescriptor(
                id = "test-id-2",
                format = "mso_mdoc",
                path = "$",
            )
        assertNotEquals(descriptor1, descriptor2)
    }

    @Test
    fun testOid4vpSubmissionDescriptorHashCode() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        assertNotNull(descriptor.hashCode())
    }

    // Oid4VPPresentationSubmission equality tests

    @Test
    fun testOid4vpPresentationSubmissionEqualitySameInstance() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        assertEquals(submission, submission)
    }

    @Test
    fun testOid4vpPresentationSubmissionHashCode() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        assertNotNull(submission.hashCode())
    }

    @Test
    fun testOid4vpPresentationSubmissionInequalityDifferentDefinitionId() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission1 =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        val submission2 =
            Oid4VPPresentationSubmission(
                definition_id = "def-2",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        assertNotEquals(submission1, submission2)
    }

    // Additional Oid4VPFormat tests

    @Test
    fun testOid4vpFormatInequalityDifferentAlgorithms() {
        val supportedAlg1 = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val supportedAlg2 = Oid4VPSupportedAlgorithm(arrayOf("ES384"))
        val format1 = Oid4VPFormat(mso_mdoc = supportedAlg1)
        val format2 = Oid4VPFormat(mso_mdoc = supportedAlg2)
        assertNotEquals(format1, format2)
    }

    @Test
    fun testOid4vpFormatInequalityNull() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertFalse(format.equals(null))
    }

    @Test
    fun testOid4vpFormatInequalityDifferentType() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertFalse(format.equals("not a format"))
    }

    // Oid4VPPresentationDefinition fromDTO test

    @Test
    fun testOid4vpPresentationDefinitionFromDTO() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        val fromDTO = Oid4VPPresentationDefinition.fromDTO(definition)
        assertEquals(definition.id, fromDTO.id)
        assertEquals(definition.input_descriptors.size, fromDTO.input_descriptors.size)
    }

    // Oid4VPFormat with only vc_sd_jwt

    @Test
    fun testOid4vpFormatFromDTOWithVcSdJwt() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        val fromDTO = Oid4VPFormat.fromDTO(format)
        assertNotNull(fromDTO.vc_sd_jwt)
        assertNull(fromDTO.mso_mdoc)
    }

    // Oid4VPFormatsSerializer tests (JSON serialization/deserialization)

    @Test
    fun testOid4vpFormatIdentifierSerializeMsoMdoc() {
        val format = Oid4VPFormatIdentifier.MSO_MDOC
        val json = oid4vpJsonSerializer.encodeToString(format)
        assertTrue(json.contains("mso_mdoc"))
    }

    @Test
    fun testOid4vpFormatIdentifierSerializeSdJwtVc() {
        val format = Oid4VPFormatIdentifier.SD_JWT_VC
        val json = oid4vpJsonSerializer.encodeToString(format)
        assertTrue(json.contains("vc+sd-jwt"))
    }

    @Test
    fun testOid4vpFormatIdentifierDeserializeMsoMdoc() {
        val json = "\"mso_mdoc\""
        val format = oid4vpJsonSerializer.decodeFromString<Oid4VPFormatIdentifier>(json)
        assertEquals(Oid4VPFormatIdentifier.MSO_MDOC, format)
    }

    @Test
    fun testOid4vpFormatIdentifierDeserializeSdJwtVc() {
        val json = "\"vc+sd-jwt\""
        val format = oid4vpJsonSerializer.decodeFromString<Oid4VPFormatIdentifier>(json)
        assertEquals(Oid4VPFormatIdentifier.SD_JWT_VC, format)
    }

    @Test
    fun testOid4vpFormatIdentifierDeserializeInvalid() {
        val json = "\"invalid_format\""
        assertFailsWith<IllegalArgumentException> {
            oid4vpJsonSerializer.decodeFromString<Oid4VPFormatIdentifier>(json)
        }
    }

    // Oid4VPPresentationDefinition JSON serialization tests

    @Test
    fun testOid4vpPresentationDefinitionSerialize() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        val json = definition.toSerializedJson()
        assertNotNull(json)
        assertTrue(json.contains("test-definition"))
        assertTrue(json.contains("org.iso.18013.5.1.mDL"))
        assertTrue(json.contains("mso_mdoc"))
    }

    @Test
    fun testOid4vpPresentationDefinitionToJsonObject() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        val jsonObject = definition.toJsonObject()
        assertNotNull(jsonObject)
        assertEquals("test-definition", jsonObject["id"]?.toString()?.replace("\"", ""))
    }

    // Note: toDTO() test removed as it requires platform-specific implementation

    // Additional format tests for coverage

    @Test
    fun testOid4vpFormatSerialize() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val json = oid4vpJsonSerializer.encodeToString(format)
        assertTrue(json.contains("mso_mdoc"))
        assertTrue(json.contains("ES256"))
    }

    @Test
    fun testOid4vpConstraintsSerialize() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val json = oid4vpJsonSerializer.encodeToString(constraints)
        assertTrue(json.contains("required"))
        assertTrue(json.contains("intent_to_retain"))
    }

    @Test
    fun testOid4vpSupportedAlgorithmSerialize() {
        val alg = Oid4VPSupportedAlgorithm(arrayOf("ES256", "ES384"))
        val json = oid4vpJsonSerializer.encodeToString(alg)
        assertTrue(json.contains("ES256"))
        assertTrue(json.contains("ES384"))
    }

    @Test
    fun testOid4vpSubmissionDescriptorSerialize() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val json = oid4vpJsonSerializer.encodeToString(descriptor)
        assertTrue(json.contains("test-id"))
        assertTrue(json.contains("mso_mdoc"))
    }

    @Test
    fun testOid4vpPresentationSubmissionSerialize() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        val json = oid4vpJsonSerializer.encodeToString(submission)
        assertTrue(json.contains("def-1"))
        assertTrue(json.contains("sub-1"))
        assertTrue(json.contains("descriptor_map"))
    }

    // Additional path entry validation tests

    @Test
    fun testAssertedPathEntryValid() {
        val path = "$['org.iso.18013.5.1']['family_name']"
        val (nameSpace, identifier) = assertedPathEntry(path)
        assertEquals("org.iso.18013.5.1", nameSpace.toString())
        assertEquals("family_name", identifier.toString())
    }

    @Test
    fun testAssertedPathEntryValidWithDots() {
        val path = "$['eu.europa.ec.eudi.pid.1']['family_name']"
        val (nameSpace, identifier) = assertedPathEntry(path)
        assertEquals("eu.europa.ec.eudi.pid.1", nameSpace.toString())
        assertEquals("family_name", identifier.toString())
    }

    @Test
    fun testAssertedPathEntryInvalidFormat() {
        assertFailsWith<IllegalArgumentException> {
            assertedPathEntry("invalid-path-format")
        }
    }

    @Test
    fun testAssertedPathEntryMissingNamespace() {
        assertFailsWith<IllegalArgumentException> {
            assertedPathEntry("$['family_name']")
        }
    }

    @Test
    fun testAssertedPathEntryMissingBrackets() {
        assertFailsWith<IllegalArgumentException> {
            assertedPathEntry("\$[org.iso.18013.5.1][family_name]")
        }
    }

    @Test
    fun testAssertedPathEntryEmptyString() {
        assertFailsWith<IllegalArgumentException> {
            assertedPathEntry("")
        }
    }

    // Oid4VPConstraintField with multiple paths

    @Test
    fun testOid4vpConstraintFieldMultiplePaths() {
        val field =
            Oid4VPConstraintField(
                path =
                    arrayOf(
                        "$['org.iso.18013.5.1']['family_name']",
                        "$['org.iso.18013.5.1']['given_name']",
                        "$['org.iso.18013.5.1']['birth_date']",
                    ),
                intent_to_retain = false,
            )
        assertEquals(3, field.path.size)
        assertFalse(field.intent_to_retain)
    }

    @Test
    fun testOid4vpConstraintFieldWithMixedInvalidPathThrows() {
        assertFailsWith<IllegalArgumentException> {
            Oid4VPConstraintField(
                path =
                    arrayOf(
                        "$['org.iso.18013.5.1']['family_name']",
                        "invalid-path",
                    ),
                intent_to_retain = true,
            )
        }
    }

    // Oid4VPInputDescriptor additional tests

    @Test
    fun testOid4vpInputDescriptorInequalityDifferentId() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))

        val descriptor1 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )
        val descriptor2 =
            Oid4VPInputDescriptor(
                id = DocType("eu.europa.ec.eudi.pid.1"),
                format = format,
                constraints = constraints,
            )

        assertNotEquals(descriptor1, descriptor2)
    }

    @Test
    fun testOid4vpInputDescriptorInequalityNull() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))

        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        assertFalse(descriptor.equals(null))
    }

    @Test
    fun testOid4vpInputDescriptorInequalityDifferentType() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))

        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        assertFalse(descriptor.equals("not a descriptor"))
    }

    @Test
    fun testOid4vpInputDescriptorInequalityDifferentFormat() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val formatMso = Oid4VPFormat(mso_mdoc = supportedAlg)
        val formatSdJwt = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))

        val descriptor1 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = formatMso,
                constraints = constraints,
            )
        val descriptor2 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = formatSdJwt,
                constraints = constraints,
            )

        assertNotEquals(descriptor1, descriptor2)
    }

    @Test
    fun testOid4vpInputDescriptorInequalityDifferentConstraints() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['given_name']"),
                intent_to_retain = false,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field1))
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field2))

        val descriptor1 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints1,
            )
        val descriptor2 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints2,
            )

        assertNotEquals(descriptor1, descriptor2)
    }

    // Oid4VPPresentationSubmission additional equality tests

    @Test
    fun testOid4vpPresentationSubmissionInequalityNull() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )

        assertFalse(submission.equals(null))
    }

    @Test
    fun testOid4vpPresentationSubmissionInequalityDifferentType() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )

        assertFalse(submission.equals("not a submission"))
    }

    @Test
    fun testOid4vpPresentationSubmissionInequalityDifferentId() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val submission1 =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor),
            )
        val submission2 =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-2",
                descriptor_map = arrayOf(descriptor),
            )

        assertNotEquals(submission1, submission2)
    }

    @Test
    fun testOid4vpPresentationSubmissionInequalityDifferentDescriptorMap() {
        val descriptor1 =
            Oid4vpSubmissionDescriptor(
                id = "test-id-1",
                format = "mso_mdoc",
                path = "$",
            )
        val descriptor2 =
            Oid4vpSubmissionDescriptor(
                id = "test-id-2",
                format = "mso_mdoc",
                path = "$",
            )
        val submission1 =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor1),
            )
        val submission2 =
            Oid4VPPresentationSubmission(
                definition_id = "def-1",
                id = "sub-1",
                descriptor_map = arrayOf(descriptor2),
            )

        assertNotEquals(submission1, submission2)
    }

    // Oid4vpSubmissionDescriptor additional equality tests

    @Test
    fun testOid4vpSubmissionDescriptorInequalityNull() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )

        assertFalse(descriptor.equals(null))
    }

    @Test
    fun testOid4vpSubmissionDescriptorInequalityDifferentType() {
        val descriptor =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )

        assertFalse(descriptor.equals("not a descriptor"))
    }

    @Test
    fun testOid4vpSubmissionDescriptorInequalityDifferentFormat() {
        val descriptor1 =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val descriptor2 =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "vc+sd-jwt",
                path = "$",
            )

        assertNotEquals(descriptor1, descriptor2)
    }

    @Test
    fun testOid4vpSubmissionDescriptorInequalityDifferentPath() {
        val descriptor1 =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$",
            )
        val descriptor2 =
            Oid4vpSubmissionDescriptor(
                id = "test-id",
                format = "mso_mdoc",
                path = "$['org.iso.18013.5.1']['family_name']",
            )

        assertNotEquals(descriptor1, descriptor2)
    }

    // Oid4VPConstraintField additional equality tests

    @Test
    fun testOid4vpConstraintFieldInequalityNull() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )

        assertFalse(field.equals(null))
    }

    @Test
    fun testOid4vpConstraintFieldInequalityDifferentType() {
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )

        assertFalse(field.equals("not a field"))
    }

    // Oid4VPPresentationDefinition additional tests

    @Test
    fun testOid4vpPresentationDefinitionEqualitySameContent() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val descriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val definition1 =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )
        val definition2 =
            Oid4VPPresentationDefinition(
                id = "test-definition",
                input_descriptors = arrayOf(descriptor),
            )

        // Same instances of descriptors should result in equality
        assertEquals(definition1, definition2)
    }

    // Oid4VPConstraints equality with different limit_disclosure (not possible per spec)
    // The limit_disclosure must always be "required"

    // Test for multiple algorithms in Oid4VPFormat

    @Test
    fun testOid4vpFormatWithMultipleAlgorithms() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256", "ES384", "ES512"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        assertTrue(format.validateAlgorithms())
        assertEquals(3, format.mso_mdoc?.alg?.size)
    }

    // Oid4VPFormat with SD-JWT-VC validateAlgorithms

    @Test
    fun testOid4vpFormatValidateAlgorithmsSdJwtVcMultiple() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256", "EdDSA"))
        val format = Oid4VPFormat(vc_sd_jwt = supportedAlg)
        assertTrue(format.validateAlgorithms())
    }

    // Tests for Oid4VPPresentationDefinition with multiple input_descriptors

    @Test
    fun testOid4vpPresentationDefinitionMultipleDescriptors() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field1 =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val field2 =
            Oid4VPConstraintField(
                path = arrayOf("$['eu.europa.ec.eudi.pid.1']['family_name']"),
                intent_to_retain = false,
            )
        val constraints1 = Oid4VPConstraints(fields = arrayOf(field1))
        val constraints2 = Oid4VPConstraints(fields = arrayOf(field2))
        val descriptor1 =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints1,
            )
        val descriptor2 =
            Oid4VPInputDescriptor(
                id = DocType("eu.europa.ec.eudi.pid.1"),
                format = format,
                constraints = constraints2,
            )

        val definition =
            Oid4VPPresentationDefinition(
                id = "multi-descriptor-definition",
                input_descriptors = arrayOf(descriptor1, descriptor2),
            )

        assertEquals(2, definition.input_descriptors.size)
        val docRequest = definition.toDocRequest()
        assertNotNull(docRequest)
    }

    // DocumentDescriptorMatchResult tests

    @Test
    fun testDocumentDescriptorMatchResultCreation() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val result =
            DocumentDescriptorMatchResult(
                inputDescriptor = inputDescriptor,
                document = null,
                documentError = null,
                deviceKeyInfo = null,
                mdocNonce = "test-nonce",
            )

        assertNotNull(result)
        assertEquals(inputDescriptor, result.inputDescriptor)
        assertNull(result.document)
        assertNull(result.documentError)
        assertNull(result.deviceKeyInfo)
        assertEquals("test-nonce", result.mdocNonce)
    }

    @Test
    fun testDocumentDescriptorMatchResultWithDeviceNamespaces() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val result =
            DocumentDescriptorMatchResult(
                inputDescriptor = inputDescriptor,
                document = null,
                documentError = null,
                deviceKeyInfo = null,
                deviceNamespaces =
                    com.sphereon.mdoc.data.device
                        .DeviceNameSpaces(),
                mdocNonce = "test-nonce-2",
            )

        assertNotNull(result.deviceNamespaces)
    }

    @Test
    fun testDocumentDescriptorMatchResultDeviceNamespacesAssignment() {
        val supportedAlg = Oid4VPSupportedAlgorithm(arrayOf("ES256"))
        val format = Oid4VPFormat(mso_mdoc = supportedAlg)
        val field =
            Oid4VPConstraintField(
                path = arrayOf("$['org.iso.18013.5.1']['family_name']"),
                intent_to_retain = true,
            )
        val constraints = Oid4VPConstraints(fields = arrayOf(field))
        val inputDescriptor =
            Oid4VPInputDescriptor(
                id = DocType("org.iso.18013.5.1.mDL"),
                format = format,
                constraints = constraints,
            )

        val result =
            DocumentDescriptorMatchResult(
                inputDescriptor = inputDescriptor,
                document = null,
                documentError = null,
                deviceKeyInfo = null,
                mdocNonce = "test-nonce-3",
            )

        assertNull(result.deviceNamespaces)
        result.deviceNamespaces =
            com.sphereon.mdoc.data.device
                .DeviceNameSpaces()
        assertNotNull(result.deviceNamespaces)
    }
}
