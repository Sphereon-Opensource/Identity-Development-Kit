package com.sphereon.jsonld.processor

import com.sphereon.core.api.Ok
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.WellKnownContexts
import com.sphereon.jsonld.loader.BuiltInContextLinkedDataDocumentLoader
import com.sphereon.jsonld.loader.DefaultBuiltInContextRegistry
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.rdfcanon.RdfBlankNode
import com.sphereon.jsonld.rdfcanon.RdfIri
import com.sphereon.jsonld.rdfcanon.RdfLiteral
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonLdProcessorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun expandsFullIriAndContextTerms() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"name":"http://schema.org/name"},"@id":"http://example.test/alice","name":"Alice"}""",
        )
        val expanded = processor().expand(input)
        assertEquals(
            """[{"@id":"http://example.test/alice","http://schema.org/name":[{"@value":"Alice"}]}]""",
            expanded.toString(),
        )
    }

    @Test
    fun expandsOrdinaryAndAliasedNestByMergingNestedProperties() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"name":"https://example.test/name","age":"https://example.test/age","details":"@nest","n":"@nest"},"name":"top","details":{"name":["nested-1","nested-2"]},"n":{"age":3}}""",
        )

        assertEquals(
            """[{"https://example.test/name":[{"@value":"top"},{"@value":"nested-1"},{"@value":"nested-2"}],"https://example.test/age":[{"@value":3}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun expandsNestArrayByAccumulatingEachNestedPropertyMap() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"name":"https://example.test/name","n":"@nest"},"n":[{"name":"Alice"},{"name":"Bob"}]}""",
        )

        assertEquals(
            """[{"https://example.test/name":[{"@value":"Alice"},{"@value":"Bob"}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun rejectsMixedNonObjectNestArrayMembersAndValueObjectMembers() = runTest {
        assertFailsWith<JsonLdProcessingException> {
            processor().expand(json.parseToJsonElement(
                """{"@context":{"name":"https://example.test/name","n":"@nest"},"n":[{"name":"Alice"},"not-a-map"]}""",
            ))
        }
        assertFailsWith<JsonLdProcessingException> {
            processor().expand(json.parseToJsonElement(
                """{"@context":{"n":"@nest"},"n":[{"@value":"not-a-property-map"}]}""",
            ))
        }
        assertFailsWith<JsonLdProcessingException> {
            processor().expand(json.parseToJsonElement(
                """{"@context":{"n":"@nest","value":"@value"},"n":[{"value":"not-a-property-map"}]}""",
            ))
        }
    }

    @Test
    fun termDefinitionNestRemainsExpansionTransparent() = runTest {
        // A term-level @nest mapping controls compaction placement. During
        // expansion, the aliased @nest property is semantically transparent
        // and the term's mapped IRI remains the expanded property key.
        val input = json.parseToJsonElement(
            """{"@context":{"labels":"@nest","name":{"@id":"https://example.test/name","@nest":"labels"}},"labels":{"name":"Alice"}}""",
        )

        assertEquals(
            """[{"https://example.test/name":[{"@value":"Alice"}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun rejectsValueObjectUsedAsNestedPropertyValue() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"labels":"@nest","name":{"@id":"https://example.test/name","@nest":"labels"}},"labels":{"@value":"not a property map"}}""",
        )

        assertFailsWith<JsonLdProcessingException> {
            processor().expand(input)
        }
    }

    @Test
    fun rejectsValueObjectAliasUsedAsNestedPropertyValue() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"labels":"@nest","value":"@value","name":{"@id":"https://example.test/name","@nest":"labels"}},"labels":{"value":"not a property map"}}""",
        )

        assertFailsWith<JsonLdProcessingException> {
            processor().expand(input)
        }
    }

    @Test
    fun nestMergesSetWrappedPropertiesWithoutLeakingSetKeyword() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"name":"https://example.test/name","n":"@nest"},"n":{"@set":{"name":"Alice"}}}""",
        )

        assertEquals(
            """[{"https://example.test/name":[{"@value":"Alice"}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun setAcceptsScalarStringNumberAndBooleanValues() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"name":"https://example.test/name","count":"https://example.test/count","enabled":"https://example.test/enabled","s":"@set"},"name":{"s":"Alice"},"count":{"s":2},"enabled":{"s":true}}""",
        )

        assertEquals(
            """[{"https://example.test/name":[{"@value":"Alice"}],"https://example.test/count":[{"@value":2}],"https://example.test/enabled":[{"@value":true}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun setAcceptsNullAndArrayValues() = runTest {
        val nullInput = json.parseToJsonElement(
            """{"@context":{"name":"https://example.test/name","s":"@set"},"name":{"s":null}}""",
        )
        assertEquals("[]", processor().expand(nullInput).toString())

        val arrayInput = json.parseToJsonElement(
            """{"@context":{"name":"https://example.test/name","s":"@set"},"name":{"s":["Alice",2,false]}}""",
        )
        assertEquals(
            """[{"https://example.test/name":[{"@value":"Alice"},{"@value":2},{"@value":false}]}]""",
            processor().expand(arrayInput).toString(),
        )
    }

    @Test
    fun setAcceptsNodeAndValueObjects() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"person":"https://example.test/person","label":"https://example.test/label","s":"@set"},"person":{"s":{"@id":"https://example.test/alice"}},"label":{"s":{"@value":"Alice"}}}""",
        )

        assertEquals(
            """[{"https://example.test/person":[{"@id":"https://example.test/alice"}],"https://example.test/label":[{"@value":"Alice"}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun nestHonorsScopedNonPropagatingContext() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"n":"@nest","name":"https://outer.example/name","child":"https://outer.example/child"},"n":{"@context":{"@propagate":false,"name":"https://inner.example/name"},"name":"parent","child":{"name":"nested"}}}""",
        )

        val expanded = processor().expand(input).toString()
        assertTrue(expanded.contains("https://inner.example/name"), expanded)
        assertTrue(expanded.contains("https://outer.example/name"), expanded)
        assertTrue(!expanded.contains("https://inner.example/name\":[{\"@value\":\"nested"), expanded)
    }

    @Test
    fun rejectsInvalidNestValuesAndConflictingNestDefinitions() = runTest {
        assertFailsWith<JsonLdProcessingException> {
            processor().expand(json.parseToJsonElement(
                """{"@context":{"n":"@nest"},"n":"not-an-object"}""",
            ))
        }
        assertFailsWith<JsonLdProcessingException> {
            processor().expand(json.parseToJsonElement(
                """{"@context":{"nested":"@nest","parent":{"@reverse":"https://example.test/parent","@nest":"nested"}}}""",
            ))
        }
        assertFailsWith<JsonLdProcessingException> {
            processor().expand(json.parseToJsonElement(
                """{"@context":{"n":{"@nest":"not-an-alias"}}}""",
            ))
        }
    }

    @Test
    fun emitsRdfBlankNodeAndTypedValues() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"ex":"https://example.test/","age":{"@id":"ex:age","@type":"http://www.w3.org/2001/XMLSchema#integer"}},"age":42}""",
        )
        val dataset = processor().toRdf(input)
        assertEquals(1, dataset.quads.size)
        val quad = dataset.quads.single()
        assertTrue(quad.subject is RdfBlankNode)
        assertEquals(RdfIri("https://example.test/age"), quad.predicate)
        assertEquals(RdfLiteral("42", datatype = "http://www.w3.org/2001/XMLSchema#integer"), quad.objectTerm)
    }

    @Test
    fun emitsListAndReverseProperties() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"ex":"https://example.test/","member":{"@id":"ex:member","@container":"@list"},"parent":{"@reverse":"ex:child"}},"@id":"ex:group","member":["a","b"],"parent":{"@id":"ex:p"}}""",
        )
        val dataset = processor().toRdf(input)
        assertEquals(6, dataset.quads.size)
        assertTrue(dataset.quads.any { it.predicate.value == "https://example.test/child" && it.subject == RdfIri("https://example.test/p") })
        assertTrue(dataset.quads.any { it.predicate.value.endsWith("#first") })
    }

    @Test
    fun resolvesRemoteContextThroughLoader() = runTest {
        val remote = buildJsonObject { put("@context", buildJsonObject { put("name", "https://example.test/name") }) }
        val loader = LinkedDataDocumentLoader { iri -> Ok(LinkedDataDocument(iri, remote)) }
        val input = json.parseToJsonElement("""{"@context":"https://example.test/context","name":"Alice"}""")
        val expanded = JsonLdProcessor(loader).expand(input)
        assertTrue(expanded.toString().contains("https://example.test/name"))
    }

    @Test
    fun importsObjectContextBeforeLocalDefinitionsAndAllowsLocalOverride() = runTest {
        val imported = buildJsonObject {
            put("@context", buildJsonObject {
                put("@vocab", "https://import.example/vocab#")
                put("name", "imported-name")
                put("onlyImport", "only-import")
            })
        }
        val loader = LinkedDataDocumentLoader { iri ->
            assertEquals("https://example.test/import.jsonld", iri)
            Ok(LinkedDataDocument(iri, imported))
        }

        val input = json.parseToJsonElement(
            """{"@context":{"@import":"https://example.test/import.jsonld","name":"https://local.example/name"},"name":"Alice","onlyImport":"yes"}""",
        )

        assertEquals(
            """[{"https://local.example/name":[{"@value":"Alice"}],"https://import.example/vocab#only-import":[{"@value":"yes"}]}]""",
            JsonLdProcessor(loader).expand(input).toString(),
        )
    }

    /**
     * Pinned to the W3C JSON-LD 1.1 processor fixtures expand/so09-in.jsonld
     * and expand/so09-context.jsonld. The local @vocab is part of the merged
     * context, so the imported term's relative @id uses the local vocabulary.
     */
    @Test
    fun officialSourcedContextLocalVocabOverridesImportedVocab() = runTest {
        val loader = LinkedDataDocumentLoader { iri ->
            assertEquals("https://w3c.github.io/json-ld-api/tests/expand/so09-context.jsonld", iri)
            Ok(LinkedDataDocument(iri, json.parseToJsonElement(
                """{"@context":{"@vocab":"http://example.org/source/","term":{"@id":"term"}}}""",
            )))
        }
        val input = json.parseToJsonElement(
            """{"@context":{"@version":1.1,"@import":"so09-context.jsonld","@vocab":"http://example.org/redefined/"},"term":"value"}""",
        )

        assertEquals(
            """[{"http://example.org/redefined/term":[{"@value":"value"}]}]""",
            JsonLdProcessor(loader).expand(
                input,
                JsonLdProcessingOptions(baseIri = "https://w3c.github.io/json-ld-api/tests/expand/so09-in.jsonld"),
            ).toString(),
        )
    }

    /** Pinned to W3C expand/so11-in.jsonld and expand/so11-out.jsonld. */
    @Test
    fun officialSourcedContextWrapperProtectionAllowsItsLocalOverride() = runTest {
        val loader = LinkedDataDocumentLoader { iri ->
            assertEquals("https://w3c.github.io/json-ld-api/tests/expand/so08-context.jsonld", iri)
            Ok(LinkedDataDocument(iri, json.parseToJsonElement(
                """{"@context":{"term":"http://example.org/sourced"}}""",
            )))
        }
        val input = json.parseToJsonElement(
            """{"@context":{"@version":1.1,"@protected":true,"@import":"so08-context.jsonld","term":"http://example.org/redefined"},"term":"value"}""",
        )

        assertEquals(
            """[{"http://example.org/redefined":[{"@value":"value"}]}]""",
            JsonLdProcessor(loader).expand(
                input,
                JsonLdProcessingOptions(baseIri = "https://w3c.github.io/json-ld-api/tests/expand/so11-in.jsonld"),
            ).toString(),
        )
    }

    /** Negative source-context cases pinned to W3C expand/so10, so12, and so13. */
    @Test
    fun officialSourcedContextNegativeFixturesRemainRejected() = runTest {
        val loader = LinkedDataDocumentLoader { iri ->
            when (iri) {
                "https://w3c.github.io/json-ld-api/tests/expand/so10-context.jsonld" -> Ok(
                    LinkedDataDocument(iri, json.parseToJsonElement(
                        """{"@context":{"term":"http://example.org/protected"}}""",
                    )),
                )
                "https://w3c.github.io/json-ld-api/tests/expand/so12-in.jsonld" -> Ok(
                    LinkedDataDocument(iri, json.parseToJsonElement(
                        """{"@context":{"@import":"so12-in.jsonld"}}""",
                    )),
                )
                "https://w3c.github.io/json-ld-api/tests/expand/so13-context.jsonld" -> Ok(
                    LinkedDataDocument(iri, json.parseToJsonElement(
                        """{"@context":[{"term":"http://example.org/term"},{"term2":"http://example.org/term2"}]}""",
                    )),
                )
                else -> error("unexpected W3C fixture URL: $iri")
            }
        }
        val base = "https://w3c.github.io/json-ld-api/tests/expand/so10-in.jsonld"
        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(loader).expand(
                json.parseToJsonElement(
                    """{"@context":[{"@version":1.1,"@protected":true,"@import":"so10-context.jsonld"},{"term":"http://example.org/unprotected"}],"term":"value"}""",
                ),
                JsonLdProcessingOptions(baseIri = base),
            )
        }

        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(loader).expand(
                json.parseToJsonElement("""{"@context":{"@import":"so12-in.jsonld"}}"""),
                JsonLdProcessingOptions(baseIri = "https://w3c.github.io/json-ld-api/tests/expand/so12-in.jsonld"),
            )
        }

        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(loader).expand(
                json.parseToJsonElement("""{"@context":{"@import":"so13-context.jsonld"},"term":"value"}"""),
                JsonLdProcessingOptions(baseIri = "https://w3c.github.io/json-ld-api/tests/expand/so13-in.jsonld"),
            )
        }
    }

    /** Context and @import URLs resolve against the base, never term mappings. */
    @Test
    fun contextAndImportUrlsBypassTermAndVocabMappings() = runTest {
        val expected = "https://example.test/docs/mapped"
        val imported = buildJsonObject { put("@context", buildJsonObject { put("name", "https://example.test/name") }) }
        val loader = LinkedDataDocumentLoader { iri ->
            assertEquals(expected, iri)
            Ok(LinkedDataDocument(iri, imported))
        }
        val input = json.parseToJsonElement(
            """{"@context":{"mapped":"https://mapped.example/","@import":"mapped"},"name":"Alice"}""",
        )

        assertTrue(JsonLdProcessor(loader).expand(
            input,
            JsonLdProcessingOptions(baseIri = "https://example.test/docs/input.jsonld"),
        ).toString().contains("https://example.test/name"))

        val remoteContextInput = json.parseToJsonElement(
            """{"@context":[{"mapped":"https://mapped.example/"},"mapped"],"name":"Alice"}""",
        )
        assertTrue(JsonLdProcessor(loader).expand(
            remoteContextInput,
            JsonLdProcessingOptions(baseIri = "https://example.test/docs/input.jsonld"),
        ).toString().contains("https://example.test/name"))
    }

    @Test
    fun rejectsImportedContextThatIsNotAnObjectOrContainsImport() = runTest {
        val arrayImport = LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, buildJsonObject {
                put("@context", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject { put("name", "https://example.test/name") })))
            }))
        }
        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(arrayImport).expand(json.parseToJsonElement("""{"@context":{"@import":"https://example.test/import"}}"""))
        }

        val recursiveImport = LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, buildJsonObject {
                put("@context", buildJsonObject { put("@import", "https://example.test/other") })
            }))
        }
        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(recursiveImport).expand(json.parseToJsonElement("""{"@context":{"@import":"https://example.test/import"}}"""))
        }
    }

    @Test
    fun rejectsProtectedImportedRedefinitionAndProtectedContextNullification() = runTest {
        val loader = LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, buildJsonObject {
                put("@context", buildJsonObject {
                    put("@protected", true)
                    put("name", "https://example.test/name")
                })
            }))
        }
        assertTrue(JsonLdProcessor(loader).expand(json.parseToJsonElement(
            """{"@context":{"@import":"https://example.test/import","name":"https://example.test/other"},"name":"Alice"}""",
        )).toString().contains("https://example.test/other"))

        val unprotectedImport = LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, buildJsonObject {
                put("@context", buildJsonObject { put("name", "https://example.test/name") })
            }))
        }
        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(unprotectedImport).expand(json.parseToJsonElement(
                """{"@context":[{"@protected":true,"@import":"https://example.test/import"},{"name":"https://example.test/other"}],"name":"Alice"}""",
            ))
        }

        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(processorLoader()).expand(json.parseToJsonElement(
                """{"@context":[{"@protected":true,"name":"https://example.test/name"},null]}""",
            ))
        }

        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(processorLoader()).expand(json.parseToJsonElement(
                """{"@context":[{"@protected":true,"missing":{"@id":null}},{"missing":"https://example.test/redefined"}]}""",
            ))
        }

        val equivalentNullDefinition = JsonLdProcessor(processorLoader()).expand(json.parseToJsonElement(
            """{"@context":[{"@protected":true,"missing":{"@id":null}},{"missing":null}],"missing":"ignored"}""",
        ))
        assertTrue(equivalentNullDefinition.isEmpty())
    }

    @Test
    fun importedContextProtectionCanBeExplicitlyDisabledByContainingContext() = runTest {
        val loader = LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, buildJsonObject {
                put("@context", buildJsonObject {
                    put("@protected", true)
                    put("name", "https://example.test/imported-name")
                })
            }))
        }

        val input = json.parseToJsonElement(
            """{"@context":{"@import":"https://example.test/import","@protected":false,"name":"https://example.test/local-name"},"name":"Alice"}""",
        )

        assertTrue(JsonLdProcessor(loader).expand(input).toString().contains("https://example.test/local-name"))
    }

    @Test
    fun importedPropagationCanBeExplicitlyInheritedByContainingContext() = runTest {
        val loader = LinkedDataDocumentLoader { iri ->
            Ok(LinkedDataDocument(iri, buildJsonObject {
                put("@context", buildJsonObject {
                    put("@propagate", false)
                    put("name", "https://example.test/name")
                })
            }))
        }
        val input = json.parseToJsonElement(
            """{"@context":{"@import":"https://example.test/import","child":"https://example.test/child"},"name":"root","child":{"name":"nested"}}""",
        )

        val expanded = JsonLdProcessor(loader).expand(input).toString()
        assertEquals(1, expanded.split("https://example.test/name").size - 1)
    }

    @Test
    fun nonPropagatingScopedContextRevertsForNestedNode() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"@vocab":"https://outer.example/","name":"https://outer.example/name","child":"https://outer.example/child"},"child":{"@context":{"@propagate":false,"name":"https://inner.example/name"},"name":"parent","child":{"name":"nested"}}}""",
        )

        val expanded = processor().expand(input).toString()
        assertTrue(expanded.contains("https://inner.example/name"), expanded)
        assertTrue(expanded.contains("https://outer.example/name"), expanded)
        assertTrue(!expanded.contains("""https://inner.example/name":[{"@value":"nested"}]"""), expanded)
    }

    @Test
    fun propertyScopedContextMayOverrideProtectedTerm() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"@protected":true,"name":"https://example.test/name","child":{"@id":"https://example.test/child","@context":{"name":"https://example.test/other"}}},"child":{"name":"Alice"}}""",
        )

        assertTrue(processor().expand(input).toString().contains("https://example.test/other"))
    }

    @Test
    fun emitsNamedGraph() = runTest {
        val input = json.parseToJsonElement(
            """{"@id":"https://example.test/graph","@graph":[{"@id":"https://example.test/s","https://example.test/p":"v"}]}""",
        )
        val dataset = processor().toRdf(input)
        assertEquals(1, dataset.quads.size)
        assertEquals(RdfIri("https://example.test/graph"), dataset.quads.single().graphName)
    }

    @Test
    fun preservesDirectionAndJsonLiteralDatatypes() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"label":{"@id":"https://example.test/label"},"payload":{"@id":"https://example.test/payload","@type":"@json"}},"label":{"@value":"Hallo","@language":"nl","@direction":"ltr"},"payload":{"@value":{"b":1,"a":2},"@type":"@json"}}""",
        )
        val dataset = processor().toRdf(input)
        assertTrue(dataset.quads.any { it.predicate.value.endsWith("/label") && (it.objectTerm as RdfLiteral).datatype == "https://www.w3.org/ns/i18n#nl_ltr" })
        assertTrue(dataset.quads.any { it.predicate.value.endsWith("/payload") && (it.objectTerm as RdfLiteral).datatype == "http://www.w3.org/1999/02/22-rdf-syntax-ns#JSON" })
    }

    @Test
    fun failsClosedOnDepthLimit() = runTest {
        val input = json.parseToJsonElement("""{"@context":{"ex":"https://example.test/"},"ex:p":{"ex:q":{"ex:r":"x"}}}""")
        assertFailsWith<JsonLdProcessingException> {
            JsonLdProcessor(processorLoader(), JsonLdProcessingLimits(maxDepth = 1)).expand(input)
        }
    }

    @Test
    fun activatesTypeScopedContextRegardlessOfTypeMemberOrder() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"ex":"https://example.test/","Thing":{"@id":"ex:Thing","@context":{"name":"ex:name"}},"type":"@type"},"name":"Alice","type":"Thing"}""",
        )

        assertEquals(
            """[{"https://example.test/name":[{"@value":"Alice"}],"@type":["https://example.test/Thing"]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun typeScopedContextDoesNotPropagateUnlessRequested() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"@vocab":"https://outer.example/","child":"https://outer.example/child","Thing":{"@id":"https://outer.example/Thing","@context":{"@vocab":"https://inner.example/","name":"https://inner.example/name"}}},"@type":"Thing","child":{"name":"nested"}}""",
        )

        val expanded = processor().expand(input).toString()
        assertTrue(expanded.contains("https://outer.example/name"), expanded)
        assertTrue(!expanded.contains("https://inner.example/name"), expanded)
    }

    @Test
    fun appliesPropertyScopedContextToNestedValue() = runTest {
        val input = json.parseToJsonElement(
            """{"@context":{"ex":"https://example.test/","person":{"@id":"ex:person","@context":{"name":"ex:name"}}},"person":{"name":"Alice"}}""",
        )

        assertEquals(
            """[{"https://example.test/person":[{"https://example.test/name":[{"@value":"Alice"}]}]}]""",
            processor().expand(input).toString(),
        )
    }

    @Test
    fun emitsRdfUsingBundledVcOneAndTwoContexts() = runTest {
        for (contextIri in listOf(WellKnownContexts.VCDM_1_1, WellKnownContexts.VCDM_2_0)) {
            val input = json.parseToJsonElement(
                """{"@context":"$contextIri","id":"https://example.test/credential/1","type":["VerifiableCredential"],"issuer":"did:example:issuer","credentialSubject":{"id":"did:example:subject"}}""",
            )
            val dataset = bundledProcessor().toRdf(input)

            assertTrue(dataset.quads.any { it.predicate.value == "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" && it.objectTerm == RdfIri("https://www.w3.org/2018/credentials#VerifiableCredential") })
            assertTrue(dataset.quads.any { it.predicate.value.endsWith("#issuer") && it.objectTerm == RdfIri("did:example:issuer") })
            assertTrue(dataset.quads.any { it.predicate.value.endsWith("#credentialSubject") && it.objectTerm == RdfIri("did:example:subject") })
        }
    }

    /** W3C JSON-LD 1.1 expand/td-prefix-* negative term-definition cases. */
    @Test
    fun rejectsInvalidPrefixFlagsAndPrefixTermShapes() = runTest {
        val invalidContexts = listOf(
            """{"@context":{"ex":{"@id":"https://example.test/","@prefix":"true"}}}""",
            """{"@context":{"ex:term":{"@id":"https://example.test/term","@prefix":true}}}""",
            """{"@context":{"ex/term":{"@id":"https://example.test/term","@prefix":true}}}""",
            """{"@context":{"keyword":{"@id":"@type","@prefix":true}}}""",
        )

        invalidContexts.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    /** W3C JSON-LD 1.1 expand/td-reverse-* negative term-definition cases. */
    @Test
    fun rejectsReverseTermConflictsAndUnsupportedReverseContainers() = runTest {
        val invalidContexts = listOf(
            """{"@context":{"parent":{"@reverse":"https://example.test/parent","@id":"https://example.test/id"}}}""",
            """{"@context":{"parent":{"@reverse":"https://example.test/parent","@nest":"nested"}}}""",
            """{"@context":{"parent":{"@reverse":"https://example.test/parent","@container":"@language"}}}""",
            """{"@context":{"parent":{"@reverse":"https://example.test/parent","@container":"@list"}}}""",
            """{"@context":{"parent":{"@reverse":"https://example.test/parent","@container":["@set","@index"]}}}""",
            """{"@context":{"parent":{"@reverse":"@type"}}}""",
            """{"@context":{"parent":{"@reverse":42}}}""",
        )

        invalidContexts.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    /** W3C JSON-LD 1.1 expand/td-container-* negative term-definition cases. */
    @Test
    fun rejectsInvalidContainerValuesAndCombinations() = runTest {
        val invalidContexts = listOf(
            """{"@context":{"value":{"@id":"https://example.test/value","@container":"@unknown"}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":["@set","@set"]}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":["@index","@language"]}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":["@graph","@type"]}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":["@graph","@id","@index"]}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":["@id","@language"]}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":["@set","@list"]}}}""",
        )

        invalidContexts.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    /** W3C JSON-LD 1.1 expand/td-type-* negative term-definition cases. */
    @Test
    fun rejectsInvalidTypeMappingsAndTypeLanguageDirectionConflicts() = runTest {
        val invalidContexts = listOf(
            """{"@context":{"value":{"@id":"https://example.test/value","@type":true}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@type":"@unknown"}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@type":"relative"}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@type":"@id","@language":"en"}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@type":"@id","@direction":"ltr"}}}""",
            """{"@context":{"value":{"@id":"https://example.test/value","@container":"@type","@type":"https://example.test/Type"}}}""",
        )

        invalidContexts.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    /** W3C JSON-LD 1.1 expand/td-direction-* negative term-definition cases. */
    @Test
    fun rejectsInvalidTermDirectionValues() = runTest {
        val invalidContexts = listOf(
            """{"@context":{"label":{"@id":"https://example.test/label","@direction":"sideways"}}}""",
            """{"@context":{"label":{"@id":"https://example.test/label","@direction":true}}}""",
        )

        invalidContexts.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    @Test
    fun rejectsInvalidValueObjectDirectionAndTypeCombination() = runTest {
        val invalidValues = listOf(
            """{"@context":{"label":"https://example.test/label"},"label":{"@value":"text","@direction":"sideways"}}""",
            """{"@context":{"label":"https://example.test/label"},"label":{"@value":"text","@direction":true}}""",
            """{"@context":{"label":"https://example.test/label"},"label":{"@value":"text","@direction":"ltr","@type":"https://example.test/Type"}}""",
        )

        invalidValues.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    /** W3C JSON-LD 1.1 expand/td-iri-* negative IRI-mapping cases. */
    @Test
    fun rejectsRelativeAndContextKeywordIriMappings() = runTest {
        val invalidContexts = listOf(
            """{"@context":{"value":{"@id":"relative"}}}""",
            """{"@context":{"value":{"@reverse":"relative"}}}""",
            """{"@context":{"value":{"@id":"@context"}}}""",
        )

        invalidContexts.forEach { source ->
            assertFailsWith<JsonLdProcessingException> {
                processor().expand(json.parseToJsonElement(source))
            }
        }
    }

    private fun processor() = JsonLdProcessor(processorLoader())

    private fun bundledProcessor() = JsonLdProcessor(BuiltInContextLinkedDataDocumentLoader(DefaultBuiltInContextRegistry()))

    private fun processorLoader(): LinkedDataDocumentLoader = LinkedDataDocumentLoader { iri ->
        Ok(LinkedDataDocument(iri, JsonObject(emptyMap())))
    }
}
