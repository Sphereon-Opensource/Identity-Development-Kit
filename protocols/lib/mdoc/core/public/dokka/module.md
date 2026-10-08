# Module lib-mdoc-core-public

Public data model for ISO/IEC 18013-5 mdoc, the mobile driving licence and more general mdoc document formats. It defines the data-element model, device-authentication types, item-request shapes, and the codec contracts the rest of the mdoc stack relies on.

Depend on this module from code that has to describe mdoc payloads without coupling to the CBOR codec. Runtime implementations live in `lib-mdoc-core-impl`; the transport and engagement layers live under `lib-mdoc-transport-*` and `lib-mdoc-datatransfer-*`.
