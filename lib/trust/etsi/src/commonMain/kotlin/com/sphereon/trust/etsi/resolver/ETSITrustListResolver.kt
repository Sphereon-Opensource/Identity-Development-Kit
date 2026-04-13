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

package com.sphereon.trust.etsi.resolver

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListData
import com.sphereon.trust.core.resolver.TrustListResolutionException
import com.sphereon.trust.core.resolver.TrustListResolver
import com.sphereon.trust.etsi.signature.XmlSignatureVerificationOptions
import com.sphereon.trust.etsi.signature.XmlSignatureVerifier
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope

/**
 * Resolver for ETSI trust lists that delegates to appropriate resolvers based on URI scheme.
 *
 * This resolver coordinates multiple resolution strategies (HTTP, file, etc.)
 * and adds ETSI-specific logic like signature verification.
 */
@Inject
@SingleIn(SessionScope::class)
class ETSITrustListResolver(
    private val resolvers: Set<TrustListResolver>,
    private val signatureVerifier: XmlSignatureVerifier,
    private val execution: SessionExecution
) {
    private val logger = execution.log.logManager.withTagAsync("ETSITrustListResolver")

    /**
     * Resolves an ETSI trust list from the given URI.
     *
     * @param uri The URI of the trust list
     * @param options Resolution options
     * @return The resolved trust list data
     */
    suspend fun resolve(uri: String, options: ResolutionOptions = ResolutionOptions()): TrustListData {
        logger.info("Resolving ETSI trust list from $uri")

        // Find a resolver that supports this URI
        val resolver = resolvers.firstOrNull { it.supports(uri) }
            ?: throw IllegalArgumentException("No resolver found for URI: $uri")

        logger.debug("Using resolver: ${resolver.getId()}")

        // Resolve the trust list
        val trustListData = resolver.resolve(uri, options)

        // TODO: Add ETSI-specific verification (e.g., XML signature verification)
        if (options.verifySignature) {
            verifySignature(trustListData)
        }

        return trustListData
    }

    /**
     * Verifies the XML signature of the trust list.
     *
     * ETSI trust lists must be signed according to XML Signature specification.
     * When XAdES properties are present (standard for ETSI trust lists),
     * signing time and certificate information are logged.
     */
    private suspend fun verifySignature(trustListData: TrustListData) {
        logger.debug("Verifying XML signature for trust list from ${trustListData.sourceUri}")

        val verificationOptions = XmlSignatureVerificationOptions(
            validateCertificateChain = true,
            checkRevocation = false, // Can be made configurable in the future
            requireSignature = true
        )

        val verificationResult = signatureVerifier.verifyFromBytes(trustListData.data, verificationOptions)

        if (!verificationResult.valid) {
            logger.error("XML signature verification failed for ${trustListData.sourceUri}: ${verificationResult.errorMessage}")
            throw TrustListResolutionException(
                "XML signature verification failed: ${verificationResult.errorMessage}"
            )
        }

        if (!verificationResult.signaturePresent) {
            logger.error("No XML signature found in trust list from ${trustListData.sourceUri}")
            throw TrustListResolutionException(
                "No XML signature found in trust list"
            )
        }

        // Log XAdES information if present
        if (verificationResult.xadesProperties != null) {
            val sigTime = verificationResult.signingTime
            logger.info("Trust list from ${trustListData.sourceUri} signed at $sigTime (XAdES validated)")
        } else {
            logger.info("XML signature verified successfully for ${trustListData.sourceUri}")
        }
    }

    /**
     * Gets the list of well-known ETSI trust list locations.
     *
     * @return Map of territory code to TSL URI
     */
    fun getWellKnownTrustLists(): Map<String, String> {
        return mapOf(
            "EU" to "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
            "BE" to "https://tsl.belgium.be/tsl-be.xml",
            "NL" to "https://www.acm.nl/sites/default/files/documents/tsl-nl.xml",
            "DE" to "https://www.bundesnetzagentur.de/tsl-de.xml",
            "FR" to "https://ssi.gouv.fr/eidas/TL-FR.xml",
            // Add more as needed
        )
    }

    /**
     * Resolves the EU List of Trusted Lists (LOTL).
     *
     * The LOTL contains pointers to all member state trust lists.
     */
    suspend fun resolveEULOTL(options: ResolutionOptions = ResolutionOptions()): TrustListData {
        return resolve(getWellKnownTrustLists()["EU"]!!, options)
    }
}
