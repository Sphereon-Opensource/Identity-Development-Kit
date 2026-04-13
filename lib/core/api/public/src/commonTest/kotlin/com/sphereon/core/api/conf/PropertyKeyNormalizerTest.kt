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

package com.sphereon.core.api.conf

import com.sphereon.core.api.session.IdkErrorTypeCommandErrorMapper
import com.sphereon.core.api.session.supportsOrError
import com.sphereon.di.context.NoOpSessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropertyKeyNormalizerTest {
    @Test
    fun testEmptyKeyReturnsEmptyResult() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("")
        assertEquals("", result)
    }

    @Test
    fun testSingleDefaultDelimiterKeyReturnsDefaultDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize(".")
        assertEquals(".", result)
    }

    @Test
    fun testSingleOtherDelimiterKeyReturnsDefaultDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("_")
        assertEquals(".", result)
    }

    @Test
    fun testSingleLowercaseCharacterRemainsUnchanged() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("a")
        assertEquals("a", result)
    }

    @Test
    fun testSingleUppercaseCharacterNormalizesToLowercase() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("A")
        assertEquals("a", result)
    }

    @Test
    fun testConsecutiveUppercaseCharactersNormalizeWithDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("AB")
        assertEquals("a.b", result)
    }

    @Test
    fun testMixedCaseStringNormalizesCorrectly() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("PropertyKey")
        assertEquals("property.key", result)
    }

    @Test
    fun testUnderscoresAreReplacedWithDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("property_key")
        assertEquals("property.key", result)
    }

    @Test
    fun testSpacesAreReplacedWithDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("property key example")
        assertEquals("property.key.example", result)
    }

    @Test
    fun testConsecutiveSpecialCharactersAreReplacedWithSingleDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("property__key--example..with  spaces")
        assertEquals("property.key.example.with.spaces", result)
    }

    @Test
    fun testSpecialCharactersLikeDashesAndDotsNormalize() {
        val normalizer = PropertyKeyNormalizerImpl()
        val result = normalizer.normalize("property-key.example")
        assertEquals("property.key.example", result)
    }

    @Test
    fun testCustomDelimiterNormalizesCorrectly() {
        val normalizer = PropertyKeyNormalizerImpl("_")
        val result = normalizer.normalize("property.key Example")
        assertEquals("property_key_example", result)
    }
}

class PropertyKeyNormalizerCamelCaseTest {
    private val normalizer = PropertyKeyNormalizerImpl()

    @Test
    fun normalizeCamelCaseSplitsOnUppercase() {
        assertEquals("my.key", normalizer.normalize("myKey"))
    }

    @Test
    fun normalizeMultipleCamelCaseWords() {
        assertEquals("my.property.key", normalizer.normalize("myPropertyKey"))
    }

    @Test
    fun normalizeStartsWithUppercase() {
        assertEquals("my.key", normalizer.normalize("MyKey"))
    }

    @Test
    fun normalizeAllUppercaseChars() {
        assertEquals("a.b.c", normalizer.normalize("ABC"))
    }

    @Test
    fun normalizeMixedCaseWithNumbers() {
        assertEquals("my.key123", normalizer.normalize("myKey123"))
    }
}

class PropertyKeyNormalizerSpecialCharsTest {
    private val normalizer = PropertyKeyNormalizerImpl()

    @Test
    fun normalizeSpaceBecomesDelimiter() {
        assertEquals("my.key", normalizer.normalize("my key"))
    }

    @Test
    fun normalizeUnderscoreBecomesDelimiter() {
        assertEquals("my.key", normalizer.normalize("my_key"))
    }

    @Test
    fun normalizeHyphenBecomesDelimiter() {
        assertEquals("my.key", normalizer.normalize("my-key"))
    }

    @Test
    fun normalizeDotRemainsDelimiter() {
        assertEquals("my.key", normalizer.normalize("my.key"))
    }

