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

package com.sphereon.trust.etsi

import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.trust.etsi.resolution.ExternalIdentifierX509ETSIValidationOpts
import com.sphereon.trust.etsi.resolution.ExternalIdentifierX509ETSIValidationResult
import com.sphereon.trust.etsi.resolution.TslDeterminationMethod
import com.sphereon.trust.etsi.resolution.TspMatchType

/**
 * Examples of X.509 certificate validation against ETSI trust lists.
 *
 * These examples demonstrate the primary use case: validating certificates against
 * ETSI trust lists using the identifier resolution framework.
 */
object X509ETSIValidationExamples {

    /**
     * Example 1: Basic certificate validation with automatic LOTL navigation.
     *
     * This is the most common use case:
     * 1. Provide a certificate to validate
     * 2. System extracts country from certificate's C attribute
     * 3. System navigates LOTL to find appropriate member state trust list
     * 4. System checks if certificate is in trust list or issued by CA in trust list
     * 5. Returns validation result
     */
    suspend fun basicValidation(
        identifierService: IdentifierService,
        certificateBase64: String
    ) {
        // Simply provide the certificate - the system handles everything else
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(certificateBase64)),
            // All other settings use sensible defaults
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        println("=== Certificate Validation Result ===")
        println("Trusted: ${result.trusted}")
        println("Status: ${result.trustStatus}")

        if (result.trusted) {
            println("\nCertificate is TRUSTED")
            println("Trust Anchor: ${result.trustAnchor?.name}")
            println("TSP: ${result.matchedTSP?.tspName}")
            println("Service: ${result.matchedTSP?.serviceName}")
            println("Service Type: ${result.matchedTSP?.serviceTypeIdentifier}")
            println("Match Type: ${result.matchedTSP?.matchType}")

            if (result.matchedTSP?.matchType == TspMatchType.CA_CHAIN_MATCH) {
                println("CA Chain: ${result.matchedTSP.caChain?.size} certificates")
            }

            println("\nValidation Path: ${result.validationPath.joinToString(" → ")}")
        } else {
            println("\nCertificate is NOT TRUSTED")
            println("Reason: ${result.details}")
        }

