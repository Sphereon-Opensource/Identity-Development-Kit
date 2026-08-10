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

package com.sphereon.core.api.conf

import kotlinx.io.files.Path
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ConfigContentRevisionTest {
    @Test
    fun revisionIsStableWhileNothingChanges() {
        val environment = environment(RevisionSource("local"))

        val first = environment.configContentRevision()

        assertEquals(first, environment.configContentRevision())
    }

    @Test
    fun revisionMovesWhenARefreshableSourceChangesItsContent() {
        val source = RevisionSource("local")
        val environment = environment(source)
        val before = environment.configContentRevision()

        source.recordWrite()

        assertNotEquals(before, environment.configContentRevision())
    }

    @Test
    fun revisionMovesWhenASourceIsAdded() {
        val sources = DefaultPropertySources(mutableListOf(RevisionSource("local")))
        val environment = TestEnvironment(sources)
        val before = environment.configContentRevision()

        sources.add(MutableMapPropertySource("added"))

        assertNotEquals(before, environment.configContentRevision())
    }

    @Test
    fun revisionMovesWhenAParentEnvironmentChanges() {
        val parentSource = RevisionSource("parent")
        val parent = environment(parentSource)
        val child = environment(RevisionSource("child"), parent = parent)
        val before = child.configContentRevision()

        parentSource.recordWrite()

        assertNotEquals(before, child.configContentRevision())
    }

    @Test
    fun refreshIsRequestedOnlyWhenAsked() {
        val source = RevisionSource("local")
        val environment = environment(source)

        environment.configContentRevision(refresh = false)
        assertEquals(0, source.refreshCount)

        environment.configContentRevision(refresh = true)
        assertEquals(1, source.refreshCount)
    }

    private fun environment(
        source: PropertySource<*>,
        parent: ConfigEnvironment? = null,
    ): ConfigEnvironment = TestEnvironment(DefaultPropertySources(mutableListOf(source)), parent)

    /**
     * Composes parent sources the same way `AbstractConfigEnvironment` does, which is what makes
     * `includeParents = true` carry the parents' structural and content revisions.
     */
    private class TestEnvironment(
        private val sources: PropertySources,
        override val parent: ConfigEnvironment? = null,
    ) : ConfigEnvironment {
        override val level: ConfigLevel = ConfigLevel.APP

        override fun getPropertySources(includeParents: Boolean): PropertySources =
            if (includeParents) sources.copy(parent?.getPropertySources(true)) else sources

        override fun getActiveProfile(): String = "test"

        override fun getAppName(): String = "test-app"

        override fun getConfigLocation(): Path = Path("/test/config")

        @Suppress("DEPRECATION")
        override fun getNamespace(): String = ""

        override fun containsProperty(key: String): Boolean = false

        override fun <T : Any> getProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T? = defaultValue

        override fun getPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String? = defaultValue

        override fun <T : Any> getRequiredProperty(
            key: String,
            targetType: KClass<T>,
            defaultValue: T?,
        ): T = defaultValue ?: error("Required property '$key' not found")

        override fun getRequiredPropertyAsString(
            key: String,
            defaultValue: String?,
        ): String = defaultValue ?: error("Required property '$key' not found")

        override fun getAllProperties(): Map<String, Any> = emptyMap()

        override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = emptyMap()

        override fun getSubProperties(
            prefixes: Set<String>,
            stripPrefix: Boolean,
        ): Map<String, Any> = emptyMap()

        override fun getSubPropertiesAsString(
            prefixes: Set<String>,
            stripPrefix: Boolean,
            redact: Boolean,
        ): Map<String, String> = emptyMap()
    }

    private class RevisionSource(
        name: String,
    ) : MutableMapPropertySource(name),
        RefreshablePropertySource {
        var refreshCount = 0
            private set

        private var revision = 0L

        override val contentRevision: Long
            get() = revision

        override fun refreshIfNeeded() {
            refreshCount += 1
        }

        fun recordWrite() {
            revision += 1
        }
    }
}
