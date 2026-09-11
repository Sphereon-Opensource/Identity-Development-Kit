/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.rdfcanon

/** RDF 1.1 terms accepted by the RDFC-1.0 input model. */
sealed interface RdfTerm

sealed interface RdfSubject : RdfTerm
sealed interface RdfObject : RdfTerm
sealed interface RdfGraphName : RdfTerm

data class RdfIri(val value: String) : RdfSubject, RdfObject, RdfGraphName {
    init { require(value.isNotEmpty()) { "An RDF IRI must not be empty" } }
}

data class RdfBlankNode(val identifier: String) : RdfSubject, RdfObject, RdfGraphName {
    init {
        require(identifier.isNotEmpty()) { "An RDF blank-node identifier must not be empty" }
        require(!identifier.contains(' ')) { "An RDF blank-node identifier must not contain spaces" }
    }
}

/** A literal whose datatype is omitted is the RDF simple/xsd:string form. */
data class RdfLiteral(
    val lexicalForm: String,
    val language: String? = null,
    val datatype: String? = null,
) : RdfObject {
    init {
        require(language == null || datatype == null) { "A literal cannot have both language and datatype" }
        require(language == null || language.isNotEmpty()) { "A language tag must not be empty" }
        require(datatype == null || datatype.isNotEmpty()) { "A literal datatype must not be empty" }
    }
}

/**
 * RDF-star quoted triples are represented so callers can detect them, but are
 * rejected by the RDFC-1.0 canonicalizer: RDFC-1.0 is defined over RDF 1.1
 * datasets and does not define a quoted-triple canonical form.
 */
data class RdfQuotedTriple(
    val subject: RdfSubject,
    val predicate: RdfIri,
    val objectTerm: RdfObject,
) : RdfSubject, RdfObject

data class RdfQuad(
    val subject: RdfSubject,
    val predicate: RdfIri,
    val objectTerm: RdfObject,
    val graphName: RdfGraphName? = null,
)

/** An RDF dataset is a set of quads; duplicate concrete statements are ignored. */
data class RdfDataset(val quads: List<RdfQuad>) {
    val distinctQuads: List<RdfQuad> get() = quads.distinct()
}

object RdfVocab {
    const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
}
