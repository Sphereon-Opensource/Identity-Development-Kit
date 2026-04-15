# Module lib-mdoc-transport-ble-impl

Runtime for the mdoc BLE transport surface declared in `lib-mdoc-transport-ble-public`. It is the central-role (reader-side) implementation that turns the platform-neutral BLE primitives from `lib-data-link-ble-public` into the ISO 18013-5 device-retrieval transport.

On mobile, pair with the mobile BLE stack; in tests, the BLE test fixtures under `lib-data-link-ble-*` let this module run without a real adapter.
