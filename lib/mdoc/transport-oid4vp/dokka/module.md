# Module lib-mdoc-transport-oid4vp

OID4VP binding that carries an mdoc exchange over the ISO 18013-7 online flow. It adapts the verifier-initiated OID4VP request onto the mdoc transport surface, and maps DCQL queries into mdoc device-requests so the same wallet and reader code can participate in either the proximity (18013-5) or online (18013-7) scenario.

Reach for this module when the verifier is reaching the wallet over the web rather than over BLE or NFC.