    @Test
    fun normalizeMultipleSpecialChars() {
        assertEquals("my.key.name", normalizer.normalize("my_key-name"))
    }

    @Test
    fun normalizeMixedSpecialCharsAndCamelCase() {
        assertEquals("my.property.key.name", normalizer.normalize("my_propertyKey-name"))
    }
}

class PropertyKeyNormalizerCustomDelimiterTest {
    @Test
    fun normalizeWithCustomDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl(delimiter = "_")
        assertEquals("my_key", normalizer.normalize("myKey"))
    }

    @Test
    fun normalizeWithHyphenDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl(delimiter = "-")
        assertEquals("my-key", normalizer.normalize("myKey"))
    }

    @Test
    fun normalizeSpecialCharsWithCustomDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl(delimiter = "_")
        assertEquals("my_key_name", normalizer.normalize("my.key.name"))
    }

    @Test
    fun normalizeConsecutiveDelimitersWithCustomDelimiter() {
        val normalizer = PropertyKeyNormalizerImpl(delimiter = "_")
        assertEquals("my_key", normalizer.normalize("my__key"))
    }
}

class PropertyKeyNormalizerEdgeCasesTest {
    private val normalizer = PropertyKeyNormalizerImpl()

    @Test
    fun normalizeKeyWithNumbers() {
        assertEquals("key123value", normalizer.normalize("key123value"))
    }

    @Test
    fun normalizeKeyWithNumbersAndUppercase() {
        assertEquals("key123.value", normalizer.normalize("key123Value"))
    }

    @Test
    fun normalizeLeadingSpecialChar() {
        assertEquals(".my.key", normalizer.normalize("_myKey"))
    }

    @Test
    fun normalizeTrailingSpecialChar() {
        assertEquals("my.key.", normalizer.normalize("myKey_"))
    }
}

class PropertyKeyNormalizerInterfaceTest {
    @Test
    fun interfaceCanBeUsedAsType() {
        val normalizer: PropertyKeyNormalizer = PropertyKeyNormalizerImpl()
        assertEquals("my.key", normalizer.normalize("myKey"))
    }

    @Test
    fun functionalInterfaceCanBeLambda() {
        val normalizer = PropertyKeyNormalizer { key -> key.lowercase() }
        assertEquals("mykey", normalizer.normalize("MyKey"))
    }
}

class PropertyKeyNormalizerCommandImplTest {
    @Test
    fun supportsReturnsTrueForString() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            assertTrue(command.supports("myKey"))
        }

    @Test
    fun supportsReturnsFalseForNonString() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            assertFalse(command.supports(123))
            assertFalse(command.supports(listOf("a")))
            assertFalse(command.supports(mapOf("a" to "b")))
        }

    @Test
    fun supportsWithContextDelegatesToContextFreePrimary() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            assertTrue(command.supports("myKey"))
            assertFalse(command.supports(123))
        }

    @Test
    fun supportsOrErrorWithContextUsesContextFreePrimaryPath() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            val result = command.supportsOrError("myKey", IdkErrorTypeCommandErrorMapper)
            assertTrue(result.isOk)
        }

    @Test
    fun executeNormalizesKeyCorrectly() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            val result = command.execute("myPropertyKey")
            assertTrue(result.isOk)
            assertEquals("my.property.key", result.getOrNull())
        }

    @Test
    fun executeWithCustomDelimiter() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl(delimiter = "_")
            val result = command.execute("myPropertyKey")
            assertTrue(result.isOk)
            assertEquals("my_property_key", result.getOrNull())
        }

    @Test
    fun normalizerPropertyIsAccessible() {
        val command = PropertyKeyNormalizerCommandImpl()
        assertNotNull(command.normalizer)
        assertEquals("my.key", command.normalizer.normalize("myKey"))
    }

    @Test
    fun executeWithEmptyString() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            val result = command.execute("")
            assertTrue(result.isOk)
            assertEquals("", result.getOrNull())
        }

    @Test
    fun executeNormalizesUnderscores() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            val result = command.execute("my_property_key")
            assertTrue(result.isOk)
            assertEquals("my.property.key", result.getOrNull())
        }

    @Test
    fun executeNormalizesHyphens() =
        runTest {
            val command = PropertyKeyNormalizerCommandImpl()
            val result = command.execute("my-property-key")
            assertTrue(result.isOk)
            assertEquals("my.property.key", result.getOrNull())
        }
}

