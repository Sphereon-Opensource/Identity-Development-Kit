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

package com.sphereon.core.api.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchesAdvancedPatternExactTest {

    @Test
    fun exactMatchReturnsTrue() {
        assertTrue(matchesAdvancedPattern("kms.keys.get", "kms.keys.get"))
    }

    @Test
    fun differentValueReturnsFalse() {
        assertFalse(matchesAdvancedPattern("kms.keys.get", "kms.keys.delete"))
    }

    @Test
    fun differentLengthReturnsFalse() {
        assertFalse(matchesAdvancedPattern("kms.keys.get", "kms.keys"))
    }
}

class MatchesAdvancedPatternSingleWildcardTest {

    @Test
    fun singleWildcardMatchesAnySegment() {
        assertTrue(matchesAdvancedPattern("did.*.resolve", "did.manager.resolve"))
    }

    @Test
    fun singleWildcardMatchesAnotherValue() {
        assertTrue(matchesAdvancedPattern("did.*.resolve", "did.resolver.resolve"))
    }

    @Test
    fun singleWildcardAtStart() {
        assertTrue(matchesAdvancedPattern("*.keys.get", "kms.keys.get"))
    }

    @Test
    fun singleWildcardAtEnd() {
        assertTrue(matchesAdvancedPattern("kms.keys.*", "kms.keys.get"))
    }

    @Test
    fun multipleSingleWildcards() {
        assertTrue(matchesAdvancedPattern("*.*.create", "party.parties.create"))
    }

    @Test
    fun singleWildcardDoesNotMatchMultipleSegments() {
        assertFalse(matchesAdvancedPattern("kms.*.create", "kms.keys.sub.create"))
    }
}

class MatchesAdvancedPatternDoubleWildcardTest {

    @Test
    fun doubleWildcardMatchesAnyRemainingSegments() {
        assertTrue(matchesAdvancedPattern("kms.**", "kms.keys.get"))
    }

    @Test
    fun doubleWildcardMatchesSingleRemainingSegment() {
        assertTrue(matchesAdvancedPattern("kms.**", "kms.get"))
    }

    @Test
    fun doubleWildcardMatchesManyRemainingSegments() {
        assertTrue(matchesAdvancedPattern("core.**", "core.a.b.c.d.e.f"))
    }

    @Test
    fun doubleWildcardMatchesNoRemainingWhenAtEnd() {
        assertTrue(matchesAdvancedPattern("kms.keys.**", "kms.keys.get"))
    }

    @Test
    fun doubleWildcardDoesNotMatchDifferentPrefix() {
        assertFalse(matchesAdvancedPattern("kms.**", "did.manager.resolve"))
    }
}

class MatchesAdvancedPatternBracedOptionsTest {

    @Test
    fun bracedOptionsMatchFirstOption() {
        assertTrue(matchesAdvancedPattern("party.{parties,resources}.create", "party.parties.create"))
    }

    @Test
    fun bracedOptionsMatchSecondOption() {
        assertTrue(matchesAdvancedPattern("party.{parties,resources}.create", "party.resources.create"))
    }

    @Test
    fun bracedOptionsDoNotMatchOther() {
        assertFalse(matchesAdvancedPattern("party.{parties,resources}.create", "party.users.create"))
    }

    @Test
    fun bracedOptionsWithThreeChoices() {
        assertTrue(matchesAdvancedPattern("kms.{keys,certs,tokens}.get", "kms.certs.get"))
    }

    @Test
    fun bracedOptionsWithSpaces() {
        assertTrue(matchesAdvancedPattern("kms.{ keys , certs }.get", "kms.keys.get"))
    }
}

class MatchesAdvancedPatternMixedTest {

    @Test
    fun mixedSingleWildcardAndBracedOptions() {
        assertTrue(matchesAdvancedPattern("*.{parties,resources}.create", "party.parties.create"))
    }

    @Test
    fun doubleWildcardAtEndWithPrefix() {
        assertTrue(matchesAdvancedPattern("{kms,did}.**", "kms.keys.get"))
    }
}

class MatchesSimplePatternExactTest {

    @Test
    fun exactMatchReturnsTrue() {
        assertTrue(matchesSimplePattern("kms.keys.get", "kms.keys.get"))
    }

    @Test
    fun differentValueReturnsFalse() {
        assertFalse(matchesSimplePattern("kms.keys.get", "kms.keys.delete"))
    }
}

class MatchesSimplePatternSingleWildcardSuffixTest {

    @Test
    fun singleWildcardSuffixMatchesOneLevel() {
        assertTrue(matchesSimplePattern("kms.keys.*", "kms.keys.get"))
    }

    @Test
    fun singleWildcardSuffixDoesNotMatchMultipleLevels() {
        assertFalse(matchesSimplePattern("kms.*", "kms.keys.get"))
    }

    @Test
    fun singleWildcardSuffixMatchesDifferentActions() {
        assertTrue(matchesSimplePattern("kms.keys.*", "kms.keys.delete"))
    }

    @Test
    fun singleWildcardSuffixDoesNotMatchDifferentPrefix() {
        assertFalse(matchesSimplePattern("kms.keys.*", "kms.certs.get"))
    }
}

class MatchesSimplePatternDoubleWildcardSuffixTest {

    @Test
    fun doubleWildcardSuffixMatchesAnyDepth() {
        assertTrue(matchesSimplePattern("kms.**", "kms.keys.get"))
    }

    @Test
    fun doubleWildcardSuffixMatchesSingleLevel() {
        assertTrue(matchesSimplePattern("kms.**", "kms.get"))
    }

    @Test
    fun doubleWildcardSuffixMatchesManyLevels() {
        assertTrue(matchesSimplePattern("core.**", "core.a.b.c.d.e"))
    }

    @Test
    fun doubleWildcardSuffixMatchesExactPrefix() {
        assertTrue(matchesSimplePattern("kms.**", "kms"))
    }

    @Test
    fun doubleWildcardSuffixDoesNotMatchDifferentPrefix() {
        assertFalse(matchesSimplePattern("kms.**", "did.manager.resolve"))
    }
}

class MatchesSimplePatternEdgeCasesTest {

    @Test
    fun noWildcardRequiresExactMatch() {
        assertTrue(matchesSimplePattern("kms.keys.get", "kms.keys.get"))
        assertFalse(matchesSimplePattern("kms.keys.get", "kms.keys.delete"))
    }

    @Test
    fun emptyPatternMatchesEmptyCommandId() {
        assertTrue(matchesSimplePattern("", ""))
    }

    @Test
    fun emptyPatternDoesNotMatchNonEmpty() {
        assertFalse(matchesSimplePattern("", "kms.keys"))
    }
}
