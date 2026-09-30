package com.sphereon.catalog.eu.testutil

import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import java.io.File
import java.io.InputStream
import java.io.Reader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

/**
 * Validates catalogue documents against the official 1.0.1 XSDs kept next to the test fixtures. The schemas import the W3C
 * xml.xsd and xmldsig schemas by URL; those imports are answered from local stand-ins in the same directory.
 */
class CatalogueXsd(
    private val directory: File,
) {
    val coa: Schema = load("catalogue_of_attributes.xsd")
    val attribute: Schema = load("catalogue_attribute.xsd")
    val cos: Schema = load("catalogue_of_schemes.xsd")
    val scheme: Schema = load("catalogue_scheme.xsd")

    private fun load(root: String): Schema {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        factory.resourceResolver = LocalResolver(directory)
        return factory.newSchema(StreamSource(File(directory, root)))
    }

    /** Validation errors of [xml] against [schema]; empty when the document is valid. */
    fun errors(
        schema: Schema,
        xml: ByteArray,
    ): List<String> {
        val problems = mutableListOf<String>()
        val validator = schema.newValidator()
        validator.errorHandler =
            object : ErrorHandler {
                override fun warning(e: SAXParseException) = Unit

                override fun error(e: SAXParseException) {
                    problems += "line ${e.lineNumber}: ${e.message}"
                }

                override fun fatalError(e: SAXParseException) {
                    problems += "line ${e.lineNumber}: ${e.message}"
                }
            }
        try {
            validator.validate(StreamSource(xml.inputStream()))
        } catch (e: SAXParseException) {
            // already recorded by the handler
        }
        return problems
    }

    private class LocalResolver(
        private val directory: File,
    ) : LSResourceResolver {
        override fun resolveResource(
            type: String?,
            namespaceURI: String?,
            publicId: String?,
            systemId: String?,
            baseURI: String?,
        ): LSInput? {
            val name = systemId?.substringAfterLast('/') ?: return null
            val file = File(directory, name)
            if (!file.exists()) return null
            return object : LSInput {
                override fun getCharacterStream(): Reader? = null

                override fun setCharacterStream(characterStream: Reader?) = Unit

                override fun getByteStream(): InputStream = file.inputStream()

                override fun setByteStream(byteStream: InputStream?) = Unit

                override fun getStringData(): String? = null

                override fun setStringData(stringData: String?) = Unit

                override fun getSystemId(): String = file.toURI().toString()

                override fun setSystemId(systemId: String?) = Unit

                override fun getPublicId(): String? = publicId

                override fun setPublicId(publicId: String?) = Unit

                override fun getBaseURI(): String? = baseURI

                override fun setBaseURI(baseURI: String?) = Unit

                override fun getEncoding(): String? = null

                override fun setEncoding(encoding: String?) = Unit

                override fun getCertifiedText(): Boolean = false

                override fun setCertifiedText(certifiedText: Boolean) = Unit
            }
        }
    }

    companion object {
        fun forTestResources(root: String): CatalogueXsd = CatalogueXsd(File(root, "eu-catalogues/xsd"))
    }
}
