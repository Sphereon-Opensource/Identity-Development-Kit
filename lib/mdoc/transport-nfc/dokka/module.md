# Module lib-mdoc-transport-nfc

NFC engagement and handover for mdoc (ISO 18013-5). It builds the handover-select message a reader presents over NFC, handles the associated CBOR codecs, and maps the handover payload into the BLE connection method used for the actual data exchange. In other words: NFC here is the rendezvous, not the data transport.

Pair with `lib-mdoc-transport-ble` (or another transport referenced in the handover) to do the actual retrieval.
