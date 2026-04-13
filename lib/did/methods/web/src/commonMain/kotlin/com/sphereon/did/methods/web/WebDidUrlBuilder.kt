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

package com.sphereon.did.methods.web

/**
 * Utility for converting between did:web DIDs and URLs.
 *
 * did:web format:
 * - did:web:example.com -> https://example.com/.well-known/did.json
 * - did:web:example.com:path:to:doc -> https://example.com/path/to/doc/did.json
 * - did:web:example.com%3A8080 -> https://example.com:8080/.well-known/did.json
 *
 * @see <a href="https://w3c-ccg.github.io/did-method-web/">did:web Method Specification</a>
 */
object WebDidUrlBuilder {

    /**
     * The DID document filename.
     */
    const val DID_DOCUMENT_FILENAME = "did.json"

    /**
     * The well-known path for root domain DIDs.
     */
    const val WELL_KNOWN_PATH = ".well-known"

    /**
     * Converts a did:web DID to the corresponding HTTPS URL.
     *
     * @param did The did:web DID
     * @return The HTTPS URL where the DID document should be hosted
     * @throws IllegalArgumentException if the DID is not a valid did:web
     */
    fun didToUrl(did: String): String {
        require(did.startsWith("did:web:")) {
            "DID must start with 'did:web:': $did"
        }

        // Extract the method-specific identifier
        val methodSpecificId = did.removePrefix("did:web:")

        // Split by colons to get domain and path segments
        val segments = methodSpecificId.split(":")

        // First segment is the domain (with optional port encoded as %3A)
        val domain = segments[0].replace("%3A", ":")

        // Remaining segments form the path
        val pathSegments = segments.drop(1)

        return if (pathSegments.isEmpty()) {
            // No path - use .well-known
            "https://$domain/$WELL_KNOWN_PATH/$DID_DOCUMENT_FILENAME"
        } else {
            // Has path - append did.json to path
            val path = pathSegments.joinToString("/")
            "https://$domain/$path/$DID_DOCUMENT_FILENAME"
        }
    }

    /**
     * Converts a domain (and optional path) to a did:web DID.
     *
     * @param domain The domain name (may include port)
     * @param path Optional path segments
     * @return The did:web DID
     */
    fun urlToDid(domain: String, path: List<String> = emptyList()): String {
        // Encode port separator
        val encodedDomain = domain.replace(":", "%3A")

        return if (path.isEmpty()) {
            "did:web:$encodedDomain"
        } else {
            val pathString = path.joinToString(":")
            "did:web:$encodedDomain:$pathString"
        }
    }

    /**
     * Extracts the domain from a did:web DID.
     *
     * @param did The did:web DID
     * @return The domain (with port if present)
     */
    fun extractDomain(did: String): String {
        require(did.startsWith("did:web:")) {
            "DID must start with 'did:web:': $did"
        }

        val methodSpecificId = did.removePrefix("did:web:")
        val firstSegment = methodSpecificId.split(":")[0]
        return firstSegment.replace("%3A", ":")
    }

    /**
     * Extracts the path segments from a did:web DID.
     *
     * @param did The did:web DID
     * @return The path segments (empty list if no path)
     */
    fun extractPath(did: String): List<String> {
        require(did.startsWith("did:web:")) {
            "DID must start with 'did:web:': $did"
        }

        val methodSpecificId = did.removePrefix("did:web:")
        val segments = methodSpecificId.split(":")
        return segments.drop(1)
    }

    /**
     * Validates that a string is a valid did:web DID.
     *
     * @param did The DID to validate
     * @return True if valid, false otherwise
     */
    fun isValidDidWeb(did: String): Boolean {
        if (!did.startsWith("did:web:")) return false

        val methodSpecificId = did.removePrefix("did:web:")
        if (methodSpecificId.isEmpty()) return false

        val segments = methodSpecificId.split(":")
        if (segments.isEmpty() || segments[0].isEmpty()) return false

        // Domain must be valid
        val domain = segments[0].replace("%3A", ":")
        if (domain.isEmpty()) return false

        return true
    }
}