        println("\n=== Trust List Information ===")
        println("Territory: ${result.trustListInfo.territory}")
        println("TSL URI: ${result.trustListInfo.tslUri}")
        println("Determination: ${result.trustListInfo.determinationMethod}")
        println("From Cache: ${result.trustListInfo.fromCache}")
        println("Sequence: ${result.trustListInfo.sequenceNumber}")
    }

    /**
     * Example 2: Validation with certificate chain.
     *
     * When you have the full certificate chain, provide it for CA chain matching.
     */
    suspend fun validationWithChain(
        identifierService: IdentifierService,
        certificateBase64: String,
        chainBase64: List<String>  // Intermediate and root certificates
    ) {
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = (listOf(certificateBase64) + chainBase64).toTypedArray()),
            allowCAChainMatch = true  // Allow matching via CA chain (default: true)
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        when (result.matchedTSP?.matchType) {
            TspMatchType.DIRECT_MATCH -> {
                println("Certificate is directly listed in trust list")
            }
            TspMatchType.CA_CHAIN_MATCH -> {
                println("Certificate was issued by a CA in the trust list")
                println("CA Chain depth: ${result.matchedTSP.caChain?.size}")
            }
            else -> {
                println("Certificate not found in trust list")
            }
        }
    }

    /**
     * Example 3: Validation with explicit trust list (skip LOTL navigation).
     *
     * If you already know which trust list to use, you can specify it directly.
     */
    suspend fun validationWithExplicitTSL(
        identifierService: IdentifierService,
        certificateBase64: String,
        tslUri: String = "https://www.acm.nl/sites/default/files/documents/tsl-nl.xml"
    ) {
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(certificateBase64)),
            explicitTslUri = tslUri,  // Skip LOTL navigation, use this TSL directly
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        // The determination method will be EXPLICIT
        // assert(result.trustListInfo.determinationMethod == TslDeterminationMethod.EXPLICIT)
    }

    /**
     * Example 4: Validation with service type filter.
     *
     * Only validate against specific service types (e.g., only qualified CAs).
     */
    suspend fun validationWithServiceTypeFilter(
        identifierService: IdentifierService,
        certificateBase64: String
    ) {
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(certificateBase64)),
            serviceTypeFilter = listOf(
                "http://uri.etsi.org/TrstSvc/Svctype/CA/QC",  // Qualified CA
                "http://uri.etsi.org/TrstSvc/Svctype/CA/PKC"  // Public Key Certificate CA
            )
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        if (result.trusted) {
            println("Certificate is issued by a qualified CA")
            println("Service Type: ${result.matchedTSP?.serviceTypeIdentifier}")
        }
    }

    /**
     * Example 5: Historical validation (validate at specific point in time).
     *
     * Useful for auditing or verifying signatures created in the past.
     */
    suspend fun historicalValidation(
        identifierService: IdentifierService,
        certificateBase64: String,
        validationTime: kotlinx.datetime.Instant
    ) {
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(certificateBase64)),
            validationTime = validationTime  // Validate as of this time
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        println("Historical validation at: $validationTime")
        println("Was trusted: ${result.trusted}")
        println("Status at that time: ${result.matchedTSP?.serviceStatus}")
    }

    /**
     * Example 6: Validation with custom caching.
     *
     * Control trust list caching behavior.
     */
    suspend fun validationWithCustomCaching(
        identifierService: IdentifierService,
        certificateBase64: String
    ) {
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(certificateBase64)),
            useCache = true,
            maxCacheAge = 1800000  // 30 minutes (default is 1 hour)
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        if (result.trustListInfo.fromCache) {
            println("Trust list was loaded from cache")
        } else {
            println("Trust list was freshly fetched")
        }
    }

    /**
     * Example 7: Complete validation flow with detailed logging.
     */
    suspend fun completeValidationFlow(
        identifierService: IdentifierService,
        certificateBase64: String,
        chainBase64: List<String>?
    ): Boolean {
        println("=== Starting ETSI Trust Validation ===\n")

        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(certificateBase64)),
            lotlUri = "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
            allowCAChainMatch = true,
            useCache = true
        )

        println("Step 1: Resolving certificate against ETSI trust lists...")
        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        println("Step 2: Trust list determination")
        println("  Method: ${result.trustListInfo.determinationMethod}")
        when (result.trustListInfo.determinationMethod) {
            TslDeterminationMethod.LOTL_NAVIGATION -> {
                println("  - Extracted country from certificate")
                println("  - Navigated LOTL to find member state trust list")
            }
            TslDeterminationMethod.EXPLICIT -> {
                println("  - Used explicitly provided trust list")
            }
            TslDeterminationMethod.OTHER -> {
                println("  - Used alternative determination method")
            }
        }
        println("  Territory: ${result.trustListInfo.territory}")
        println("  TSL: ${result.trustListInfo.tslUri}")
        println("  From Cache: ${result.trustListInfo.fromCache}")

        println("\nStep 3: Trust list search")
        if (result.matchedTSP != null) {
            println("  Found matching TSP")
            println("  TSP: ${result.matchedTSP.tspName}")
            println("  Service: ${result.matchedTSP.serviceName}")
            println("  Match Type: ${result.matchedTSP.matchType}")

            when (result.matchedTSP.matchType) {
                TspMatchType.DIRECT_MATCH -> {
                    println("  - Certificate is directly listed in trust list")
                }
                TspMatchType.CA_CHAIN_MATCH -> {
                    println("  - Certificate is issued by a CA in the trust list")
                    println("  - CA chain length: ${result.matchedTSP.caChain?.size}")
                }
                TspMatchType.SUBJECT_MATCH -> {
                    println("  - Certificate subject matches TSP")
                }
            }
        } else {
            println("  No matching TSP found in trust list")
        }

        println("\nStep 4: Status evaluation")
        println("  Service Status: ${result.matchedTSP?.serviceStatus}")
        println("  Trust Status: ${result.trustStatus}")
        println("  Trusted: ${result.trusted}")

        println("\nStep 5: Result")
        if (result.trusted) {
            println("  CERTIFICATE IS TRUSTED")
            println("  Trust Anchor: ${result.trustAnchor?.name}")
            println("  Validation Path: ${result.validationPath.joinToString(" -> ")}")
        } else {
            println("  CERTIFICATE IS NOT TRUSTED")
            println("  Reason: ${result.details}")
        }

        println("\n=== Validation Complete ===")

        return result.trusted
    }

    /**
     * Example 8: Integration with existing X.509 validation.
     *
     * Shows how ETSI validation integrates with standard X.509 chain validation.
     */
    suspend fun integratedValidation(
        identifierService: IdentifierService,
        certificateBase64: String,
        chainBase64: List<String>
    ) {
        // The service automatically performs X.509 chain validation first
        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = KeyInfo<Nothing>(key = null, x5c = (listOf(certificateBase64) + chainBase64).toTypedArray())
        )

        val result = identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult

        // If not trusted, could be due to:
        // 1. X.509 chain validation failed
        // 2. Certificate not in trust list
        // 3. Service status is not "granted"

        if (!result.trusted) {
            when {
                result.matchedTSP == null -> {
                    println("Certificate not found in trust list")
                }
                result.trustStatus == com.sphereon.trust.core.model.TrustStatus.VALIDATION_ERROR -> {
                    println("X.509 chain validation failed: ${result.details}")
                }
                else -> {
                    println("Service status issue: ${result.matchedTSP.serviceStatus}")
                }
            }
        }
    }

    /**
     * Example 9: Batch validation of multiple certificates.
     */
    suspend fun batchValidation(
        identifierService: IdentifierService,
        certificates: List<String>
    ) {
        println("Validating ${certificates.size} certificates...\n")

        val results = certificates.map { cert ->
            val opts = ExternalIdentifierX509ETSIValidationOpts(
                identifier = KeyInfo<Nothing>(key = null, x5c = arrayOf(cert)),
                useCache = true  // Reuse cached trust lists
            )
            identifierService.resolve(opts) as ExternalIdentifierX509ETSIValidationResult
        }

        val trusted = results.count { it.trusted }
        val untrusted = results.count { !it.trusted }

        println("Results:")
        println("  Trusted: $trusted")
        println("  Untrusted: $untrusted")

        // Group by territory
        val byTerritory = results.groupBy { it.trustListInfo.territory }
        println("\nBy Territory:")
        byTerritory.forEach { (territory, results) ->
            val trustedCount = results.count { it.trusted }
            println("  $territory: $trustedCount/${results.size} trusted")
        }

        // Check cache efficiency
        val cached = results.count { it.trustListInfo.fromCache }
        println("\nCache efficiency: $cached/${results.size} from cache")
    }
}
