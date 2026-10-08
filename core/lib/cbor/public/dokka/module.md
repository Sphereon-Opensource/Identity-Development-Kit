# Module lib-cbor-public

Public CBOR data model (RFC 8949) shared by every IDK module that speaks a binary format, primarily ISO 18013-5 mdoc and COSE. It defines only the item hierarchy and related types; no runtime encoder or decoder lives here.

Depend on this module when you need to build or pattern-match CBOR values in a platform-agnostic way without pulling in the codec. Runtime encoding and decoding is provided by `lib-cbor-impl`.
