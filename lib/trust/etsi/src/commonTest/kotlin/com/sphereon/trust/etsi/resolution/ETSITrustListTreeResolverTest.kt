package com.sphereon.trust.etsi.resolution

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListData
import com.sphereon.trust.core.resolver.TrustListResolutionException
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.model.ETSIAdditionalInformation
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIOtherLoTEPointer
import com.sphereon.trust.etsi.model.ETSIServiceDigitalIdentity
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

class ETSITrustListTreeResolverTest {
    @Test
    fun euVerifiedRootPermitsCrossHostMemberStatePointer() = runTest {
        val childUri = "https://member-state.example/trusted-list.xml"
        val root = list(
            territory = "EU",
            pointers = listOf(pointer(childUri, certificate = "AQID")),
        )
        val child = list(territory = "NL")
        val resolver = fixture(mapOf(ROOT_URI to root, childUri to child))

        val tree = resolver.resolve(
            ROOT_URI,
            ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
            ETSITrustListTreePolicy(
                allowedHosts = setOf("eu.example"),
                allowUnlistedChildHosts = true,
                territory = "NL",
            ),
        )

        assertEquals(listOf(ROOT_URI, childUri), tree.lists.map { it.uri })
    }

    @Test
    fun customCrossHostChildIsDeniedWithoutExplicitAllowlist() = runTest {
        val childUri = "https://child.example/trusted-list.xml"
        val resolver = fixture(
            mapOf(
                ROOT_URI to list("EU", listOf(pointer(childUri, "AQID"))),
                childUri to list("NL"),
            ),
        )

        val failure = assertFailsWith<TrustListResolutionException> {
            resolver.resolve(
                ROOT_URI,
                ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
                ETSITrustListTreePolicy(allowedHosts = setOf("eu.example"), territory = "NL"),
            )
        }

        assertEquals("TRUST_LIST_URL_REJECTED", failure.reasonCode)
    }

    @Test
    fun customCrossHostChildSucceedsOnlyWhenExplicitlyAllowlisted() = runTest {
        val childUri = "https://child.example/trusted-list.xml"
        val resolver = fixture(mapOf(ROOT_URI to list("EU", listOf(pointer(childUri, "AQID"))), childUri to list("NL")))

        val tree = resolver.resolve(
            ROOT_URI,
            ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
            ETSITrustListTreePolicy(
                allowedHosts = setOf("eu.example", "child.example"),
                territory = "NL",
            ),
        )

        assertEquals(listOf(ROOT_URI, childUri), tree.lists.map { it.uri })
    }

    @Test
    fun childSignatureVerificationReceivesPointerCertificateRoots() = runTest {
        val childUri = "https://member-state.example/trusted-list.xml"
        val calls = mutableListOf<Pair<String, List<ByteArray>?>>()
        val resolver = fixture(
            mapOf(ROOT_URI to list("EU", listOf(pointer(childUri, "AQID"))), childUri to list("NL")),
            calls,
        )

        resolver.resolve(
            ROOT_URI,
            ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
            ETSITrustListTreePolicy(setOf("eu.example"), allowUnlistedChildHosts = true, territory = "NL"),
        )

        assertContentEquals(byteArrayOf(9), calls[0].second!!.single())
        assertContentEquals(byteArrayOf(1, 2, 3), calls[1].second!!.single())
    }

    @Test
    fun resolvedTreeRetainsResolverCanonicalUrisAndExactRootAndChildPayloadBytes() = runTest {
        val canonicalRootUri = "https://eu.example/eu-lotl.xml?resolved=1"
        val requestedChildUri = "https://member-state.example/trusted-list.xml"
        val canonicalChildUri = "https://member-state.example/trusted-list.xml?resolved=2"
        val rootBytes = byteArrayOf(0, 1, 2)
        val childBytes = byteArrayOf(3, 4, 5, 6)
        val resolver = ETSITrustListTreeResolver(
            documentResolver = ETSITrustListDocumentResolver { uri, _ ->
                if (uri == ROOT_URI) {
                    TrustListData(rootBytes, canonicalRootUri, retrievedAt = 0L)
                } else {
                    TrustListData(childBytes, canonicalChildUri, retrievedAt = 0L)
                }
            },
            parser = object : ETSITrustListParser {
                override fun parseFromBytes(xmlData: ByteArray): ETSILoTE = when (xmlData.toList()) {
                    rootBytes.toList() -> list("EU", listOf(pointer(requestedChildUri, "AQID")))
                    childBytes.toList() -> list("NL")
                    else -> error("unexpected payload")
                }

                override fun parseFromString(xmlString: String): ETSILoTE = error("unused")
                override fun validate(xmlData: ByteArray): Boolean = true
                override fun parseFromJson(jsonString: String): ETSILoTE = error("unused")
            },
        )

        val tree = resolver.resolve(
            ROOT_URI,
            ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
            ETSITrustListTreePolicy(setOf("eu.example"), allowUnlistedChildHosts = true, territory = "NL"),
        )

        assertEquals(listOf(canonicalRootUri, canonicalChildUri), tree.lists.map { it.uri })
        assertContentEquals(rootBytes, tree.lists[0].data.data)
        assertContentEquals(childBytes, tree.lists[1].data.data)
        assertEquals(canonicalChildUri, tree.lists[1].data.sourceUri)
    }

