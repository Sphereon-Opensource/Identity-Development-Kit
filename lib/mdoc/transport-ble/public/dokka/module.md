# Module lib-mdoc-transport-ble-public

Public surface of the BLE transport for mdoc (ISO 18013-5 device retrieval): the central-service and connection-method contracts, the data-channel model, and the event-dispatcher that higher layers subscribe to.

Depend on this module from engagement-layer code that has to speak the BLE transport contract without committing to a specific runtime. The runtime lives in `lib-mdoc-transport-ble-impl`.
