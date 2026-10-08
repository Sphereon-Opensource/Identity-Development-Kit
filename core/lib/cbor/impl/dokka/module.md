# Module lib-cbor-impl

Runtime CBOR encoder and decoder for the data model declared in `lib-cbor-public`. It handles canonical encoding, tagged values, and indefinite-length items, the combination required by ISO 18013-5 mdoc and COSE. Pull it in whenever code needs to actually serialize or parse CBOR rather than just describe its shape.

Downstream consumers get a working CBOR facade as soon as the module is on the classpath.