    @Test
    fun missingOrMalformedPointerCertificateRootsFailClosedAndTs602PointersNeverEnterQeaaPath() = runTest {
        val missingChild = "https://missing.example/trusted-list.xml"
        val malformedChild = "https://malformed.example/trusted-list.xml"
        val missing = fixture(mapOf(ROOT_URI to list("EU", listOf(pointer(missingChild, null)))))
        assertFailsWith<TrustListResolutionException> {
            missing.resolve(
                ROOT_URI,
                ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
                ETSITrustListTreePolicy(setOf("eu.example"), allowUnlistedChildHosts = true),
            )
        }

        val malformed = fixture(mapOf(ROOT_URI to list("EU", listOf(pointer(malformedChild, "not-base64")))))
        assertFailsWith<TrustListResolutionException> {
            malformed.resolve(
                ROOT_URI,
                ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
                ETSITrustListTreePolicy(setOf("eu.example"), allowUnlistedChildHosts = true),
            )
        }

        val ts602Pointer = pointer(
            location = "https://pid.example/providers.json",
            certificate = "AQID",
            additionalInformation = ETSIAdditionalInformation(
                otherInformation = listOf("http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList"),
            ),
        )
        val excluded = fixture(mapOf(ROOT_URI to list("EU", listOf(ts602Pointer))))
        val tree = excluded.resolve(
            ROOT_URI,
            ResolutionOptions(trustedSignerRoots = listOf(byteArrayOf(9))),
            ETSITrustListTreePolicy(setOf("eu.example"), allowUnlistedChildHosts = true),
        )
        assertEquals(listOf(ROOT_URI), tree.lists.map { it.uri })
        assertTrue(EidasRole.QEAA_PROVIDER.trustListProfile.name.contains("612"))
    }

    private fun fixture(
        lists: Map<String, ETSILoTE>,
        calls: MutableList<Pair<String, List<ByteArray>?>> = mutableListOf(),
    ) = ETSITrustListTreeResolver(
        documentResolver = ETSITrustListDocumentResolver { uri, options ->
            calls += uri to options.trustedSignerRoots
            TrustListData(uri.encodeToByteArray(), uri, retrievedAt = 0L)
        },
        parser = MappingParser(lists),
    )

    private class MappingParser(private val lists: Map<String, ETSILoTE>) : ETSITrustListParser {
        override fun parseFromBytes(xmlData: ByteArray): ETSILoTE = lists.getValue(xmlData.decodeToString())
        override fun parseFromString(xmlString: String): ETSILoTE = lists.getValue(xmlString)
        override fun validate(xmlData: ByteArray): Boolean = lists.containsKey(xmlData.decodeToString())
        override fun parseFromJson(jsonString: String): ETSILoTE = lists.getValue(jsonString)
    }

    private fun pointer(
        location: String,
        certificate: String?,
        additionalInformation: ETSIAdditionalInformation? = null,
    ) = ETSIOtherLoTEPointer(
        schemeOperatorName = listOf(MultiLangString("en", "Member State")),
        schemeTerritory = "NL",
        location = location,
        serviceDigitalIdentities = certificate?.let { listOf(ETSIServiceDigitalIdentity(x509Certificates = listOf(it))) } ?: emptyList(),
        additionalInformation = additionalInformation,
    )

    private fun list(territory: String, pointers: List<ETSIOtherLoTEPointer> = emptyList()) = ETSILoTE(
        sequenceNumber = 1,
        type = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/TrustedList",
        schemeOperatorName = listOf(MultiLangString("en", "Operator")),
        statusDeterminationApproach = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
        schemeTerritory = territory,
        listIssueDateTime = Instant.parse("2026-01-01T00:00:00Z"),
        nextUpdate = Instant.parse("2027-01-01T00:00:00Z"),
        trustedEntities = emptyList(),
        pointersToOtherLoTE = pointers,
    )

    private companion object {
        const val ROOT_URI = "https://eu.example/eu-lotl.xml"
    }
}