class CamelCaseKeyDenormalizerImplTest {
    private val denormalizer = CamelCaseKeyDenormalizerImpl()

    @Test
    fun denormalizesToCamelCase() {
        assertEquals("exposePrivateKeys", denormalizer.denormalize("expose.private.keys"))
    }

    @Test
    fun denormalizesMultipleWords() {
        assertEquals("myPropertyKeyName", denormalizer.denormalize("my.property.key.name"))
    }

    @Test
    fun denormalizesSingleWord() {
        assertEquals("singleword", denormalizer.denormalize("singleword"))
    }

    @Test
    fun denormalizesEmptyString() {
        assertEquals("", denormalizer.denormalize(""))
    }

    @Test
    fun denormalizesTwoWords() {
        assertEquals("twoWords", denormalizer.denormalize("two.words"))
    }

    @Test
    fun denormalizesWithNumbers() {
        assertEquals("key123Value", denormalizer.denormalize("key123.value"))
    }

    @Test
    fun denormalizesConfigPropertyNames() {
        assertEquals("exposePrivateKeysDuringGeneration", denormalizer.denormalize("expose.private.keys.during.generation"))
        assertEquals("persistKeysDuringGeneration", denormalizer.denormalize("persist.keys.during.generation"))
        assertEquals("defaultConfigValues", denormalizer.denormalize("default.config.values"))
        assertEquals("autoCreateCertificate", denormalizer.denormalize("auto.create.certificate"))
        assertEquals("keyvaultUrl", denormalizer.denormalize("keyvault.url"))
    }
}

class CamelCaseKeyDenormalizerImplCustomDelimiterTest {
    @Test
    fun denormalizesWithUnderscoreDelimiter() {
        val denormalizer = CamelCaseKeyDenormalizerImpl(delimiter = "_")
        assertEquals("myPropertyKey", denormalizer.denormalize("my_property_key"))
    }

    @Test
    fun denormalizesWithHyphenDelimiter() {
        val denormalizer = CamelCaseKeyDenormalizerImpl(delimiter = "-")
        assertEquals("myPropertyKey", denormalizer.denormalize("my-property-key"))
    }
}

class NoOpKeyDenormalizerTest {
    @Test
    fun returnsKeyUnchanged() {
        assertEquals("expose.private.keys", NoOpKeyDenormalizer.denormalize("expose.private.keys"))
    }

    @Test
    fun returnsSingleWordUnchanged() {
        assertEquals("singleword", NoOpKeyDenormalizer.denormalize("singleword"))
    }

    @Test
    fun returnsEmptyStringUnchanged() {
        assertEquals("", NoOpKeyDenormalizer.denormalize(""))
    }
}

class PropertyKeyDenormalizerInterfaceTest {
    @Test
    fun interfaceCanBeUsedAsType() {
        val denormalizer: PropertyKeyDenormalizer = CamelCaseKeyDenormalizerImpl()
        assertEquals("myKey", denormalizer.denormalize("my.key"))
    }

    @Test
    fun functionalInterfaceCanBeLambda() {
        val denormalizer = PropertyKeyDenormalizer { key -> key.uppercase() }
        assertEquals("MY.KEY", denormalizer.denormalize("my.key"))
    }

    // =========== Bracket-Quoted Literal Segment Tests ===========

    private val bracketNormalizer = PropertyKeyNormalizerImpl()

