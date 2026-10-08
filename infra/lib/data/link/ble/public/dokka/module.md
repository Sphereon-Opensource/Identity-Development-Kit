# Module lib-data-link-ble-public

Public Bluetooth Low Energy transport primitives: the error and event model, permission helpers, response envelope, and state-guard used by proximity flows. It is a platform-neutral surface; the actual BLE stack lives behind platform-specific code under the mdoc BLE transport and under Compose / mobile app glue.

Depend on this module from any code that needs to speak the IDK BLE event vocabulary (mdoc device-retrieval flows, wallet proximity features) without binding to a specific platform.
