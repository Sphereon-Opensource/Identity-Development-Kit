# Module lib-mdoc-reader

Reader-side of the mdoc exchange. It covers the engagement manager, the device-request builder, and the reader connection surface that a verifying application uses to consume an mdoc presentation, in both the ISO 18013-5 proximity scenario and the -7 online scenario.

Pair with the appropriate transport module (`lib-mdoc-transport-ble`, `-nfc`, `-oid4vp`, `-restapi`) depending on how the reader is being deployed.