    @Test
    fun bracketSegmentPreservedExactly() {
        assertEquals("[TestCredential]", bracketNormalizer.normalize("[TestCredential]"))
    }

    @Test
    fun bracketSegmentCasePreserved() {
        assertEquals("credentials.[TestCredential].format", bracketNormalizer.normalize("credentials.[TestCredential].format"))
    }

    @Test
    fun bracketSegmentWithMixedNormalizedParts() {
        assertEquals(
            "credentials.[PID].signing.key.alias",
            bracketNormalizer.normalize("credentials.[PID].signingKeyAlias"),
        )
    }

    @Test
    fun bracketSegmentHyphensPreserved() {
        assertEquals(
            "kms.providers.[MyHSM-Provider].type",
            bracketNormalizer.normalize("kms.providers.[MyHSM-Provider].type"),
        )
    }

    @Test
    fun multipleBracketSegments() {
        assertEquals(
            "a.[Literal1].b.[Literal2].c",
            bracketNormalizer.normalize("a.[Literal1].b.[Literal2].c"),
        )
    }

    @Test
    fun bracketSegmentAtStart() {
        assertEquals("[Root].child.key", bracketNormalizer.normalize("[Root].child.key"))
    }

    @Test
    fun bracketSegmentAtEnd() {
        assertEquals("parent.child.[Leaf]", bracketNormalizer.normalize("parent.child.[Leaf]"))
    }

    @Test
    fun emptyBracketsPreserved() {
        assertEquals("a.[].b", bracketNormalizer.normalize("a.[].b"))
    }

    @Test
    fun unclosedBracketTreatedAsRegularChar() {
        val result = bracketNormalizer.normalize("[unclosed")
        assertEquals("[unclosed", result)
    }

    @Test
    fun noBracketsBackwardsCompatible() {
        assertEquals("test.credential", bracketNormalizer.normalize("TestCredential"))
    }

    @Test
    fun bracketWithHyphenDelimiterBetween() {
        assertEquals(
            "creds.[MyId].format",
            bracketNormalizer.normalize("creds-[MyId]-format"),
        )
    }

    // =========== Bracket-Aware Map & SubProperties Tests ===========

    @Test
    fun mapPropertySourceBracketLookup() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "credentials.[TestCredential].format" to "dc+sd-jwt",
                    "credentials.[PID].format" to "dc+sd-jwt",
                    "credentials.[PID].scope" to "eu_pid",
                ),
            )

        assertEquals("dc+sd-jwt", source.getPropertyAsString("credentials.[TestCredential].format"))
        assertEquals("dc+sd-jwt", source.getPropertyAsString("credentials.[PID].format"))
        assertEquals("eu_pid", source.getPropertyAsString("credentials.[PID].scope"))
    }

    @Test
    fun mapPropertySourceBracketGetAllPropertyNames() {
        val source =
            MapPropertySource(
                "test",
                mapOf(
                    "credentials.[TestCredential].format" to "dc+sd-jwt",
                    "other.key" to "value",
                ),
            )

        val names = source.getAllPropertyNames()
        assertTrue(names.contains("credentials.[TestCredential].format"))
        assertTrue(names.contains("other.key"))
    }

    // =========== Bracket-Aware Denormalizer Tests ===========

    @Test
    fun denormalizerPreservesBrackets() {
        val denormalizer = CamelCaseKeyDenormalizerImpl()
        val result = denormalizer.denormalize("credentials.[TestCredential].signing.key.alias")
        // Bracket segment preserved, surrounding segments camelCased
        assertTrue(result.contains("[TestCredential]"), "Bracket segment should be preserved in denormalized output")
    }

    @Test
    fun denormalizerBracketOnly() {
        val denormalizer = CamelCaseKeyDenormalizerImpl()
        assertEquals("[TestCredential]", denormalizer.denormalize("[TestCredential]"))
    }
}
