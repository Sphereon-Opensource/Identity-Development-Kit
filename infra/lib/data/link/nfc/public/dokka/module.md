# Module lib-data-link-nfc-public

Public NFC transport primitives shared by higher-level flows: command APDUs, NDEF messages and records, and the handover request / select records used to hand an NFC-initiated session off to BLE. Platform-neutral model only; runtime dispatch lives in `lib-data-link-nfc-impl`.

Depend on this module from code that needs to speak NFC without binding to a specific platform driver.
