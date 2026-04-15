# Module lib-mdoc-datatransfer-impl

Runtime for the mdoc engagement contracts in `lib-mdoc-datatransfer-public`. It orchestrates a device-engagement instance end to end: picking the right transport (BLE, NFC, OID4VP, REST), assembling the engagement payload on the holder side, and dispatching lifecycle events through the internal mdoc event hub so higher-level UI and logging can follow along.

Pair with one or more `lib-mdoc-transport-*` modules to provide the actual transports.
