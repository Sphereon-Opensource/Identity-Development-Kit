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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertiesFileTest {
    private fun getTestResourcePath(name: String): Path {
        val resource =
            this::class.java.classLoader.getResource(name)
                ?: throw IllegalArgumentException("Resource not found: $name")
        // Go via URI/File so spaces and other percent-encoded characters in the path
        // (e.g. ".../Sphereon%20IDTech/...") are decoded back to the real filesystem path.
        return Path(File(resource.toURI()).absolutePath)
    }

    @Test
    fun readPropertiesFromPathReadsBasicProperties() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("TestApp", properties["app.name"])
        assertEquals("1.0.0", properties["app.version"])
    }

    @Test
    fun readPropertiesFromPathReadsDatabaseProperties() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("jdbc:sqlite:mem:test", properties["database.url"])
        assertEquals("admin", properties["database.username"])
    }

    @Test
    fun readPropertiesFromPathReadsNumericValuesAsStrings() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("8080", properties["server.port"])
        assertEquals("100", properties["max.connections"])
    }

    @Test
    fun readPropertiesFromPathStripsQuotes() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("hello world", properties["quoted.value"])
        assertEquals("test value", properties["single.quoted"])
    }

    @Test
    fun readPropertiesFromPathIgnoresHashComments() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        // Comments should not be in the properties
        properties.keys.forEach { key ->
            assertTrue(!key.startsWith("#"), "Key should not start with #: $key")
        }
    }

    @Test
    fun readPropertiesFromPathIgnoresSemicolonComments() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        // Semicolon comments should not be in the properties
        properties.keys.forEach { key ->
            assertTrue(!key.startsWith(";"), "Key should not start with ;: $key")
        }
    }

    @Test
    fun readPropertiesFromPathIgnoresColonComments() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        // Colon comments should not be in the properties
        properties.keys.forEach { key ->
            assertTrue(!key.startsWith(":"), "Key should not start with :: $key")
        }
    }

    @Test
    fun readPropertiesFromPathIgnoresExclamationComments() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        // Exclamation comments should not be in the properties
        properties.keys.forEach { key ->
            assertTrue(!key.startsWith("!"), "Key should not start with !: $key")
        }
    }

    @Test
    fun readPropertiesFromPathReturnsEmptyMapForNonExistentFile() {
        val path = Path("/nonexistent/path/to/file.properties")
        val properties = readPropertiesFromPath(path)

        assertTrue(properties.isEmpty())
    }

    @Test
    fun readPropertiesFromPathReturnsCorrectNumberOfProperties() {
        val path = getTestResourcePath("test.properties")
        val properties = readPropertiesFromPath(path)

        // Should have 8 properties (excluding comments and empty lines)
        assertEquals(8, properties.size)
    }

    @Test
    fun readPropertiesFromPathParsesColonSeparatedEntries() {
        val path = getTestResourcePath("test-edge.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("colonValue", properties["colon.key"])
    }

    @Test
    fun readPropertiesFromPathParsesWhitespaceSeparatedEntries() {
        val path = getTestResourcePath("test-edge.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("spaceValue", properties["space.key"])
        assertEquals("spaced", properties["mixed.whitespace"])
    }

    @Test
    fun readPropertiesFromPathParsesEscapedAndUnicodeValues() {
        val path = getTestResourcePath("test-edge.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("escaped=value:ok", properties["escaped.key.name"])
        assertEquals("Hi", properties["unicode.key"])
        assertEquals("hello world", properties["escaped.whitespace"])
    }

    @Test
    fun readPropertiesFromPathKeepsSeparatorCharactersInsideValues() {
        val path = getTestResourcePath("test-edge.properties")
        val properties = readPropertiesFromPath(path)

        assertEquals("jdbc:postgresql://localhost:5432/db?x=a=b", properties["value.with.equals"])
        assertEquals("http://localhost:8080/path", properties["value.with.colon"])
    }

    @Test
    fun readPropertiesFromPathIgnoresInvalidLinesWithoutSeparator() {
        val path = getTestResourcePath("test-edge.properties")
        val properties = readPropertiesFromPath(path)

        assertTrue(!properties.containsKey("invalidlinewithoutseparator"))
    }
}

class PropertiesFilePropertySourceTest {
    private fun getTestResourcePath(name: String): Path {
        val resource =
            this::class.java.classLoader.getResource(name)
                ?: throw IllegalArgumentException("Resource not found: $name")
        return Path(File(resource.toURI()).absolutePath)
    }

    @Test
    fun propertiesFilePropertySourceReadsFromFile() {
        val path = getTestResourcePath("test.properties")
        val source = PropertiesFilePropertySource("test-props", path)

        assertEquals("TestApp", source.getProperty("app.name", String::class))
        assertEquals("1.0.0", source.getProperty("app.version", String::class))
    }

    @Test
    fun propertiesFilePropertySourceHasCorrectName() {
        val path = getTestResourcePath("test.properties")
        val source = PropertiesFilePropertySource("my-source", path)

        assertEquals("my-source", source.getName())
    }

    @Test
    fun propertiesFilePropertySourceGetAllPropertyNamesReturnsKeys() {
        val path = getTestResourcePath("test.properties")
        val source = PropertiesFilePropertySource("test-props", path)

        val names = source.getAllPropertyNames()
        assertTrue(names.contains("app.name"))
        assertTrue(names.contains("database.url"))
    }
}
