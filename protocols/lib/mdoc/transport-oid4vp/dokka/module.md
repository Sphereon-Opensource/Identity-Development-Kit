# Module lib-mdoc-transport-oid4vp

OID4VP binding that carries an mdoc exchange over the ISO 18013-7 online flow. It adapts the verifier-initiated OID4VP request onto the mdoc transport surface and implements the Annex B restricted Presentation Exchange profile. The regular OpenID4VP/DCQL wallet route is separate and is not reinterpreted by this transport, while the same wallet can still support both the proximity (18013-5) and online (18013-7) scenarios.

Reach for this module when the verifier is reaching the wallet over the web rather than over BLE or NFC.
